"""Emotes (player animations) from any folder of client files into a resource pack for the mod's emote wheel.

Clients keep emotes in different places and formats, so this looks through everything under a folder (and inside
any .jar or .zip in it) for the formats the mod plays:

- **Bedrock / GeckoLib animations** (`{"animations": {...}}`, what Blockbench exports, and what Lunar and NoRisk
  build with) whose bones are a player's: `head`, `body`, `right_arm`, ... in any spelling, GeckoLib's
  `bipedHead`, `bipedRightArm`, ... too, and NoRisk's `bipedRig`, the whole player). Each animation becomes one
  emote, its bones renamed to the mod's and anything the mod can't play dropped (other bones), with the reason in
  the report. Body parts made bigger or smaller (`scale`) stay so. Molang is kept: the mod works it out as the emote plays. A whole-player bone turns where
  the model next to the animation has it (`root_pivot`).
- **NoRisk's names**: its shop files (`*.norisk.json` with an `emoteFile`) name the emotes.
- Packs this tool wrote (an earlier run copied into `resourcepacks`, say) are passed over, so emotes aren't found
  twice. Animations of cosmetics (files next to a cosmetic's `.geo.json`, `.gek.json` or `.norisk.json`) aren't
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
    "root": ("root", "player", "wholebody", "entity", "bipedrig", "rig"),
    "right_arm": ("rightarm", "armright", "rarm", "bipedrightarm", "playerrightarm"),
    "left_arm": ("leftarm", "armleft", "larm", "bipedleftarm", "playerleftarm"),
    "right_leg": ("rightleg", "legright", "rleg", "bipedrightleg", "playerrightleg"),
    "left_leg": ("leftleg", "legleft", "lleg", "bipedleftleg", "playerleftleg"),
}
_BONE_OF = {alias: bone for bone, aliases in BONES.items() for alias in aliases}
# Channels the mod plays.
CHANNELS = ("rotation", "position", "scale")
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
    name: str | None = None  # the name its client gives it (the mod shows it), or None for one from the file


@dataclass
class Context:
    """What the first look through the folder found that the emotes need."""
    cosmetic_folders: set[str] = field(default_factory=set)
    # Folders (as _folder gives them) with Bedrock models in them, to find a whole-player bone's pivot.
    models: dict[str, list[Path]] = field(default_factory=dict)
    # Emote names from NoRisk's shop files, by the animation file's name (see _key).
    names: dict[str, str] = field(default_factory=dict)
    # Folders of packs this tool wrote, passed over.
    own_packs: list[str] = field(default_factory=list)


@dataclass
class Found:
    emotes: list[Emote] = field(default_factory=list)
    skipped: list[tuple[str, str]] = field(default_factory=list)  # (source, why)
    unknown: list[tuple[str, str]] = field(default_factory=list)  # (source, what it looks like)
    problems: list[str] = field(default_factory=list)
    files: int = 0
    # Player-like animations in archives without "emote" in their path or name (a mod's mobs, say), not listed.
    archive_animations_passed: int = 0


# Files marking a pack this tool (or the cosmetics importer) wrote.
OWN_MARKERS = (MARKER, ".client-import.json")


def _key(name: str) -> str:
    """An animation file's name, compared loosely: no folders, extension or punctuation, lower case."""
    name = name.replace("\\", "/").rsplit("/", 1)[-1].lower()
    name = re.sub(r"(\.animations?)?(\.json)?$", "", name)
    return re.sub(r"[^a-z0-9]+", "", name)


def find(folder: Path, progress=lambda message: None) -> Found:
    found = Found()
    used: set[str] = set()
    paths = sorted(p for p in folder.rglob("*") if p.is_file())
    context = Context()
    for p in paths:
        rel = p.relative_to(folder).as_posix()
        lower = p.name.lower()
        if lower.endswith(COSMETIC_FILES):
            context.cosmetic_folders.add(_folder(rel))
        if lower.endswith(".geo.json"):
            context.models.setdefault(_folder(rel), []).append(p)
        if lower in OWN_MARKERS:
            context.own_packs.append(_folder(rel))
        if lower.endswith(".norisk.json") and p.stat().st_size <= MAX_JSON_BYTES:
            _norisk_name(context, p)
    for i, path in enumerate(paths):
        if i and i % 1000 == 0:
            progress(f"{i} of {len(paths)} files")
        rel = path.relative_to(folder).as_posix()
        # Inside a pack this tool wrote ("" is the folder itself).
        if any(own == "" or rel.startswith(own + "/") for own in context.own_packs):
            continue
        try:
            _file(found, used, rel, path.stat().st_size, path.read_bytes if path.stat().st_size <= MAX_JSON_BYTES
                  or path.suffix.lower() in ARCHIVES else None, path, context)
        except (OSError, zipfile.BadZipFile, RuntimeError) as e:
            found.problems.append(f"{rel}: {e}")
    return found


def _norisk_name(context: Context, path: Path) -> None:
    """A NoRisk shop file naming an emote: its `name` for the animation file its `emoteFile` gives."""
    try:
        doc = json.loads(path.read_bytes().decode("utf-8-sig"))
    except (OSError, ValueError, UnicodeDecodeError):
        return
    if not isinstance(doc, dict) or not isinstance(doc.get("emoteFile"), str) or not isinstance(doc.get("name"), str):
        return
    name = doc["name"].strip()
    if name:
        context.names.setdefault(_key(doc["emoteFile"]), name)
        if isinstance(doc.get("id"), str):
            context.names.setdefault(_key(doc["id"]), name)


