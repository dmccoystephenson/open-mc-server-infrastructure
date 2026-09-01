"""Client for the minecraft-wrapper service (default port 8092).

The wrapper is the service that owns the Minecraft server process: it starts
and stops it, forwards console commands, hot-deploys plugin JARs, and replaces
the world.

Two things about its API are worth knowing before using this client.

**Only two endpoints are authenticated.** ``/api/plugins/deploy`` and
``/api/world/upload`` check an ``Authorization: Bearer`` token; everything
else on the wrapper — including ``stop`` and ``command`` — is unauthenticated.
The wrapper is meant to sit on an internal network behind the web app, never
exposed directly. Sending a token to the other endpoints is harmless but does
nothing.

**The lifecycle endpoints are asynchronous.** ``start``, ``stop``,
``restart`` and ``shutdown`` answer ``202 Accepted`` as soon as the work is
scheduled. The response means "accepted", not "done" — a graceful shutdown
takes 30+ seconds after it, and failures during the operation are logged
server-side rather than returned. Poll :meth:`MinecraftWrapperClient.status`
to find out what actually happened.
"""

from __future__ import annotations

import os
import time
from pathlib import Path
from typing import Optional, Union

from ._http import DEFAULT_TIMEOUTS, MultipartBody, Timeouts, Transport
from .errors import ConfirmationRequired, OmcsiError
from .models import AlertDestination, LogTail, ServerMetrics, ServerStatus

__all__ = ["MinecraftWrapperClient", "DisruptiveOperations"]

PathLike = Union[str, "os.PathLike[str]"]

DEFAULT_WRAPPER_URL = "http://localhost:8092"


