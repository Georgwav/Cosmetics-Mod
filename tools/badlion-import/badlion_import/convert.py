"""The `import` command's planning: which cosmetics convert, and the cosmetic JSON, textures and models each needs.

Badlion's models are measured in pixels like this mod's, but in different axes, the ones of the 3D tool they were
made in (front at -Y, up at +Z):
- OBJ files are exported Y-up with the front at +Z, so a point (x, y, z) is (x, -y, -z) in this mod's model space
  (+Y down, front at -Z): a half turn around X.
- MD5 files keep the tool's axes, Z-up with the front at -Y: (x, y, z) is (x, -z, y), a quarter turn around X.
Both keep +X on the wearer's left, so nothing is mirrored and faces keep their winding.

Where each type's origin sits (the neck for head items, the feet for backpacks, ...) comes from the model bounds in
LAYOUT.md. Anything that only shows in game (texture V direction, which built-in cloak capes use) has a setting, and
`--calibrate` writes variants of a few cosmetics side by side so the right one can be picked by eye.
"""

from __future__ import annotations

import json
import math
import re
from dataclasses import dataclass, field
from pathlib import Path

from . import badlion, meshes

NAMESPACE = "blc_import"
TEXTURES = "textures/cosmetic/"
MODELS = "models/cosmetic/"


# ---------------------------------------------------------------------- types

@dataclass(frozen=True)
class TypeRule:
    slot: str
    origin: tuple[float, float, float]  # the Badlion model origin, in this mod's anchor space (pixels)
    kind: str = "worn"  # worn, cape or wings


RULES: dict[str, TypeRule] = {
    "HAT": TypeRule("head", (0, 0, 0)),
    "EARS": TypeRule("head", (0, 0, 0)),
    "MASK": TypeRule("face", (0, 0, 0)),
    "EYES": TypeRule("face", (0, 0, 0)),
    "MOUTH": TypeRule("face", (0, 0, 0)),
    "NOSE": TypeRule("face", (0, 0, 0)),
    "BANDANA": TypeRule("face", (0, 0, 0)),
    "NECK": TypeRule("body", (0, 0, 0)),
    "BELT": TypeRule("body", (0, 12, 0)),
    "BACK": TypeRule("back", (0, 24, 0)),
    "TAIL": TypeRule("back", (0, 12, 0)),
    "CAPE": TypeRule("cape", (0, 0, 0), "cape"),
    "WINGS": TypeRule("back", (0, 4, 2), "wings"),
    # Shields replace the held vanilla shield; Badlion's shield models are centred on the plate, whose centre
    # in the vanilla shield model is (0, 0, -1.5).
    "SHIELD": TypeRule("shield", (0, 0, -1.5)),
}

UNSUPPORTED: dict[str, str] = {
    "PET": "pets aren't worn on the player",
    "PLAYER_PET": "pets aren't worn on the player",
    "SWORD": "held items aren't supported",
    "BOW": "held items aren't supported",
    "PROJECTILE": "held items aren't supported",
    "SPRAY": "sprays aren't worn on the player",
    "AURA": "particle effects aren't supported",
    "BLC_NAMETAG_ICON": "nametag decorations aren't supported",
    "INSIDER_NAMETAG_ICON": "nametag decorations aren't supported",
    "INSIDER_NAMETAG_BORDER": "nametag decorations aren't supported",
    "BOOTS": "the mod has no leg anchor yet",
    "ARM_BAND": "the mod has no arm anchor yet",
}

# Built-in cloak models tried for capes without a modelName, in order of preference on a tie.
CAPE_CANDIDATES = ("cloak_v2", "cloak_1", "corsair_cloak")
# The default shape of capes without a modelName: a box like the vanilla cape, textured with the vanilla box
# layout. Badlion's cape textures are 22 x 23 blocks (704 x 736 at 32 px a block, 352 x 368 at 16): exactly
# that layout for a cape 10 wide, 22 long and 1 thick, a longer vanilla cape.
BOX_CAPE = "box"
BOX_CAPE_WIDTH = 10
BOX_CAPE_DEPTH = 1
CAPE_STYLE_ORDER = ("dynamic", "curved", "rect")
WING_ANIMATION_ORDER = ("flapping", "wings", "animation")


# ---------------------------------------------------------------------- rotations

