"""The `inspect` command: checks every assumption about Badlion's layout against the real input and reports the result.

Two outputs:
- LAYOUT.md: structure only (key names, JSON types, path patterns, counts, model names). No asset contents,
  no cosmetic names, no pixel or vertex data, so it can be shared.
- a summary for you: cosmetics present per type, which have models, which are animated, and what doesn't fit.
"""

from __future__ import annotations

import datetime
import re
from collections import Counter, defaultdict
from dataclasses import dataclass, field

from . import __version__, avif, badlion, meshes
from .archive import Archive, jar_name

CONFIRMED = "confirmed"
DIFFERS = "differs"
PARTLY = "partly"
UNCHECKED = "not checked"

STATUS_MARK = {CONFIRMED: "✅", DIFFERS: "❌", PARTLY: "⚠️", UNCHECKED: "❔"}

# Badlion's built-in cloak models, the likely model of capes without a modelName.
CAPE_CANDIDATE_HINTS = ("cloak_v2", "cloak_1")
CAPE_SAMPLES = 8
UV_GRID = 64


@dataclass
class Check:
    section: str
    claim: str
    status: str
    evidence: list[str] = field(default_factory=list)


@dataclass
class CosmeticState:
    cosmetic: badlion.Cosmetic
    texture: bool
    texture_path: str
    model: str  # "own", "default cloak", "default wing", "none", "missing"
    model_name: str | None
    problems: list[str] = field(default_factory=list)

    @property
    def present(self) -> bool:
        return self.texture and self.model != "missing"


@dataclass
class Result:
    checks: list[Check] = field(default_factory=list)
    states: list[CosmeticState] = field(default_factory=list)
    misfits: list[str] = field(default_factory=list)
    layout_details: list[str] = field(default_factory=list)
    missing_models: dict[str, list[str]] = field(default_factory=dict)
    cape_model: str | None = None

    def check(self, section: str, claim: str, status: str, *evidence: str) -> None:
        self.checks.append(Check(section, claim, status, [e for e in evidence if e]))


def _type_name(value) -> str:
    return {bool: "bool", int: "int", float: "number", str: "string", list: "list", dict: "object",
            type(None): "null"}.get(type(value), type(value).__name__)


def _shape(values) -> str:
    counts = Counter(_type_name(v) for v in values)
    return ", ".join(f"{t} ×{n}" for t, n in counts.most_common())


def generalize(path: str, folder_names: set[str]) -> str:
    """A path with ids, folder names and model names replaced by placeholders."""
    if path.startswith(badlion.TEXTURE_ROOT):
        rest = path[len(badlion.TEXTURE_ROOT):]
        segments = rest.split("/")
        out = []
        folder = segments[0] if segments else ""
        for i, seg in enumerate(segments):
            if i == 0 and seg in folder_names:
                out.append("<folder>")
            elif i > 0 and segments[i - 1] == "models" and i < len(segments) - 1:
                out.append("<model>")
            elif i > 0 and segments[i - 1] == "models":
                stem, _, ext = seg.rpartition(".")
                out.append("<model>." + ext if stem else seg)
            elif seg.startswith(folder + "_") and folder:
                out.append("<folder>_" + re.sub(r"\d+", "<n>", seg[len(folder) + 1:]))
            else:
                out.append(re.sub(r"\d+", "<n>", seg))
        return "<textures>/" + "/".join(out)
    return re.sub(r"\d+", "<n>", path)


def run(archive: Archive, verify_hashes: bool = True, progress=lambda message: None) -> Result:
    r = Result()
    present = archive.present_paths()
    _archives(archive, present, verify_hashes, r, progress)
    progress("reading cosmetic definitions")
    definitions = badlion.load(archive)
    _definitions(archive, definitions, r)
    progress("checking textures")
    _textures(archive, definitions, present, r, progress)
    progress("checking models")
    _models(archive, definitions, present, r, progress)
    _states(definitions, present, r)
    return r


# ---------------------------------------------------------------------- archives

