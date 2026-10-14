"""The `bundle` command: your packs inside a copy of the mod's jar, so no resource packs need turning on.

The mod reads cosmetics (`assets/<ns>/cosmetics/`), their models and textures and emotes (`assets/<ns>/emotes/`)
through the game's resources, which the mod's own jar is part of; a pack's `assets/` copied into the jar works
exactly as the pack does. Like the packs, the jar is for your own use: it is named `-personal` and never written
into this repository.

- Packs go in in the order given. A file in two packs is taken from the later one (as the higher of two resource
  packs would be), except translations (`lang/*.json`), which are merged; the same file twice is no clash.
- A pack file where the mod has one of its own is refused: it would replace part of the mod.
- The jar remembers what was bundled (`cosmeticsmod-personal.json` at its root), so bundling into a `-personal`
  jar again replaces those files instead of keeping stale ones.
"""

from __future__ import annotations

import json
import os
import re
import tempfile
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

from .archive import LayoutError

MOD_ID = "cosmeticsmod"
STAMP = "cosmeticsmod-personal.json"
SUFFIX = "-personal"
_LANG = re.compile(r"^assets/[^/]+/lang/[^/]+\.json$")


@dataclass
class Bundled:
    packs: list[str] = field(default_factory=list)
    files: int = 0
    # (path, the pack it was taken from, the earlier pack it replaced)
    replaced: list[tuple[str, str, str]] = field(default_factory=list)
    merged: list[str] = field(default_factory=list)
    # Files of an earlier bundle that were taken out.
    dropped: int = 0


def mod_id(jar: Path) -> str | None:
    """The Fabric mod id of a jar, or None if it isn't a Fabric mod (or not a readable jar)."""
    try:
        with zipfile.ZipFile(jar) as z:
            return json.loads(z.read("fabric.mod.json").decode("utf-8")).get("id")
    except (OSError, KeyError, ValueError, zipfile.BadZipFile):
        return None


def default_output(jar: Path) -> Path:
    """Next to the mod's jar, named -personal (a -personal jar is bundled into again in place)."""
    stem = jar.stem if jar.stem.endswith(SUFFIX) else jar.stem + SUFFIX
    return jar.with_name(stem + ".jar")


def _pack_files(pack: Path) -> dict[str, Path]:
    assets = pack / "assets"
    if not assets.is_dir():
        raise LayoutError(f"{pack} has no assets folder: point at a resource pack folder (the one with pack.mcmeta)")
    files = {}
    for path in sorted(assets.rglob("*")):
        if path.is_file():
            files[path.relative_to(pack).as_posix()] = path
    return files


def _merge_lang(earlier: bytes, later: bytes) -> bytes | None:
    try:
        merged = json.loads(earlier.decode("utf-8"))
        merged.update(json.loads(later.decode("utf-8")))
    except (ValueError, AttributeError):
        return None
    return (json.dumps(merged, indent=2, ensure_ascii=False, sort_keys=True) + "\n").encode("utf-8")


def bundle(jar: Path, packs: list[Path], output: Path) -> Bundled:
    if mod_id(jar) != MOD_ID:
        raise LayoutError(f"{jar} isn't the Cosmetics Mod jar (its fabric.mod.json doesn't say \"{MOD_ID}\")")
    if not packs:
        raise LayoutError("name at least one pack folder to bundle")
    result = Bundled()

    # What goes in: path -> (pack name, bytes), later packs over earlier ones.
    files: dict[str, tuple[str, bytes]] = {}
    for pack in packs:
        name = pack.resolve().name
        result.packs.append(name)
        for path, source in _pack_files(pack).items():
            data = source.read_bytes()
            if path in files and files[path][1] != data:
                earlier_pack, earlier = files[path]
                merged = _merge_lang(earlier, data) if _LANG.match(path) else None
                if merged is not None:
                    data = merged
                    result.merged.append(path)
                else:
                    result.replaced.append((path, name, earlier_pack))
            files[path] = (name, data)

    with zipfile.ZipFile(jar) as source:
        stamp = json.loads(source.read(STAMP).decode("utf-8")) if STAMP in source.namelist() else {}
        earlier_bundle = set(stamp.get("files", []))
        own = [info for info in source.infolist() if info.filename not in earlier_bundle and info.filename != STAMP]
        own_names = {info.filename for info in own}
        clashes = sorted(path for path in files if path in own_names)
        if clashes:
            raise LayoutError(f"{len(clashes)} pack file(s) are where the mod has its own (they'd replace part of "
                              "the mod), first: " + ", ".join(clashes[:5]))
        result.dropped = len(earlier_bundle)

        # Written beside the output and moved over it once complete (the input jar may be the output).
        output.parent.mkdir(parents=True, exist_ok=True)
        handle, temp = tempfile.mkstemp(prefix=output.name + ".", suffix=".tmp", dir=output.parent)
        os.close(handle)
        try:
            with zipfile.ZipFile(temp, "w", zipfile.ZIP_DEFLATED) as target:
                for info in own:
                    target.writestr(info, source.read(info))
                for path in sorted(files):
                    target.writestr(path, files[path][1])
                record = {"packs": result.packs, "files": sorted(files)}
                target.writestr(STAMP, json.dumps(record, indent=2) + "\n")
        except BaseException:
            Path(temp).unlink(missing_ok=True)
            raise
    # The input is closed now: Windows won't replace a file that is open.
    try:
        os.replace(temp, output)
    except BaseException:
        Path(temp).unlink(missing_ok=True)
        raise
    result.files = len(files)
    return result


def other_copies(output: Path) -> list[Path]:
    """Other jars of this mod beside the output in a mods folder: the game would find the mod twice and not start."""
    copies = []
    if output.parent.name.lower() != "mods":
        return copies
    for jar in sorted(output.parent.glob("*.jar")):
        if jar.resolve() != output.resolve() and mod_id(jar) == MOD_ID:
            copies.append(jar)
    return copies