def rotation_matrix(degrees) -> list[list[float]]:
    """Rotation applied X, then Y, then Z (Rz · Ry · Rx), as the mod applies `rotation`."""
    x, y, z = (math.radians(a) for a in degrees)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    return [[cy * cz, sx * sy * cz - cx * sz, cx * sy * cz + sx * sz],
            [cy * sz, sx * sy * sz + cx * cz, cx * sy * sz - sx * cz],
            [-sy, sx * cy, cx * cy]]


def multiply(a, b) -> list[list[float]]:
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v) -> list[float]:
    return [m[i][0] * v[0] + m[i][1] * v[1] + m[i][2] * v[2] for i in range(3)]


def euler(m) -> list[float]:
    """Degrees [x, y, z] whose rotation_matrix is m."""
    sy = -m[2][0]
    sy = max(-1.0, min(1.0, sy))
    y = math.asin(sy)
    if abs(math.cos(y)) > 1e-6:
        x = math.atan2(m[2][1], m[2][2])
        z = math.atan2(m[1][0], m[0][0])
    else:  # gimbal lock: put everything in Z
        x = 0.0
        z = math.atan2(-m[0][1], m[1][1])
    return [_tidy(math.degrees(a)) for a in (x, y, z)]


def _tidy(value: float) -> float:
    value = round(value, 4)
    return 0.0 if value == 0 else (180.0 if value == -180.0 else value)


# Badlion model axes to this mod's, per file format; capes are drawn in the vanilla cape's space, which is the
# body's turned half around Y.
OBJ_TO_MOD = rotation_matrix((180, 0, 0))
MD5_TO_MOD = rotation_matrix((90, 0, 0))
CAPE_SPACE = rotation_matrix((0, 180, 0))


# ---------------------------------------------------------------------- settings

@dataclass
class TypeSettings:
    flip_v_obj: bool = False
    flip_v_md5: bool = False
    offset: tuple[float, float, float] = (0.0, 0.0, 0.0)  # extra, in this mod's anchor space
    rotation: tuple[float, float, float] = (0.0, 0.0, 0.0)  # extra, in this mod's anchor space, applied last
    shading: str = "smooth"


@dataclass
class Settings:
    max_texture_size: int = 256
    quantize: bool = True
    cape_model: str | None = None  # "box" (default) or a cloak under the texture root, e.g. cloak/models/cloak_1/cloak_1.md5mesh
    cape_style: str | None = None  # default style of capes with cloak animations
    types: dict[str, TypeSettings] = field(default_factory=dict)

    def of(self, cosmetic_type: str) -> TypeSettings:
        if cosmetic_type not in self.types:
            self.types[cosmetic_type] = TypeSettings(**DEFAULT_TYPE_SETTINGS.get(cosmetic_type, {}))
        return self.types[cosmetic_type]


# Defaults that differ per type. Capes: `inspect` found the cloak's UVs fit real cape textures best with V up.
DEFAULT_TYPE_SETTINGS = {"CAPE": {"flip_v_md5": True}}


def load_settings(path: Path | None) -> Settings:
    s = Settings()
    if path is None:
        return s
    raw = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        raise ValueError(f"{path}: expected a JSON object")
    for key in ("max_texture_size", "quantize", "cape_model", "cape_style"):
        if key in raw:
            setattr(s, key, raw[key])
    for t, values in (raw.get("types") or {}).items():
        ts = s.of(t.upper())
        for key, value in values.items():
            if not hasattr(ts, key):
                raise ValueError(f"{path}: types.{t}.{key} is not a setting (use one of "
                                 f"{', '.join(TypeSettings.__dataclass_fields__)})")
            setattr(ts, key, tuple(value) if isinstance(value, list) else value)
    return s


# ---------------------------------------------------------------------- OBJ clean-up

@dataclass
class ObjResult:
    text: str
    triangles: int
    clamped_uvs: int
    added_uvs: int


