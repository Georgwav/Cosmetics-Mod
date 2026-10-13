"""Read-only access to Badlion's data jars, through their assets.json.

Only zip central directories are read up front. A file's bytes are read only when asked for, one entry at a
time; nothing is extracted to disk and nothing in the input folder is ever written.
"""

from __future__ import annotations

import hashlib
import json
import zipfile
from dataclasses import dataclass
from pathlib import Path

MANIFEST = "assets.json"


class LayoutError(Exception):
    """The input doesn't look the way the importer expects; the message says what differs."""


@dataclass(frozen=True)
class Listed:
    """One file as assets.json lists it."""

    path: str
    chunk: int
    hash: str
    size: int


def jar_name(chunk: int) -> str:
    return f"data{chunk}.jar"


def refuse_minecraft_dir(folder: Path) -> None:
    """The importer must never read the live Badlion install; it works on copies only."""
    parts = [p.lower() for p in folder.resolve().parts]
    if ".minecraft" in parts:
        raise LayoutError(
            f"{folder} is inside a .minecraft folder. Copy the data jars and assets.json to a folder of "
            "your own (and trim them to what you own) and point the importer there instead."
        )


def hash_kind(value: str) -> str | None:
    """The digest a hex string's length suggests."""
    return {32: "md5", 40: "sha1", 64: "sha256"}.get(len(value)) if _is_hex(value) else None


def _is_hex(value: str) -> bool:
    try:
        int(value, 16)
        return True
    except ValueError:
        return False


class Archive:
    """The input folder: assets.json plus whichever data<N>.jar files are there."""

    def __init__(self, folder: Path):
        folder = Path(folder)
        refuse_minecraft_dir(folder)
        if not folder.is_dir():
            raise LayoutError(f"{folder} is not a folder")
        manifest = folder / MANIFEST
        if not manifest.is_file():
            raise LayoutError(f"{folder} has no {MANIFEST}")
        self.folder = folder
        try:
            self.manifest = json.loads(manifest.read_text(encoding="utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as e:
            raise LayoutError(f"{MANIFEST} is not valid JSON: {e}") from e
        self.listed: dict[str, Listed] = {}
        self.duplicates: list[str] = []
        self._read_manifest()
        self.jars: dict[int, Path] = {}
        for chunk in sorted({entry.chunk for entry in self.listed.values()}):
            path = folder / jar_name(chunk)
            if path.is_file():
                self.jars[chunk] = path
        self._zips: dict[int, zipfile.ZipFile] = {}
        self._entries: dict[int, dict[str, zipfile.ZipInfo]] = {}

    def _read_manifest(self) -> None:
        m = self.manifest
        if not isinstance(m, dict) or not isinstance(m.get("chunkData"), list):
            keys = sorted(m) if isinstance(m, dict) else type(m).__name__
            raise LayoutError(f"{MANIFEST}: expected an object with a 'chunkData' list, found {keys}")
        for i, chunk in enumerate(m["chunkData"]):
            if not isinstance(chunk, dict) or not isinstance(chunk.get("chunkId"), int) \
                    or not isinstance(chunk.get("files"), list):
                found = sorted(chunk) if isinstance(chunk, dict) else type(chunk).__name__
                raise LayoutError(f"{MANIFEST}: chunkData[{i}] should have an int 'chunkId' and a 'files' list, "
                                  f"found {found}")
            for j, f in enumerate(chunk["files"]):
                if not isinstance(f, dict) or not isinstance(f.get("absolutePath"), str):
                    found = sorted(f) if isinstance(f, dict) else type(f).__name__
                    raise LayoutError(f"{MANIFEST}: chunkData[{i}].files[{j}] should have 'absolutePath', "
                                      f"'hash' and 'size', found {found}")
                path = f["absolutePath"].replace("\\", "/").lstrip("/")
                entry = Listed(path, chunk["chunkId"], str(f.get("hash", "")).lower(), int(f.get("size", -1)))
                if path in self.listed:
                    self.duplicates.append(path)
                self.listed[path] = entry

    # ------------------------------------------------------------------ zips

    def _zip(self, chunk: int) -> zipfile.ZipFile:
        z = self._zips.get(chunk)
        if z is None:
            try:
                z = zipfile.ZipFile(self.jars[chunk], "r")
            except zipfile.BadZipFile as e:
                raise LayoutError(f"{jar_name(chunk)} is not a zip file: {e}") from e
            self._zips[chunk] = z
        return z

    def entries(self, chunk: int) -> dict[str, zipfile.ZipInfo]:
        """Entries of one jar by name (from its central directory only)."""
        names = self._entries.get(chunk)
        if names is None:
            names = {info.filename: info for info in self._zip(chunk).infolist() if not info.is_dir()}
            self._entries[chunk] = names
        return names

    def locate(self, path: str) -> tuple[int, zipfile.ZipInfo] | None:
        """Where a file is: the chunk assets.json names, else any jar that has an entry by that name."""
        listed = self.listed.get(path)
        if listed is not None and listed.chunk in self.jars:
            info = self.entries(listed.chunk).get(path)
            if info is not None:
                return listed.chunk, info
        for chunk in self.jars:
            info = self.entries(chunk).get(path)
            if info is not None:
                return chunk, info
        return None

    def has(self, path: str) -> bool:
        return self.locate(path) is not None

    def read(self, path: str) -> bytes:
        found = self.locate(path)
        if found is None:
            raise KeyError(path)
        chunk, info = found
        return self._zip(chunk).read(info)

    def present_paths(self) -> set[str]:
        """Every file in the jars that are there."""
        paths: set[str] = set()
        for chunk in self.jars:
            paths.update(self.entries(chunk))
        return paths

    def content_hash(self, path: str) -> str:
        """The hash to key incremental imports on: assets.json's when it matches the bytes, else SHA-1."""
        listed = self.listed.get(path)
        return listed.hash if listed is not None and listed.hash else hashlib.sha1(self.read(path)).hexdigest()

    def verify(self, path: str) -> str:
        """'ok', 'size' (size differs), 'hash:<kind>' (matches a digest other than SHA-1), 'mismatch' or 'unlisted'."""
        listed = self.listed.get(path)
        if listed is None:
            return "unlisted"
        data = self.read(path)
        if listed.size >= 0 and len(data) != listed.size:
            return "size"
        if hashlib.sha1(data).hexdigest() == listed.hash:
            return "ok"
        for kind in ("md5", "sha256"):
            if hashlib.new(kind, data).hexdigest() == listed.hash:
                return "hash:" + kind
        return "mismatch"

    def close(self) -> None:
        for z in self._zips.values():
            z.close()
        self._zips.clear()

    def __enter__(self) -> "Archive":
        return self

    def __exit__(self, *exc) -> None:
        self.close()
