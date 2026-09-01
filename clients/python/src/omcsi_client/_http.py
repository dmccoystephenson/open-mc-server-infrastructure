"""urllib-based HTTP transport shared by the three service clients.

Standard library only, deliberately: OMCSI's services are usually driven from
operator scripts and CI jobs on machines where adding a dependency is more
friction than the request itself is worth.
"""

from __future__ import annotations

import json
import os
import socket
import ssl
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass, replace
from typing import Any, BinaryIO, Dict, Mapping, Optional, Tuple

from .errors import (
    OmcsiAuthError,
    OmcsiConflictError,
    OmcsiResponseError,
    OmcsiTimeoutError,
    OmcsiTransportError,
)

__all__ = ["Timeouts", "DEFAULT_TIMEOUTS"]

# How much of a response body to keep in an error message before truncating.
_MAX_ERROR_BODY_CHARS = 512

# Chunk size used when streaming a multipart body off disk.
_STREAM_CHUNK = 64 * 1024


@dataclass(frozen=True)
class Timeouts:
    """Per-category socket timeouts, in seconds.

    One timeout for the whole client would be wrong in both directions: a
    status poll that hangs for ten minutes is a bug, and a world upload that
    is killed after thirty seconds never completes. So each category gets its
    own budget, and every call also accepts a per-call override.

    The defaults are shaped by what the endpoints actually do:

    ``status``
        Cheap reads (``/status``, ``/metrics``, ``/logs``, ``/latest``,
        health checks). These read process state and return immediately.
    ``control``
        ``start``/``stop``/``restart``/``shutdown``/``command``. The wrapper
        answers ``202 Accepted`` and does the work asynchronously, so the
        request itself is short even though the *operation* is not — a stop
        takes 30+ seconds to finish after the response has arrived.
    ``deploy``
        Plugin JAR upload. Bounded by the wrapper's own 100 MB plugin cap,
        but slow links exist.
    ``upload``
        World archive upload. This is the long one: the request is not
        answered until the archive has been transferred, validated,
        extracted, and swapped into place, with the server stopped and
        restarted around it. Multi-GB worlds routinely take hours, which is
        why OMCSI's own nginx route allows 3600s and its Terraform variable
        ``world_upload_timeout_seconds`` defaults far higher still.
    ``backup``
        Backup trigger. ``POST /api/backups/trigger`` copies the whole server
        directory *synchronously* and only then responds, so this scales with
        world size.
    """

    status: float = 8.0
    control: float = 30.0
    deploy: float = 300.0
    upload: float = 3600.0
    backup: float = 1800.0

    def with_overrides(self, **kwargs: Optional[float]) -> "Timeouts":
        """Return a copy with the named fields replaced, ignoring ``None`` values."""
        given = {k: v for k, v in kwargs.items() if v is not None}
        return replace(self, **given) if given else self


DEFAULT_TIMEOUTS = Timeouts()