def clean_obj(text: str) -> ObjResult:
    """Rewrites an OBJ as the mod's reader wants it: absolute indices, texture coordinates within 0..1 on every
    face corner (out-of-range ones clamped, a missing one pointing at a dummy), materials and groups dropped."""
    vs, vts, vns, faces = [], [], [], []
    clamped = 0
    for raw in text.splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        parts = line.split()
        tag = parts[0]
        try:
            if tag == "v":
                vs.append([_finite(parts[1]), _finite(parts[2]), _finite(parts[3])])
            elif tag == "vt":
                u, v = _finite(parts[1]), _finite(parts[2]) if len(parts) > 2 else 0.0
                cu, cv = min(1.0, max(0.0, u)), min(1.0, max(0.0, v))
                clamped += (cu != u) or (cv != v)
                vts.append([cu, cv])
            elif tag == "vn":
                vns.append([_finite(parts[1]), _finite(parts[2]), _finite(parts[3])])
            elif tag == "f":
                corners = []
                for token in parts[1:]:
                    bits = token.split("/")
                    vi = _index(bits[0], len(vs))
                    ti = _index(bits[1], len(vts)) if len(bits) > 1 and bits[1] else None
                    ni = _index(bits[2], len(vns)) if len(bits) > 2 and bits[2] else None
                    corners.append((vi, ti, ni))
                if len(corners) >= 3:
                    faces.append(corners)
        except (ValueError, IndexError):
            continue  # a malformed line is dropped; the mod would refuse the whole file
    dummy = None
    added = 0
    for face in faces:
        if any(t is None for _, t, _ in face):
            added += 1
            if dummy is None:
                vts.append([0.0, 0.0])
                dummy = len(vts) - 1
    normals_everywhere = all(n is not None for face in faces for _, _, n in face)
    out = [f"v {_num(x)} {_num(y)} {_num(z)}" for x, y, z in vs]
    out += [f"vt {_num(u)} {_num(v)}" for u, v in vts]
    if normals_everywhere:
        out += [f"vn {_num(x)} {_num(y)} {_num(z)}" for x, y, z in vns]
    triangles = 0
    for face in faces:
        triangles += len(face) - 2
        tokens = []
        for vi, ti, ni in face:
            ti = dummy if ti is None else ti
            tokens.append(f"{vi + 1}/{ti + 1}/{ni + 1}" if normals_everywhere else f"{vi + 1}/{ti + 1}")
        out.append("f " + " ".join(tokens))
    return ObjResult("\n".join(out) + "\n", triangles, clamped, added)


def _finite(text: str) -> float:
    value = float(text)
    if not math.isfinite(value):
        raise ValueError(text)
    return value


def _index(text: str, count: int) -> int:
    value = int(text)
    index = value - 1 if value > 0 else count + value
    if value == 0 or not 0 <= index < count:
        raise IndexError(text)
    return index


def _num(value: float) -> str:
    text = f"{value:.6g}"
    return "0" if text in ("-0", "0") else text


# ---------------------------------------------------------------------- MD5

def matching_animations(mesh_text: str, animations: dict[str, str]) -> tuple[dict[str, str], list[str]]:
    """The animations whose joints are the mesh's (same names, same order); the mod refuses any other."""
    joints = meshes.md5mesh_joint_names(mesh_text)
    kept, dropped = {}, []
    for path, text in animations.items():
        if meshes.summarize_md5anim(text).joint_names == joints:
            kept[path] = text
        else:
            dropped.append(path)
    return kept, dropped


def choose(names, order) -> str | None:
    """The first of `order` among `names`, else the first name."""
    for preferred in order:
        if preferred in names:
            return preferred
    return next(iter(names), None)


# ---------------------------------------------------------------------- ids and paths

def resource_name(text: str) -> str:
    """A path segment the mod accepts (lowercase letters, digits, _ . -)."""
    s = re.sub(r"[^a-z0-9_.-]+", "_", text.lower()).strip("_")
    return s or "unnamed"


def texture_id(cosmetic: badlion.Cosmetic, suffix: str = "") -> str:
    return f"{NAMESPACE}:{TEXTURES}{cosmetic.folder}/{resource_name(f'{cosmetic.type}_{cosmetic.id}{suffix}')}.png"


def model_id(source_path: str) -> str:
    """The pack location of a Badlion model file, keeping its folder structure under <folder>/models/."""
    rest = source_path[len(badlion.TEXTURE_ROOT):]
    folder, _, tail = rest.partition("/models/")
    return f"{NAMESPACE}:{MODELS}{resource_name(folder)}/" + "/".join(resource_name(p) for p in tail.split("/"))


def location_to_path(location: str) -> str:
    namespace, _, path = location.partition(":")
    return f"assets/{namespace}/{path}"


# ---------------------------------------------------------------------- definitions to placement

def model_scale(model: badlion.Model | None) -> float:
    s = model.raw.get("scale") if model else None
    return float(s) if isinstance(s, (int, float)) and not isinstance(s, bool) and s > 0 else 1.0


def vector(raw, keys=("x", "y", "z")) -> list[float]:
    if isinstance(raw, dict):
        return [float(raw.get(k, 0) or 0) for k in keys]
    if isinstance(raw, list) and len(raw) >= 3:
        return [float(v or 0) for v in raw[:3]]
    return [0.0, 0.0, 0.0]


