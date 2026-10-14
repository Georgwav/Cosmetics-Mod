"""Lunar Client and NoRisk Client cosmetics (the files of the ones you own) into a resource pack for the mod.

Both clients build cosmetics in Blockbench, so their models and animations carry over as they are: the mod reads
Bedrock geometry (`.geo.json`) with Bedrock / GeckoLib animations (`model_animations`) itself. What this module
works out is everything around them:

- **Lunar**: each `<name>.gek.json` says what the cosmetic is (`type`, `item_render_type`), which model, texture
  and animation file it uses, and a state machine (`state_machine`) of Molang conditions choosing the animation,
  which becomes `model_states`. Textures are WebP, animated ones a strip of frames described by
  `<texture>.mcmeta` (frame size, frame time in ticks). A folder with a texture and no `.gek.json` is a cape.
  Lunar models are built on the player (feet at 0), as the mod's are.
- **NoRisk**: each `<name>.norisk.json` (`type` CAPE, HAT, ACCESSORIES, ...) sits next to `<name>.geo.json`,
  `<name>.animation.json` and `<name>.png` (+ `.png.mcmeta`, frame times in milliseconds). The models are
  GeckoLib armour models: the player's skeleton (`bipedHead`, `armorBody`, ...) with the cosmetic inside the
  bones it follows, which becomes `"skeleton": "player"`. `variants/<v>/` folders are cosmetics of their own.

Capes of both become the mod's box cape (the vanilla cape's layout, 22 x 17 blocks a frame).
"""

from __future__ import annotations

import io
import json
import re
from dataclasses import dataclass, field
from pathlib import Path

from . import __version__, convert, textures
from .archive import LayoutError

LUNAR = "lunar"
NORISK = "norisk"
NAMESPACES = {LUNAR: "lunar_import", NORISK: "norisk_import"}
MARKER = ".client-import.json"
REPORT = "import-report.md"
TICK_MS = 50
DEFAULT_FRAME_MS = 100

PACK_MCMETA = {
    "pack": {
        "description": "Your own Lunar and NoRisk cosmetics, for Cosmetics Mod (personal use only)",
        "pack_format": 34,
        "supported_formats": {"min_inclusive": 34, "max_inclusive": 999},
        "min_format": 34,
        "max_format": 999,
    }
}

# NoRisk animations that play in the world, in order of preference; "nrc.shop" is only for its shop.
NORISK_IDLE = ("nrc.idle", "misc.idle", "idle", "animation.idle", "main")
# Words in a Lunar cosmetic's name that make it an aura (a model around the whole player).
AURA_WORDS = ("aura", "particle", "ember", "firefl", "heart", "rain", "ash", "money", "leaves", "flower", "butterfl",
              "snow", "star", "spark", "bubble", "smoke")
# NoRisk types to the mod's slot and kind (one of a kind is worn at a time unless the player allows several).
NORISK_TYPES = {"HAT": ("head", "head"), "MASK": ("face", "face"), "GLASSES": ("face", "face"),
                "WINGS": ("back", "wings"), "BACK": ("back", "back"), "BACKPACK": ("back", "back"),
                "PET": ("body", "pet"), "SHIELD": ("shield", "shield"), "AURA": ("aura", "aura")}


# ---------------------------------------------------------------------- what was found


@dataclass
class TextureSource:
    path: Path
    location: str
    frame_height: int | None = None  # one frame's height in the source; None: the whole image (or square frames)
    frame_ms: int | None = None
    animated: bool = False


@dataclass
class Found:
    client: str
    id: str  # namespace:name
    name: str
    slot: str
    kind: str
    source: Path
    doc: dict
    texture: TextureSource | None
    models: list[tuple[str, Path]] = field(default_factory=list)  # (pack location, source file)
    cape: bool = False
    notes: list[str] = field(default_factory=list)


@dataclass
class Skipped:
    path: Path
    reason: str


@dataclass
class FoundAll:
    found: list[Found] = field(default_factory=list)
    skipped: list[Skipped] = field(default_factory=list)


def slug(text: str) -> str:
    s = re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")
    return s or "cosmetic"


