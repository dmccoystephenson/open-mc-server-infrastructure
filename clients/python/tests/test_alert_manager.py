"""Tests for AlertManagerClient against a stubbed alert-manager."""

from __future__ import annotations

import unittest

from omcsi_client import (
    AlertDestination,
    AlertLevel,
    AlertManagerClient,
    OmcsiResponseError,
    Timeouts,
)

from stub_server import StubService


class TestAlertManagerClient(unittest.TestCase):
    def setUp(self) -> None:
        self.service = StubService().start()
        self.addCleanup(self.service.stop)
        self.client = AlertManagerClient(
            self.service.url, timeouts=Timeouts(status=5, control=5)
        )

    def test_send_serialises_enums_by_value(self) -> None:
        self.service.respond("POST", "/api/alerts", text="Alert sent successfully")
        result = self.client.send(
            "Backup failed",
            "The nightly backup did not complete",
            level=AlertLevel.ERROR,
            source="backup-manager",
            destinations=[AlertDestination.DISCORD, AlertDestination.MINECRAFT],
        )
        self.assertEqual(result, "Alert sent successfully")
        self.assertEqual(
            self.service.last_request.json(),
            {
                "title": "Backup failed",
                "message": "The nightly backup did not complete",
                "level": "ERROR",
                "source": "backup-manager",
                "destinations": ["DISCORD", "MINECRAFT"],
            },
        )

    def test_send_accepts_plain_strings_and_upper_cases_them(self) -> None:
        self.service.respond("POST", "/api/alerts", text="ok")
        self.client.send("t", "m", level="warning", destinations=["discord"])
        body = self.service.last_request.json()
        self.assertEqual(body["level"], "WARNING")
        self.assertEqual(body["destinations"], ["DISCORD"])

    def test_send_omits_optional_fields_when_not_given(self) -> None:
        self.service.respond("POST", "/api/alerts", text="ok")
        self.client.send("t", "m")
        body = self.service.last_request.json()
        self.assertNotIn("source", body)
        self.assertNotIn("destinations", body)

    def test_blank_title_or_message_is_rejected_client_side(self) -> None:
        with self.assertRaises(ValueError):
            self.client.send("", "m")
        with self.assertRaises(ValueError):
            self.client.send("t", "  ")
        self.assertEqual(self.service.requests, [])

    def test_validation_failure_surfaces_as_a_response_error(self) -> None:
        self.service.respond(
            "POST",
            "/api/alerts",
            status=400,
            json_body={
                "timestamp": "2026-09-01T00:00:00Z",
                "status": 400,
                "error": "Bad Request",
                "message": "Validation failed",
            },
        )
        with self.assertRaises(OmcsiResponseError) as caught:
            self.client.send("t", "m")
        self.assertEqual(caught.exception.status, 400)
        self.assertEqual(caught.exception.message, "Validation failed")

    def test_recent_parses_records_and_passes_the_limit(self) -> None:
        self.service.respond(
            "GET",
            "/api/alerts",
            json_body=[
                {
                    "title": "Server started",
                    "message": "The server is up",
                    "level": "INFO",
                    "source": "minecraft-wrapper",
                    "receivedAt": "2026-09-01T00:00:00Z",
                }
            ],
        )
        records = self.client.recent(5)
        self.assertEqual(self.service.last_request.query["limit"], ["5"])
        self.assertEqual(len(records), 1)
        self.assertEqual(records[0].level, AlertLevel.INFO)
        self.assertEqual(records[0].source, "minecraft-wrapper")

    def test_recent_keeps_a_record_whose_level_this_client_does_not_know(self) -> None:
        self.service.respond(
            "GET", "/api/alerts", json_body=[{"title": "t", "level": "FATAL"}]
        )
        records = self.client.recent()
        self.assertEqual(len(records), 1)
        self.assertIsNone(records[0].level)
        self.assertEqual(records[0].raw["level"], "FATAL")

    def test_service_health(self) -> None:
        self.service.respond("GET", "/api/alerts/health", text="Alert Manager is running")
        self.assertEqual(self.client.service_health(), "Alert Manager is running")


if __name__ == "__main__":
    unittest.main()
