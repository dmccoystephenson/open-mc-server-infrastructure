"""A standard-library client for Open Minecraft Server Infrastructure.

OMCSI is several small Spring Boot services rather than one API, so this
package is one client per service:

======================  ============================  ====================
Client                  Service                       Default port
======================  ============================  ====================
MinecraftWrapperClient  minecraft-wrapper             8092
BackupManagerClient     backup-manager                8091
AlertManagerClient      alert-manager                 8090
======================  ============================  ====================

:class:`Omcsi` bundles the three plus a :class:`~omcsi_client.deposit_box.DepositBox`
when you want them all in one place; the individual clients stand alone when
you do not.

Quick start::

    from omcsi_client import Omcsi

    omcsi = Omcsi.from_env()
    print(omcsi.wrapper.status().running)

    # Anything that interrupts a live game is behind `.disruptive`
    # and needs confirm=True:
    omcsi.wrapper.disruptive.restart(confirm=True)
"""

from __future__ import annotations

import os
from typing import Optional

from ._http import DEFAULT_TIMEOUTS, Timeouts, env_flag
from .alert_manager import DEFAULT_ALERT_URL, AlertManagerClient
from .backup_manager import DEFAULT_BACKUP_URL, BackupManagerClient
from .deposit_box import DepositBox, StagedFile
from .errors import (
    ConfirmationRequired,
    OmcsiAuthError,
    OmcsiConflictError,
    OmcsiError,
    OmcsiResponseError,
    OmcsiTimeoutError,
    OmcsiTransportError,
)
from .minecraft_wrapper import (
    DEFAULT_WRAPPER_URL,
    DisruptiveOperations,
    MinecraftWrapperClient,
)
from .models import (
    AlertDestination,
    AlertLevel,
    AlertRecord,
    BackupTriggerResult,
    LatestBackup,
    LogTail,
    ServerMetrics,
    ServerStatus,
)

__version__ = "0.1.0"

__all__ = [
    "__version__",
    "Omcsi",
    "MinecraftWrapperClient",
    "BackupManagerClient",
    "AlertManagerClient",
    "DisruptiveOperations",
    "DepositBox",
    "StagedFile",
    "Timeouts",
    "DEFAULT_TIMEOUTS",
    "AlertLevel",
    "AlertDestination",
    "AlertRecord",
    "ServerStatus",
    "ServerMetrics",
    "LogTail",
    "BackupTriggerResult",
    "LatestBackup",
    "OmcsiError",
    "OmcsiResponseError",
    "OmcsiAuthError",
    "OmcsiConflictError",
    "OmcsiTransportError",
    "OmcsiTimeoutError",
    "ConfirmationRequired",
]


class Omcsi:
    """One handle on a whole OMCSI deployment.

    Nothing is contacted at construction time — the sub-clients are built
    eagerly but issue no requests until you call something, so constructing
    this against a deployment that is down is not an error.
    """

    def __init__(
        self,
        *,
        wrapper_url: str = DEFAULT_WRAPPER_URL,
        backup_url: str = DEFAULT_BACKUP_URL,
        alert_url: str = DEFAULT_ALERT_URL,
        deploy_token: Optional[str] = None,
        deposit_box_path: Optional[str] = None,
        timeouts: Optional[Timeouts] = None,
        verify_tls: bool = True,
    ) -> None:
        shared = timeouts or DEFAULT_TIMEOUTS
        self.wrapper = MinecraftWrapperClient(
            wrapper_url,
            deploy_token=deploy_token,
            timeouts=shared,
            verify_tls=verify_tls,
        )
        self.backups = BackupManagerClient(
            backup_url, timeouts=shared, verify_tls=verify_tls
        )
        self.alerts = AlertManagerClient(
            alert_url, timeouts=shared, verify_tls=verify_tls
        )
        self.deposit_box: Optional[DepositBox] = (
            DepositBox(deposit_box_path) if deposit_box_path else None
        )

    @classmethod
    def from_env(cls, *, timeouts: Optional[Timeouts] = None) -> "Omcsi":
        """Build from environment variables.

        ==========================  ===========================================
        Variable                    Meaning
        ==========================  ===========================================
        ``OMCSI_WRAPPER_URL``       minecraft-wrapper root URL
        ``OMCSI_API_BASE``          accepted as a fallback for the above, since
                                    existing OMCSI callers already set it
        ``OMCSI_BACKUP_URL``        backup-manager root URL
        ``OMCSI_ALERT_URL``         alert-manager root URL
        ``OMCSI_DEPLOY_TOKEN``      Bearer token for deploy/upload
        ``OMCSI_DEPOSIT_BOX``       host path of the deposit-box directory
        ``OMCSI_VERIFY_TLS``        set to ``0``/``false`` for a deployment
                                    still using the default self-signed cert
        ==========================  ===========================================
        """
        wrapper_url = (
            os.environ.get("OMCSI_WRAPPER_URL")
            or os.environ.get("OMCSI_API_BASE")
            or DEFAULT_WRAPPER_URL
        )
        return cls(
            wrapper_url=wrapper_url,
            backup_url=os.environ.get("OMCSI_BACKUP_URL", DEFAULT_BACKUP_URL),
            alert_url=os.environ.get("OMCSI_ALERT_URL", DEFAULT_ALERT_URL),
            deploy_token=os.environ.get("OMCSI_DEPLOY_TOKEN") or None,
            deposit_box_path=os.environ.get("OMCSI_DEPOSIT_BOX") or None,
            timeouts=timeouts,
            verify_tls=env_flag("OMCSI_VERIFY_TLS", True),
        )

    def __repr__(self) -> str:  # pragma: no cover - trivial
        return (
            f"Omcsi(wrapper={self.wrapper.base_url!r}, "
            f"backups={self.backups.base_url!r}, alerts={self.alerts.base_url!r})"
        )
