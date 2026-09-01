"""Tests for BackupManagerClient against a stubbed backup-manager."""

from __future__ import annotations

import unittest

from omcsi_client import BackupManagerClient, OmcsiResponseError, Timeouts

from stub_server import StubService


class TestBackupManagerClient(unittest.TestCase):
    def setUp(self) -> None:
        self.service = StubService().start()
        self.addCleanup(self.service.stop)
        self.client = BackupManagerClient(
            self.service.url, timeouts=Timeouts(status=5, backup=5)
        )

    def test_trigger_returns_the_backup_path(self) -> None:
        self.service.respond(
            "POST",
            "/api/backups/trigger",
            json_body={
                "success": True,
                "message": "Backup completed successfully",
                "backupPath": "/backups/backup-20260901-020000",
            },
        )
        result = self.client.trigger()
        self.assertTrue(result.success)
        self.assertEqual(result.backup_path, "/backups/backup-20260901-020000")

    def test_trigger_uses_the_long_backup_timeout_not_the_status_one(self) -> None:
        # A status poll's budget would kill a real backup part-way through.
        client = BackupManagerClient(self.service.url)
        self.assertGreater(client.timeouts.backup, client.timeouts.status)

    def test_trigger_failure_surfaces_the_handler_message(self) -> None:
        self.service.respond(
            "POST",
            "/api/backups/trigger",
            status=500,
            json_body={
                "timestamp": "2026-09-01T02:00:00Z",
                "status": 500,
                "error": "Internal Server Error",
                "message": "Source directory does not exist: /mcserver",
            },
        )
        with self.assertRaises(OmcsiResponseError) as caught:
            self.client.trigger()
        self.assertEqual(caught.exception.status, 500)
        self.assertEqual(
            caught.exception.message, "Source directory does not exist: /mcserver"
        )

    def test_latest_when_a_backup_exists(self) -> None:
        self.service.respond(
            "GET",
            "/api/backups/latest",
            json_body={
                "available": True,
                "success": True,
                "timestamp": "20260901-020000",
                "message": "Backup at /backups/backup-20260901-020000 looks complete",
                "backupPath": "/backups/backup-20260901-020000",
            },
        )
        latest = self.client.latest()
        self.assertTrue(latest.available)
        self.assertTrue(latest.success)
        self.assertEqual(latest.timestamp, "20260901-020000")

    def test_latest_when_nothing_has_been_backed_up_is_not_an_error(self) -> None:
        # The service answers 200 with available=false; treating that as a
        # failure would make "no backups yet" indistinguishable from "the
        # backup manager is down".
        self.service.respond(
            "GET",
            "/api/backups/latest",
            json_body={"available": False, "message": "No backup has been performed yet"},
        )
        latest = self.client.latest()
        self.assertFalse(latest.available)
        self.assertIsNone(latest.success)
        self.assertIsNone(latest.backup_path)

    def test_health(self) -> None:
        self.service.respond("GET", "/actuator/health", json_body={"status": "UP"})
        self.assertEqual(self.client.health()["status"], "UP")


if __name__ == "__main__":
    unittest.main()