def nice_name(text: str) -> str:
    text = re.sub(r"[_\s]+", " ", text).strip()
    return text[:1].upper() + text[1:] if text else "Cosmetic"


def detect(folder: Path) -> bool:
    """The folder holds Lunar or NoRisk cosmetic files."""
    for pattern in ("*.gek.json", "*.norisk.json"):
        if next(folder.rglob(pattern), None) is not None:
            return True
    return False


def _read_json(path: Path) -> dict:
    try:
        data = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as e:
        raise ValueError(f"can't read {path.name}: {e}") from e
    if not isinstance(data, dict):
        raise ValueError(f"{path.name} is not a JSON object")
    return data


def _mcmeta(texture: Path) -> dict | None:
    meta = texture.with_name(texture.name + ".mcmeta")
    if not meta.is_file():
        return None
    try:
        return _read_json(meta)
    except ValueError:
        return None


def _frames_from_mcmeta(meta: dict | None, tick_ms: bool) -> tuple[int | None, int | None, bool]:
    """(frame height, frame ms, animated) from a texture's .mcmeta: Lunar's and vanilla's frame time is in ticks,
    NoRisk's in milliseconds."""
    if not meta or not isinstance(meta.get("animation"), dict):
        return None, None, False
    anim = meta["animation"]
    height = anim.get("height") if isinstance(anim.get("height"), (int, float)) else None
    time = anim.get("frametime")
    ms = None
    if isinstance(time, (int, float)) and not isinstance(time, bool) and time > 0:
        ms = int(round(time * TICK_MS)) if tick_ms else int(round(time))
        ms = max(textures.MIN_FRAME_MS, min(textures.MAX_FRAME_MS, ms))
    return (int(height) if height else None), ms, True


# ---------------------------------------------------------------------- Bedrock geometry, for bounds and bones


def _geometry(geo: dict) -> dict:
    geos = geo.get("minecraft:geometry")
    if not isinstance(geos, list) or not geos or not isinstance(geos[0], dict):
        raise ValueError("not a Bedrock geometry file")
    return geos[0]


def _has_shapes(geometry: dict) -> bool:
    """Whether a Bedrock model has anything to draw: a bone with cubes or a mesh (poly_mesh)."""
    bones = geometry.get("bones") if isinstance(geometry.get("bones"), list) else []
    return any(isinstance(b, dict) and ((isinstance(b.get("cubes"), list) and b["cubes"])
                                        or isinstance(b.get("poly_mesh"), dict)) for b in bones)


NO_SHAPES = "its model has no shapes, only empty bones (the client draws this one itself, e.g. as particles); left out"


def _vec(value, default=(0.0, 0.0, 0.0)) -> list[float]:
    if isinstance(value, list) and len(value) == 3 and all(isinstance(v, (int, float)) for v in value):
        return [float(v) for v in value]
    return list(default)


def _ours(p) -> list[float]:
    """A Bedrock point (pixels from the feet, +Y up) in the mod's space (from the neck, +Y down)."""
    return [p[0], 24.0 - p[1], p[2]]


def _affine(rotation, pivot):
    """Rotation (degrees, as the mod applies them) around a pivot, as (matrix, translation)."""
    m = convert.rotation_matrix(rotation)
    r = convert.apply(m, pivot)
    return m, [pivot[i] - r[i] for i in range(3)]


def _compose(outer, inner):
    m = convert.multiply(outer[0], inner[0])
    t = convert.apply(outer[0], inner[1])
    return m, [t[i] + outer[1][i] for i in range(3)]


def _bone_list(geometry: dict) -> list[dict]:
    return [b for b in geometry.get("bones", []) if isinstance(b, dict)]


def follow_of(name: str) -> str | None:
    """The player part a bone of this name stands for (as the mod reads it), or None."""
    key = name.lower().replace("_", "")
    for prefix in ("biped", "armor"):
        if key.startswith(prefix):
            key = key[len(prefix):]
    return {"head": "head", "body": "body", "torso": "body", "rightarm": "right_arm", "leftarm": "left_arm",
            "rightleg": "right_leg", "leftleg": "left_leg"}.get(key)