def _archives(archive: Archive, present: set[str], verify_hashes: bool, r: Result, progress) -> None:
    s = "Archives"
    m = archive.manifest
    keys = set(m)
    expected = {"id", "version", "buildType", "chunkData"}
    r.check(s, "assets.json is {id, version, buildType, chunkData: [{chunkId, files: [...]}]}",
            CONFIRMED if keys == expected else (PARTLY if expected <= keys else DIFFERS),
            f"top-level keys: {', '.join(sorted(keys))}",
            f"chunkData: {len(m['chunkData'])} chunks, {len(archive.listed)} files listed")
    file_keys = Counter()
    for chunk in m["chunkData"]:
        for f in chunk["files"]:
            file_keys[tuple(sorted(f))] += 1
    r.check(s, "each file is {hash, size, absolutePath}",
            CONFIRMED if set(file_keys) == {("absolutePath", "hash", "size")} else DIFFERS,
            *[f"{', '.join(k)}: ×{n}" for k, n in file_keys.most_common()])
    if archive.duplicates:
        r.misfits.append(f"assets.json lists {len(archive.duplicates)} path(s) more than once")

    chunks = sorted({e.chunk for e in archive.listed.values()})
    found = [c for c in chunks if c in archive.jars]
    stray = sorted(p.name for p in archive.folder.glob("data*.jar") if p.name not in {jar_name(c) for c in chunks})
    r.check(s, "chunkId N is dataN.jar", CONFIRMED if found and not stray else (PARTLY if found else DIFFERS),
            f"chunk ids: {', '.join(map(str, chunks))}",
            f"jars present: {', '.join(jar_name(c) for c in found) or 'none'}",
            f"listed chunks without a jar: {', '.join(jar_name(c) for c in chunks if c not in archive.jars)}"
            if len(found) < len(chunks) else "",
            f"jars that match no chunk id: {', '.join(stray)}" if stray else "")

    methods = Counter()
    by_name = 0
    not_listed = 0
    other_chunk = 0
    for chunk in archive.jars:
        for name, info in archive.entries(chunk).items():
            methods[{0: "stored", 8: "deflated"}.get(info.compress_type, f"method {info.compress_type}")] += 1
            listed = archive.listed.get(name)
            if listed is None:
                not_listed += 1
            elif listed.chunk != chunk:
                other_chunk += 1
            else:
                by_name += 1
    r.check(s, "jars are plain zips with stored (uncompressed) entries",
            CONFIRMED if set(methods) == {"stored"} else (PARTLY if methods else UNCHECKED),
            *[f"{k}: {n} entries" for k, n in methods.most_common()])
    r.check(s, "entries are stored under their absolutePath",
            CONFIRMED if by_name and not not_listed and not other_chunk else (PARTLY if by_name else DIFFERS),
            f"{by_name} entries found under their absolutePath in the jar assets.json names",
            f"{other_chunk} entries are in a different jar than assets.json says" if other_chunk else "",
            f"{not_listed} entries are not in assets.json" if not_listed else "")
    missing = [p for p in archive.listed if archive.listed[p].chunk in archive.jars and p not in archive.entries(archive.listed[p].chunk)]
    absent_chunks = sum(1 for e in archive.listed.values() if e.chunk not in archive.jars)
    r.check(s, "trimmed jars: listed entries may be missing (not present, not an error)", CONFIRMED,
            f"{len(missing)} listed files are missing from jars that are there",
            f"{absent_chunks} listed files belong to jars that aren't there" if absent_chunks else "")

    if verify_hashes:
        progress("verifying hashes of every present file")
        results = Counter()
        for path in archive.listed:
            if archive.listed[path].chunk in archive.jars and path in archive.entries(archive.listed[path].chunk):
                results[archive.verify(path)] += 1
        total = sum(results.values())
        r.check(s, "hash is the SHA-1 of the file (used for incremental re-imports)",
                CONFIRMED if total and results["ok"] == total else (PARTLY if results["ok"] else DIFFERS),
                *[f"{k}: {n} of {total}" for k, n in results.most_common()])
    else:
        r.check(s, "hash is the SHA-1 of the file", UNCHECKED, "skipped (--no-hash)")


# ---------------------------------------------------------------------- definitions

