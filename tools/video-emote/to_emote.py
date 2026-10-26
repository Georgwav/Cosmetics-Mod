"""Turns track.py's landmarks (one dancer facing the camera) into a Bedrock emote for the client.

The client's model space: +x the player's left, +y down, forward -z; a part's rotation is Rz * Ry * Rx (degrees in the
file, the Java model's signs). The tracker's coordinates for someone facing the camera are already in that frame: x to
the picture's right (their left), y down, z away from the camera (their back).

    python3 to_emote.py pose.json out.json name [duration_s] [music_volume]

The emote names the sound emote_music:<name> at music_volume (0.35 when left out); see README.md for its pack.
"""
import json
import math
import sys

import numpy as np

POSE, OUT, NAME = sys.argv[1], sys.argv[2], sys.argv[3]
P = json.load(open(POSE))
frames = P["frames"]
n = len(frames)
duration = float(sys.argv[4]) if len(sys.argv) > 4 else n / P["fps"]
VOLUME = float(sys.argv[5]) if len(sys.argv) > 5 else 0.35
fps = n / duration
WORLD = np.array([f["world"] for f in frames], dtype=float)[:, :, :3]  # n x 33 x 3, metres
IMG = np.array([f["image"] for f in frames], dtype=float)[:, :, :3]
# Where things are across the picture is what the tracker gets right; its 3D guess tilts the head and twists the
# torso. So: x and y from the picture (in pixels), depth from the 3D guess at the picture's scale.
IMG[:, :, 0] *= P["width"]
IMG[:, :, 1] *= P["height"]
sh_img = (IMG[:, 11, :2] + IMG[:, 12, :2]) / 2 - (IMG[:, 23, :2] + IMG[:, 24, :2]) / 2
sh_w = (WORLD[:, 11, :2] + WORLD[:, 12, :2]) / 2 - (WORLD[:, 23, :2] + WORLD[:, 24, :2]) / 2
scale = np.linalg.norm(sh_img, axis=1) / np.maximum(1e-6, np.linalg.norm(sh_w, axis=1))
scale = np.convolve(np.pad(scale, 7, mode="edge"), np.ones(15) / 15, mode="valid")
# The 3D guess also puts hands further in front of the body than they are (a hand on the chest comes out reaching for
# the camera), so depth counts at 60%: from the front, which is how others see you, the arms then sit where the video
# has them.
DEPTH = 0.6
W = np.concatenate([IMG[:, :, :2], WORLD[:, :, 2:3] * scale[:, None, None] * DEPTH], axis=2)

# Smooth over time (jitter in the tracker), a little: dance moves are quick.
def gauss(a, sigma):
    r = int(math.ceil(sigma * 3))
    k = np.exp(-0.5 * (np.arange(-r, r + 1) / sigma) ** 2)
    k /= k.sum()
    pad = np.concatenate([a[:1].repeat(r, 0), a, a[-1:].repeat(r, 0)])
    out = np.empty_like(a)
    for i in range(len(a)):
        out[i] = np.tensordot(k, pad[i:i + 2 * r + 1], axes=(0, 0))
    return out

W = gauss(W, 1.3)

NOSE, L_EAR, R_EAR, L_SH, R_SH, L_EL, R_EL, L_WR, R_WR = 0, 7, 8, 11, 12, 13, 14, 15, 16
L_HIP, R_HIP, L_AN, R_AN = 23, 24, 27, 28


def unit(v):
    return v / (np.linalg.norm(v) + 1e-9)


def frame_from(left, down):
    """A rotation whose columns are the part's left, down and back axes (left kept, down made square to it)."""
    x = unit(left)
    y = unit(down - np.dot(down, x) * x)
    z = np.cross(x, y)
    return np.stack([x, y, z], axis=1)


def euler(m):
    """The client's decompose: Rz * Ry * Rx -> (x, y, z) radians."""
    r20 = max(-1.0, min(1.0, m[2, 0]))
    if abs(r20) < 0.99999:
        return math.atan2(m[2, 1], m[2, 2]), math.asin(-r20), math.atan2(m[1, 0], m[0, 0])
    sy = -r20
    return math.atan2(m[0, 1] * sy, m[1, 1]), math.pi / 2 * math.copysign(1, sy), 0.0


def rot(x, y, z):
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    return np.array([[cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx],
                     [sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx],
                     [-sy, cy * sx, cy * cx]])


