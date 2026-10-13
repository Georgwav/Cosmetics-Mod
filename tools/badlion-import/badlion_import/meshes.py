"""Structure of OBJ and MD5 files: counts, bounds and UV ranges, never their geometry as such.

Used by `inspect` to describe models in LAYOUT.md and to check which built-in cloak model a cape texture
belongs to (by comparing where the model's UVs land with where the texture has pixels).
"""

from __future__ import annotations

import math
import re
from dataclasses import dataclass, field


@dataclass
class MeshSummary:
    kind: str
    vertices: int = 0
    triangles: int = 0
    faces: int = 0
    groups: list[str] = field(default_factory=list)
    bounds: tuple[tuple[float, float, float], tuple[float, float, float]] | None = None
    uv_bounds: tuple[float, float, float, float] | None = None
    joints: int = 0
    submeshes: list[str] = field(default_factory=list)
    multi_weight_vertices: int = 0
    problems: list[str] = field(default_factory=list)
    # UV triangles (u0, v0, u1, v1, u2, v2) in file convention, for the cape check.
    uv_triangles: list[tuple[float, ...]] = field(default_factory=list)


@dataclass
class AnimSummary:
    frames: int = 0
    frame_rate: float = 0.0
    joints: int = 0
    joint_names: list[str] = field(default_factory=list)
    problems: list[str] = field(default_factory=list)


def _grow(bounds, x, y, z):
    if bounds is None:
        return (x, y, z), (x, y, z)
    lo, hi = bounds
    return (min(lo[0], x), min(lo[1], y), min(lo[2], z)), (max(hi[0], x), max(hi[1], y), max(hi[2], z))


def _grow_uv(bounds, u, v):
    if bounds is None:
        return u, v, u, v
    return min(bounds[0], u), min(bounds[1], v), max(bounds[2], u), max(bounds[3], v)


def summarize_obj(text: str) -> MeshSummary:
    s = MeshSummary("obj")
    vt: list[tuple[float, float]] = []
    for line in text.splitlines():
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        parts = line.split()
        tag = parts[0]
        try:
            if tag == "v":
                s.vertices += 1
                s.bounds = _grow(s.bounds, float(parts[1]), float(parts[2]), float(parts[3]))
            elif tag == "vt":
                u, v = float(parts[1]), float(parts[2])
                vt.append((u, v))
                s.uv_bounds = _grow_uv(s.uv_bounds, u, v)
            elif tag in ("o", "g"):
                name = line[1:].strip()
                if name not in s.groups:
                    s.groups.append(name)
            elif tag == "f":
                s.faces += 1
                corners = parts[1:]
                s.triangles += max(0, len(corners) - 2)
                uvs = []
                for corner in corners:
                    bits = corner.split("/")
                    if len(bits) < 2 or not bits[1]:
                        uvs = None
                        break
                    index = int(bits[1])
                    uvs.append(vt[index - 1] if index > 0 else vt[index])
                if uvs is None:
                    if "faces without texture coordinates" not in s.problems:
                        s.problems.append("faces without texture coordinates")
                    continue
                for i in range(1, len(uvs) - 1):
                    s.uv_triangles.append((*uvs[0], *uvs[i], *uvs[i + 1]))
        except (ValueError, IndexError):
            if f"bad '{tag}' line" not in s.problems:
                s.problems.append(f"bad '{tag}' line")
    return s


_TOKEN = re.compile(r'"[^"\n]*"|\(|\)|\{|\}|[^\s(){}"]+')


def _tokens(text: str) -> list[str]:
    return _TOKEN.findall(re.sub(r"//[^\n]*", "", text))


def _quat(x: float, y: float, z: float) -> tuple[float, float, float, float]:
    t = 1.0 - x * x - y * y - z * z
    return x, y, z, (0.0 if t < 0 else -math.sqrt(t))


def _rotate(q, v):
    qx, qy, qz, qw = q
    x, y, z = v
    tx, ty, tz = 2 * (qy * z - qz * y), 2 * (qz * x - qx * z), 2 * (qx * y - qy * x)
    return (x + qw * tx + (qy * tz - qz * ty), y + qw * ty + (qz * tx - qx * tz), z + qw * tz + (qx * ty - qy * tx))