def _definitions(archive: Archive, d: badlion.Definitions, r: Result) -> None:
    s = "Definitions"
    where = {badlion.COSMETICS_JSON: d.cosmetics_chunk, badlion.MODELS_JSON: d.models_chunk,
             badlion.EXAMPLE_JSON: archive.locate(badlion.EXAMPLE_JSON)[0] if archive.has(badlion.EXAMPLE_JSON) else None}
    jars = {c for c in where.values() if c is not None}
    r.check(s, "definitions are all in one jar (usually data5)",
            CONFIRMED if len(jars) == 1 else (DIFFERS if len(jars) > 1 else UNCHECKED),
            *[f"{p}: {jar_name(c) if c is not None else 'not found'}" for p, c in where.items()])

    by_type = d.by_type()
    r.check(s, "cosmetics.json → registeredCosmetics, a map of TYPE → list", CONFIRMED,
            *[f"{t}: {len(v)}" for t, v in sorted(by_type.items())])

    duplicate_in_type = {t: [i for i, n in Counter(str(c.id) for c in v).items() if n > 1] for t, v in by_type.items()}
    duplicate_in_type = {t: v for t, v in duplicate_in_type.items() if v}
    shared_ids = Counter(str(c.id) for c in d.cosmetics)
    across = sum(1 for _, n in shared_ids.items() if n > 1)
    r.check(s, "cosmeticId is unique per type, not globally",
            CONFIRMED if not duplicate_in_type and across else (PARTLY if not duplicate_in_type else DIFFERS),
            f"ids used by more than one type: {across}",
            *[f"repeated within {t}: {len(v)} ids" for t, v in duplicate_in_type.items()])

    missing_model = {t: sum(1 for c in v if not c.model_name) for t, v in by_type.items()}
    r.check(s, "modelName is missing for most capes and a few wings", CONFIRMED,
            *[f"{t}: {n} of {len(by_type[t])} without modelName" for t, n in sorted(missing_model.items()) if n])

    field_values = defaultdict(list)
    for c in d.cosmetics:
        for k, v in c.raw.items():
            field_values[k].append(v)
    unexpected = sorted(set(field_values) - set(badlion.COSMETIC_FIELDS) - set(badlion.COSMETIC_GROUPS))
    never = [f for f in badlion.COSMETIC_FIELDS + badlion.COSMETIC_GROUPS if f not in field_values]
    r.check(s, "cosmetic fields are the expected ones",
            CONFIRMED if not unexpected and not never else PARTLY,
            f"not in the expected list: {', '.join(unexpected)}" if unexpected else "",
            f"expected but never seen: {', '.join(never)}" if never else "")
    r.layout_details.append("### Cosmetic entry fields\n")
    r.layout_details.append("| Field | Entries | JSON types |\n|---|---|---|")
    for k in sorted(field_values, key=lambda k: (k not in badlion.COSMETIC_FIELDS, k)):
        if k in badlion.COSMETIC_GROUPS:
            continue
        r.layout_details.append(f"| `{k}` | {len(field_values[k])} | {_shape(field_values[k])} |")
    r.layout_details.append("")
    for group in badlion.COSMETIC_GROUPS:
        values = field_values.get(group, [])
        if not values:
            continue
        sub = defaultdict(list)
        for v in values:
            if isinstance(v, dict):
                for k, x in v.items():
                    sub[k].append(x)
        r.layout_details.append(f"### `{group}` ({len(values)} cosmetics, {_shape(values)})\n")
        if sub:
            r.layout_details.append("| Key | Entries | JSON types |\n|---|---|---|")
            for k in sorted(sub):
                r.layout_details.append(f"| `{k}` | {len(sub[k])} | {_shape(sub[k])} |")
        r.layout_details.append("")

    model_values = defaultdict(list)
    for model in d.models.values():
        for k, v in model.raw.items():
            model_values[k].append(v)
    unexpected_model = sorted(set(model_values) - set(badlion.MODEL_FIELDS))
    zero_scale = sum(1 for v in model_values.get("scale", []) if v == 0)
    translation_shape = Counter(tuple(sorted(v)) if isinstance(v, dict) else _type_name(v)
                                for v in model_values.get("translation", []))
    referenced = {(c.type, c.model_name) for c in d.cosmetics if c.model_name}
    undefined = sorted(referenced - set(d.models))
    r.check(s, "models.json → modelData[TYPE][modelName] with scale (0 = default), optional translation {x,y,z}, "
               "flipUVs, applyChroma, md5format, subModelTexture, useSubFolder",
            CONFIRMED if d.models and not unexpected_model else (PARTLY if d.models else DIFFERS),
            f"{len(d.models)} models in {len({t for t, _ in d.models})} types",
            f"scale 0: {zero_scale} of {len(model_values.get('scale', []))}",
            f"translation shapes: {dict(translation_shape)}" if translation_shape else "no translation seen",
            f"other keys: {', '.join(unexpected_model)}" if unexpected_model else "",
            f"modelNames used by cosmetics but not in models.json: {len(undefined)}" if undefined else "")
    r.layout_details.append("### models.json settings\n")
    r.layout_details.append("| Key | Models | JSON types |\n|---|---|---|")
    for k in sorted(model_values):
        r.layout_details.append(f"| `{k}` | {len(model_values[k])} | {_shape(model_values[k])} |")
    r.layout_details.append("")
    for t, name in undefined:
        r.misfits.append(f"{t} model '{name}' is used by cosmetics but has no models.json entry")
    _placement_values(d, r)

    if d.example_text is None:
        r.check(s, "example.json is a commented schema example", UNCHECKED, "example.json not found")
    else:
        import json

        try:
            json.loads(d.example_text)
            r.check(s, "example.json isn't valid JSON (it has comments)", DIFFERS, "it parses as plain JSON")
        except json.JSONDecodeError:
            try:
                parsed = json.loads(badlion.strip_comments(d.example_text))
                top = sorted(parsed) if isinstance(parsed, dict) else _type_name(parsed)
                r.check(s, "example.json is a commented schema example", CONFIRMED,
                        "parses once comments are removed", f"top-level keys: {top}")
            except json.JSONDecodeError as e:
                r.check(s, "example.json is a commented schema example", PARTLY,
                        f"still not JSON without comments ({e.msg} at line {e.lineno})")


