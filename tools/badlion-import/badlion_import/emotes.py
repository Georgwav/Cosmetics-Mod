"""Emotes (player animations) from any folder of client files into a resource pack for the mod's emote wheel.

Clients keep emotes in different places and formats, so this looks through everything under a folder (and inside
any .jar or .zip in it) for the formats the mod plays:

- **Bedrock / GeckoLib animations** (`{"animations": {...}}`, what Blockbench exports, and what Lunar and NoRisk
  build with) whose bones are a player's: `head`, `body`, `right_arm`, ... in any spelling, GeckoLib's
  `bipedHead`, `bipedRightArm`, ... too. Each animation becomes one emote, its bones renamed to the mod's and
  anything the mod can't play dropped (other bones, `scale`, channels written in Molang), with the reason in the
  report. Animations of cosmetics (files next to a cosmetic's `.geo.json`, `.gek.json` or `.norisk.json`) aren't
  emotes and are left out, unless their path says "emote". Inside archives (mostly mods, whose mobs have bones
  named like a player's too) only animations whose path or name says "emote" are taken.
- **Emotecraft** JSON emotes (`{"emote": {"moves": [...]}}`), copied as they are.

Anything else that looks like an emote (a path containing "emote", or Emotecraft's binary `.emotecraft`) is listed
in the report with what it is, so it can be found and supported.
"""

from __future__ import annotations

import io
import json
import re
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

from . import __version__
from .archive import LayoutError

MARKER = ".emote-import.json"
REPORT = "emote-report.md"
ARCHIVES = {".jar", ".zip", ".mcpack", ".pak"}
MAX_JSON_BYTES = 8_000_000

PACK_MCMETA = {
    "pack": {
        "description": "Your own client emotes, for Cosmetics Mod (personal use only)",
        "pack_format": 34,
        "supported_formats": {"min_inclusive": 34, "max_inclusive": 999},
        "min_format": 34,
        "max_format": 999,
    }
}

# The mod's player bones, by every name a client gives them (lower case, without _ - . or spaces).
BONES = {
    "head": ("head", "bipedhead", "playerhead"),
    "body": ("body", "torso", "chest", "upperbody", "bipedbody", "playerbody"),
    "waist": ("waist", "hips", "hip"),
    "root": ("root", "player", "wholebody", "entity"),
    "right_arm": ("rightarm", "armright", "rarm", "bipedrightarm", "playerrightarm"),
    "left_arm": ("leftarm", "armleft", "larm", "bipedleftarm", "playerleftarm"),
    "right_leg": ("rightleg", "legright", "rleg", "bipedrightleg", "playerrightleg"),
    "left_leg": ("leftleg", "legleft", "lleg", "bipedleftleg", "playerleftleg"),
}
_BONE_OF = {alias: bone for bone, aliases in BONES.items() for alias in aliases}
# Channels the mod plays (it has no scale).
CHANNELS = ("rotation", "position")
# Files that say a folder holds a cosmetic, whose animations are the cosmetic's own.
COSMETIC_FILES = (".geo.json", ".gek.json", ".norisk.json")
NUMBER = re.compile(r"^\s*[-+]?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?\s*$")


def bone_of(name: str) -> str | None:
    """The mod's name for a player bone, or None for any other bone."""
    return _BONE_OF.get(re.sub(r"[\s_.\-]+", "", name.lower()))


def slug(text: str) -> str:
    s = re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")
    return s or "emote"


def namespace_for(path: str) -> str:
    """Emotes keep the client they came from in their id, guessed from where they were found."""
    p = path.lower()
    if "lunar" in p:
        return "lunar_emotes"
    if "norisk" in p:
        return "norisk_emotes"
    if "badlion" in p or "blc" in p:
        return "badlion_emotes"
    return "imported_emotes"


# ---------------------------------------------------------------------- what was found


