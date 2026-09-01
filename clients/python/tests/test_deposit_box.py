"""Tests for the deposit box helper.

Filesystem-only: the deposit box is a bind mount, not an endpoint, so there is
nothing here to stub over HTTP.
"""

from __future__ import annotations

import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from omcsi_client import DepositBox


class TestDepositBox(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.box_dir = self.root / "deposit-box"
        self.box_dir.mkdir()
        (self.box_dir / "README.md").write_text("# Deposit Box\n")
        self.box = DepositBox(self.box_dir)
        self.source = self.root / "world.zip"
        self.source.write_bytes(b"PK\x03\x04world archive")

    def test_stage_copies_and_reports_both_paths(self) -> None:
        staged = self.box.stage(self.source)
        self.assertEqual(staged.host_path, self.box_dir / "world.zip")
        self.assertEqual(staged.container_path, "/deposit-box/world.zip")
        self.assertEqual(staged.host_path.read_bytes(), b"PK\x03\x04world archive")
        # A copy, not a move: a failed transfer must not cost the original.
        self.assertTrue(self.source.exists())

    def test_stage_can_rename(self) -> None:
        staged = self.box.stage(self.source, name="incoming.zip")
        self.assertEqual(staged.container_path, "/deposit-box/incoming.zip")

    def test_stage_refuses_a_name_that_escapes_the_box(self) -> None:
        for bad in ("../escaped.zip", "..", "nested/../../escaped.zip"):
            with self.subTest(name=bad):
                with self.assertRaises(ValueError):
                    self.box.stage(self.source, name=bad)

    def test_stage_rejects_a_missing_source(self) -> None:
        with self.assertRaises(ValueError):
            self.box.stage(self.root / "absent.zip")

    def test_a_missing_box_directory_says_what_it_is(self) -> None:
        box = DepositBox(self.root / "nope")
        self.assertFalse(box.exists)
        with self.assertRaises(FileNotFoundError) as caught:
            box.stage(self.source)
        self.assertIn("deposit-box", str(caught.exception))

    def test_list_files_hides_the_directory_readme(self) -> None:
        self.box.stage(self.source)
        self.assertEqual(self.box.list_files(), ["world.zip"])

    def test_retrieve_copies_back_out(self) -> None:
        self.box.stage(self.source, name="collected.zip")
        destination = self.root / "out"
        destination.mkdir()
        result = self.box.retrieve("collected.zip", destination)
        self.assertEqual(result, destination / "collected.zip")
        self.assertEqual(result.read_bytes(), b"PK\x03\x04world archive")

    def test_retrieve_handles_a_directory(self) -> None:
        tree = self.box_dir / "plugins"
        tree.mkdir()
        (tree / "a.jar").write_bytes(b"jar")
        out = self.root / "collected"
        self.box.retrieve("plugins", out)
        self.assertEqual((out / "a.jar").read_bytes(), b"jar")

    def test_retrieve_missing_entry_raises(self) -> None:
        with self.assertRaises(FileNotFoundError):
            self.box.retrieve("absent.zip", self.root)

    def test_remove_deletes_one_entry(self) -> None:
        self.box.stage(self.source)
        self.box.remove("world.zip")
        self.assertEqual(self.box.list_files(), [])

    def test_container_path_for_needs_no_disk_access(self) -> None:
        box = DepositBox("/nowhere", container_path="/deposit-box/")
        self.assertEqual(box.container_path_for("world.zip"), "/deposit-box/world.zip")


if __name__ == "__main__":
    unittest.main()