def bone_places(geometry: dict) -> dict[str, tuple]:
    """Each bone's transform at rest (with its parents'), in the mod's space."""
    bones = {str(b.get("name", "")).lower(): b for b in _bone_list(geometry)}
    places: dict[str, tuple] = {}

    def place(name: str, depth: int = 0):
        if name in places:
            return places[name]
        bone = bones[name]
        own = _affine(_vec(bone.get("rotation")), _ours(_vec(bone.get("pivot"))))
        parent = str(bone.get("parent", "")).lower()
        result = own
        if parent and parent in bones and parent != name and depth < 64:
            result = _compose(place(parent, depth + 1), own)
        places[name] = result
        return result

    for name in bones:
        place(name)
    return places


def bone_chain(geometry: dict, name: str) -> list[str]:
    """A bone's name and its ancestors', lower case, nearest first."""
    bones = {str(b.get("name", "")).lower(): b for b in _bone_list(geometry)}
    chain, current = [], name.lower()
    while current in bones and current not in chain and len(chain) < 64:
        chain.append(current)
        current = str(bones[current].get("parent", "")).lower()
    return chain


def corners(geometry: dict, only=None) -> list[list[float]]:
    """Every cube corner in the mod's space, through the bone and cube transforms; `only(bone name)` filters."""
    places = bone_places(geometry)
    points = []
    for bone in _bone_list(geometry):
        name = str(bone.get("name", "")).lower()
        if only is not None and not only(name):
            continue
        for cube in bone.get("cubes", []) or []:
            if not isinstance(cube, dict):
                continue
            o, s = _vec(cube.get("origin")), _vec(cube.get("size"))
            lo = [o[0], 24.0 - (o[1] + s[1]), o[2]]
            hi = [o[0] + s[0], 24.0 - o[1], o[2] + s[2]]
            own = _affine(_vec(cube.get("rotation")), _ours(_vec(cube.get("pivot"))))
            m = _compose(places[name], own)
            for i in range(8):
                p = [(lo, hi)[(i >> a) & 1][a] for a in range(3)]
                q = convert.apply(m[0], p)
                points.append([q[a] + m[1][a] for a in range(3)])
    return points


def bounds(points) -> tuple[list[float], list[float]] | None:
    if not points:
        return None
    return [min(p[a] for p in points) for a in range(3)], [max(p[a] for p in points) for a in range(3)]


def cube_bones(geometry: dict) -> list[str]:
    return [str(b.get("name", "")) for b in _bone_list(geometry) if b.get("cubes")]


def followed_parts(geometry: dict) -> list[str]:
    """The player parts the model's cubes follow (by their bones' names), most cubes first."""
    counts: dict[str, int] = {}
    for bone in _bone_list(geometry):
        cubes = len(bone.get("cubes", []) or [])
        if not cubes:
            continue
        part = next((follow_of(n) for n in reversed(bone_chain(geometry, str(bone.get("name", ""))))
                     if follow_of(n)), None)
        if part:
            counts[part] = counts.get(part, 0) + cubes
    return sorted(counts, key=lambda k: -counts[k])


# ---------------------------------------------------------------------- finding cosmetics


class _Index:
    """Files of the input by lower-case name, to resolve the paths descriptors give."""

    def __init__(self, root: Path):
        self.root = root
        self.by_name: dict[str, list[Path]] = {}
        for path in root.rglob("*"):
            if path.is_file():
                self.by_name.setdefault(path.name.lower(), []).append(path)

    def find(self, near: Path, reference: str, extensions=()) -> Path | None:
        """The file a descriptor names (`lunar:cosmetics/.../x.geo.json`), nearest to `near` first; other
        extensions are tried for its stem (a `.png` reference to a `.webp` file)."""
        base = reference.replace("\\", "/").rsplit("/", 1)[-1].lower()
        names = [base]
        for ext in extensions:
            stem = base
            for known in (".png", ".webp", ".geo.json", ".anim.json", ".animation.json", ".json"):
                if stem.endswith(known):
                    stem = stem[: -len(known)]
                    break
            names.append(stem + ext)
        for name in names:
            candidates = self.by_name.get(name, [])
            if not candidates:
                continue
            for folder in (near.parent, near.parent.parent):
                for c in candidates:
                    if c.parent == folder:
                        return c
            if len(candidates) == 1:
                return candidates[0]
            # The one sharing the most folders with the descriptor.
            return max(candidates, key=lambda c: len(Path(*_common(c.parts, near.parts))))
        return None


