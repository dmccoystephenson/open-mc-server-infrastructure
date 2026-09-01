"""Client for the alert-manager service (default port 8090).

The alert manager is the fan-out point: it takes an alert and delivers it to
Discord, to the Minecraft server over RCON, or both. The other OMCSI services
post to it internally, and this client is the same door they use.
"""

from __future__ import annotations

from typing import Iterable, List, Optional, Union

from ._http import DEFAULT_TIMEOUTS, Timeouts, Transport
from .models import AlertDestination, AlertLevel, AlertRecord

__all__ = ["AlertManagerClient"]

DEFAULT_ALERT_URL = "http://localhost:8090"


class AlertManagerClient:
    """Talks to one alert-manager instance."""

    def __init__(
        self,
        base_url: str = DEFAULT_ALERT_URL,
        *,
        timeouts: Optional[Timeouts] = None,
        verify_tls: bool = True,
    ) -> None:
        self._transport = Transport(
            base_url, timeouts=timeouts or DEFAULT_TIMEOUTS, verify_tls=verify_tls
        )

    @property
    def base_url(self) -> str:
        return self._transport.base_url

    @property
    def timeouts(self) -> Timeouts:
        return self._transport.timeouts

    def send(
        self,
        title: str,
        message: str,
        *,
        level: Union[AlertLevel, str] = AlertLevel.INFO,
        source: Optional[str] = None,
        destinations: Optional[Iterable[Union[AlertDestination, str]]] = None,
        timeout: Optional[float] = None,
    ) -> str:
        """``POST /api/alerts`` — send an alert.

        ``title``, ``message`` and ``level`` are validated server-side and a
        blank title or an unknown level comes back as HTTP 400. Omitting
        ``destinations`` leaves routing to the service's own configuration.
        """
        if not title or not title.strip():
            raise ValueError("alert title must not be blank")
        if not message or not message.strip():
            raise ValueError("alert message must not be blank")
        body = {
            "title": title,
            "message": message,
            "level": level.value if isinstance(level, AlertLevel) else str(level).upper(),
        }
        if source:
            body["source"] = source
        if destinations is not None:
            body["destinations"] = [
                d.value if isinstance(d, AlertDestination) else str(d).upper()
                for d in destinations
            ]
        return self._transport.text(
            "POST", "/api/alerts", json_body=body, timeout=timeout or self.timeouts.control
        )

    def recent(self, limit: int = 10, *, timeout: Optional[float] = None) -> List[AlertRecord]:
        """``GET /api/alerts`` — the most recent alerts, newest first.

        The service clamps ``limit`` to ``[1, 100]`` rather than rejecting an
        out-of-range value, so asking for 1000 quietly returns 100.
        """
        payload = self._transport.json(
            "GET",
            "/api/alerts",
            params={"limit": limit},
            timeout=timeout or self.timeouts.status,
        )
        return [AlertRecord.from_json(item) for item in (payload or [])]

    def service_health(self, *, timeout: Optional[float] = None) -> str:
        """``GET /api/alerts/health`` — the service's own plain-text check."""
        return self._transport.text(
            "GET", "/api/alerts/health", timeout=timeout or self.timeouts.status
        )

    def health(self, *, timeout: Optional[float] = None) -> dict:
        """``GET /actuator/health`` — Spring Boot's own health report."""
        return self._transport.json(
            "GET", "/actuator/health", timeout=timeout or self.timeouts.status
        ) or {}