class MultipartBody:
    """A streaming ``multipart/form-data`` body.

    Reads the file off disk in chunks rather than into memory, because the
    world-upload endpoint is routinely handed archives larger than the host's
    RAM. The exact content length is computed up front so ``urllib`` sends a
    ``Content-Length`` instead of falling back to chunked transfer encoding,
    which Spring's multipart resolver and nginx both handle far less happily.
    """

    def __init__(
        self,
        fields: Mapping[str, str],
        file_field: str,
        filename: str,
        stream: BinaryIO,
        file_size: int,
        content_type: str = "application/octet-stream",
    ) -> None:
        self.boundary = uuid.uuid4().hex
        self._stream = stream
        self._file_size = file_size
        self._parts: list = []

        dashes = f"--{self.boundary}\r\n".encode()
        for name, value in fields.items():
            if value is None:
                continue
            header = (
                dashes
                + f'Content-Disposition: form-data; name="{_escape(name)}"\r\n\r\n'.encode()
                + str(value).encode("utf-8")
                + b"\r\n"
            )
            self._parts.append(header)

        self._parts.append(
            dashes
            + (
                f'Content-Disposition: form-data; name="{_escape(file_field)}"; '
                f'filename="{_escape(filename)}"\r\n'
            ).encode()
            + f"Content-Type: {content_type}\r\n\r\n".encode()
        )
        self._epilogue = f"\r\n--{self.boundary}--\r\n".encode()

        self._preamble = b"".join(self._parts)
        self._preamble_pos = 0
        self._epilogue_pos = 0
        self._file_done = False

    @property
    def content_type(self) -> str:
        return f"multipart/form-data; boundary={self.boundary}"

    @property
    def content_length(self) -> int:
        return len(self._preamble) + self._file_size + len(self._epilogue)

    def read(self, size: int = -1) -> bytes:
        if size is None or size < 0:
            size = _STREAM_CHUNK
        if self._preamble_pos < len(self._preamble):
            chunk = self._preamble[self._preamble_pos : self._preamble_pos + size]
            self._preamble_pos += len(chunk)
            return chunk
        if not self._file_done:
            chunk = self._stream.read(size)
            if chunk:
                return chunk
            self._file_done = True
        if self._epilogue_pos < len(self._epilogue):
            chunk = self._epilogue[self._epilogue_pos : self._epilogue_pos + size]
            self._epilogue_pos += len(chunk)
            return chunk
        return b""


def _escape(value: str) -> str:
    """Escape a form field name for a Content-Disposition header."""
    return value.replace('"', "%22").replace("\r", "").replace("\n", "")


