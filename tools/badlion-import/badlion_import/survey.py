"""The `survey` command: what any folder of cosmetic files looks like inside, to plan an importer for it (Lunar,
NoRisk, ...). Structure only, like LAYOUT.md: path patterns, file kinds and sizes, the keys and value types of
JSON files, image sizes, model counts and bounds, and the first bytes of files it can't read. No pixels, no
vertices, no text values other than short id-like ones (types, enum names), so SURVEY.md can be shared.

Zip-like archives (.jar, .zip, ...) are looked into, one level of nesting deep. The input is only read.
"""

from __future__ import annotations

import io
import json
import re
import zipfile
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path

from . import meshes

ARCHIVES = {".jar", ".zip", ".mcpack", ".pak"}
IMAGES = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".avif", ".bmp", ".tga"}
TEXTS = {".json", ".mcmeta", ".bbmodel", ".gltf", ".txt", ".properties", ".yml", ".yaml", ".toml", ".cfg", ".csv"}
MAX_JSON_BYTES = 32 * 1024 * 1024
MAX_MODEL_BYTES = 64 * 1024 * 1024
MAX_PATTERNS = 400
MAX_SHAPES = 120
# A string value is shown (with how often it appears) only if it looks like an id or enum name.
ID_LIKE = re.compile(r"^[A-Za-z0-9_.:/-]{1,40}$")
MAX_VALUES = 8

_NUMBER = re.compile(r"\d+")
_HEX = re.compile(r"\b[0-9a-f]{12,}\b", re.IGNORECASE)
_UUID = re.compile(r"\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b", re.IGNORECASE)


def pattern(path: str) -> str:
    """A path with ids and numbers replaced by placeholders, so files of one kind group together."""
    path = _UUID.sub("<uuid>", path)
    path = _HEX.sub("<hex>", path)
    return _NUMBER.sub("<n>", path)


# ---------------------------------------------------------------------- JSON shapes

@dataclass
class Shape:
    """What a JSON value looks like across every file of a pattern: types seen, object keys, list items."""

    count: int = 0
    types: Counter = field(default_factory=Counter)
    keys: dict = field(default_factory=dict)
    items: "Shape | None" = None
    values: Counter = field(default_factory=Counter)
    numbers: list = field(default_factory=list)  # min, max

    def add(self, value, depth: int = 0) -> None:
        self.count += 1
        kind = _kind(value)
        self.types[kind] += 1
        if depth > 8:
            return
        if isinstance(value, dict):
            # Objects keyed by ids (a map of cosmetic id -> entry) would list every id: fold those keys.
            folded = len(value) > 24 and sum(1 for k in value if _NUMBER.fullmatch(str(k)) or _HEX.search(str(k))) > len(value) / 2
            for k, v in value.items():
                key = "<key>" if folded else str(k)
                self.keys.setdefault(key, Shape()).add(v, depth + 1)
        elif isinstance(value, list):
            if self.items is None:
                self.items = Shape()
            for v in value[:200]:
                self.items.add(v, depth + 1)
        elif isinstance(value, str):
            if ID_LIKE.match(value) and not _UUID.search(value):
                self.values[value] += 1
        elif kind == "number":
            if not self.numbers:
                self.numbers = [value, value]
            else:
                self.numbers = [min(self.numbers[0], value), max(self.numbers[1], value)]

    def lines(self, name: str, indent: int = 0) -> list[str]:
        pad = "  " * indent
        kinds = ", ".join(f"{t} ×{n}" for t, n in self.types.most_common())
        extra = ""
        if self.numbers:
            extra += f"; {_num(self.numbers[0])} to {_num(self.numbers[1])}"
        if self.values:
            shown = self.values.most_common(MAX_VALUES)
            extra += "; e.g. " + ", ".join(f"`{v}` ×{n}" for v, n in shown)
            if len(self.values) > MAX_VALUES:
                extra += f", … {len(self.values)} different"
        out = [f"{pad}- `{name}`: {kinds}{extra}"]
        for k, sub in list(self.keys.items())[:80]:
            out += sub.lines(k, indent + 1)
        if len(self.keys) > 80:
            out.append(f"{pad}  - … {len(self.keys) - 80} more keys")
        if self.items is not None and self.items.count:
            out += self.items.lines("[]", indent + 1)
        return out


def _kind(value) -> str:
    return {bool: "bool", int: "number", float: "number", str: "string", list: "list", dict: "object",
            type(None): "null"}.get(type(value), type(value).__name__)


