"""Client for the backup-manager service (default port 8091)."""

from __future__ import annotations

from typing import Optional

from ._http import DEFAULT_TIMEOUTS, Timeouts, Transport
from .models import BackupTriggerResult, LatestBackup

__all__ = ["BackupManagerClient"]

DEFAULT_BACKUP_URL = "http://localhost:8091"


class BackupManagerClient:
    """Talks to one backup-manager instance.

    The service also runs backups on a schedule of its own
    (``BACKUP_SCHEDULE``, 2 AM daily by default); this client covers the
    manual path.
    """

    def __init__(
        self,
        base_url: str = DEFAULT_BACKUP_URL,
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

    def latest(self, *, timeout: Optional[float] = None) -> LatestBackup:
        """``GET /api/backups/latest`` — status of the newest backup.

        "No backup has ever been taken" is a successful response with
        ``available=False``, not an error.
        """
        payload = self._transport.json(
            "GET", "/api/backups/latest", timeout=timeout or self.timeouts.status
        )
        return LatestBackup.from_json(payload or {})

    def trigger(self, *, timeout: Optional[float] = None) -> BackupTriggerResult:
        """``POST /api/backups/trigger`` — take a backup now.

        **Synchronous.** Unlike the wrapper's lifecycle endpoints, this does
        not return until the whole server directory has been copied, so the
        call scales with world size — hence the generous default
        (:attr:`~omcsi_client._http.Timeouts.backup`, 30 minutes). A
        :class:`~omcsi_client.errors.OmcsiTimeoutError` here means this client
        stopped waiting; the backup itself is probably still running.

        Retention runs as part of the same call: once the copy completes, old
        backups are pruned to fit ``BACKUP_MAX_SIZE_MB``. The newest backup is
        never deleted to satisfy that cap, so a cap smaller than a single
        backup leaves exactly one backup and no history.
        """
        payload = self._transport.json(
            "POST", "/api/backups/trigger", timeout=timeout or self.timeouts.backup
        )
        return BackupTriggerResult.from_json(payload or {})

    def health(self, *, timeout: Optional[float] = None) -> dict:
        """``GET /actuator/health`` — Spring Boot's own health report."""
        return self._transport.json(
            "GET", "/actuator/health", timeout=timeout or self.timeouts.status
        ) or {}