def color_of(raw) -> str | None:
    """Badlion's defaultColor (an RGB number, or an object with r, g, b) as #RRGGBB."""
    if isinstance(raw, bool):
        return None
    if isinstance(raw, int):
        return None if raw < 0 else f"#{raw & 0xFFFFFF:06X}"
    if isinstance(raw, dict):
        keys = {k.lower(): v for k, v in raw.items()}
        rgb = [keys.get(k, keys.get(long)) for k, long in (("r", "red"), ("g", "green"), ("b", "blue"))]
        if all(isinstance(c, (int, float)) and not isinstance(c, bool) for c in rgb):
            scale = 255 if all(0 <= c <= 1 for c in rgb) and any(isinstance(c, float) for c in rgb) else 1
            return "#" + "".join(f"{max(0, min(255, int(round(c * scale)))):02X}" for c in rgb)
    return None


@dataclass
class Placement:
    """Where a model goes, as the mod's mesh keys."""

    scale: float
    rotation: list[float]
    offset: list[float]

    def mesh_keys(self) -> dict:
        keys = {}
        if abs(self.scale - 1.0) > 1e-9:
            keys["scale"] = round(self.scale, 6)
        if any(abs(a) > 1e-9 for a in self.rotation):
            keys["rotation"] = self.rotation
        if any(abs(o) > 1e-9 for o in self.offset):
            keys["offset"] = [round(o, 4) for o in self.offset]
        return keys


def placement(cosmetic: badlion.Cosmetic, model: badlion.Model | None, md5: bool, rule: TypeRule,
              ts: TypeSettings) -> Placement:
    """The model's Badlion transform (scale, rotation, translation, the cosmetic's sizing) followed by the axis
    change, the type's origin and the type's extra settings."""
    convert = MD5_TO_MOD if md5 else OBJ_TO_MOD
    if rule.kind == "cape":
        convert = multiply(CAPE_SPACE, convert)
    extra = rotation_matrix(ts.rotation)
    own = rotation_matrix(vector(model.raw.get("rotation"))) if model and model.raw.get("rotation") else None
    total = multiply(extra, multiply(convert, own) if own else convert)

    sizing = cosmetic.raw.get("sizingData") if isinstance(cosmetic.raw.get("sizingData"), dict) else {}
    s = model_scale(model)
    sizing_scale = sizing.get("scale")
    if isinstance(sizing_scale, (int, float)) and not isinstance(sizing_scale, bool) and sizing_scale > 0:
        s *= sizing_scale
    translation = vector(model.raw.get("translation")) if model else [0.0, 0.0, 0.0]
    sizing_offset = vector(sizing, ("offsetX", "offsetY", "offsetZ"))
    t = [translation[i] + sizing_offset[i] for i in range(3)]
    moved = apply(multiply(extra, convert), t)
    origin = apply(extra, rule.origin)
    offset = [moved[i] + origin[i] + ts.offset[i] for i in range(3)]
    return Placement(s, euler(total), offset)


# ---------------------------------------------------------------------- the plan

@dataclass
class TextureJob:
    source: str
    location: str
    fixed_delay: object = None
    file_timing: bool = True


@dataclass
class ModelFile:
    source: str
    location: str
    md5: bool
    triangles: int = 0


@dataclass
class Planned:
    cosmetic: badlion.Cosmetic
    id: str
    name: str
    textures: list[TextureJob]
    models: list[ModelFile]
    build: object  # callable(texture_info: dict[location, Converted]) -> cosmetic JSON dict
    triangles: int = 0
    notes: list[str] = field(default_factory=list)


@dataclass
class Skipped:
    cosmetic: badlion.Cosmetic
    reason: str


@dataclass
class Plan:
    planned: list[Planned] = field(default_factory=list)
    skipped: list[Skipped] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)


def find_texture(cosmetic: badlion.Cosmetic, present: set[str]) -> str | None:
    """The cosmetic's texture: the pattern its animationData says, else the other one."""
    f = cosmetic.folder
    static = f"{badlion.TEXTURE_ROOT}{f}/{f}_{cosmetic.id}.avif"
    animated = f"{badlion.TEXTURE_ROOT}{f}/{cosmetic.id}/{f}_{cosmetic.id}.avif"
    for path in ((animated, static) if cosmetic.animated else (static, animated)):
        if path in present:
            return path
    return None


