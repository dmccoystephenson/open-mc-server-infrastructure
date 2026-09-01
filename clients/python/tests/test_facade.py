"""Tests for the Omcsi facade and its environment wiring."""

from __future__ import annotations

import os
import unittest
from unittest import mock

from omcsi_client import Omcsi, Timeouts

from stub_server import StubService


class TestFacade(unittest.TestCase):
    def test_defaults_point_at_the_documented_ports(self) -> None:
        omcsi = Omcsi()
        self.assertEqual(omcsi.wrapper.base_url, "http://localhost:8092")
        self.assertEqual(omcsi.backups.base_url, "http://localhost:8091")
        self.assertEqual(omcsi.alerts.base_url, "http://localhost:8090")
        self.assertIsNone(omcsi.deposit_box)

    def test_trailing_slashes_are_normalised(self) -> None:
        omcsi = Omcsi(wrapper_url="http://example.test:8092/")
        self.assertEqual(omcsi.wrapper.base_url, "http://example.test:8092")

    def test_timeouts_are_shared_across_the_sub_clients(self) -> None:
        timeouts = Timeouts(status=1, control=2, deploy=3, upload=4, backup=5)
        omcsi = Omcsi(timeouts=timeouts)
        self.assertEqual(omcsi.wrapper.timeouts, timeouts)
        self.assertEqual(omcsi.backups.timeouts, timeouts)
        self.assertEqual(omcsi.alerts.timeouts, timeouts)

    def test_construction_contacts_nothing(self) -> None:
        # Building a handle on a deployment that is down must not raise.
        Omcsi(wrapper_url="http://127.0.0.1:1", backup_url="http://127.0.0.1:1")

    def test_from_env_reads_every_documented_variable(self) -> None:
        env = {
            "OMCSI_WRAPPER_URL": "https://mc.example.test",
            "OMCSI_BACKUP_URL": "https://backups.example.test",
            "OMCSI_ALERT_URL": "https://alerts.example.test",
            "OMCSI_DEPLOY_TOKEN": "tok",
            "OMCSI_DEPOSIT_BOX": "/srv/omcsi/deposit-box",
        }
        with mock.patch.dict(os.environ, env, clear=True):
            omcsi = Omcsi.from_env()
        self.assertEqual(omcsi.wrapper.base_url, "https://mc.example.test")
        self.assertEqual(omcsi.backups.base_url, "https://backups.example.test")
        self.assertEqual(omcsi.alerts.base_url, "https://alerts.example.test")
        self.assertIsNotNone(omcsi.deposit_box)
        self.assertEqual(omcsi.deposit_box.container_path_for("x"), "/deposit-box/x")

    def test_from_env_accepts_the_existing_omcsi_api_base_variable(self) -> None:
        # OMCSI's own integration-test harness already sets OMCSI_API_BASE for
        # the wrapper, so honouring it saves callers a rename.
        with mock.patch.dict(
            os.environ, {"OMCSI_API_BASE": "http://wrapper.example.test:8092"}, clear=True
        ):
            omcsi = Omcsi.from_env()
        self.assertEqual(omcsi.wrapper.base_url, "http://wrapper.example.test:8092")

    def test_omcsi_wrapper_url_wins_over_omcsi_api_base(self) -> None:
        with mock.patch.dict(
            os.environ,
            {
                "OMCSI_API_BASE": "http://old.example.test",
                "OMCSI_WRAPPER_URL": "http://new.example.test",
            },
            clear=True,
        ):
            omcsi = Omcsi.from_env()
        self.assertEqual(omcsi.wrapper.base_url, "http://new.example.test")

    def test_verify_tls_flag_is_read_from_the_environment(self) -> None:
        with mock.patch.dict(os.environ, {"OMCSI_VERIFY_TLS": "0"}, clear=True):
            omcsi = Omcsi.from_env()
        self.assertFalse(omcsi.wrapper._transport.verify_tls)
        with mock.patch.dict(os.environ, {}, clear=True):
            omcsi = Omcsi.from_env()
        self.assertTrue(omcsi.wrapper._transport.verify_tls)

    def test_the_deploy_token_reaches_the_wrapper_client_only(self) -> None:
        service = StubService().start()
        self.addCleanup(service.stop)
        omcsi = Omcsi(wrapper_url=service.url, deploy_token="tok")
        self.assertEqual(omcsi.wrapper._transport.token, "tok")
        self.assertIsNone(omcsi.backups._transport.token)
        self.assertIsNone(omcsi.alerts._transport.token)


if __name__ == "__main__":
    unittest.main()
