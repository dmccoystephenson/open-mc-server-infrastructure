"""The deposit box: the file channel that is not an HTTP endpoint.

OMCSI's ``deposit-box/`` directory is bind-mounted into the Minecraft server
container at ``/deposit-box``. Nothing serves it and no API reads from it — it
is a plain shared directory, and that is exactly why it matters here.

The HTTP upload endpoints (``/api/plugins/deploy``, ``/api/world/upload``) are
the right route for a plugin JAR and for a small world. They are the wrong
route for a large one: the upload is not resumable, it is buffered to disk on
the way through, and it must clear nginx's body limit, the web app's multipart
limit and the wrapper's own size cap in series. A multi-GB world that drops at
90% starts from zero.

Putting the file in the deposit box sidesteps all of that. The cost is that it
is only half of an operation: this class gets the file to a path the container
can see, and something else — an ``exec`` into the container, or a shell on the
node — has to move it into place. :meth:`DepositBox.stage` is deliberately
honest about that boundary and returns both paths so the caller can build the
second half.

The deposit box is a Docker Compose feature. The Helm chart does not mount it,
so on Kubernetes the equivalent is ``kubectl cp`` into the pod.
"""

from __future__ import annotations

import os
import shutil
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Union

__all__ = ["DepositBox", "StagedFile"]

PathLike = Union[str, "os.PathLike[str]"]

DEFAULT_CONTAINER_PATH = "/deposit-box"


@dataclass(frozen=True)
class StagedFile:
    """A file placed in the deposit box, seen from both sides of the mount."""

    host_path: Path
    container_path: str

    def __str__(self) -> str:  # pragma: no cover - trivial
        return f"{self.host_path} -> {self.container_path}"


class DepositBox:
    """The host side of OMCSI's shared ``deposit-box`` directory.

    :param host_path: the directory on the host, usually ``deposit-box``
        inside an OMCSI checkout.
    :param container_path: where that directory is mounted inside the
        container. Only change this if the compose file was changed.
    """

    def __init__(
        self,
        host_path: PathLike,
        *,
        container_path: str = DEFAULT_CONTAINER_PATH,
    ) -> None:
        self.host_path = Path(host_path)
        self.container_path = container_path.rstrip("/") or "/"

    def __repr__(self) -> str:  # pragma: no cover - trivial
        return f"DepositBox({str(self.host_path)!r}, container_path={self.container_path!r})"

    @property
    def exists(self) -> bool:
        """Whether the host directory is actually there."""
        return self.host_path.is_dir()

    def _require_dir(self) -> Path:
        if not self.host_path.is_dir():
            raise FileNotFoundError(
                f"deposit box directory not found: {self.host_path}. "
                "This is the 'deposit-box' directory inside an OMCSI checkout, "
                "bind-mounted into the server container by compose.yml."
            )
        return self.host_path

    def _resolve(self, name: str) -> Path:
        """Resolve a name inside the box, refusing anything that escapes it."""
        if not name or name in (".", ".."):
            raise ValueError(f"invalid deposit box entry name: {name!r}")
        base = self.host_path.resolve()
        target = (base / name).resolve()
        if target != base and base not in target.parents:
            raise ValueError(f"{name!r} resolves outside the deposit box")
        return target

    def stage(self, source: PathLike, *, name: Optional[str] = None) -> StagedFile:
        """Copy ``source`` into the deposit box and report where it landed.

        Returns a :class:`StagedFile` carrying the host path (for checking the
        copy) and the container path (for the ``docker exec`` or ``kubectl
        exec`` that finishes the job). Copying, not moving, so a failed
        transfer never costs the original.
        """
        self._require_dir()
        src = Path(source)
        if not src.is_file():
            raise ValueError(f"not a readable file: {src}")
        target = self._resolve(name or src.name)
        shutil.copy2(src, target)
        return StagedFile(
            host_path=target,
            container_path=f"{self.container_path}/{target.name}",
        )

    def retrieve(self, name: str, destination: PathLike) -> Path:
        """Copy something out of the deposit box to ``destination``.

        The other direction of the same channel: the documented way to get a
        world or a plugins directory *off* the server is to copy it into
        ``/deposit-box`` from inside the container, then collect it here.
        """
        self._require_dir()
        source = self._resolve(name)
        if not source.exists():
            raise FileNotFoundError(f"no such entry in the deposit box: {name}")
        target = Path(destination)
        if target.is_dir():
            target = target / source.name
        if source.is_dir():
            shutil.copytree(source, target, dirs_exist_ok=True)
        else:
            shutil.copy2(source, target)
        return target

    def list_files(self) -> List[str]:
        """Names of the entries currently in the box, excluding its README."""
        self._require_dir()
        return sorted(
            entry.name
            for entry in self.host_path.iterdir()
            if entry.name not in ("README.md", ".gitkeep")
        )

    def remove(self, name: str) -> None:
        """Delete one entry from the box.

        Scoped to a single named entry on purpose — there is no "empty the
        box" here, because the deposit box is also where retrieved server
        data waits to be collected.
        """
        self._require_dir()
        target = self._resolve(name)
        if target.is_dir():
            shutil.rmtree(target)
        elif target.exists():
            target.unlink()

    def container_path_for(self, name: str) -> str:
        """The in-container path a given entry has, without touching the disk."""
        return f"{self.container_path}/{name}"