def limb(d):
    """(x, y, z) radians turning a limb that hangs along +y to point along d: the shortest swing there, so the limb
    doesn't roll about its own length (an X-then-Z pair spins wildly as the limb nears pointing straight ahead)."""
    d = unit(d)
    rest = np.array([0.0, 1.0, 0.0])
    axis = np.cross(rest, d)
    s, c = np.linalg.norm(axis), float(np.dot(rest, d))
    if s < 1e-6:
        if c > 0:
            return 0.0, 0.0, 0.0
        axis, s = np.array([1.0, 0.0, 0.0]), 0.0  # straight up: over the front
    k = axis / (s if s > 1e-6 else 1.0)
    angle = math.atan2(s, c)
    K = np.array([[0, -k[2], k[1]], [k[2], 0, -k[0]], [-k[1], k[0], 0]])
    R = np.eye(3) + math.sin(angle) * K + (1 - math.cos(angle)) * (K @ K)
    return euler(R)


rows = {k: [] for k in ("root", "waist", "head", "right_arm", "left_arm", "right_leg", "left_leg")}
for t in range(n):
    p = W[t]
    hip = (p[L_HIP] + p[R_HIP]) / 2
    sh = (p[L_SH] + p[R_SH]) / 2
    # Root: the hips' turn about the vertical.
    hl = p[L_HIP] - p[R_HIP]
    yaw = math.atan2(-hl[2], hl[0])
    R_root = rot(0, yaw, 0)
    # Torso: shoulders' line and the spine.
    T = frame_from(p[L_SH] - p[R_SH], hip - sh)
    W_rel = R_root.T @ T
    # Head: the ears' line; down is square to it, away from the line from the ears' middle to the nose.
    ears = (p[L_EAR] + p[R_EAR]) / 2
    hx = unit(p[L_EAR] - p[R_EAR])
    fwd = p[NOSE] - ears
    fwd = unit(fwd - np.dot(fwd, hx) * hx)
    back = -fwd
    down = np.cross(back, hx)  # x cross y = z, so y = z cross x
    H = np.stack([hx, unit(down), back], axis=1)
    H_rel = T.T @ H
    rows["root"].append((0.0, yaw, 0.0))
    rows["waist"].append(euler(W_rel))
    rows["head"].append(euler(H_rel))
    # An arm aims at a point on the forearm, nearer the wrist: a straight arm as long as the block player's, aimed at
    # a hand folded in to the chest, would reach past it to the face. A straight arm points the same either way.
    hand = {R_WR: 0.35 * p[R_EL] + 0.65 * p[R_WR], L_WR: 0.35 * p[L_EL] + 0.65 * p[L_WR]}
    for name, a, b, parent in (("right_arm", R_SH, R_WR, T), ("left_arm", L_SH, L_WR, T),
                               ("right_leg", R_HIP, R_AN, R_root), ("left_leg", L_HIP, L_AN, R_root)):
        rows[name].append(limb(parent.T @ (hand.get(b, p[b]) - p[a])))

deg = {k: np.degrees(np.unwrap(np.array(v), axis=0)) for k, v in rows.items()}
# Calibrate the head and torso to the dancer's own posture: their typical pose counts as upright and square (the
# tracker's depth puts the nose a little below the ears, and leans and twists the torso a little).
for k in ("head", "waist"):
    deg[k] -= np.median(deg[k], axis=0)
# The hips' typical turn counts as facing forward (the camera may not be square to them).
deg["root"][:, 1] -= np.median(deg["root"][:, 1])
# Keep the waist and head within what a body does (the tracker's depth can overshoot).
deg["waist"] = np.clip(deg["waist"], [-35, -50, -30], [35, 50, 30])
deg["head"] = np.clip(deg["head"], [-45, -70, -35], [45, 70, 35])

STEP = 2  # every second frame: 15 keyframes a second, Catmull-Rom between
bones = {}
for k, v in deg.items():
    tl = {}
    for i in list(range(0, n, STEP)) + ([n - 1] if (n - 1) % STEP else []):
        t = round(i / fps, 3)
        tl[f"{t:g}"] = {"post": [round(float(c), 1) for c in v[i]], "lerp_mode": "catmullrom"}
    bones[k] = {"rotation": tl}

anim = {
    "animation_length": round((n - 1) / fps, 3),
    # The video's music, quiet (see README.md); silent where no pack has it.
    "sound": "emote_music:" + NAME,
    "sound_volume": VOLUME,
    "bones": bones,
}
doc = {"format_version": "1.8.0", "animations": {f"animation.cosmeticsmod.{NAME}": anim}}
json.dump(doc, open(OUT, "w"), separators=(",", ":"))
# Also the per-frame values, for a look at the curves.
json.dump({"fps": fps, "deg": {k: v.round(2).tolist() for k, v in deg.items()}}, open(OUT + ".frames.json", "w"))
print(n, "frames ->", len(next(iter(bones.values()))["rotation"]), "keyframes per bone,", f"{duration:.2f} s")
for k, v in deg.items():
    print(f"  {k:10s} min {np.round(v.min(0)).astype(int)} max {np.round(v.max(0)).astype(int)}")