def _common(a, b):
    out = []
    for x, y in zip(a, b):
        if x != y:
            break
        out.append(x)
    return out or ["."]


def find(root: Path, names: list[str] | None = None) -> FoundAll:
    """Every Lunar and NoRisk cosmetic in the folder. `names` keeps only those whose name or path contains one of
    them (any case)."""
    index = _Index(root)
    result = FoundAll()
    used_ids: set[str] = set()
    gek_folders: set[Path] = set()
    for gek in sorted(root.rglob("*.gek.json")):
        gek_folders.add(gek.parent)
        try:
            result.found.append(_lunar(gek, root, index, used_ids))
        except ValueError as e:
            result.skipped.append(Skipped(gek, str(e)))
    # Lunar capes: a texture in a folder of its own, with no model.
    for texture in sorted([*root.rglob("*.webp")]):
        folder = texture.parent
        if folder in gek_folders or folder.parent in gek_folders or any(folder.glob("*.geo.json")):
            continue
        try:
            result.found.append(_lunar_cape(texture, root, used_ids))
        except ValueError as e:
            result.skipped.append(Skipped(texture, str(e)))
    for descriptor in sorted(root.rglob("*.norisk.json")):
        rel = descriptor.relative_to(root).as_posix().lower()
        if "/shop/" in f"/{rel}" or "/emotes/" in f"/{rel}":
            continue
        try:
            result.found.extend(_norisk(descriptor, root, used_ids))
        except ValueError as e:
            result.skipped.append(Skipped(descriptor, str(e)))
    if names:
        wanted = [n.lower() for n in names]
        result.found = [f for f in result.found
                        if any(n in f.name.lower() or n in f.source.relative_to(root).as_posix().lower() for n in wanted)]
    return result


def _unique(namespace: str, name: str, used: set[str]) -> str:
    base = f"{namespace}:{name}"
    candidate, n = base, 2
    while candidate in used:
        candidate = f"{base}_{n}"
        n += 1
    used.add(candidate)
    return candidate


def _lunar_name(descriptor: Path, root: Path, stem: str) -> tuple[str, str]:
    """(display name, id) for a Lunar cosmetic: its folder's name, with the file's own when they differ."""
    folder = descriptor.parent
    if folder.name.lower() == "textures":  # butterfly_aura/textures/pink.gek.json: a colour of the aura
        return f"{nice_name(folder.parent.name)} ({nice_name(stem)})", f"{slug(folder.parent.name)}_{slug(stem)}"
    if folder == root:
        return nice_name(stem), slug(stem)
    if slug(folder.name) == slug(stem):
        return nice_name(folder.name), slug(stem)
    return f"{nice_name(folder.name)} ({nice_name(stem)})", f"{slug(folder.name)}_{slug(stem)}"