def _file(found: Found, used: set[str], rel: str, size: int, read, path: Path | None, context: Context,
          depth: int = 0) -> None:
    found.files += 1
    name = rel.rsplit("/", 1)[-1].lower()
    ext = Path(name).suffix
    if ext in ARCHIVES and depth == 0:
        _archive(found, used, rel, path, context)
        return
    if ext == ".emotecraft":
        found.unknown.append((rel, f"Emotecraft binary emote ({size} bytes): not supported yet"))
        return
    if ext != ".json" or read is None or size > MAX_JSON_BYTES:
        if "emote" in rel.lower() and ext not in (".png", ".ogg", ".webp", ".jpg", ".mcmeta", ".class"):
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
        cosmetic = folder in context.cosmetic_folders and "emote" not in rel.lower()
        _bedrock(found, used, rel, doc, cosmetic, context, in_archive=depth > 0)
    elif "emote" in rel.lower() and not name.endswith((".particle.json", ".geo.json", ".norisk.json")):
        found.unknown.append((rel, "JSON with keys " + ", ".join(sorted(doc)[:12])))


def _archive(found: Found, used: set[str], rel: str, path: Path, context: Context) -> None:
    try:
        z = zipfile.ZipFile(path)
    except zipfile.BadZipFile:
        return
    with z:
        infos = [i for i in z.infolist() if not i.is_dir()]
        inner = Context(context.cosmetic_folders | {_folder(f"{rel}!/{i.filename}") for i in infos
                                                    if i.filename.lower().endswith(COSMETIC_FILES)},
                        context.models, context.names, context.own_packs)
        for info in infos:
            entry = f"{rel}!/{info.filename}"
            try:
                _file(found, used, entry, info.file_size, lambda i=info: z.read(i), None, inner, depth=1)
            except (OSError, zipfile.BadZipFile, RuntimeError, NotImplementedError) as e:
                found.problems.append(f"{entry}: {e}")


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


def _bedrock(found: Found, used: set[str], rel: str, doc: dict, cosmetic: bool, context: Context | None = None,
             in_archive: bool = False) -> None:
    context = context or Context()
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
        root = next((name for name, bone in mapped.items() if bone == "root"), None)
        if root is not None and "root" in converted["bones"]:
            pivot = _pivot(context.models.get(_folder(rel), []), root)
            if pivot:
                converted["root_pivot"] = pivot
        # One animation in a file: the file's name (NoRisk names its animations nrc.idle, emote, ...); several:
        # the file's and each one's.
        key_name = re.sub(r"^(animation|emote|emotes|player)\.", "", key, flags=re.I)
        name = stem if len(animations) == 1 else f"{stem}_{key_name}"
        emote_id = _unique(namespace_for(rel), slug(name), used)
        out = {"format_version": doc.get("format_version", "1.8.0"),
               "animations": {f"animation.emote.{emote_id.split(':', 1)[1]}": converted}}
        shown = context.names.get(_key(rel)) if len(animations) == 1 else None
        found.emotes.append(Emote(emote_id, source, out, notes, shown))


def _pivot(models: list[Path], bone: str) -> float | None:
    """How far up from the feet (pixels) the whole-player bone {@code bone} turns in a model next to the animation,
    or None (the feet, or no model says)."""
    for model in models:
        try:
            doc = json.loads(model.read_bytes().decode("utf-8-sig"))
        except (OSError, ValueError, UnicodeDecodeError):
            continue
        for geometry in doc.get("minecraft:geometry", []) if isinstance(doc, dict) else []:
            for b in geometry.get("bones", []) if isinstance(geometry, dict) else []:
                if isinstance(b, dict) and b.get("name") == bone and isinstance(b.get("pivot"), list) \
                        and len(b["pivot"]) == 3:
                    y = b["pivot"][1]
                    if isinstance(y, (int, float)) and y and abs(y) <= 64:
                        return round(float(y), 4)
                    return None
    return None


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
                continue
            clean, ok = _numbers(value)
            if not ok:
                notes.append(f"'{name}' {channel} isn't numbers or Molang: left out")
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
        # Molang: the mod works it out as the emote plays.
        return value.strip() or 0.0, True
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
    # Names the clients give their emotes, as the mod's translations of the emote ids.
    for old in output.glob("assets/*/lang/en_us.json"):
        old.unlink()
    names: dict[str, dict[str, str]] = {}
    for emote in found.emotes:
        if emote.name:
            namespace, name = emote.id.split(":", 1)
            names.setdefault(namespace, {})[f"emote.{namespace}.{name}"] = emote.name
    for namespace, lang in names.items():
        target = output / "assets" / namespace / "lang" / "en_us.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(lang, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    (output / "pack.mcmeta").write_text(json.dumps(PACK_MCMETA, indent=2) + "\n", encoding="utf-8")
    (output / MARKER).write_text(json.dumps({"version": __version__}), encoding="utf-8")
    return written


def report(found: Found, folder: Path) -> str:
    lines = [f"# Emote import from `{folder.name}`", "",
             f"Looked through {found.files} files (inside archives too). "
             f"{len(found.emotes)} emote(s) written, {len(found.skipped)} animation(s) left out, "
             f"{len(found.unknown)} emote-like file(s) not understood.", ""]
    if found.emotes:
        lines += ["## Emotes", "", "| Emote | Name | From | Notes |", "|---|---|---|---|"]
        for e in found.emotes:
            lines.append(f"| `{e.id}` | {e.name or '-'} | `{e.source}` | {'; '.join(e.notes) or '-'} |")
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