class MinecraftWrapperClient:
    """Talks to one minecraft-wrapper instance.

    :param base_url: root URL of the wrapper, e.g. ``http://localhost:8092``.
        When OMCSI is deployed behind its nginx proxy, note that the proxy
        forwards ``/`` to the *web app*, not the wrapper — see the package
        README for which host to point this at.
    :param deploy_token: value of ``DEPLOY_AUTH_TOKEN`` on the wrapper.
        Required for :meth:`deploy_plugin` and
        :meth:`DisruptiveOperations.replace_world`, ignored elsewhere.
    :param timeouts: per-category timeouts; see :class:`~omcsi_client._http.Timeouts`.
    :param verify_tls: set ``False`` only for a deployment still using OMCSI's
        default self-signed nginx certificate.
    """

    def __init__(
        self,
        base_url: str = DEFAULT_WRAPPER_URL,
        *,
        deploy_token: Optional[str] = None,
        timeouts: Optional[Timeouts] = None,
        verify_tls: bool = True,
    ) -> None:
        self._transport = Transport(
            base_url,
            token=deploy_token,
            timeouts=timeouts or DEFAULT_TIMEOUTS,
            verify_tls=verify_tls,
        )
        self.disruptive = DisruptiveOperations(self)

    @property
    def base_url(self) -> str:
        return self._transport.base_url

    @property
    def timeouts(self) -> Timeouts:
        return self._transport.timeouts

    # -- reads --------------------------------------------------------------

    def status(self, *, timeout: Optional[float] = None) -> ServerStatus:
        """``GET /api/server/status`` — is the server process alive?"""
        payload = self._transport.json(
            "GET", "/api/server/status", timeout=timeout or self.timeouts.status
        )
        return ServerStatus.from_json(payload or {})

    def metrics(self, *, timeout: Optional[float] = None) -> ServerMetrics:
        """``GET /api/server/metrics`` — heap, process memory, uptime, TPS."""
        payload = self._transport.json(
            "GET", "/api/server/metrics", timeout=timeout or self.timeouts.status
        )
        return ServerMetrics.from_json(payload or {})

    def logs(self, lines: int = 100, *, timeout: Optional[float] = None) -> LogTail:
        """``GET /api/server/logs`` — the tail of the server log.

        The server clamps ``lines`` to ``[1, LOGS_DIAGNOSTIC_MAX_LINES]``, so
        asking for more than the deployment allows silently returns fewer
        rather than failing. Raises
        :class:`~omcsi_client.errors.OmcsiResponseError` with status 403 when
        the deployment has not set ``LOGS_DIAGNOSTIC_ENABLED=true``.
        """
        payload = self._transport.json(
            "GET",
            "/api/server/logs",
            params={"lines": lines},
            timeout=timeout or self.timeouts.status,
        )
        return LogTail.from_json(payload or {})

    def health(self, *, timeout: Optional[float] = None) -> dict:
        """``GET /actuator/health`` — Spring Boot's own health report."""
        return self._transport.json(
            "GET", "/actuator/health", timeout=timeout or self.timeouts.status
        ) or {}

    def wait_until_running(
        self,
        *,
        timeout: float = 300.0,
        poll_interval: float = 10.0,
        request_timeout: Optional[float] = None,
    ) -> ServerStatus:
        """Poll :meth:`status` until ``running`` is true, or give up.

        Transport failures are swallowed while waiting: a wrapper that is
        itself still starting refuses connections for a while, and treating
        that as fatal would make this useless in exactly the situation it
        exists for. A non-2xx *response* is also tolerated for the same
        reason. Raises :class:`~omcsi_client.errors.OmcsiError` on expiry.
        """
        deadline = time.monotonic() + timeout
        last_error: Optional[BaseException] = None
        while True:
            try:
                current = self.status(timeout=request_timeout)
                if current.running:
                    return current
            except OmcsiError as exc:
                last_error = exc
            if time.monotonic() + poll_interval >= deadline:
                break
            time.sleep(poll_interval)
        suffix = f" (last error: {last_error})" if last_error else ""
        raise OmcsiError(
            f"server did not report running within {timeout}s{suffix}"
        )

    # -- non-disruptive writes ---------------------------------------------

    def start_server(self, *, timeout: Optional[float] = None) -> str:
        """``POST /api/server/start`` — schedule a start; returns immediately.

        Raises :class:`~omcsi_client.errors.OmcsiConflictError` (HTTP 409) if
        the server is already running, which is usually a benign outcome
        rather than a failure.
        """
        return self._transport.text(
            "POST", "/api/server/start", timeout=timeout or self.timeouts.control
        )

    def send_message(
        self,
        text: str,
        destination: Union[AlertDestination, str] = AlertDestination.MINECRAFT,
        *,
        timeout: Optional[float] = None,
    ) -> str:
        """``POST /api/messages`` — broadcast a message via the alert manager.

        Fire-and-forget by design: the wrapper hands the message to the alert
        manager and swallows any failure there, so a 200 means the wrapper
        accepted the message, not that it was delivered.
        """
        if not text or not text.strip():
            raise ValueError("message text must not be blank")
        value = destination.value if isinstance(destination, AlertDestination) else str(destination)
        return self._transport.text(
            "POST",
            "/api/messages",
            json_body={"text": text, "destination": value},
            timeout=timeout or self.timeouts.control,
        )

    def deploy_plugin(
        self,
        jar_path: PathLike,
        *,
        plugin_name: Optional[str] = None,
        branch: Optional[str] = None,
        repo_url: Optional[str] = None,
        timeout: Optional[float] = None,
    ) -> str:
        """``POST /api/plugins/deploy`` — hot-deploy a plugin JAR.

        Requires a deploy token. The JAR is streamed off disk, not buffered
        into memory.

        ``plugin_name`` is the *destination* filename inside the server's
        plugins directory and defaults to the basename of ``jar_path``. It
        must end in ``.jar`` and must not contain path separators; reusing an
        existing filename replaces that plugin in place. Note the asymmetry
        this creates: deploying ``MyPlugin-1.2.3.jar`` without a
        ``plugin_name`` leaves a versioned filename behind, so the next
        version lands *alongside* it rather than replacing it, and the server
        loads both. Pass a stable ``plugin_name`` for anything you intend to
        upgrade.

        A deploy does not load the plugin — the server has to be restarted
        (or the plugin reloaded) before the new JAR takes effect.
        """
        source = Path(jar_path)
        name = plugin_name or source.name
        if not name.endswith(".jar"):
            raise ValueError(f"plugin_name must end with .jar, got {name!r}")
        if "/" in name or "\\" in name or ".." in name:
            raise ValueError(f"plugin_name must not contain path separators or '..': {name!r}")
        fields = {"pluginName": name}
        if branch:
            fields["branch"] = branch
        if repo_url:
            fields["repoUrl"] = repo_url
        return self._upload(
            "/api/plugins/deploy",
            source,
            fields=fields,
            filename=name,
            content_type="application/java-archive",
            timeout=timeout or self.timeouts.deploy,
        )

    # -- internal -----------------------------------------------------------

    def _upload(
        self,
        path: str,
        source: Path,
        *,
        fields: dict,
        filename: str,
        content_type: str,
        timeout: float,
    ) -> str:
        if not source.is_file():
            raise ValueError(f"not a readable file: {source}")
        size = source.stat().st_size
        with source.open("rb") as handle:
            body = MultipartBody(
                fields=fields,
                file_field="file",
                filename=filename,
                stream=handle,
                file_size=size,
                content_type=content_type,
            )
            return self._transport.text(
                "POST", path, multipart=body, timeout=timeout, authenticate=True
            )