def summarize_md5mesh(text: str) -> MeshSummary:
    """Joint and mesh counts, bind-pose bounds and UV range of an .md5mesh."""
    s = MeshSummary("md5mesh")
    tok = _tokens(text)
    joints: list[tuple[tuple[float, ...], tuple[float, ...]]] = []
    i = 0

    def num(k):
        return float(tok[k])

    try:
        while i < len(tok):
            t = tok[i]
            if t == "joints":
                i += 2
                while tok[i] != "}":
                    # "name" parent ( x y z ) ( qx qy qz )
                    pos = (num(i + 3), num(i + 4), num(i + 5))
                    q = _quat(num(i + 8), num(i + 9), num(i + 10))
                    joints.append((pos, q))
                    i += 12
                s.joints = len(joints)
            elif t == "mesh":
                i += 2
                shader = ""
                verts: dict[int, tuple[float, float, int, int]] = {}
                tris: list[tuple[int, int, int]] = []
                weights: dict[int, tuple[int, float, tuple[float, float, float]]] = {}
                while tok[i] != "}":
                    if tok[i] == "shader":
                        shader = tok[i + 1].strip('"')
                        i += 2
                    elif tok[i] == "vert":
                        verts[int(tok[i + 1])] = (num(i + 3), num(i + 4), int(tok[i + 6]), int(tok[i + 7]))
                        i += 8
                    elif tok[i] == "tri":
                        tris.append((int(tok[i + 2]), int(tok[i + 3]), int(tok[i + 4])))
                        i += 5
                    elif tok[i] == "weight":
                        weights[int(tok[i + 1])] = (int(tok[i + 2]), num(i + 3), (num(i + 5), num(i + 6), num(i + 7)))
                        i += 9
                    else:
                        i += 2
                s.submeshes.append(shader)
                s.vertices += len(verts)
                s.triangles += len(tris)
                s.faces += len(tris)
                for index, (u, v, start, count) in verts.items():
                    s.uv_bounds = _grow_uv(s.uv_bounds, u, v)
                    if sum(1 for w in range(start, start + count) if weights.get(w, (0, 0.0))[1] > 1e-4) > 1:
                        s.multi_weight_vertices += 1
                    p = [0.0, 0.0, 0.0]
                    for w in range(start, start + count):
                        if w not in weights or weights[w][0] >= len(joints):
                            s.problems.append(f"vert {index} uses a missing weight or joint")
                            break
                        joint, bias, wp = weights[w]
                        jp, jq = joints[joint]
                        r = _rotate(jq, wp)
                        for k in range(3):
                            p[k] += (jp[k] + r[k]) * bias
                    s.bounds = _grow(s.bounds, *p)
                for a, b, c in tris:
                    if a in verts and b in verts and c in verts:
                        s.uv_triangles.append((verts[a][0], verts[a][1], verts[b][0], verts[b][1],
                                               verts[c][0], verts[c][1]))
                i += 1
            else:
                i += 1
    except (ValueError, IndexError):
        s.problems.append("could not be read as MD5Version 10")
    return s


def summarize_md5anim(text: str) -> AnimSummary:
    a = AnimSummary()
    tok = _tokens(text)
    try:
        for i, t in enumerate(tok):
            if t == "numFrames":
                a.frames = int(tok[i + 1])
            elif t == "frameRate":
                a.frame_rate = float(tok[i + 1])
            elif t == "numJoints":
                a.joints = int(tok[i + 1])
            elif t == "hierarchy":
                k = i + 2
                while tok[k] != "}":
                    a.joint_names.append(tok[k].strip('"'))
                    k += 4
                break
    except (ValueError, IndexError):
        a.problems.append("could not be read as MD5Version 10")
    return a


def md5mesh_joint_names(text: str) -> list[str]:
    tok = _tokens(text)
    for i, t in enumerate(tok):
        if t == "joints":
            names = []
            k = i + 2
            while k < len(tok) and tok[k] != "}":
                names.append(tok[k].strip('"'))
                k += 12
            return names
    return []


def uv_coverage(triangles, width: int, height: int, flip_v: bool) -> list[bool]:
    """Which cells of a width x height grid the UV triangles cover (V down when flip_v is False)."""
    grid = [False] * (width * height)
    for tri in triangles:
        pts = []
        for k in range(3):
            u, v = tri[2 * k], tri[2 * k + 1]
            if flip_v:
                v = 1.0 - v
            pts.append((u * width, v * height))
        (x0, y0), (x1, y1), (x2, y2) = pts
        area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if abs(area) < 1e-12:
            continue
        lo_x = max(0, int(math.floor(min(x0, x1, x2))))
        hi_x = min(width - 1, int(math.ceil(max(x0, x1, x2))))
        lo_y = max(0, int(math.floor(min(y0, y1, y2))))
        hi_y = min(height - 1, int(math.ceil(max(y0, y1, y2))))
        for y in range(lo_y, hi_y + 1):
            cy = y + 0.5
            for x in range(lo_x, hi_x + 1):
                cx = x + 0.5
                w0 = (x1 - cx) * (y2 - cy) - (x2 - cx) * (y1 - cy)
                w1 = (x2 - cx) * (y0 - cy) - (x0 - cx) * (y2 - cy)
                w2 = (x0 - cx) * (y1 - cy) - (x1 - cx) * (y0 - cy)
                if (w0 >= 0 and w1 >= 0 and w2 >= 0) or (w0 <= 0 and w1 <= 0 and w2 <= 0):
                    grid[y * width + x] = True
    return grid


def overlap_score(covered: list[bool], opaque: list[bool]) -> float:
    """Intersection over union of two masks: 1.0 when the UVs land exactly on the painted pixels."""
    both = sum(1 for a, b in zip(covered, opaque) if a and b)
    either = sum(1 for a, b in zip(covered, opaque) if a or b)
    return both / either if either else 0.0