class Transport:
    """Issues requests against one OMCSI service and normalises its failures."""

    def __init__(
        self,
        base_url: str,
        *,
        token: Optional[str] = None,
        timeouts: Optional[Timeouts] = None,
        verify_tls: bool = True,
        user_agent: str = "omcsi-client",
    ) -> None:
        if not base_url:
            raise ValueError("base_url must not be empty")
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.timeouts = timeouts or DEFAULT_TIMEOUTS
        self.verify_tls = verify_tls
        self.user_agent = user_agent
        self._opener = self._build_opener()

    def _build_opener(self) -> urllib.request.OpenerDirector:
        handlers: list = []
        if not self.verify_tls:
            # OMCSI's nginx ships a self-signed certificate by default, so a
            # stock install cannot be reached with verification on. This is
            # opt-in per client and is the programmatic equivalent of the
            # `curl -k` that OMCSI's own docs use against a fresh deployment.
            context = ssl.create_default_context()
            context.check_hostname = False
            context.verify_mode = ssl.CERT_NONE
            handlers.append(urllib.request.HTTPSHandler(context=context))
        # No redirect following beyond urllib's default, and no cookie jar:
        # these are machine-to-machine API calls, not a browser session.
        return urllib.request.build_opener(*handlers)

    # -- request plumbing ---------------------------------------------------

    def request(
        self,
        method: str,
        path: str,
        *,
        params: Optional[Mapping[str, Any]] = None,
        json_body: Any = None,
        text_body: Optional[str] = None,
        multipart: Optional[MultipartBody] = None,
        timeout: Optional[float] = None,
        authenticate: bool = False,
    ) -> Tuple[int, bytes]:
        """Send one request and return ``(status, body)`` for any 2xx response.

        Anything else becomes an exception: a non-2xx response becomes an
        :class:`OmcsiResponseError` (or one of its more specific subclasses),
        and a failure to get a response at all becomes an
        :class:`OmcsiTransportError`.
        """
        url = self.base_url + path
        if params:
            filtered = {k: v for k, v in params.items() if v is not None}
            if filtered:
                url = f"{url}?{urllib.parse.urlencode(filtered)}"

        headers: Dict[str, str] = {"User-Agent": self.user_agent}
        data: Any = None

        if multipart is not None:
            data = multipart
            headers["Content-Type"] = multipart.content_type
            headers["Content-Length"] = str(multipart.content_length)
        elif json_body is not None:
            data = json.dumps(json_body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        elif text_body is not None:
            # The wrapper's POST /api/server/command takes a bare `@RequestBody
            # String`, not a JSON document. Sending JSON here would deliver a
            # quoted string to the Minecraft console.
            data = text_body.encode("utf-8")
            headers["Content-Type"] = "text/plain; charset=utf-8"

        if authenticate:
            if not self.token:
                raise OmcsiAuthError(
                    "no deploy token configured on this client; "
                    "pass deploy_token=... or set OMCSI_DEPLOY_TOKEN",
                    status=401,
                    method=method,
                    url=url,
                )
            headers["Authorization"] = f"Bearer {self.token}"

        request = urllib.request.Request(url, data=data, headers=headers, method=method)
        effective_timeout = timeout if timeout is not None else self.timeouts.status

        try:
            with self._opener.open(request, timeout=effective_timeout) as response:
                return response.status, response.read()
        except urllib.error.HTTPError as exc:
            raw = exc.read()
            raise _response_error(method, url, exc.code, raw) from exc
        except urllib.error.URLError as exc:
            if isinstance(exc.reason, (socket.timeout, TimeoutError)):
                raise OmcsiTimeoutError(
                    f"{method} {url} timed out after {effective_timeout}s",
                    reason=exc,
                ) from exc
            raise OmcsiTransportError(
                f"could not reach {url}: {exc.reason}", reason=exc
            ) from exc
        except (socket.timeout, TimeoutError) as exc:
            raise OmcsiTimeoutError(
                f"{method} {url} timed out after {effective_timeout}s", reason=exc
            ) from exc
        except (OSError, ssl.SSLError) as exc:
            raise OmcsiTransportError(f"could not reach {url}: {exc}", reason=exc) from exc

    def json(self, method: str, path: str, **kwargs: Any) -> Any:
        """Send a request and decode a JSON response body."""
        status, raw = self.request(method, path, **kwargs)
        if not raw:
            return None
        try:
            return json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise OmcsiResponseError(
                f"expected a JSON body but got {raw[:_MAX_ERROR_BODY_CHARS]!r}",
                status=status,
                method=method,
                url=self.base_url + path,
                body=raw,
            ) from exc

    def text(self, method: str, path: str, **kwargs: Any) -> str:
        """Send a request and decode a plain-text response body.

        The ``/api/server/*`` endpoints answer with a bare human-readable
        string ("Server start initiated"), not JSON.
        """
        _, raw = self.request(method, path, **kwargs)
        return raw.decode("utf-8", errors="replace").strip()


def _response_error(method: str, url: str, status: int, raw: bytes) -> OmcsiResponseError:
    """Turn a non-2xx response into the most specific exception that fits."""
    message = _extract_message(raw) or f"HTTP {status}"
    cls = OmcsiResponseError
    if status == 401:
        cls = OmcsiAuthError
    elif status == 409:
        cls = OmcsiConflictError
    return cls(message, status=status, method=method, url=url, body=raw)


def _extract_message(raw: bytes) -> str:
    """Pull a human-readable message out of an error body.

    OMCSI answers in two shapes. The ``GlobalExceptionHandler`` in each
    service returns ``{"timestamp", "status", "error", "message"}``; the
    hand-written branches in the controllers return a bare string. Try the
    structured form, fall back to the text.
    """
    if not raw:
        return ""
    text = raw.decode("utf-8", errors="replace").strip()
    try:
        parsed = json.loads(text)
    except json.JSONDecodeError:
        return text[:_MAX_ERROR_BODY_CHARS]
    if isinstance(parsed, dict):
        for key in ("message", "error", "detail"):
            value = parsed.get(key)
            if isinstance(value, str) and value:
                return value
    return text[:_MAX_ERROR_BODY_CHARS]


def env_flag(name: str, default: bool) -> bool:
    """Read a boolean environment variable, tolerating the usual spellings."""
    raw = os.environ.get(name)
    if raw is None or raw == "":
        return default
    return raw.strip().lower() in ("1", "true", "yes", "on")
