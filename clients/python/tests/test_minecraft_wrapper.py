"""Tests for MinecraftWrapperClient against a stubbed wrapper."""

from __future__ import annotations

import json
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from omcsi_client import (
    AlertDestination,
    ConfirmationRequired,
    MinecraftWrapperClient,
    OmcsiAuthError,
    OmcsiConflictError,
    OmcsiResponseError,
    Timeouts,
)

from stub_server import StubService

STATUS_PAYLOAD = {
    "running": True,
    "pid": 4242,
    "serverJar": "/mcserver/spigot-26.1.jar",
    "serverDirectory": "/mcserver",
    "uptimeSeconds": 900,
    "startedAt": "2026-09-01T00:00:00Z",
}

METRICS_PAYLOAD = {
    "wrapperHeapUsedMb": 128,
    "wrapperHeapMaxMb": 512,
    "wrapperHeapUsedPercent": 25.0,
    "serverMemoryMb": 3072,
    "serverUptimeSeconds": 900,
    "tps": "20.0, 20.0, 19.8",
}


class WrapperTestCase(unittest.TestCase):
    def setUp(self) -> None:
        self.service = StubService().start()
        self.addCleanup(self.service.stop)
        self.client = MinecraftWrapperClient(
            self.service.url, deploy_token="s3cret", timeouts=Timeouts(status=5, control=5)
        )


class TestReads(WrapperTestCase):
    def test_status_is_parsed(self) -> None:
        self.service.respond("GET", "/api/server/status", json_body=STATUS_PAYLOAD)
        status = self.client.status()
        self.assertTrue(status.running)
        self.assertEqual(status.pid, 4242)
        self.assertEqual(status.server_jar, "/mcserver/spigot-26.1.jar")
        self.assertEqual(status.uptime_seconds, 900)
        self.assertEqual(status.raw, STATUS_PAYLOAD)

    def test_status_tolerates_a_stopped_server_payload(self) -> None:
        # @JsonInclude(NON_NULL) drops pid/uptime/startedAt when nothing runs.
        self.service.respond(
            "GET",
            "/api/server/status",
            json_body={"running": False, "serverDirectory": "/mcserver"},
        )
        status = self.client.status()
        self.assertFalse(status.running)
        self.assertIsNone(status.pid)
        self.assertIsNone(status.started_at)

    def test_metrics_is_parsed(self) -> None:
        self.service.respond("GET", "/api/server/metrics", json_body=METRICS_PAYLOAD)
        metrics = self.client.metrics()
        self.assertEqual(metrics.wrapper_heap_used_mb, 128)
        self.assertEqual(metrics.wrapper_heap_used_percent, 25.0)
        self.assertEqual(metrics.tps, "20.0, 20.0, 19.8")
        self.assertIsNone(metrics.tps_note)

    def test_logs_sends_the_lines_parameter(self) -> None:
        self.service.respond(
            "GET", "/api/server/logs", json_body={"lines": ["a", "b"], "count": 2}
        )
        tail = self.client.logs(25)
        self.assertEqual(tail.lines, ["a", "b"])
        self.assertEqual(tail.count, 2)
        self.assertEqual(self.service.last_request.query["lines"], ["25"])

    def test_logs_disabled_raises_with_the_service_message(self) -> None:
        self.service.respond(
            "GET",
            "/api/server/logs",
            status=403,
            text="Server log access is disabled. Set logs.diagnostic.enabled=true to enable.",
        )
        with self.assertRaises(OmcsiResponseError) as caught:
            self.client.logs()
        self.assertEqual(caught.exception.status, 403)
        self.assertIn("logs.diagnostic.enabled=true", caught.exception.message)

    def test_wait_until_running_polls_until_the_server_reports_running(self) -> None:
        answers = [
            {"running": False},
            {"running": False},
            {"running": True, "pid": 7},
        ]

        @self.service.handler("GET", "/api/server/status")
        def _status(_request):
            from stub_server import Response

            payload = answers.pop(0) if len(answers) > 1 else answers[0]
            return Response(
                status=200,
                body=json.dumps(payload).encode(),
                content_type="application/json",
            )

        status = self.client.wait_until_running(timeout=5, poll_interval=0.01)
        self.assertTrue(status.running)
        self.assertEqual(status.pid, 7)