def glow_texture(cosmetic: badlion.Cosmetic, present: set[str]) -> str | None:
    path = f"{badlion.TEXTURE_ROOT}{cosmetic.folder}/{cosmetic.id}/glow.avif"
    return path if path in present else None


def box_cape_layout(source_size: tuple[int, int]) -> tuple[int, int, int]:
    """(layout width, layout height, cape length) in blocks for a cape texture of this size (one frame). A 2:1
    texture is the vanilla 64 x 32 layout (16 long); anything else is 22 blocks wide like Badlion's capes, and
    as long as its height says."""
    w, h = source_size
    if w == 2 * h:
        return 64, 32, 16
    width = 2 * (BOX_CAPE_WIDTH + BOX_CAPE_DEPTH)
    length = min(64, max(4, round(h * width / w) - BOX_CAPE_DEPTH))
    return width, length + BOX_CAPE_DEPTH, length


def box_faces(size: tuple[float, float, float], scale_u: float, scale_v: float) -> dict[str, list[float]]:
    """Per-face UVs in pixels of the vanilla box layout (as the mod's `uv` box mode lays it out) at the origin,
    scaled from blocks to pixels."""
    dx, dy, dz = size
    u0, u1, u2, u3, u4, u5 = 0, dz, dz + dx, dz + 2 * dx, 2 * dz + dx, 2 * dz + 2 * dx
    v0, v1, v2 = 0, dz, dz + dy
    faces = {"down": (u1, v0, u2, v1), "up": (u2, v1, u3, v0), "north": (u1, v1, u2, v2),
             "south": (u4, v1, u5, v2), "west": (u0, v1, u1, v2), "east": (u2, v1, u4, v2)}
    return {k: [round(a * scale_u, 4), round(b * scale_v, 4), round(c * scale_u, 4), round(d * scale_v, 4)]
            for k, (a, b, c, d) in faces.items()}


def cape_model_candidates(present: set[str]) -> list[str]:
    root = badlion.TEXTURE_ROOT + "cloak/models/"
    return [f"{root}{n}/{n}.md5mesh" for n in CAPE_CANDIDATES if f"{root}{n}/{n}.md5mesh" in present]