def _lunar(gek: Path, root: Path, index: _Index, used: set[str]) -> Found:
    data = _read_json(gek)
    stem = gek.name[: -len(".gek.json")]
    name, ident = _lunar_name(gek, root, stem)
    ns = NAMESPACES[LUNAR]
    cid = _unique(ns, ident, used)
    notes: list[str] = []
    model_ref = data.get("model") or f"{stem}.geo.json"
    geo_path = index.find(gek, str(model_ref))
    if geo_path is None:
        raise ValueError(f"no model file {model_ref}")
    geo = _read_json(geo_path)
    geometry = _geometry(geo)
    if not _has_shapes(geometry):
        raise ValueError(NO_SHAPES)
    texture_ref = data.get("texture") or f"{stem}.webp"
    texture_path = index.find(gek, str(texture_ref), extensions=(".webp", ".png"))
    if texture_path is None:
        raise ValueError(f"no texture {texture_ref}")
    anim_path = index.find(gek, str(data["animation"]), extensions=(".anim.json", ".animation.json")) \
        if data.get("animation") else None

    kind_word = f"{name} {gek.parent.name} {stem}".lower()
    render_type = str(data.get("item_render_type", "")).upper()
    gek_type = str(data.get("type", "")).lower()
    if render_type == "SHIELD" or "shield" in kind_word:
        slot, kind = "shield", "shield"
    elif "wing" in kind_word:
        slot, kind = "back", "wings"
    elif any(w in kind_word for w in AURA_WORDS):
        slot, kind = "aura", "aura"
    else:
        slot, kind = "body", gek_type or "body"
        notes.append(f"Lunar type '{gek_type}': worn on the body where it was modelled")

    model_id = cid.split(":", 1)[1]
    doc: dict = {"slot": slot, "name": name, "kind": kind, "texture": f"{ns}:textures/cosmetic/{model_id}.png",
                 "model": f"{ns}:{model_id}"}
    models = [(f"{ns}:cosmetic_models/{model_id}.geo.json", geo_path)]
    if anim_path is not None:
        doc["model_animations"] = f"{ns}:{model_id}.animation"
        models.append((f"{ns}:cosmetic_models/{model_id}.animation.json", anim_path))
        states = lunar_states(data.get("state_machine"), _clip_names(anim_path))
        if states:
            doc["model_states"] = states
    elif data.get("animation"):
        notes.append(f"animation file {data['animation']} not found; stands still")
    if data.get("is_translucent") is True:
        doc["bones"] = {bone: {"mode": "translucent"} for bone in cube_bones(geometry)}
    if slot == "shield":
        placement = shield_placement(geometry)
        if placement:
            doc["model_placement"] = placement
        notes.append("placed on the shield by its plate's size; its first-person display files aren't used")
    meta = _mcmeta(texture_path)
    frame_h, frame_ms, animated = _frames_from_mcmeta(meta, tick_ms=True)
    texture = TextureSource(texture_path, doc["texture"], frame_h, frame_ms, animated or data.get("animated_texture") is True)
    return Found(LUNAR, cid, name, slot, kind, gek, doc, texture, models, notes=notes)


def _clip_names(anim_path: Path) -> list[str]:
    try:
        anims = _read_json(anim_path).get("animations")
    except ValueError:
        return []
    return list(anims) if isinstance(anims, dict) else []


def lunar_states(machine, clips: list[str]) -> list[list[dict]]:
    """Lunar's `state_machine` (Molang controllers) as the mod's `model_states`."""
    if not isinstance(machine, dict) or not isinstance(machine.get("controllers"), list):
        return [[{"animation": clips[0]}]] if clips else []
    lower = {c.lower(): c for c in clips}
    controllers = []
    for controller in machine["controllers"]:
        if not isinstance(controller, dict):
            continue
        default_ticks = _number(controller.get("default_transition_ticks"), 0.0)
        states = []
        for state in controller.get("states") or []:
            if not isinstance(state, dict):
                continue
            anim = lower.get(str(state.get("anim", "")).lower())
            if anim is None:
                continue
            entry: dict = {"animation": anim}
            when = str(state.get("plays_when", "1")).strip()
            if when not in ("", "1", "true"):
                entry["when"] = when
            ticks = _number(state.get("transition_ticks"), default_ticks)
            if ticks > 0:
                entry["blend"] = round(min(60.0, ticks / 20.0), 3)
            states.append(entry)
        if states:
            controllers.append(states)
    return controllers


def _number(value, default: float) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def shield_placement(geometry: dict) -> dict | None:
    """Puts a shield built on the player's arm onto the vanilla shield: the plate's thin side along Z, its middle
    where the vanilla plate's is (0, 0, -1.5)."""
    skip = {"body", "bipedrightarm", "armorrightarm", "bipedleftarm", "armorleftarm"}
    box = bounds(corners(geometry, only=lambda n: n not in skip))
    if box is None:
        return None
    lo, hi = box
    size = [hi[a] - lo[a] for a in range(3)]
    thin = min(range(3), key=lambda a: size[a])
    rotation = [0.0, 0.0, 0.0]
    if thin == 0:
        rotation = [0.0, 90.0, 0.0]
    elif thin == 1:
        rotation = [90.0, 0.0, 0.0]
    m = convert.rotation_matrix(rotation)
    centre = convert.apply(m, [(lo[a] + hi[a]) / 2 for a in range(3)])
    target = [0.0, 0.0, -1.5]
    return {"rotation": rotation, "offset": [round(target[a] - centre[a], 4) for a in range(3)]}