PLACEMENT_MODEL_KEYS = ("scale", "translation", "rotation", "sizingData")
OTHER_MODEL_KEYS = ("shieldModelData", "subModels", "extraModels", "animationProperties", "animationData")
PLACEMENT_COSMETIC_KEYS = ("sizingData", "wingData", "petData")


def _cell(value, limit: int = 160) -> str:
    import json

    if value is None:
        return ""
    text = json.dumps(value, sort_keys=True, separators=(",", ":"))
    return (text if len(text) <= limit else text[:limit - 1] + "…").replace("|", "/")


def _placement_values(d: badlion.Definitions, r: Result) -> None:
    """The numbers that place each model and cosmetic (scale, offsets, rotations), for checking the conversion."""
    r.layout_details.append("### Placement values per model (models.json, models cosmetics use)\n")
    r.layout_details.append("| Model | Cosmetics | md5 | flipUVs | " + " | ".join(PLACEMENT_MODEL_KEYS)
                            + " | other |\n|" + "---|" * (len(PLACEMENT_MODEL_KEYS) + 5))
    for (t, name), model in sorted(d.models.items()):
        if not model.users:
            continue
        raw = model.raw
        other = {k: raw[k] for k in OTHER_MODEL_KEYS if k in raw}
        r.layout_details.append(f"| `{t}/{name}` | {len(model.users)} | {'yes' if model.md5 else ''} | "
                                f"{'yes' if raw.get('flipUVs') is True else ''} | "
                                + " | ".join(_cell(raw.get(k), 60) for k in PLACEMENT_MODEL_KEYS)
                                + f" | {_cell(other, 300)} |")
    r.layout_details.append("")
    r.layout_details.append("### Placement values per cosmetic (cosmetics.json, ones that have any)\n")
    r.layout_details.append("| Cosmetic | Model | " + " | ".join(PLACEMENT_COSMETIC_KEYS) + " |\n|"
                            + "---|" * (len(PLACEMENT_COSMETIC_KEYS) + 2))
    for c in sorted(d.cosmetics, key=lambda c: (c.type, str(c.id))):
        if not any(isinstance(c.raw.get(k), dict) for k in PLACEMENT_COSMETIC_KEYS):
            continue
        r.layout_details.append(f"| {c.type} {c.id} | `{c.model_name or ''}` | "
                                + " | ".join(_cell(c.raw.get(k), 200) for k in PLACEMENT_COSMETIC_KEYS) + " |")
    r.layout_details.append("")


# ---------------------------------------------------------------------- textures