@dataclass
class Emote:
    id: str  # namespace:name
    source: str  # where it came from (inside an archive: archive!/entry)
    doc: dict  # the emote file to write
    notes: list[str] = field(default_factory=list)


@dataclass
class Found:
    emotes: list[Emote] = field(default_factory=list)
    skipped: list[tuple[str, str]] = field(default_factory=list)  # (source, why)
    unknown: list[tuple[str, str]] = field(default_factory=list)  # (source, what it looks like)
    problems: list[str] = field(default_factory=list)
    files: int = 0
    # Player-like animations in archives without "emote" in their path or name (a mod's mobs, say), not listed.
    archive_animations_passed: int = 0


def find(folder: Path, progress=lambda message: None) -> Found:
    found = Found()
    used: set[str] = set()
    paths = sorted(p for p in folder.rglob("*") if p.is_file())
    cosmetic_folders = {_folder(p.relative_to(folder).as_posix()) for p in paths
                        if p.name.lower().endswith(COSMETIC_FILES)}
    for i, path in enumerate(paths):
        if i and i % 1000 == 0:
            progress(f"{i} of {len(paths)} files")
        rel = path.relative_to(folder).as_posix()
        try:
            _file(found, used, rel, path.stat().st_size, path.read_bytes if path.stat().st_size <= MAX_JSON_BYTES
                  or path.suffix.lower() in ARCHIVES else None, path, cosmetic_folders)
        except (OSError, zipfile.BadZipFile, RuntimeError) as e:
            found.problems.append(f"{rel}: {e}")
    return found


def _file(found: Found, used: set[str], rel: str, size: int, read, path: Path | None, cosmetic_folders: set[str],
          depth: int = 0) -> None:
    found.files += 1
    name = rel.rsplit("/", 1)[-1].lower()
    ext = Path(name).suffix
    if ext in ARCHIVES and depth == 0:
        _archive(found, used, rel, path, cosmetic_folders)
        return
    if ext == ".emotecraft":
        found.unknown.append((rel, f"Emotecraft binary emote ({size} bytes): not supported yet"))
        return
    if ext != ".json" or read is None or size > MAX_JSON_BYTES:
        if "emote" in rel.lower() and ext not in (".png", ".ogg", ".webp", ".jpg"):
            found.unknown.append((rel, f"{ext or 'no extension'} file, {size} bytes"))
        return
    try:
        doc = json.loads(read().decode("utf-8-sig"))
    except (ValueError, UnicodeDecodeError):
        if "emote" in rel.lower():
            found.unknown.append((rel, "not readable JSON"))
        return
    if not isinstance(doc, dict):
        return
    folder = _folder(rel)
    if isinstance(doc.get("emote"), dict) and isinstance(doc["emote"].get("moves"), list):
        _emotecraft(found, used, rel, doc)
    elif isinstance(doc.get("animations"), dict):
        cosmetic = folder in cosmetic_folders and "emote" not in rel.lower()
        _bedrock(found, used, rel, doc, cosmetic, in_archive=depth > 0)
    elif "emote" in rel.lower() and not name.endswith(".particle.json"):
        found.unknown.append((rel, "JSON with keys " + ", ".join(sorted(doc)[:12])))


def _archive(found: Found, used: set[str], rel: str, path: Path, cosmetic_folders: set[str]) -> None:
    try:
        z = zipfile.ZipFile(path)
    except zipfile.BadZipFile:
        return
    with z:
        infos = [i for i in z.infolist() if not i.is_dir()]
        inner_cosmetic = {_folder(f"{rel}!/{i.filename}") for i in infos
                          if i.filename.lower().endswith(COSMETIC_FILES)}
        for info in infos:
            inner = f"{rel}!/{info.filename}"
            try:
                _file(found, used, inner, info.file_size, lambda i=info: z.read(i), None,
                      cosmetic_folders | inner_cosmetic, depth=1)
            except (OSError, zipfile.BadZipFile, RuntimeError, NotImplementedError) as e:
                found.problems.append(f"{inner}: {e}")


