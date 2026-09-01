"""Typed views over OMCSI's JSON responses.

Every model keeps the decoded payload in ``raw`` and parses defensively:
OMCSI's DTOs are annotated ``@JsonInclude(NON_NULL)``, so fields legitimately
vanish from the wire when the server is stopped or a value could not be
determined on the current platform. A client that insisted on their presence
would break on a perfectly normal response.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, List, Optional

__all__ = [
    "AlertLevel",
    "AlertDestination",
    "ServerStatus",
    "ServerMetrics",
    "LogTail",
    "BackupTriggerResult",
    "LatestBackup",
    "AlertRecord",
]


class AlertLevel(str, Enum):
    """Severity levels accepted by the alert manager."""

    INFO = "INFO"
    WARNING = "WARNING"
    ERROR = "ERROR"
    CRITICAL = "CRITICAL"


class AlertDestination(str, Enum):
    """Where an alert or message is delivered.

    Also the destination vocabulary for the wrapper's ``/api/messages``, which
    upper-cases whatever it is given and forwards it to the alert manager as a
    one-element destination list.
    """

    DISCORD = "DISCORD"
    MINECRAFT = "MINECRAFT"


def _as_int(value: Any) -> Optional[int]:
    if value is None:
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _as_float(value: Any) -> Optional[float]:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


@dataclass(frozen=True)
class ServerStatus:
    """``GET /api/server/status``.

    ``running`` reflects whether the wrapper's child process exists, which is
    the wrapper's own view and not a game-level liveness check. Treat a
    ``True`` here as "a process is alive", not as "players can log in".
    """

    running: bool
    pid: Optional[int] = None
    server_jar: Optional[str] = None
    server_directory: Optional[str] = None
    uptime_seconds: Optional[int] = None
    started_at: Optional[str] = None
    raw: Dict[str, Any] = field(default_factory=dict, repr=False)

    @classmethod
    def from_json(cls, payload: Dict[str, Any]) -> "ServerStatus":
        return cls(
            running=bool(payload.get("running", False)),
            pid=_as_int(payload.get("pid")),
            server_jar=payload.get("serverJar"),
            server_directory=payload.get("serverDirectory"),
            uptime_seconds=_as_int(payload.get("uptimeSeconds")),
            started_at=payload.get("startedAt"),
            raw=payload,
        )


@dataclass(frozen=True)
class ServerMetrics:
    """``GET /api/server/metrics``.

    Every field is best-effort on the server side. ``server_memory_mb`` is
    Linux-only, and ``tps`` is only populated for Paper/Spigot servers that
    log a TPS line; when it is absent, ``tps_note`` says why.
    """

    wrapper_heap_used_mb: Optional[int] = None
    wrapper_heap_max_mb: Optional[int] = None
    wrapper_heap_used_percent: Optional[float] = None
    server_memory_mb: Optional[int] = None
    server_uptime_seconds: Optional[int] = None
    tps: Optional[str] = None
    tps_note: Optional[str] = None
    raw: Dict[str, Any] = field(default_factory=dict, repr=False)

    @classmethod
    def from_json(cls, payload: Dict[str, Any]) -> "ServerMetrics":
        return cls(
            wrapper_heap_used_mb=_as_int(payload.get("wrapperHeapUsedMb")),
            wrapper_heap_max_mb=_as_int(payload.get("wrapperHeapMaxMb")),
            wrapper_heap_used_percent=_as_float(payload.get("wrapperHeapUsedPercent")),
            server_memory_mb=_as_int(payload.get("serverMemoryMb")),
            server_uptime_seconds=_as_int(payload.get("serverUptimeSeconds")),
            tps=payload.get("tps"),
            tps_note=payload.get("tpsNote"),
            raw=payload,
        )


@dataclass(frozen=True)
class LogTail:
    """``GET /api/server/logs``: the tail of ``logs/latest.log``.

    Disabled by default server-side; a deployment that has not set
    ``LOGS_DIAGNOSTIC_ENABLED=true`` answers 403 instead, which surfaces as an
    :class:`~omcsi_client.errors.OmcsiResponseError`.
    """

    lines: List[str] = field(default_factory=list)
    count: int = 0

    @classmethod
    def from_json(cls, payload: Dict[str, Any]) -> "LogTail":
        lines = [str(line) for line in payload.get("lines", [])]
        return cls(lines=lines, count=_as_int(payload.get("count")) or len(lines))


@dataclass(frozen=True)
class BackupTriggerResult:
    """``POST /api/backups/trigger``."""

    success: bool
    message: Optional[str] = None
    backup_path: Optional[str] = None
    raw: Dict[str, Any] = field(default_factory=dict, repr=False)

    @classmethod
    def from_json(cls, payload: Dict[str, Any]) -> "BackupTriggerResult":
        return cls(
            success=bool(payload.get("success", False)),
            message=payload.get("message"),
            backup_path=payload.get("backupPath"),
            raw=payload,
        )


@dataclass(frozen=True)
class LatestBackup:
    """``GET /api/backups/latest``.

    ``available`` is false — with HTTP 200, not an error — when no backup has
    been taken yet. ``success`` is a tri-state: ``None`` when nothing is
    available, otherwise whether the newest backup directory looks complete.
    """

    available: bool
    success: Optional[bool] = None
    timestamp: Optional[str] = None
    message: Optional[str] = None
    backup_path: Optional[str] = None
    raw: Dict[str, Any] = field(default_factory=dict, repr=False)

    @classmethod
    def from_json(cls, payload: Dict[str, Any]) -> "LatestBackup":
        success = payload.get("success")
        return cls(
            available=bool(payload.get("available", False)),
            success=None if success is None else bool(success),
            timestamp=payload.get("timestamp"),
            message=payload.get("message"),
            backup_path=payload.get("backupPath"),
            raw=payload,
        )


@dataclass(frozen=True)
class AlertRecord:
    """One entry from ``GET /api/alerts``, newest first."""

    title: Optional[str] = None
    message: Optional[str] = None
    level: Optional[AlertLevel] = None
    source: Optional[str] = None
    received_at: Optional[str] = None
    raw: Dict[str, Any] = field(default_factory=dict, repr=False)

    @classmethod
    def from_json(cls, payload: Dict[str, Any]) -> "AlertRecord":
        raw_level = payload.get("level")
        try:
            level = AlertLevel(raw_level) if raw_level is not None else None
        except ValueError:
            # A future server may add a level this client does not know about.
            # Losing the record over an unrecognised enum would be worse than
            # handing back a record whose level is None but whose raw payload
            # still has it.
            level = None
        return cls(
            title=payload.get("title"),
            message=payload.get("message"),
            level=level,
            source=payload.get("source"),
            received_at=payload.get("receivedAt"),
            raw=payload,
        )