def _num(value) -> str:
    return f"{value:.6g}" if isinstance(value, float) else str(value)


# ---------------------------------------------------------------------- the survey

@dataclass
class Survey:
    files: int = 0
    bytes: int = 0
    extensions: Counter = field(default_factory=Counter)
    patterns: Counter = field(default_factory=Counter)
    archives: list = field(default_factory=list)  # (path, entries, nested)
    shapes: dict = field(default_factory=dict)  # pattern -> Shape
    json_errors: Counter = field(default_factory=Counter)
    images: dict = field(default_factory=lambda: defaultdict(Counter))  # pattern -> size counter
    models: list = field(default_factory=list)  # (path, summary)
    magic: dict = field(default_factory=lambda: defaultdict(Counter))  # extension -> first bytes
    problems: list = field(default_factory=list)


def run(folder: Path, progress=lambda message: None) -> Survey:
    s = Survey()
    paths = sorted(p for p in folder.rglob("*") if p.is_file())
    for i, path in enumerate(paths):
        if i % 500 == 0 and i:
            progress(f"{i} of {len(paths)} files")
        rel = path.relative_to(folder).as_posix()
        try:
            _file(s, rel, path.stat().st_size, lambda p=path: p.read_bytes(), _head(path), depth=0, path=path)
        except (OSError, zipfile.BadZipFile, RuntimeError) as e:
            s.problems.append(f"{rel}: {e}")
    return s


def _head(path: Path) -> bytes:
    with path.open("rb") as f:
        return f.read(8)


def _file(s: Survey, rel: str, size: int, read, head: bytes, depth: int, path: Path | None = None) -> None:
    s.files += 1
    s.bytes += size
    ext = Path(rel).suffix.lower()
    s.extensions[ext or "(none)"] += 1
    pat = pattern(rel)
    s.patterns[pat] += 1
    if ext in ARCHIVES or head[:4] == b"PK\x03\x04":
        _archive(s, rel, path if path is not None else io.BytesIO(read()), head, depth)
    elif ext in TEXTS and size <= MAX_JSON_BYTES:
        _text(s, rel, pat, read())
    elif ext in IMAGES:
        _image(s, pat, read())
    elif ext in (".obj", ".md5mesh", ".md5anim") and size <= MAX_MODEL_BYTES:
        _model(s, rel, ext, read())
    else:
        s.magic[ext or "(none)"][head.hex(" ") if head else "(empty)"] += 1


def _archive(s: Survey, rel: str, source, head: bytes, depth: int) -> None:
    try:
        z = zipfile.ZipFile(source)
    except zipfile.BadZipFile:
        s.magic[Path(rel).suffix.lower()][head.hex(" ")] += 1
        return
    with z:
        infos = [i for i in z.infolist() if not i.is_dir()]
        s.archives.append((rel, len(infos)))
        for info in infos:
            inner = f"{rel}!/{info.filename}"
            if depth >= 1 and Path(info.filename).suffix.lower() in ARCHIVES:
                s.files += 1
                s.patterns[pattern(inner)] += 1
                continue
            try:
                with z.open(info) as f:
                    head = f.read(8)
                _file(s, inner, info.file_size, lambda i=info: z.read(i), head, depth + 1)
            except (OSError, zipfile.BadZipFile, RuntimeError, NotImplementedError) as e:
                s.problems.append(f"{inner}: {e}")