class TestLifecycle(WrapperTestCase):
    def test_start_returns_the_accepted_message(self) -> None:
        self.service.respond(
            "POST", "/api/server/start", status=202, text="Server start initiated"
        )
        self.assertEqual(self.client.start_server(), "Server start initiated")

    def test_start_on_a_running_server_raises_a_conflict(self) -> None:
        self.service.respond(
            "POST", "/api/server/start", status=409, text="Server is already running"
        )
        with self.assertRaises(OmcsiConflictError) as caught:
            self.client.start_server()
        self.assertEqual(caught.exception.status, 409)
        self.assertEqual(caught.exception.message, "Server is already running")

    def test_stop_requires_confirmation_and_sends_nothing_without_it(self) -> None:
        self.service.respond("POST", "/api/server/stop", status=202, text="Server stop initiated")
        with self.assertRaises(ConfirmationRequired):
            self.client.disruptive.stop()
        self.assertEqual(self.service.requests, [])
        self.assertEqual(
            self.client.disruptive.stop(confirm=True), "Server stop initiated"
        )

    def test_restart_and_shutdown_require_confirmation(self) -> None:
        self.service.respond("POST", "/api/server/restart", status=202, text="restarting")
        self.service.respond("POST", "/api/server/shutdown", status=202, text="shutting down")
        with self.assertRaises(ConfirmationRequired):
            self.client.disruptive.restart()
        with self.assertRaises(ConfirmationRequired):
            self.client.disruptive.graceful_shutdown()
        self.assertEqual(self.client.disruptive.restart(confirm=True), "restarting")
        self.assertEqual(
            self.client.disruptive.graceful_shutdown(confirm=True), "shutting down"
        )

    def test_stop_when_not_running_raises_a_conflict(self) -> None:
        self.service.respond(
            "POST", "/api/server/stop", status=409, text="Server is not running"
        )
        with self.assertRaises(OmcsiConflictError):
            self.client.disruptive.stop(confirm=True)


class TestConsoleCommand(WrapperTestCase):
    def setUp(self) -> None:
        super().setUp()
        self.service.respond("POST", "/api/server/command", text="Command sent successfully")

    def test_command_requires_confirmation(self) -> None:
        with self.assertRaises(ConfirmationRequired):
            self.client.disruptive.send_console_command("seed")
        self.assertEqual(self.service.requests, [])

    def test_command_is_sent_as_plain_text_not_json(self) -> None:
        # The wrapper takes a bare `@RequestBody String`; a JSON document
        # would reach the console with its quotes attached.
        self.client.disruptive.send_console_command("say hello", confirm=True)
        request = self.service.last_request
        self.assertEqual(request.text(), "say hello")
        self.assertTrue(request.headers["Content-Type"].startswith("text/plain"))

    def test_blank_command_is_rejected_client_side(self) -> None:
        with self.assertRaises(ValueError):
            self.client.disruptive.send_console_command("   ", confirm=True)
        self.assertEqual(self.service.requests, [])


class TestMessages(WrapperTestCase):
    def test_message_defaults_to_minecraft(self) -> None:
        self.service.respond("POST", "/api/messages", text="Message sent successfully")
        self.client.send_message("server going down in 5")
        body = self.service.last_request.json()
        self.assertEqual(body, {"text": "server going down in 5", "destination": "MINECRAFT"})

    def test_message_destination_enum_is_serialised_by_value(self) -> None:
        self.service.respond("POST", "/api/messages", text="ok")
        self.client.send_message("hi", AlertDestination.DISCORD)
        self.assertEqual(self.service.last_request.json()["destination"], "DISCORD")

    def test_blank_message_is_rejected_client_side(self) -> None:
        with self.assertRaises(ValueError):
            self.client.send_message("  ")