def _textures(archive: Archive, d: badlion.Definitions, present: set[str], r: Result, progress) -> None:
    s = "Textures"
    root = badlion.TEXTURE_ROOT
    folders_present = {p[len(root):].split("/", 1)[0] for p in present if p.startswith(root)}
    types = sorted(d.by_type())
    mapped = {t: badlion.folder_for(t) for t in types}
    r.check(s, "folders: CAPE → cloak, WINGS → wing, otherwise the lowercase type",
            CONFIRMED if all(f in folders_present for f in mapped.values()) else PARTLY,
            *[f"{t} → {f}/ {'found' if f in folders_present else 'NOT FOUND'}" for t, f in mapped.items()],
            f"folders no type maps to: {', '.join(sorted(folders_present - set(mapped.values())))}"
            if folders_present - set(mapped.values()) else "")

    all_avif = [p for p in present if p.startswith(root) and p.endswith(".avif")]
    gui = [p for p in all_avif if "/gui/" in p or p.endswith("_gui.avif")]
    r.check(s, "gui/ subfolders and *_gui.avif are menu icons (skipped)", CONFIRMED, f"{len(gui)} such files")

    expected = {}
    for c in d.cosmetics:
        expected[c.texture_path()] = c
    static_ok = static_n = anim_ok = anim_n = wrong_kind = 0
    for c in d.cosmetics:
        f = c.folder
        static = f"{root}{f}/{f}_{c.id}.avif"
        animated = f"{root}{f}/{c.id}/{f}_{c.id}.avif"
        if c.animated:
            anim_n += 1
            anim_ok += animated in present
            wrong_kind += animated not in present and static in present
        else:
            static_n += 1
            static_ok += static in present
            wrong_kind += static not in present and animated in present
    r.check(s, "static: <folder>/<folder>_<id>.avif", CONFIRMED if static_ok else UNCHECKED,
            f"{static_ok} of {static_n} cosmetics without animationData have it (the rest aren't in your jars)")
    r.check(s, "animated (has animationData): <folder>/<id>/<folder>_<id>.avif", CONFIRMED if anim_ok else UNCHECKED,
            f"{anim_ok} of {anim_n} cosmetics with animationData have it",
            f"{wrong_kind} cosmetics have their texture at the other pattern" if wrong_kind else "")
    if wrong_kind:
        r.misfits.append(f"{wrong_kind} cosmetics have a texture at the static/animated path that doesn't match "
                         "whether they have animationData")

    unmatched = Counter(generalize(p, folders_present) for p in all_avif if p not in expected and p not in gui)
    r.layout_details.append("### Texture files that match no cosmetic\n")
    if unmatched:
        r.layout_details.append("| Pattern | Files |\n|---|---|")
        for pattern, n in unmatched.most_common(40):
            r.layout_details.append(f"| `{pattern}` | {n} |")
        r.misfits.append(f"{sum(unmatched.values())} .avif files match no cosmetic (patterns in LAYOUT.md)")
    else:
        r.layout_details.append("None.")
    r.layout_details.append("")

    # Decode every present cosmetic texture: sizes, sequences and timing.
    sizes = defaultdict(Counter)
    brands = Counter()
    seq_frames = Counter()
    timing = Counter()
    decode_failures = 0
    animation_keys = Counter()
    for path, c in sorted(expected.items()):
        if path not in present:
            continue
        data = archive.read(path)
        try:
            info = avif.read_container(data)
        except ValueError:
            r.misfits.append(f"{c.type} {c.id}: texture is not an ISOBMFF/AVIF file")
            continue
        brands[info.brand] += 1
        try:
            decoded = avif.decode(data)
        except Exception as e:  # noqa: BLE001 - any decoder failure is reported, never fatal
            decode_failures += 1
            r.misfits.append(f"{c.type} {c.id}: texture could not be decoded ({type(e).__name__})")
            continue
        w, h = decoded.size
        sizes[c.folder][f"{w}×{h}" + (f" ×{len(decoded.frames)} frames" if len(decoded.frames) > 1 else "")] += 1
        if c.animated:
            data_ = c.raw["animationData"]
            animation_keys.update(data_.keys())
            seq_frames[len(decoded.frames)] += 1
            if not info.sequence:
                r.misfits.append(f"{c.type} {c.id}: has animationData but its texture is a still AVIF ('{info.brand}')")
            if info.frames is not None and info.frames != len(decoded.frames):
                r.misfits.append(f"{c.type} {c.id}: container says {info.frames} frames, decoder gave {len(decoded.frames)}")
            if info.durations_ms and any(abs(a - b) > 1.5 for a, b in zip(info.durations_ms, decoded.durations_ms)):
                timing["decoder durations differ from the container"] += 1
            custom = data_.get("customFrameDelay")
            if custom is True:
                timing["customFrameDelay: true (file timing)"] += 1
                if not info.durations_ms:
                    r.misfits.append(f"{c.type} {c.id}: customFrameDelay but the file has no frame timing")
            elif "frameDelay" in data_:
                timing["frameDelay (fixed)"] += 1
            else:
                timing["neither customFrameDelay nor frameDelay"] += 1
        elif len(decoded.frames) > 1:
            r.misfits.append(f"{c.type} {c.id}: texture has {len(decoded.frames)} frames but no animationData")
    r.check(s, "animated textures are AVIF image sequences (ftyp avis) with frame timing",
            CONFIRMED if seq_frames and brands.get("avis") else (UNCHECKED if not seq_frames else PARTLY),
            f"ftyp brands: {dict(brands)}", f"frames per sequence: {dict(sorted(seq_frames.items()))}",
            f"timing: {dict(timing)}", f"animationData keys: {dict(animation_keys)}",
            f"{decode_failures} textures failed to decode" if decode_failures else "")
    r.layout_details.append("### Texture sizes per folder (width×height of one frame)\n")
    r.layout_details.append("| Folder | Sizes |\n|---|---|")
    for folder, counter in sorted(sizes.items()):
        r.layout_details.append(f"| `{folder}` | " + ", ".join(f"{k} ×{n}" for k, n in counter.most_common(8)) + " |")
    r.layout_details.append("")
    cape_sizes = sizes.get("cloak", Counter())
    small = sum(n for k, n in cape_sizes.items() if k.startswith("64×32"))
    r.check(s, "cape textures are high-res (e.g. 704×736 per frame), not 64×32",
            UNCHECKED if not cape_sizes else (CONFIRMED if not small else
                                              (DIFFERS if small == sum(cape_sizes.values()) else PARTLY)),
            f"cloak sizes: {dict(cape_sizes.most_common(5))}" if cape_sizes else "no cape textures present")