class Planner:
    """Builds the plan. File contents are read through `read` (path -> bytes) so the plan can be tested and
    model files are read once however many cosmetics share them."""

    def __init__(self, definitions: badlion.Definitions, present: set[str], read, settings: Settings):
        self.d = definitions
        self.present = present
        self.read = read
        self.settings = settings
        self._models: dict[str, ModelFile] = {}
        self._locations: dict[str, str] = {}  # pack location -> source, to keep case-only differences apart
        self._texts: dict[str, str] = {}
        self._anims: dict[str, tuple[dict[str, str], list[str]]] = {}
        self.obj_stats = {"clamped_uvs": 0, "added_uvs": 0}
        cloak = settings.cape_model and badlion.TEXTURE_ROOT + settings.cape_model
        self.cape_model = cloak if settings.cape_model != BOX_CAPE and cloak in present else BOX_CAPE

    # -------------------------------------------------------------- files

    def text(self, path: str) -> str:
        if path not in self._texts:
            self._texts[path] = self.read(path).decode("utf-8", "replace")
        return self._texts[path]

    def model_file(self, path: str) -> ModelFile:
        found = self._models.get(path)
        if found is None:
            md5 = path.endswith(".md5mesh")
            if md5:
                triangles = meshes.summarize_md5mesh(self.text(path)).triangles
            else:
                cleaned = clean_obj(self.text(path))
                self._texts[path] = cleaned.text
                self.obj_stats["clamped_uvs"] += cleaned.clamped_uvs
                self.obj_stats["added_uvs"] += cleaned.added_uvs
                triangles = cleaned.triangles
            found = ModelFile(path, self.location_of(path), md5, triangles)
            self._models[path] = found
        return found

    def animations(self, mesh_path: str) -> tuple[dict[str, str], list[str]]:
        """name -> pack location of the .md5anim files next to an .md5mesh that fit it, and the ones that don't."""
        if mesh_path not in self._anims:
            folder = mesh_path.rsplit("/", 1)[0] + "/"
            files = {p: self.text(p) for p in sorted(self.present)
                     if p.startswith(folder) and p.endswith(".md5anim") and "/" not in p[len(folder):]}
            kept, dropped = matching_animations(self.text(mesh_path), files)
            for p in kept:
                self._models.setdefault(p, ModelFile(p, self.location_of(p), True))
            names = {resource_name(p.rsplit("/", 1)[1][:-len(".md5anim")]): self._models[p].location for p in kept}
            self._anims[mesh_path] = (names, dropped)
        return self._anims[mesh_path]

    def location_of(self, path: str) -> str:
        """model_id(path), numbered when another file already lowercases to the same location."""
        location = model_id(path)
        stem, dot, ext = location.rpartition(".")
        n = 2
        while self._locations.setdefault(location, path) != path:
            location = f"{stem}_{n}{dot}{ext}"
            n += 1
        return location

    # -------------------------------------------------------------- per cosmetic

    def plan(self, cosmetics) -> Plan:
        plan = Plan()
        for c in cosmetics:
            result = self.plan_one(c)
            (plan.planned if isinstance(result, Planned) else plan.skipped).append(result)
        return plan

    def mesh_source(self, c: badlion.Cosmetic) -> tuple[str | None, badlion.Model | None, str | None]:
        """(mesh path, models.json entry, reason it can't convert)."""
        rule = RULES[c.type]
        if c.model_name:
            model = self.d.model_of(c)
            if model is None and rule.kind == "cape":
                # Capes naming a model Badlion doesn't define ("cloak", "cape", ...) get its built-in cape.
                return self.cape_model, None, None
            if model is None:
                return None, None, f"model '{c.model_name}' has no models.json entry"
            if model.mesh_path() not in self.present:
                return None, model, f"model '{c.model_name}' is not in your jars"
            return model.mesh_path(), model, None
        if rule.kind == "cape":
            return self.cape_model, None, None
        return None, None, "no model (Badlion draws it from the player skin or a built-in model)"

    def plan_one(self, c: badlion.Cosmetic, *, variant: dict | None = None):
        if c.type in UNSUPPORTED:
            return Skipped(c, UNSUPPORTED[c.type])
        if c.type not in RULES:
            return Skipped(c, f"unknown type {c.type}")
        texture = find_texture(c, self.present)
        if texture is None:
            return Skipped(c, "texture is not in your jars (not owned)")
        mesh_path, model, problem = self.mesh_source(c)
        if variant and variant.get("mesh"):
            mesh_path, model, problem = variant["mesh"], None, None
        if problem:
            return Skipped(c, problem)
        notes = []
        if c.model_name and model is None and not variant:
            notes.append(f"model '{c.model_name}' isn't in models.json; uses the built-in cape")
        if mesh_path == BOX_CAPE:
            return self.plan_box_cape(c, texture, variant, notes)

        rule = RULES[c.type]
        ts = self.settings.of(c.type)
        mf = self.model_file(mesh_path)
        for key in ("subModels", "extraModels", "shieldModelData"):
            if model and model.raw.get(key):
                notes.append(f"{key} not converted (only the main model is)")
        # The type's setting (or the calibration variant's), turned around for models Badlion flips.
        flip = ts.flip_v_md5 if mf.md5 else ts.flip_v_obj
        if variant and "flip" in variant:
            flip = variant["flip"]
        if model and model.raw.get("flipUVs") is True:
            flip = not flip
        place = placement(c, model, mf.md5, rule, ts)

        mesh = {"model": mf.location, "shading": ts.shading, **place.mesh_keys()}
        if flip:
            mesh["flip_v"] = True
        styles: dict[str, str] = {}
        if mf.md5:
            styles, dropped = self.animations(mesh_path)
            if dropped:
                notes.append(f"{len(dropped)} animation(s) skipped: their joints don't match the mesh")

        tex_loc = texture_id(c)
        textures = [TextureJob(texture, tex_loc, (c.raw.get("animationData") or {}).get("frameDelay"),
                               bool((c.raw.get("animationData") or {}).get("customFrameDelay", True)))]
        glow = glow_texture(c, self.present)
        glow_loc = texture_id(c, "_glow") if glow else None
        if glow:
            textures.append(TextureJob(glow, glow_loc, (c.raw.get("animationData") or {}).get("frameDelay"),
                                       bool((c.raw.get("animationData") or {}).get("customFrameDelay", True))))
        mode = "translucent" if c.raw.get("transparent") is True else "cutout"
        tinted = not (model and model.raw.get("applyChroma") is False)
        triangles = mf.triangles * (2 if rule.kind == "wings" else 1)

        model_files = [mf]
        if mf.md5:
            model_files += [self._models[p] for p in self._models
                            if self._models[p].location in styles.values()]

        cosmetic_type = c.type
        cape_style = self.settings.cape_style
        cid = variant["id"] if variant else badlion.cosmetic_id(c)
        display_name = variant["name"] if variant else c.name

        def build(converted: dict) -> dict:
            info = converted[tex_loc]
            doc = {"slot": rule.slot, "name": display_name, "texture": tex_loc, "texture_size": list(info.frame_size)}
            animation = texture_animation(info)
            if animation:
                doc["texture_animation"] = animation
            options = options_of(c)
            if options:
                doc["options"] = options
            color = color_of(c.raw.get("defaultColor"))
            if color:
                doc["default_color"] = color

            def part(name, texture_location=None, part_mode=mode, extra=None):
                p = {"name": name, "mode": part_mode}
                if texture_location:
                    p["texture"] = texture_location
                    glow_info = converted[texture_location]
                    glow_animation = texture_animation(glow_info)
                    if glow_animation:
                        p["texture_animation"] = glow_animation
                if not tinted:
                    p["tint"] = False
                p["meshes"] = [{**mesh, **(extra or {})}]
                return p

            if rule.kind == "wings":
                doc["wings"] = wings_of(c, mf.md5, place)
                pivot = [round(o, 4) for o in place.offset]
                anim = {}
                if styles:
                    name = choose(styles, WING_ANIMATION_ORDER)
                    anim = {"bone_animation": styles[name]}
                parts = [
                    {**part("right_wing", extra=anim), "wing": "right", "wing_pivot": pivot},
                    {**part("left_wing", extra={**anim, "mirror": True}), "wing": "left", "wing_pivot": pivot},
                ]
                if glow:
                    parts += [
                        {**part("right_wing_glow", glow_loc, "emissive", anim), "wing": "right", "wing_pivot": pivot},
                        {**part("left_wing_glow", glow_loc, "emissive", {**anim, "mirror": True}), "wing": "left",
                         "wing_pivot": pivot},
                    ]
                doc["parts"] = parts
            else:
                extra = {}
                if styles:
                    if rule.kind == "cape" or len(styles) > 1:
                        extra = {"bone_animations": styles}
                        default = cape_style if cape_style in styles else choose(styles, CAPE_STYLE_ORDER)
                        if default:
                            doc["default_style"] = default
                    else:
                        extra = {"bone_animation": next(iter(styles.values()))}
                parts = [part(cosmetic_type.lower(), extra=extra)]
                if glow:
                    parts.append(part("glow", glow_loc, "emissive", extra))
                doc["parts"] = parts
            return doc

        return Planned(c, cid, display_name, textures, model_files, build, triangles, notes)

    def plan_box_cape(self, c: badlion.Cosmetic, texture: str, variant: dict | None, notes: list[str]) -> Planned:
        """A cape without a model of its own: a box like the vanilla cape, swinging with the vanilla cape's
        physics, its size and UVs from the texture's layout (see BOX_CAPE)."""
        timing = c.raw.get("animationData") or {}
        tex_loc = texture_id(c)
        textures = [TextureJob(texture, tex_loc, timing.get("frameDelay"), bool(timing.get("customFrameDelay", True)))]
        glow = glow_texture(c, self.present)
        glow_loc = texture_id(c, "_glow") if glow else None
        if glow:
            textures.append(TextureJob(glow, glow_loc, timing.get("frameDelay"),
                                       bool(timing.get("customFrameDelay", True))))
        mode = "translucent" if c.raw.get("transparent") is True else "cutout"
        cid = variant["id"] if variant else badlion.cosmetic_id(c)
        display_name = variant["name"] if variant else c.name

        def build(converted: dict) -> dict:
            info = converted[tex_loc]
            layout_w, layout_h, length = box_cape_layout(info.source_size)
            fw, fh = info.frame_size
            size = (BOX_CAPE_WIDTH, length, BOX_CAPE_DEPTH)
            cube = {"from": [-BOX_CAPE_WIDTH / 2, 0, -BOX_CAPE_DEPTH], "to": [BOX_CAPE_WIDTH / 2, length, 0],
                    "faces": box_faces(size, fw / layout_w, fh / layout_h)}
            doc = {"slot": "cape", "name": display_name, "texture": tex_loc, "texture_size": [fw, fh]}
            animation = texture_animation(info)
            if animation:
                doc["texture_animation"] = animation
            options = options_of(c)
            if options:
                doc["options"] = options
            color = color_of(c.raw.get("defaultColor"))
            if color:
                doc["default_color"] = color
            parts = [{"name": "cape", "mode": mode, "cubes": [cube]}]
            if glow:
                glow_info = converted[glow_loc]
                gw, gh = glow_info.frame_size
                glow_part = {"name": "glow", "mode": "emissive", "texture": glow_loc,
                             "cubes": [{**cube, "faces": box_faces(size, gw / layout_w, gh / layout_h)}]}
                glow_animation = texture_animation(glow_info)
                if glow_animation:
                    glow_part["texture_animation"] = glow_animation
                parts.append(glow_part)
            doc["parts"] = parts
            return doc

        return Planned(c, cid, display_name, textures, [], build, 12, notes)