class DisruptiveOperations:
    """The wrapper calls that interrupt a live game.

    These live behind ``client.disruptive`` and each takes a mandatory
    ``confirm=True`` for one reason: on a running server they disconnect
    players, and one of them replaces the world outright. Making them the
    longest things in this package to type is the point. ``confirm`` is
    checked before any network call, so a missing one costs nothing.
    """

    def __init__(self, client: MinecraftWrapperClient) -> None:
        self._client = client

    @property
    def _transport(self) -> Transport:
        return self._client._transport

    @staticmethod
    def _require(confirm: bool, what: str) -> None:
        if confirm is not True:
            raise ConfirmationRequired(
                f"{what} disconnects players on a live server; "
                f"pass confirm=True to proceed"
            )

    def stop(self, *, confirm: bool = False, timeout: Optional[float] = None) -> str:
        """``POST /api/server/stop`` — stop the server. Disconnects everyone.

        Returns as soon as the stop is scheduled (HTTP 202); the server keeps
        shutting down afterwards. Raises
        :class:`~omcsi_client.errors.OmcsiConflictError` (HTTP 409) if it was
        not running to begin with.
        """
        self._require(confirm, "stopping the server")
        return self._transport.text(
            "POST", "/api/server/stop", timeout=timeout or self._client.timeouts.control
        )

    def restart(self, *, confirm: bool = False, timeout: Optional[float] = None) -> str:
        """``POST /api/server/restart`` — stop then start. Disconnects everyone.

        Unlike ``stop``, this never returns 409: the wrapper schedules the
        restart whatever state the server is in.
        """
        self._require(confirm, "restarting the server")
        return self._transport.text(
            "POST", "/api/server/restart", timeout=timeout or self._client.timeouts.control
        )

    def graceful_shutdown(self, *, confirm: bool = False, timeout: Optional[float] = None) -> str:
        """``POST /api/server/shutdown`` — warn players, then shut down.

        Takes 30+ seconds server-side. The HTTP call returns straight away
        with 202; the warnings and the shutdown happen after it.
        """
        self._require(confirm, "shutting the server down")
        return self._transport.text(
            "POST", "/api/server/shutdown", timeout=timeout or self._client.timeouts.control
        )

    def send_console_command(
        self, command: str, *, confirm: bool = False, timeout: Optional[float] = None
    ) -> str:
        """``POST /api/server/command`` — run a command on the server console.

        Unrestricted: the wrapper forwards whatever it is given to the
        server's stdin, so ``stop``, ``op``, ``ban`` and ``/kill @a`` all work
        exactly as typed. There is no allow-list and no authentication on this
        endpoint, which is why it is here rather than on the main client.

        The body is sent as ``text/plain``, matching the wrapper's bare
        ``@RequestBody String`` parameter — a JSON document would arrive at
        the console with its quotes still attached.

        **The response says nothing about the command's output.** It reports
        only that the command was written to stdin. Read
        :meth:`MinecraftWrapperClient.logs` (or the container log) to see what
        the server did with it.
        """
        self._require(confirm, "running a console command")
        if not command or not command.strip():
            raise ValueError("command must not be blank")
        return self._transport.text(
            "POST",
            "/api/server/command",
            text_body=command,
            timeout=timeout or self._client.timeouts.control,
        )

    def replace_world(
        self,
        archive_path: PathLike,
        *,
        confirm: bool = False,
        timeout: Optional[float] = None,
    ) -> str:
        """``POST /api/world/upload`` — replace the world with a ZIP archive.

        The most destructive call in the package: the wrapper stops the
        server, deletes the current world, extracts the archive in its place,
        and restarts. Requires a deploy token.

        Only ``.zip`` is accepted — a ``.7z`` or ``.rar`` is rejected as a
        malformed archive no matter how the size limits are configured. The
        archive may have the world folder at the top level or the world
        contents at the root.

        This is also the wrong tool for a multi-GB world. The upload is not
        resumable, and it is buffered to disk on the way through, so a dropped
        connection near the end starts over. The default
        :attr:`~omcsi_client._http.Timeouts.upload` of one hour reflects that
        it is a long call, not that an hour is always enough. For large
        worlds, move the archive in through the deposit box instead — see
        :mod:`omcsi_client.deposit_box`.

        A request that passes this client can still be rejected further up:
        nginx's ``client_max_body_size``, the web app's multipart limit and
        the wrapper's own ``WORLD_UPLOAD_MAX_FILE_SIZE_MB`` are applied in
        series and the smallest wins. A bare 413 with no message usually means
        nginx refused the body before the application ever saw it.
        """
        self._require(confirm, "replacing the world")
        source = Path(archive_path)
        return self._client._upload(
            "/api/world/upload",
            source,
            fields={},
            filename=source.name,
            content_type="application/zip",
            timeout=timeout or self._client.timeouts.upload,
        )