# ---------------------------------------------------------------------- models

def _models(archive: Archive, d: badlion.Definitions, present: set[str], r: Result, progress) -> None:
    s = "Models"
    root = badlion.TEXTURE_ROOT
    model_files = sorted(p for p in present if p.endswith((".obj", ".md5mesh", ".md5anim")))
    outside = [p for p in model_files if not (p.startswith(root) and "/models/" in p)]
    r.check(s, "models live under <folder>/models/", CONFIRMED if model_files and not outside else
            (UNCHECKED if not model_files else PARTLY),
            f"{len(model_files)} model files present",
            f"{len(outside)} elsewhere: {sorted(Counter(generalize(p, set()) for p in outside))[:5]}" if outside else "")

    obj_ok = obj_n = md5_ok = md5_n = 0
    anim_names = Counter()
    used_files = set()
    for (t, name), model in sorted(d.models.items()):
        if not model.users:
            continue
        path = model.mesh_path()
        if model.md5:
            md5_n += 1
            if path in present:
                md5_ok += 1
                used_files.add(path)
                for a in model.animation_paths(present):
                    used_files.add(a)
                    anim_names[a.rsplit("/", 1)[1][:-len(".md5anim")]] += 1
        else:
            obj_n += 1
            if path in present:
                obj_ok += 1
                used_files.add(path)
    r.check(s, "OBJ: <folder>/models/<modelName>.obj", CONFIRMED if obj_ok else UNCHECKED,
            f"{obj_ok} of {obj_n} OBJ models in use are present")
    r.check(s, "MD5 (md5format): <folder>/models/<modelName>/<modelName>.md5mesh plus .md5anim files",
            CONFIRMED if md5_ok else UNCHECKED, f"{md5_ok} of {md5_n} MD5 models in use are present",
            f"animation names: {dict(anim_names.most_common(20))}" if anim_names else "")

    shared = [m for m in d.models.values() if len(m.users) > 1]
    r.check(s, "many cosmetics share one model", CONFIRMED if shared else UNCHECKED,
            f"{len(shared)} models are shared; most users: "
            + ", ".join(f"{m.type}/{m.name} ×{len(m.users)}" for m in sorted(shared, key=lambda m: -len(m.users))[:5]))
    for model in d.models.values():
        if model.users and model.mesh_path() not in present:
            r.missing_models[f"{model.type}/{model.name}"] = [u.key for u in model.users]

    # Every model file present, described (structure only).
    r.layout_details.append("### Model files present\n")
    r.layout_details.append("| File | Kind | Vertices | Triangles | Joints | Bounds (min → max) | UV range | Notes |")
    r.layout_details.append("|---|---|---|---|---|---|---|---|")
    summaries = {}
    for p in model_files:
        text = archive.read(p).decode("utf-8", "replace")
        if p.endswith(".md5anim"):
            a = meshes.summarize_md5anim(text)
            r.layout_details.append(f"| `{p[len(root):]}` | md5anim | | | {a.joints} | | | "
                                    f"{a.frames} frames at {a.frame_rate:g} fps {' '.join(a.problems)} |")
            continue
        summary = meshes.summarize_obj(text) if p.endswith(".obj") else meshes.summarize_md5mesh(text)
        summaries[p] = summary
        b = summary.bounds
        bounds = f"({b[0][0]:.3g}, {b[0][1]:.3g}, {b[0][2]:.3g}) → ({b[1][0]:.3g}, {b[1][1]:.3g}, {b[1][2]:.3g})" if b else ""
        uv = summary.uv_bounds
        uv_text = f"u {uv[0]:.3g}..{uv[2]:.3g}, v {uv[1]:.3g}..{uv[3]:.3g}" if uv else ""
        notes = []
        if summary.groups:
            notes.append(f"{len(summary.groups)} objects/groups")
        if summary.submeshes:
            notes.append(f"{len(summary.submeshes)} meshes")
        if summary.multi_weight_vertices:
            notes.append(f"{summary.multi_weight_vertices} multi-weight vertices")
        notes += summary.problems
        r.layout_details.append(f"| `{p[len(root):]}` | {summary.kind} | {summary.vertices} | {summary.triangles} | "
                                f"{summary.joints or ''} | {bounds} | {uv_text} | {'; '.join(notes)} |")
    r.layout_details.append("")

    wings_without = [c for c in d.by_type().get("WINGS", []) if not c.model_name]
    r.check(s, "wings without modelName need a default wing model", CONFIRMED if wings_without else UNCHECKED,
            f"{len(wings_without)} wings without modelName (fall back to the mod's bundled wings)")

    _cape_check(archive, d, present, summaries, r, progress)