def texture_animation(info) -> dict | None:
    if info.frames <= 1:
        return None
    times = info.frame_times
    if len(set(times)) == 1:
        return {"frames": info.frames, "frame_time": times[0]}
    return {"frames": info.frames, "frame_times": times}


def options_of(c: badlion.Cosmetic) -> dict:
    """Badlion's per-cosmetic choices as the mod's player settings: colour only where Badlion allows chroma."""
    options = {}
    if c.raw.get("allowChroma") is not True:
        options["tint"] = False
        options["rainbow"] = False
    if c.raw.get("allowSaturation") is not True:
        options["saturation"] = False
    return options


def wings_of(c: badlion.Cosmetic, md5: bool, place: Placement) -> dict:
    """The mod's wing behaviour from Badlion's wingData. Skeletal wings flap through their own animation, so
    their parts don't turn."""
    data = c.raw.get("wingData") if isinstance(c.raw.get("wingData"), dict) else {}
    wings: dict = {"axis": "y"}
    base = data.get("flapBase")
    if isinstance(base, (int, float)) and not isinstance(base, bool):
        wings["base"] = float(base)
    offset = vector(data, ("xOffset", "yOffset", "zOffset"))
    if any(offset):
        wings["offset"] = [round(v, 4) for v in apply(OBJ_TO_MOD, offset)]
    sneak = [0.0, float(data.get("sneakOffset") or 0), float(data.get("sneakZOffset") or 0)]
    if any(sneak):
        wings["sneak_offset"] = sneak
    armor = data.get("armorOffset")
    if isinstance(armor, (int, float)) and not isinstance(armor, bool) and armor:
        wings["armor_offset"] = [0.0, 0.0, float(armor)]
    if md5:
        for state in ("idle", "moving", "flying", "sneaking"):
            wings[state] = {"amplitude": 0}
    return wings


