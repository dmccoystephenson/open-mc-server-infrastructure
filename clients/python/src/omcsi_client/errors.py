"""Exception hierarchy for the OMCSI client.

The split that matters to callers is between *the service answered and said
no* (:class:`OmcsiResponseError`, which carries an HTTP status) and *the
service could not be reached at all* (:class:`OmcsiTransportError`, which has
no status because no response ever arrived). Retrying is usually reasonable
for the second and usually pointless for the first.
"""

from __future__ import annotations

from typing import Optional

__all__ = [
    "OmcsiError",
    "OmcsiResponseError",
    "OmcsiAuthError",
    "OmcsiConflictError",
    "OmcsiTransportError",
    "OmcsiTimeoutError",
    "ConfirmationRequired",
]


class OmcsiError(Exception):
    """Base class for every error raised by this package."""


class OmcsiResponseError(OmcsiError):
    """An OMCSI service returned a non-2xx response.

    The request reached the service and was refused or failed there, so
    ``status`` is always set. ``message`` is the service's own explanation
    where it gave one: OMCSI's ``GlobalExceptionHandler`` returns a JSON body
    with a ``message`` field, while the plainer endpoints (``/api/server/*``)
    return a bare string. Both are unwrapped into ``message``; ``body`` keeps
    the raw bytes for anything this cannot parse.
    """

    def __init__(
        self,
        message: str,
        *,
        status: int,
        method: str = "",
        url: str = "",
        body: bytes = b"",
    ) -> None:
        super().__init__(f"{method} {url} -> HTTP {status}: {message}".strip())
        self.message = message
        self.status = status
        self.method = method
        self.url = url
        self.body = body


class OmcsiAuthError(OmcsiResponseError):
    """HTTP 401 — the Bearer token was missing, wrong, or unconfigured server-side.

    Note the third case: the wrapper rejects *every* plugin deploy and world
    upload when ``deploy.auth.token`` is blank in its own configuration, so a
    401 does not necessarily mean the token you sent is wrong.
    """


class OmcsiConflictError(OmcsiResponseError):
    """HTTP 409 — the server is not in a state that allows the request.

    Raised by ``start`` when the Minecraft server is already running and by
    ``stop`` when it is not running. Both are frequently benign: the desired
    state already holds.
    """


class OmcsiTransportError(OmcsiError):
    """The service could not be reached — no HTTP response was ever received.

    Connection refused, DNS failure, TLS handshake failure, and so on. There
    is deliberately no ``status`` attribute: inventing one would blur the very
    distinction this class exists to draw.
    """

    def __init__(self, message: str, *, reason: Optional[BaseException] = None) -> None:
        super().__init__(message)
        self.reason = reason


class OmcsiTimeoutError(OmcsiTransportError):
    """The request timed out.

    A subclass of :class:`OmcsiTransportError` because nothing came back, but
    worth catching separately: on the long operations (backup trigger, world
    upload) a timeout usually means the client gave up, *not* that the server
    stopped working. The operation may well still be running.
    """


class ConfirmationRequired(OmcsiError):
    """A disruptive operation was called without ``confirm=True``.

    Raised before any network call is made, so nothing has happened to the
    game server when you see this.
    """