def _lunar_cape(texture: Path, root: Path, used: set[str]) -> Found:
    stem = texture.stem
    name, ident = _lunar_name(texture, root, stem)
    ns = NAMESPACES[LUNAR]
    cid = _unique(ns, ident, used)
    meta = _mcmeta(texture)
    frame_h, frame_ms, animated = _frames_from_mcmeta(meta, tick_ms=True)
    location = f"{ns}:textures/cosmetic/{cid.split(':', 1)[1]}.png"
    doc = {"slot": "cape", "name": name, "kind": "cape", "texture": location}
    return Found(LUNAR, cid, name, "cape", "cape", texture, doc,
                 TextureSource(texture, location, frame_h, frame_ms, animated), cape=True)


def _norisk(descriptor: Path, root: Path, used: set[str]) -> list[Found]:
    data = _read_json(descriptor)
    stem = descriptor.name[: -len(".norisk.json")]
    folder = descriptor.parent
    ctype = str(data.get("type", "")).upper()
    name = str(data.get("name") or nice_name(stem))
    ns = NAMESPACES[NORISK]
    settings = data.get("defaultSettings") if isinstance(data.get("defaultSettings"), dict) else {}
    png = folder / f"{stem}.png"
    if not png.is_file():
        pngs = sorted(folder.glob("*.png"))
        png = pngs[0] if len(pngs) == 1 else None
    if png is None:
        raise ValueError("no texture")
    meta = _mcmeta(png)
    frame_h, frame_ms, animated = _frames_from_mcmeta(meta, tick_ms=False)

    if ctype == "CAPE":
        cid = _unique(ns, slug(f"cape_{stem}"), used)
        location = f"{ns}:textures/cosmetic/{cid.split(':', 1)[1]}.png"
        doc = {"slot": "cape", "name": name, "kind": "cape", "texture": location}
        return [Found(NORISK, cid, name, "cape", "cape", descriptor, doc,
                      TextureSource(png, location, frame_h, frame_ms, animated), cape=True)]

    geo_path = folder / f"{stem}.geo.json"
    if not geo_path.is_file():
        raise ValueError(f"{ctype.lower() or 'cosmetic'} without a model ({stem}.geo.json)")
    geo = _read_json(geo_path)
    geometry = _geometry(geo)
    if not _has_shapes(geometry):
        raise ValueError(NO_SHAPES)
    notes: list[str] = []
    slot, kind = NORISK_TYPES.get(ctype, ("body", None))
    if kind is None:
        parts = followed_parts(geometry)
        kind = f"accessory_{parts[0]}" if parts else "accessory"
    cid = _unique(ns, slug(stem), used)
    model_id = cid.split(":", 1)[1]
    doc: dict = {"slot": slot, "name": name, "kind": kind, "texture": f"{ns}:textures/cosmetic/{model_id}.png",
                 "model": f"{ns}:{model_id}", "skeleton": "player"}
    models = [(f"{ns}:cosmetic_models/{model_id}.geo.json", geo_path)]
    anim_path = folder / f"{stem}.animation.json"
    if anim_path.is_file():
        clips = _moving_clips(anim_path)
        idle = next((c for want in NORISK_IDLE for c in clips if c.lower() == want), None)
        if idle is not None:
            doc["model_animations"] = f"{ns}:{model_id}.animation"
            doc["model_states"] = [{"animation": idle}]
            models.append((f"{ns}:cosmetic_models/{model_id}.animation.json", anim_path))
    placement = {}
    scale = settings.get("scale")
    if isinstance(scale, (int, float)) and not isinstance(scale, bool) and scale > 0 and abs(scale - 1) > 1e-3:
        placement["scale"] = round(float(scale), 4)
    offset = settings.get("offset")
    if isinstance(offset, dict):
        o = [_number(offset.get(k), 0.0) for k in ("x", "y", "z")]
        if any(abs(v) > 1e-6 for v in o):
            # NoRisk's offsets: taken as pixels, +Y up like the model.
            placement["offset"] = [o[0], -o[1], o[2]]
            notes.append(f"offset {o} taken as pixels")
    if placement:
        doc["model_placement"] = placement
    if data.get("shouldReplaceHelmet") is True:
        notes.append("NoRisk hides the helmet for it; here the helmet setting decides")
    texture = TextureSource(png, doc["texture"], frame_h, frame_ms, animated)
    found = [Found(NORISK, cid, name, slot, kind, descriptor, doc, texture, models, notes=notes)]

    # Other textures for the same model (a flag per country): a cosmetic each.
    extra = folder / "textures"
    if extra.is_dir():
        for alt in sorted(extra.glob("*.png")):
            vid = _unique(ns, f"{model_id}_{slug(alt.stem)}", used)
            vname = f"{name} ({alt.stem.upper()})"
            vdoc = {**doc, "name": vname, "texture": f"{ns}:textures/cosmetic/{vid.split(':', 1)[1]}.png"}
            vmeta = _mcmeta(alt)
            vh, vms, vanimated = _frames_from_mcmeta(vmeta, tick_ms=False)
            found.append(Found(NORISK, vid, vname, slot, kind, alt, vdoc,
                               TextureSource(alt, vdoc["texture"], vh, vms, vanimated), models, notes=[]))
    return found