# ---------------------------------------------------------------------- calibration

def calibration_plan(planner: Planner, cosmetics) -> Plan:
    """A few cosmetics in every variant worth comparing by eye: the box cape and each built-in cloak (in both
    texture V directions) on one cape without a model, and both V directions on one OBJ- and one MD5-based
    cosmetic of every type."""
    plan = Plan()
    present = [c for c in cosmetics if c.type in RULES and find_texture(c, planner.present)]
    capes = [c for c in present if c.type == "CAPE" and not c.model_name]
    if capes:
        c = capes[0]
        plan.planned.append(planner.plan_box_cape(c, find_texture(c, planner.present), {
            "id": "calib_cape_box", "name": "Calibrate cape: box (vanilla shape)"}, []))
        for mesh in cape_model_candidates(planner.present):
            short = mesh.rsplit("/", 1)[1][:-len(".md5mesh")]
            for flip in (False, True):
                cid = f"calib_cape_{resource_name(short)}_{'v_up' if flip else 'v_down'}"
                result = planner.plan_one(c, variant={"id": cid, "mesh": mesh, "flip": flip,
                                                      "name": f"Calibrate cape: {short}, V {'up' if flip else 'down'}"})
                (plan.planned if isinstance(result, Planned) else plan.skipped).append(result)
    for t in RULES:
        for want_md5 in (False, True):
            sample = None
            for c in present:
                if c.type != t or not c.model_name:
                    continue
                mesh_path, model, problem = planner.mesh_source(c)
                if problem is None and mesh_path.endswith(".md5mesh") == want_md5:
                    sample = c
                    break
            if sample is None:
                continue
            kind = "md5" if want_md5 else "obj"
            for flip in (False, True):
                cid = f"calib_{resource_name(t)}_{kind}_{'flipped' if flip else 'as_is'}"
                result = planner.plan_one(sample, variant={
                    "id": cid, "flip": flip,
                    "name": f"Calibrate {t.lower()} ({kind}): V {'flipped' if flip else 'as is'}"})
                (plan.planned if isinstance(result, Planned) else plan.skipped).append(result)
    return plan