def _folder(rel: str) -> str:
    """The folder part of a found file's path (inside an archive: archive!/folder)."""
    return rel.rsplit("/", 1)[0] if "/" in rel else ""


def _unique(namespace: str, name: str, used: set[str]) -> str:
    candidate, n = f"{namespace}:{name}", 2
    while candidate in used:
        candidate = f"{namespace}:{name}_{n}"
        n += 1
    used.add(candidate)
    return candidate


def _emotecraft(found: Found, used: set[str], rel: str, doc: dict) -> None:
    stem = rel.rsplit("/", 1)[-1][:-len(".json")]
    name = doc.get("name") if isinstance(doc.get("name"), str) else stem
    found.emotes.append(Emote(_unique(namespace_for(rel), slug(name), used), rel, doc))


def _bedrock(found: Found, used: set[str], rel: str, doc: dict, cosmetic: bool, in_archive: bool = False) -> None:
    stem = re.sub(r"(\.animations?)?\.json$", "", rel.rsplit("/", 1)[-1], flags=re.I)
    animations = {k: v for k, v in doc["animations"].items() if isinstance(v, dict)}
    for key, animation in animations.items():
        source = f"{rel} → {key}"
        bones = animation.get("bones") if isinstance(animation.get("bones"), dict) else {}
        mapped = {name: bone_of(name) for name in bones}
        parts = {b for b in mapped.values() if b}
        if cosmetic:
            if parts:
                found.skipped.append((source, "a cosmetic's own animation (its folder has the cosmetic's model)"))
            continue
        if not parts:
            continue
        emote_named = "emote" in rel.lower() or "emote" in key.lower()
        if in_archive and not emote_named:
            found.archive_animations_passed += 1
            continue
        if len(parts) < 2 and not emote_named:
            found.skipped.append((source, f"moves only {', '.join(sorted(parts))}: not a whole-player animation"))
            continue
        if len(parts) * 2 < len(bones) and not emote_named:
            found.skipped.append((source, f"mostly other bones ({len(bones) - len(parts)} of {len(bones)}): "
                                          "a model's animation, not a player's"))
            continue
        converted, notes = convert_animation(animation, mapped)
        if not converted["bones"]:
            found.skipped.append((source, "nothing the mod can play: " + "; ".join(notes)))
            continue
        name = re.sub(r"^animation\.", "", key, flags=re.I)
        name = re.sub(r"^(emote|emotes|player)\.", "", name, flags=re.I)
        if len(animations) == 1 and name.lower() in ("", "emote", "animation", "main", "default"):
            name = stem
        emote_id = _unique(namespace_for(rel), slug(name), used)
        out = {"format_version": doc.get("format_version", "1.8.0"),
               "animations": {f"animation.emote.{emote_id.split(':', 1)[1]}": converted}}
        found.emotes.append(Emote(emote_id, source, out, notes))


def convert_animation(animation: dict, mapped: dict[str, str | None]) -> tuple[dict, list[str]]:
    """One Bedrock / GeckoLib animation with the mod's bone names and only what the mod plays, and notes on what
    was left out."""
    notes: list[str] = []
    out: dict = {}
    for key in ("loop", "animation_length", "elytra", "props"):
        if key in animation:
            out[key] = animation[key]
    bones_out: dict = {}
    latest = 0.0
    for name, bone in (animation.get("bones") or {}).items():
        target = mapped.get(name)
        if not target:
            notes.append(f"bone '{name}' isn't a player's: left out")
            continue
        if not isinstance(bone, dict):
            continue
        channels = {}
        for channel, value in bone.items():
            if channel not in CHANNELS:
                if channel == "scale":
                    notes.append(f"'{name}' scale: the mod doesn't scale emote parts, left out")
                continue
            clean, ok = _numbers(value)
            if not ok:
                notes.append(f"'{name}' {channel} uses Molang (bake it in Blockbench): left out")
                continue
            channels[channel] = clean
            if isinstance(clean, dict):
                for t in clean:
                    if NUMBER.match(str(t)):
                        latest = max(latest, float(t))
        if channels:
            existing = bones_out.get(target)
            if existing:
                notes.append(f"'{name}' and another bone are both {target}: the first kept")
                continue
            bones_out[target] = channels
    if "animation_length" not in out and latest > 0:
        out["animation_length"] = latest
    out["bones"] = bones_out
    return out, notes