def _cape_check(archive, d, present, summaries, r: Result, progress) -> None:
    """Which cloak model's UVs fit the textures of capes without a modelName best."""
    s = "Capes without modelName"
    root = badlion.TEXTURE_ROOT
    candidates = {p: m for p, m in summaries.items()
                  if p.startswith(root + "cloak/models/") and m.uv_triangles}
    capes = [c for c in d.by_type().get("CAPE", []) if not c.model_name and c.texture_path() in present]
    if not candidates or not capes:
        r.check(s, "capes use the built-in cloak model (cloak_v2 / cloak_1, MD5 with rect/curved/dynamic)", UNCHECKED,
                f"{len(candidates)} cloak model files and {len(capes)} capes without modelName are present")
        return
    progress(f"comparing {len(candidates)} cloak models with {min(len(capes), CAPE_SAMPLES)} cape textures")
    samples = capes[:: max(1, len(capes) // CAPE_SAMPLES)][:CAPE_SAMPLES]
    masks = []
    aspects = []
    for c in samples:
        frame = avif.decode(archive.read(c.texture_path())).frames[0]
        aspects.append(frame.size[0] / frame.size[1])
        small = frame.resize((UV_GRID, UV_GRID))
        masks.append([a > 16 for a in small.getchannel("A").tobytes()])
    scores = []
    for p, summary in candidates.items():
        for flip in (False, True):
            covered = meshes.uv_coverage(summary.uv_triangles, UV_GRID, UV_GRID, flip)
            score = sum(meshes.overlap_score(covered, m) for m in masks) / len(masks)
            scores.append((score, p, flip))
    # Best overlap first; on a tie, the file's own V direction.
    scores.sort(key=lambda entry: (-entry[0], entry[2], entry[1]))
    best = scores[0]
    r.cape_model = best[1]
    hinted = [p for p in candidates if any(h in p for h in CAPE_CANDIDATE_HINTS)]
    r.check(s, "capes use the built-in cloak model (cloak_v2 / cloak_1, MD5 with rect/curved/dynamic)",
            CONFIRMED if best[0] >= 0.6 and best[1] in hinted else (PARTLY if best[0] >= 0.4 else DIFFERS),
            f"best UV fit: {best[1][len(root):]}{' (V flipped)' if best[2] else ''}, overlap {best[0]:.2f}",
            f"its animations: {', '.join(a.rsplit('/', 1)[1] for a in sorted(present) if a.endswith('.md5anim') and a.rsplit('/', 1)[0] == best[1].rsplit('/', 1)[0]) or 'none'}",
            f"texture aspect (w/h) of the samples: {min(aspects):.3g} to {max(aspects):.3g}",
            *[f"{p[len(root):]}{' V flipped' if f else ''}: {sc:.2f}" for sc, p, f in scores[:6]])


# ---------------------------------------------------------------------- per cosmetic

def _states(d: badlion.Definitions, present: set[str], r: Result) -> None:
    cloak_default = r.cape_model is not None
    for c in d.cosmetics:
        state = CosmeticState(c, c.texture_path() in present, c.texture_path(), "none", c.model_name)
        model = d.model_of(c)
        if c.model_name:
            if model is None:
                state.model = "missing"
                state.problems.append("modelName has no models.json entry")
            elif model.mesh_path() in present:
                state.model = "own"
            else:
                state.model = "missing"
                state.problems.append(f"model {model.name} not in your jars")
        elif c.type == "CAPE":
            state.model = "default cloak" if cloak_default else "missing"
            if not cloak_default:
                state.problems.append("no cloak model found for capes without modelName")
        elif c.type == "WINGS":
            state.model = "default wing"
        if not state.texture:
            state.problems.append("texture not in your jars")
        r.states.append(state)


# ---------------------------------------------------------------------- output

def layout_markdown(r: Result, archive: Archive) -> str:
    lines = [
        "# Badlion data layout",
        "",
        f"Written by `badlion-import inspect` {__version__} on {datetime.date.today().isoformat()} from the trimmed "
        "input folder. Structure only: key names, JSON types, path patterns, counts and model file names. No asset "
        "contents, no cosmetic names.",
        "",
        "Status: ✅ confirmed, ⚠️ partly (see the evidence), ❌ differs, ❔ not checked (nothing in the input to "
        "check it with).",
        "",
    ]
    section = None
    for check in r.checks:
        if check.section != section:
            if section is not None:
                lines.append("")
            section = check.section
            lines += [f"## {section}", ""]
        lines.append(f"- {STATUS_MARK[check.status]} **{check.claim}**")
        for e in check.evidence:
            lines.append(f"  - {e}")
    lines += ["", "## Details", ""] + r.layout_details
    if r.cape_model:
        lines += ["## Model for capes without modelName", "",
                  f"`{r.cape_model[len(badlion.TEXTURE_ROOT):]}`: of the built-in cloaks, its UVs overlap the painted "
                  "area of sample cape textures best (see the scores above). The importer makes capes without a "
                  "modelName a box like the vanilla cape instead, from the top 22 x 17 blocks of the textures; "
                  "set `cape_model` to use a cloak.", ""]
    return "\n".join(lines).rstrip() + "\n"


def summary_text(r: Result, verbose: bool = False) -> str:
    lines = ["Cosmetics per type (present = texture and model are in your jars)", ""]
    header = f"{'Type':<14}{'Registered':>11}{'Present':>9}{'Own model':>11}{'Default model':>15}{'Animated':>10}"
    lines += [header, "-" * len(header)]
    by_type = defaultdict(list)
    for st in r.states:
        by_type[st.cosmetic.type].append(st)
    for t in sorted(by_type):
        sts = by_type[t]
        here = [s for s in sts if s.present]
        lines.append(f"{t:<14}{len(sts):>11}{len(here):>9}{sum(s.model == 'own' for s in here):>11}"
                     f"{sum(s.model.startswith('default') for s in here):>15}{sum(s.cosmetic.animated for s in here):>10}")
    lines.append("")
    if r.missing_models:
        lines.append("Models missing from your jars, and the cosmetics that need them:")
        for model, users in sorted(r.missing_models.items()):
            lines.append(f"  {model}: {len(users)} cosmetic(s): {', '.join(users[:12])}{' ...' if len(users) > 12 else ''}")
        lines.append("")
    wings = [s for s in r.states if s.model == "default wing" and s.present]
    if wings:
        lines.append(f"Needs the default wing model ({len(wings)}): " + ", ".join(s.cosmetic.key for s in wings))
        lines.append("")
    if r.misfits:
        lines.append("Doesn't fit the expected layout:")
        lines += [f"  - {m}" for m in r.misfits[:60]]
        if len(r.misfits) > 60:
            lines.append(f"  ... and {len(r.misfits) - 60} more")
        lines.append("")
    differs = [c for c in r.checks if c.status in (DIFFERS, PARTLY)]
    if differs:
        lines.append("Checks that didn't fully pass (details in LAYOUT.md):")
        lines += [f"  {STATUS_MARK[c.status]} {c.section}: {c.claim}" for c in differs]
        lines.append("")
    if verbose:
        lines.append("Present cosmetics:")
        for st in sorted((s for s in r.states if s.present), key=lambda s: (s.cosmetic.type, str(s.cosmetic.id))):
            c = st.cosmetic
            lines.append(f"  {c.type:<10} {str(c.id):>6}  {c.name:<32} model: {st.model_name or st.model}"
                         f"{'  animated' if c.animated else ''}")
    return "\n".join(lines).rstrip() + "\n"