def _text(s: Survey, rel: str, pat: str, data: bytes) -> None:
    ext = Path(rel).suffix.lower()
    if ext not in (".json", ".mcmeta", ".bbmodel", ".gltf"):
        return
    try:
        value = json.loads(data.decode("utf-8-sig"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        # Some clients' JSON has comments or trailing commas.
        try:
            from .badlion import strip_comments

            value = json.loads(re.sub(r",(\s*[}\]])", r"\1", strip_comments(data.decode("utf-8", "replace"))))
        except (json.JSONDecodeError, ValueError):
            s.json_errors[pat] += 1
            return
    s.shapes.setdefault(pat, Shape()).add(value)


def _image(s: Survey, pat: str, data: bytes) -> None:
    try:
        from PIL import Image

        with Image.open(io.BytesIO(data)) as image:
            frames = getattr(image, "n_frames", 1)
            label = f"{image.size[0]}×{image.size[1]}" + (f" ×{frames} frames" if frames > 1 else "")
    except Exception:  # noqa: BLE001 - an image Pillow can't open is reported, not fatal
        label = "unreadable (" + data[:8].hex(" ") + ")"
    s.images[pat][label] += 1


def _model(s: Survey, rel: str, ext: str, data: bytes) -> None:
    text = data.decode("utf-8", "replace")
    if ext == ".obj":
        summary = meshes.summarize_obj(text)
    elif ext == ".md5mesh":
        summary = meshes.summarize_md5mesh(text)
    else:
        anim = meshes.summarize_md5anim(text)
        s.models.append((rel, f"md5anim: {anim.frames} frames at {anim.frame_rate:g} fps, {anim.joints} joints"))
        return
    bounds = ""
    if summary.bounds:
        lo, hi = summary.bounds
        bounds = f", bounds ({', '.join(f'{v:.3g}' for v in lo)}) → ({', '.join(f'{v:.3g}' for v in hi)})"
    uv = ""
    if summary.uv_bounds:
        u0, v0, u1, v1 = summary.uv_bounds
        uv = f", uv {u0:.3g}..{u1:.3g} × {v0:.3g}..{v1:.3g}"
    s.models.append((rel, f"{summary.kind}: {summary.vertices} vertices, {summary.triangles} triangles"
                          + (f", {summary.joints} joints" if summary.joints else "") + bounds + uv))


# ---------------------------------------------------------------------- report

def markdown(s: Survey, folder_name: str, version: str) -> str:
    lines = [f"# Survey of `{folder_name}`", "",
             f"Written by `badlion-import survey` {version}. Structure only: path patterns, file kinds and sizes, "
             "JSON keys and value types (and short id-like values), image sizes, model counts and bounds, the first "
             "bytes of unknown files. No pixels, vertices or other contents.", "",
             f"{s.files} files, {s.bytes / 1_000_000:.1f} MB.", ""]
    lines += ["## File kinds", "", "| Extension | Files |", "|---|---|"]
    lines += [f"| `{e}` | {n} |" for e, n in s.extensions.most_common()]
    if s.archives:
        lines += ["", "## Archives", "", "| Archive | Entries |", "|---|---|"]
        lines += [f"| `{pattern(a)}` | {n} |" for a, n in s.archives[:200]]
    lines += ["", f"## Path patterns (most common {MAX_PATTERNS})", "", "| Pattern | Files |", "|---|---|"]
    lines += [f"| `{p}` | {n} |" for p, n in s.patterns.most_common(MAX_PATTERNS)]
    if len(s.patterns) > MAX_PATTERNS:
        lines.append(f"| … {len(s.patterns) - MAX_PATTERNS} more patterns | |")
    if s.shapes:
        lines += ["", "## JSON files", "", "What each kind of JSON file holds: keys, value types, number ranges and "
                  "id-like values with how often they appear.", ""]
        for pat, shape in sorted(s.shapes.items(), key=lambda kv: -kv[1].count)[:MAX_SHAPES]:
            lines += [f"### `{pat}` ({shape.count} files)", ""] + shape.lines("(file)") + [""]
        if len(s.shapes) > MAX_SHAPES:
            lines.append(f"… {len(s.shapes) - MAX_SHAPES} more kinds of JSON file")
    if s.json_errors:
        lines += ["", "JSON that couldn't be read: " + ", ".join(f"`{p}` ×{n}" for p, n in s.json_errors.most_common(30))]
    if s.images:
        lines += ["", "## Images", "", "| Pattern | Sizes |", "|---|---|"]
        for pat, sizes in sorted(s.images.items(), key=lambda kv: -sum(kv[1].values()))[:MAX_PATTERNS]:
            lines.append(f"| `{pat}` | " + ", ".join(f"{k} ×{n}" for k, n in sizes.most_common(6)) + " |")
    if s.models:
        lines += ["", "## Models", "", "| File | Summary |", "|---|---|"]
        lines += [f"| `{p}` | {summary} |" for p, summary in s.models[:1500]]
        if len(s.models) > 1500:
            lines.append(f"| … {len(s.models) - 1500} more | |")
    if s.magic:
        lines += ["", "## Other files (first bytes)", "", "| Extension | First bytes |", "|---|---|"]
        for ext, heads in sorted(s.magic.items(), key=lambda kv: -sum(kv[1].values())):
            lines.append(f"| `{ext}` | " + ", ".join(f"`{h}` ×{n}" for h, n in heads.most_common(6)) + " |")
    if s.problems:
        lines += ["", "## Problems", ""] + [f"- {p}" for p in s.problems[:100]]
    return "\n".join(lines).rstrip() + "\n"
