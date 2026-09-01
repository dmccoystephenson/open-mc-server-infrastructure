"""Tests for the transport layer: reachability, timeouts, error unwrapping.

The distinction these cover — "the service said no" versus "the service was
not there" — is the one thing a caller most needs the client to get right,
because the correct reaction differs: retry the second, not the first.
"""

from __future__ import annotations

import unittest

from omcsi_client import (
    MinecraftWrapperClient,
    OmcsiError,
    OmcsiResponseError,
    OmcsiTimeoutError,
    OmcsiTransportError,
    Timeouts,
)

from stub_server import StubService, closed_port


class TestUnreachableService(unittest.TestCase):
    def test_connection_refused_is_a_transport_error_with_no_status(self) -> None:
        client = MinecraftWrapperClient(
            f"http://127.0.0.1:{closed_port()}", timeouts=Timeouts(status=2)
        )
        with self.assertRaises(OmcsiTransportError) as caught:
            client.status()
        self.assertNotIsInstance(caught.exception, OmcsiResponseError)
        self.assertFalse(hasattr(caught.exception, "status"))

    def test_transport_errors_are_omcsi_errors(self) -> None:
        client = MinecraftWrapperClient(f"http://127.0.0.1:{closed_port()}")
        with self.assertRaises(OmcsiError):
            client.status()

    def test_empty_base_url_is_rejected_at_construction(self) -> None:
        with self.assertRaises(ValueError):
            MinecraftWrapperClient("")


class TestTimeouts(unittest.TestCase):
    def setUp(self) -> None:
        self.service = StubService().start()
        self.addCleanup(self.service.stop)

    def test_a_slow_response_raises_a_timeout_error(self) -> None:
        self.service.respond(
            "GET", "/api/server/status", json_body={"running": True}, delay=2.0
        )
        client = MinecraftWrapperClient(self.service.url, timeouts=Timeouts(status=0.2))
        with self.assertRaises(OmcsiTimeoutError):
            client.status()

    def test_a_timeout_is_a_transport_error_not_a_response_error(self) -> None:
        # Nothing came back, so there is no status to report and no server
        # decision to react to.
        self.service.respond("GET", "/api/server/status", json_body={}, delay=2.0)
        client = MinecraftWrapperClient(self.service.url, timeouts=Timeouts(status=0.2))
        with self.assertRaises(OmcsiTransportError):
            client.status()

    def test_a_per_call_timeout_overrides_the_client_default(self) -> None:
        self.service.respond(
            "GET", "/api/server/status", json_body={"running": True}, delay=0.4
        )
        client = MinecraftWrapperClient(self.service.url, timeouts=Timeouts(status=0.1))
        with self.assertRaises(OmcsiTimeoutError):
            client.status()
        self.assertTrue(client.status(timeout=5).running)


class TestErrorBodies(unittest.TestCase):
    def setUp(self) -> None:
        self.service = StubService().start()
        self.addCleanup(self.service.stop)
        self.client = MinecraftWrapperClient(self.service.url, timeouts=Timeouts(status=5))

    def test_a_structured_error_body_yields_its_message_field(self) -> None:
        self.service.respond(
            "GET",
            "/api/server/status",
            status=500,
            json_body={
                "timestamp": "2026-09-01T00:00:00Z",
                "status": 500,
                "error": "Internal Server Error",
                "message": "An unexpected error occurred",
            },
        )
        with self.assertRaises(OmcsiResponseError) as caught:
            self.client.status()
        self.assertEqual(caught.exception.message, "An unexpected error occurred")
        self.assertEqual(caught.exception.status, 500)

    def test_a_bare_string_error_body_is_used_verbatim(self) -> None:
        self.service.respond(
            "POST", "/api/server/command", status=400, text="Server is not running"
        )
        with self.assertRaises(OmcsiResponseError) as caught:
            self.client.disruptive.send_console_command("seed", confirm=True)
        self.assertEqual(caught.exception.message, "Server is not running")

    def test_the_raw_body_is_preserved_for_anything_unparsed(self) -> None:
        self.service.respond("GET", "/api/server/status", status=502, text="<html>bad gateway")
        with self.assertRaises(OmcsiResponseError) as caught:
            self.client.status()
        self.assertEqual(caught.exception.body, b"<html>bad gateway")

    def test_the_exception_string_names_the_method_and_url(self) -> None:
        self.service.respond("GET", "/api/server/status", status=404, text="nope")
        with self.assertRaises(OmcsiResponseError) as caught:
            self.client.status()
        text = str(caught.exception)
        self.assertIn("GET", text)
        self.assertIn("/api/server/status", text)
        self.assertIn("404", text)

    def test_a_non_json_body_where_json_is_expected_is_a_response_error(self) -> None:
        self.service.respond("GET", "/api/server/status", status=200, text="not json at all")
        with self.assertRaises(OmcsiResponseError):
            self.client.status()


if __name__ == "__main__":
    unittest.main()
