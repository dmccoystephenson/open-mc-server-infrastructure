"""A tiny stubbed OMCSI service, for testing the client without a deployment.

Nothing here talks to a real OMCSI install: these tests run on a laptop and in
CI, neither of which has a Minecraft server. The stub speaks real HTTP on
localhost so the client's transport — urllib, headers, multipart framing,
timeouts — is genuinely exercised rather than mocked out.
"""

from __future__ import annotations

import email
import json
import socket
import threading
import time
from dataclasses import dataclass, field
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Callable, Dict, List, Optional, Tuple
from urllib.parse import parse_qs, urlparse


@dataclass
class RecordedRequest:
    method: str
    path: str
    query: Dict[str, List[str]]
    headers: Dict[str, str]
    body: bytes = b""

    def json(self):
        return json.loads(self.body.decode("utf-8"))

    def text(self) -> str:
        return self.body.decode("utf-8")

    def multipart(self) -> Tuple[Dict[str, str], Dict[str, Tuple[str, bytes]]]:
        """Parse a multipart body into ``(fields, files)``.

        ``files`` maps the form field name to ``(filename, content)``.
        """
        raw = (
            b"Content-Type: " + self.headers["Content-Type"].encode() + b"\r\n\r\n" + self.body
        )
        message = email.message_from_bytes(raw)
        fields: Dict[str, str] = {}
        files: Dict[str, Tuple[str, bytes]] = {}
        for part in message.get_payload():
            name = part.get_param("name", header="content-disposition")
            filename = part.get_param("filename", header="content-disposition")
            payload = part.get_payload(decode=True) or b""
            if filename:
                files[name] = (filename, payload)
            else:
                fields[name] = payload.decode("utf-8")
        return fields, files


@dataclass
class Response:
    status: int = 200
    body: bytes = b""
    content_type: str = "text/plain;charset=UTF-8"
    delay: float = 0.0


@dataclass
class StubService:
    """Route table plus request log for one stubbed OMCSI service."""

    routes: Dict[Tuple[str, str], Callable[[RecordedRequest], Response]] = field(
        default_factory=dict
    )
    requests: List[RecordedRequest] = field(default_factory=list)
    _server: Optional[ThreadingHTTPServer] = None
    _thread: Optional[threading.Thread] = None

    # -- route registration -------------------------------------------------

    def handler(self, method: str, path: str):
        """Decorator registering a callable that builds the response."""

        def register(func: Callable[[RecordedRequest], Response]):
            self.routes[(method.upper(), path)] = func
            return func

        return register

    def respond(
        self,
        method: str,
        path: str,
        *,
        status: int = 200,
        text: Optional[str] = None,
        json_body=None,
        delay: float = 0.0,
    ) -> None:
        """Register a fixed response."""
        if json_body is not None:
            body = json.dumps(json_body).encode()
            content_type = "application/json"
        else:
            body = (text or "").encode()
            content_type = "text/plain;charset=UTF-8"
        response = Response(status=status, body=body, content_type=content_type, delay=delay)
        self.routes[(method.upper(), path)] = lambda _req: response

    # -- lifecycle ----------------------------------------------------------

    def start(self) -> "StubService":
        self._server = _QuietServer(("127.0.0.1", 0), _make_handler(self))
        # A short poll interval keeps stop() prompt: shutdown() waits up to one
        # interval for the serve loop to notice, and with one stub per test the
        # default 0.5s would dominate the suite's runtime.
        self._thread = threading.Thread(
            target=self._server.serve_forever, kwargs={"poll_interval": 0.02}, daemon=True
        )
        self._thread.start()
        return self

    def stop(self) -> None:
        if self._server is not None:
            self._server.shutdown()
            self._server.server_close()
            self._server = None
        if self._thread is not None:
            self._thread.join(timeout=5)
            self._thread = None

    def __enter__(self) -> "StubService":
        return self.start()

    def __exit__(self, *exc) -> None:
        self.stop()

    @property
    def url(self) -> str:
        assert self._server is not None, "stub service is not running"
        host, port = self._server.server_address[:2]
        return f"http://{host}:{port}"

    @property
    def last_request(self) -> RecordedRequest:
        assert self.requests, "no requests were recorded"
        return self.requests[-1]


class _QuietServer(ThreadingHTTPServer):
    """A ThreadingHTTPServer that neither lingers nor complains.

    ``daemon_threads`` keeps ``server_close()`` from blocking on handler
    threads that are still parked on a keep-alive read — with one stub per
    test that join dominates the suite's runtime. ``handle_error`` is silenced
    because the timeout tests deliberately hang up mid-response, and a
    BrokenPipeError traceback there is the test working, not failing.
    """

    daemon_threads = True

    def handle_error(self, request, client_address) -> None:
        pass


def _make_handler(service: StubService):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, *args) -> None:  # keep the test output readable
            pass

        def _dispatch(self) -> None:
            parsed = urlparse(self.path)
            length = int(self.headers.get("Content-Length") or 0)
            body = self.rfile.read(length) if length else b""
            record = RecordedRequest(
                method=self.command,
                path=parsed.path,
                query=parse_qs(parsed.query),
                headers=dict(self.headers.items()),
                body=body,
            )
            service.requests.append(record)

            route = service.routes.get((self.command, parsed.path))
            if route is None:
                self._write(Response(status=404, body=b"no stub route"))
                return
            response = route(record)
            if response.delay:
                time.sleep(response.delay)
            self._write(response)

        def _write(self, response: Response) -> None:
            try:
                self.send_response(response.status)
                self.send_header("Content-Type", response.content_type)
                self.send_header("Content-Length", str(len(response.body)))
                self.end_headers()
                if response.body:
                    self.wfile.write(response.body)
            except (BrokenPipeError, ConnectionResetError):
                # The client gave up first — which is exactly what the timeout
                # tests are asserting.
                pass

        do_GET = _dispatch
        do_POST = _dispatch
        do_PUT = _dispatch
        do_DELETE = _dispatch

    return Handler


def closed_port() -> int:
    """Return a port number nothing is listening on."""
    sock = socket.socket()
    sock.bind(("127.0.0.1", 0))
    port = sock.getsockname()[1]
    sock.close()
    return port