def _moving_clips(anim_path: Path) -> list[str]:
    """Animations in the file that move a bone (NoRisk's often only name one)."""
    try:
        anims = _read_json(anim_path).get("animations")
    except ValueError:
        return []
    if not isinstance(anims, dict):
        return []
    return [n for n, a in anims.items() if isinstance(a, dict) and isinstance(a.get("bones"), dict) and a["bones"]]


# ---------------------------------------------------------------------- writing


@dataclass
class TextureInfo:
    frame_size: tuple[int, int]
    frames: int
    frame_ms: int | None


@dataclass
class Written:
    cosmetics: list[Found] = field(default_factory=list)
    failed: list[tuple[Found, str]] = field(default_factory=list)


def convert_texture(source: TextureSource, max_size: int, quantize: bool) -> tuple[bytes, TextureInfo]:
    """A PNG or WebP (a strip of frames when animated) as the PNG strip the mod reads, scaled down to max_size."""
    from PIL import Image

    with Image.open(source.path) as opened:
        image = opened.convert("RGBA")
    w, h = image.size
    frame_h = h
    if source.animated:
        if source.frame_height and 0 < source.frame_height <= h:
            frame_h = source.frame_height
        elif h > w and h % w == 0:
            frame_h = w  # vanilla's square frames
    frames = max(1, h // frame_h)
    tw, th = textures.target_size(w, frame_h, frames, max_size)
    if frames > 1:
        strip = Image.new("RGBA", (tw, th * frames))
        for i in range(frames):
            frame = image.crop((0, i * frame_h, w, (i + 1) * frame_h))
            if (tw, th) != (w, frame_h):
                frame = frame.resize((tw, th), Image.LANCZOS)
            strip.paste(frame, (0, i * th))
        image = strip
    elif (tw, th) != (w, h):
        image = image.resize((tw, th), Image.LANCZOS)
    if quantize:
        image = image.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    out = io.BytesIO()
    image.save(out, format="PNG", optimize=False, compress_level=9)
    ms = (source.frame_ms or DEFAULT_FRAME_MS) if frames > 1 else None
    return out.getvalue(), TextureInfo((tw, th), frames, ms)


def finish(found: Found, info: TextureInfo) -> dict:
    """The cosmetic's JSON, now that its texture's frames are known."""
    doc = dict(found.doc)
    animation = {"frames": info.frames, "frame_time": info.frame_ms} if info.frames > 1 else None
    if found.cape:
        layout_w, layout_h, length = convert.box_cape_layout(info.frame_size)
        fw, fh = info.frame_size
        size = (convert.BOX_CAPE_WIDTH, length, convert.BOX_CAPE_DEPTH)
        cube = {"from": [-convert.BOX_CAPE_WIDTH / 2, 0, -convert.BOX_CAPE_DEPTH],
                "to": [convert.BOX_CAPE_WIDTH / 2, length, 0],
                "faces": convert.box_faces(size, fw / layout_w, fh / layout_h)}
        doc["texture_size"] = [fw, fh]
        doc["parts"] = [{"name": "cape", "cubes": [cube]}]
    if animation:
        doc["texture_animation"] = animation
    return doc


def check_output(output: Path) -> None:
    if output.exists() and not output.is_dir():
        raise LayoutError(f"{output} exists and is not a folder")
    if output.is_dir() and any(output.iterdir()) and not (output / MARKER).is_file():
        raise LayoutError(f"{output} is not empty and wasn't written by this tool; choose an empty or new folder")


def write(found: list[Found], output: Path, *, max_size: int, quantize: bool, progress=lambda m: None) -> Written:
    check_output(output)
    output.mkdir(parents=True, exist_ok=True)
    result = Written()
    written: set[Path] = set()
    infos: dict[str, TextureInfo] = {}
    for i, f in enumerate(found):
        if i and i % 20 == 0:
            progress(f"{i} of {len(found)} cosmetics")
        try:
            if f.texture.location not in infos:
                png, info = convert_texture(f.texture, max_size, quantize)
                target = output / convert.location_to_path(f.texture.location)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(png)
                written.add(target)
                infos[f.texture.location] = info
            for location, source in f.models:
                target = output / convert.location_to_path(location)
                if target not in written:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    # Re-written compactly; the mod reads JSON with comments-free strict parsing.
                    target.write_text(json.dumps(_read_json(source), separators=(",", ":")), encoding="utf-8")
                    written.add(target)
            doc = finish(f, infos[f.texture.location])
        except (OSError, ValueError) as e:
            result.failed.append((f, f"{type(e).__name__}: {e}"))
            continue
        ns, name = f.id.split(":", 1)
        target = output / "assets" / ns / "cosmetics" / f"{name}.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
        written.add(target)
        result.cosmetics.append(f)
    mcmeta = output / "pack.mcmeta"
    mcmeta.write_text(json.dumps(PACK_MCMETA, indent=2) + "\n", encoding="utf-8")
    written.add(mcmeta)
    for ns in NAMESPACES.values():
        ours = output / "assets" / ns
        if ours.is_dir():
            for path in sorted(ours.rglob("*"), reverse=True):
                if path.is_file() and path not in written:
                    path.unlink()
                elif path.is_dir() and not any(path.iterdir()):
                    path.rmdir()
    (output / MARKER).write_text(json.dumps({"version": __version__}), encoding="utf-8")
    return result


def report(result: FoundAll, written: Written, root: Path) -> str:
    lines = [f"# Lunar / NoRisk import of `{root.name}`", "",
             f"Written by `badlion-import` {__version__}: {len(written.cosmetics)} cosmetic(s), "
             f"{len(written.failed)} failed, {len(result.skipped)} skipped.", ""]
    for client in (LUNAR, NORISK):
        mine = [f for f in written.cosmetics if f.client == client]
        if not mine:
            continue
        lines += [f"## {client.capitalize()}", "", "| Cosmetic | Id | Slot | Kind | Notes |", "|---|---|---|---|---|"]
        for f in sorted(mine, key=lambda f: (f.slot, f.name.lower())):
            extras = []
            if "model_animations" in f.doc:
                extras.append("animated")
            if f.texture and f.texture.animated:
                extras.append("animated texture")
            notes = "; ".join(extras + f.notes)
            lines.append(f"| {f.name} | `{f.id}` | {f.slot} | {f.kind} | {notes} |")
        lines.append("")
    if written.failed:
        lines += ["## Failed", ""] + [f"- {f.name} (`{f.source.relative_to(root).as_posix()}`): {why}"
                                      for f, why in written.failed] + [""]
    if result.skipped:
        lines += ["## Skipped", ""] + [f"- `{s.path.relative_to(root).as_posix()}`: {s.reason}"
                                       for s in result.skipped] + [""]
    return "\n".join(lines)