class TestUploads(WrapperTestCase):
    def setUp(self) -> None:
        super().setUp()
        self.tmp = TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.jar = Path(self.tmp.name) / "MyPlugin-1.2.3.jar"
        self.jar.write_bytes(b"PK\x03\x04fake jar bytes")

    def test_deploy_sends_multipart_with_bearer_token(self) -> None:
        self.service.respond("POST", "/api/plugins/deploy", text="Plugin deployed successfully")
        result = self.client.deploy_plugin(
            self.jar, plugin_name="MyPlugin.jar", branch="main", repo_url="https://example.test/r"
        )
        self.assertEqual(result, "Plugin deployed successfully")
        request = self.service.last_request
        self.assertEqual(request.headers["Authorization"], "Bearer s3cret")
        fields, files = request.multipart()
        self.assertEqual(fields["pluginName"], "MyPlugin.jar")
        self.assertEqual(fields["branch"], "main")
        self.assertEqual(fields["repoUrl"], "https://example.test/r")
        self.assertEqual(files["file"][0], "MyPlugin.jar")
        self.assertEqual(files["file"][1], b"PK\x03\x04fake jar bytes")

    def test_deploy_sets_content_length_rather_than_chunking(self) -> None:
        # Spring's multipart resolver and nginx both cope far better with a
        # declared length than with chunked transfer encoding.
        self.service.respond("POST", "/api/plugins/deploy", text="ok")
        self.client.deploy_plugin(self.jar, plugin_name="MyPlugin.jar")
        headers = self.service.last_request.headers
        self.assertIn("Content-Length", headers)
        self.assertNotIn("Transfer-Encoding", headers)
        self.assertEqual(int(headers["Content-Length"]), len(self.service.last_request.body))

    def test_deploy_defaults_the_plugin_name_to_the_file_name(self) -> None:
        self.service.respond("POST", "/api/plugins/deploy", text="ok")
        self.client.deploy_plugin(self.jar)
        fields, _ = self.service.last_request.multipart()
        self.assertEqual(fields["pluginName"], "MyPlugin-1.2.3.jar")

    def test_deploy_rejects_names_the_server_would_reject(self) -> None:
        for bad in ("MyPlugin.zip", "../evil.jar", "nested/MyPlugin.jar"):
            with self.subTest(name=bad):
                with self.assertRaises(ValueError):
                    self.client.deploy_plugin(self.jar, plugin_name=bad)
        self.assertEqual(self.service.requests, [])

    def test_deploy_without_a_token_fails_before_sending_anything(self) -> None:
        tokenless = MinecraftWrapperClient(self.service.url)
        with self.assertRaises(OmcsiAuthError):
            tokenless.deploy_plugin(self.jar, plugin_name="MyPlugin.jar")
        self.assertEqual(self.service.requests, [])

    def test_deploy_surfaces_a_server_side_401(self) -> None:
        self.service.respond("POST", "/api/plugins/deploy", status=401, text="Unauthorized")
        with self.assertRaises(OmcsiAuthError) as caught:
            self.client.deploy_plugin(self.jar, plugin_name="MyPlugin.jar")
        self.assertEqual(caught.exception.status, 401)

    def test_world_upload_requires_confirmation(self) -> None:
        archive = Path(self.tmp.name) / "world.zip"
        archive.write_bytes(b"PK\x03\x04world")
        self.service.respond("POST", "/api/world/upload", text="World uploaded successfully")
        with self.assertRaises(ConfirmationRequired):
            self.client.disruptive.replace_world(archive)
        self.assertEqual(self.service.requests, [])
        self.assertEqual(
            self.client.disruptive.replace_world(archive, confirm=True),
            "World uploaded successfully",
        )
        _, files = self.service.last_request.multipart()
        self.assertEqual(files["file"][0], "world.zip")
        self.assertEqual(files["file"][1], b"PK\x03\x04world")

    def test_missing_file_is_reported_before_the_request(self) -> None:
        with self.assertRaises(ValueError):
            self.client.deploy_plugin(Path(self.tmp.name) / "absent.jar")


class TestHealth(WrapperTestCase):
    def test_actuator_health(self) -> None:
        self.service.respond("GET", "/actuator/health", json_body={"status": "UP"})
        self.assertEqual(self.client.health()["status"], "UP")


if __name__ == "__main__":
    unittest.main()