def _numbers(value):
    """The value with numbers written as text made numbers; ok False when it holds an expression."""
    if isinstance(value, bool):
        return value, False
    if isinstance(value, (int, float)):
        return value, True
    if isinstance(value, str):
        if NUMBER.match(value):
            return float(value), True
        return value, value.strip() == ""
    if isinstance(value, list):
        items = [_numbers(v) for v in value]
        return [v for v, _ in items], all(ok for _, ok in items)
    if isinstance(value, dict):
        out, ok_all = {}, True
        for k, v in value.items():
            if k in ("lerp_mode", "easing", "easingArgs"):
                out[k] = v
                continue
            clean, ok = _numbers(v)
            out[k] = clean
            ok_all = ok_all and ok
        return out, ok_all
    return value, False


# ---------------------------------------------------------------------- writing


def check_output(output: Path) -> None:
    if output.exists() and not output.is_dir():
        raise LayoutError(f"{output} exists and is not a folder")
    if output.is_dir() and any(output.iterdir()) and not (output / MARKER).is_file():
        raise LayoutError(f"{output} is not empty and wasn't written by this tool; choose an empty or new folder")


def write(found: Found, output: Path) -> list[Path]:
    """Writes the pack (replacing the emotes of an earlier run), returns the emote files written."""
    check_output(output)
    output.mkdir(parents=True, exist_ok=True)
    for old in output.glob("assets/*/emotes/*.json"):
        old.unlink()
    written = []
    for emote in found.emotes:
        namespace, name = emote.id.split(":", 1)
        target = output / "assets" / namespace / "emotes" / f"{name}.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(emote.doc, indent=1) + "\n", encoding="utf-8")
        written.append(target)
    (output / "pack.mcmeta").write_text(json.dumps(PACK_MCMETA, indent=2) + "\n", encoding="utf-8")
    (output / MARKER).write_text(json.dumps({"version": __version__}), encoding="utf-8")
    return written


def report(found: Found, folder: Path) -> str:
    lines = [f"# Emote import from `{folder.name}`", "",
             f"Looked through {found.files} files (inside archives too). "
             f"{len(found.emotes)} emote(s) written, {len(found.skipped)} animation(s) left out, "
             f"{len(found.unknown)} emote-like file(s) not understood.", ""]
    if found.emotes:
        lines += ["## Emotes", "", "| Emote | From | Notes |", "|---|---|---|"]
        for e in found.emotes:
            lines.append(f"| `{e.id}` | `{e.source}` | {'; '.join(e.notes) or '-'} |")
        lines.append("")
    if found.unknown:
        lines += ["## Emote-like files not understood", "",
                  "Send this list along: these may be emotes in a format the importer doesn't read yet.", ""]
        lines += [f"- `{src}`: {what}" for src, what in found.unknown]
        lines.append("")
    if found.skipped:
        lines += ["## Animations left out", ""]
        lines += [f"- `{src}`: {why}" for src, why in found.skipped]
        lines.append("")
    if found.archive_animations_passed:
        lines += [f"{found.archive_animations_passed} animation(s) inside archives moved player-like bones but don't "
                  "say \"emote\" in their path or name (a mod's mobs, say), so they were passed over.", ""]
    if found.problems:
        lines += ["## Problems", ""] + [f"- {p}" for p in found.problems] + [""]
    return "\n".join(lines)
