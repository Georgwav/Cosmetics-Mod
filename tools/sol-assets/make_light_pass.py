#!/usr/bin/env python3
"""Writes the Sol light pass (post-processing chains and shaders) for each of Minecraft's render eras.

One set of GLSL bodies, wrapped for each era's post-effect format: 1.21.1's shaders/post + shaders/program, the
post_effect format with programs (1.21.2-1.21.4), typed uniforms (1.21.5), uniform blocks (1.21.6-1.21.8),
version 330 with the core screen quad (1.21.9-26.2) and separate shader objects (26.3). The passes read their
per-frame values (sun, camera, theme) from a small parameter texture Sol updates every frame, so no era needs
uniforms set from code. Stdlib only; run from the repository root; writes src/era/<era>/resources.

Chains:
  sol_world_medium / sol_world_high   after the level, before the hand: sunlight on blocks (high), scattering
                                      toward the sun and ground haze, light shafts (high).
  sol_grade                           after the hand: bloom (half-resolution threshold, five-level dual-filter mip
                                      chain), the theme's colour grade (a 32x32x32 LUT), vignette, dither.
"""
import json
import os
import struct
import zlib

ROOT = "src/era"
ERAS = ["e1", "e2", "e3", "e4", "e5", "e6"]

# ---------------------------------------------------------------------------------------------------- GLSL bodies

PARAMS = r"""
// Sol's per-frame values: 64 floats, each one's IEEE bits in a texel's bytes (r = lowest).
float P(int i) {
    vec4 t = texelFetch(ParamsSampler, ivec2(i, 0), 0);
    int b0 = int(t.r * 255.0 + 0.5);
    int b1 = int(t.g * 255.0 + 0.5);
    int b2 = int(t.b * 255.0 + 0.5);
    int b3 = int(t.a * 255.0 + 0.5);
    int e = ((b3 & 127) << 1) | (b2 >> 7);
    if (e == 0) {
        return 0.0;
    }
    float m = float(((b2 & 127) << 16) | (b1 << 8) | b0) / 8388608.0;
    float v = (1.0 + m) * exp2(float(e - 127));
    return b3 >= 128 ? -v : v;
}

vec3 P3(int i) {
    return vec3(P(i), P(i + 1), P(i + 2));
}

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}
"""

DEPTH = r"""
bool isSky(float depth) {
    return abs(depth - P(10)) < 1e-6;
}

// Distance along the view axis from a depth-buffer value (the projection's own mapping, reversed or not).
float linearDepth(float depth) {
    return 1.0 / max(1e-6, depth * P(8) + P(9));
}

vec3 viewAt(vec2 uv, float depth) {
    float z = linearDepth(depth);
    return vec3((uv * 2.0 - 1.0) * vec2(P(6), P(7)) * z, -z);
}
"""

BODIES = {
    # The bright sky round the sun, at a quarter of the screen: what the light shafts are made of.
    "sol_mask": (["In", "Depth", "Params"], PARAMS + DEPTH + r"""
void main() {
    float vis = P(2);
    float depth = texture(DepthSampler, texCoord).r;
    if (vis <= 0.0 || !isSky(depth)) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec3 c = texture(InSampler, texCoord).rgb;
    vec2 d = (texCoord - vec2(P(0), P(1))) * vec2(P(28), 1.0);
    float w = 1.0 - smoothstep(0.0, 0.45, length(d));
    // Only what stands out from the sky round it (the scene's bloom threshold rises with a bright sky).
    fragColor = vec4(c * smoothstep(max(0.62, P(19)), 1.0, luma(c)) * w * vis, 1.0);
}
"""),
    # Light shafts: the mask blurred toward the sun (the terrain masks them, as it isn't in the mask).
    "sol_rays": (["In", "Params"], PARAMS + r"""
void main() {
    float vis = P(2);
    if (vis <= 0.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec2 delta = (vec2(P(0), P(1)) - texCoord) * (0.94 / 40.0);
    vec2 uv = texCoord;
    float decay = 1.0;
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 40; i++) {
        uv += delta;
        acc += texture(InSampler, uv).rgb * decay;
        decay *= 0.955;
    }
    fragColor = vec4(acc * 0.045, 1.0);
}
"""),
    # The world in the theme's light: sunlight on the blocks (faces toward the sun take its colour, faces away
    # the shadow colour, strongest at golden hour, a warm rim on silhouettes against a low sun), the fog
    # scattering brighter and warmer toward the sun and thicker near the ground, and the light shafts.
    "sol_world": (["In", "Depth", "Params", "Shafts"], PARAMS + DEPTH + r"""
void main() {
    vec3 col = texture(InSampler, texCoord).rgb;
    float depth = texture(DepthSampler, texCoord).r;
    vec3 sunCol = P3(32);
    vec3 shafts = P(11) >= 2.0 ? texture(ShaftsSampler, texCoord).rgb * sunCol * P(21) : vec3(0.0);
    if (isSky(depth)) {
        // A lightning flash lights the clouds from inside.
        fragColor = vec4(col + shafts + sunCol * P(48) * 0.22, 1.0);
        return;
    }
    // A theme that dims its own day dims the world (the sky and the light it adds stay bright).
    col *= P(58);
    vec3 pos = viewAt(texCoord, depth);
    float dist = length(pos);
    vec3 dir = pos / max(dist, 1e-4);
    vec3 sunV = normalize(P3(3));
    float cosS = dot(dir, sunV);
    float fogFar = max(1.0, P(15));
    float fogged = smoothstep(P(14), fogFar, dist);
    // How much a lightning flash brightens this pixel (faces toward the bolt more, when the normals are known).
    float flashGain = 0.9;
    // The aurora's and the nebula's light: two colours sweeping over the ground in slow bands round the eye.
    vec3 lightCol = sunCol;
    float bandLift = 1.0;
    if (P(54) > 0.0) {
        vec3 east = P3(55);
        vec3 north = cross(P3(24), east);
        float ang = atan(dot(pos, north), dot(pos, east));
        float k = 0.5 + 0.5 * sin(ang * 3.0 + P(22) * 0.22 + 0.08 * dist);
        lightCol = mix(sunCol, P3(51), k * P(54));
        bandLift = 1.0 + 0.35 * P(54) * sin(ang * 5.0 - P(22) * 0.3 + 0.05 * dist);
    }
    if (P(11) >= 2.0) {
        vec2 px = 1.0 / vec2(textureSize(DepthSampler, 0));
        float dR = texture(DepthSampler, texCoord + vec2(px.x, 0.0)).r;
        float dL = texture(DepthSampler, texCoord - vec2(px.x, 0.0)).r;
        float dU = texture(DepthSampler, texCoord + vec2(0.0, px.y)).r;
        float dD = texture(DepthSampler, texCoord - vec2(0.0, px.y)).r;
        vec3 pR = viewAt(texCoord + vec2(px.x, 0.0), dR);
        vec3 pL = viewAt(texCoord - vec2(px.x, 0.0), dL);
        vec3 pU = viewAt(texCoord + vec2(0.0, px.y), dU);
        vec3 pD = viewAt(texCoord - vec2(0.0, px.y), dD);
        // The neighbour on the same surface (the smaller step) on each axis, so edges don't smear the normal.
        vec3 ddx = abs(pR.z - pos.z) < abs(pos.z - pL.z) ? pR - pos : pos - pL;
        vec3 ddy = abs(pU.z - pos.z) < abs(pos.z - pD.z) ? pU - pos : pos - pD;
        vec3 n = normalize(cross(ddx, ddy));
        if (dot(n, dir) > 0.0) {
            n = -n;
        }
        // Wrapped light: ground and anything open to the sky catches a low sun (and the bright sky round it);
        // only faces turned away from it fall into the shadow colour.
        float lit = smoothstep(-0.45, 0.25, dot(n, sunV) + 0.35 * max(0.0, dot(n, P3(24))));
        vec3 sunMul = lightCol / max(0.25, luma(lightCol)) * 1.32 * bandLift;
        vec3 sh = P3(35);
        vec3 shadowMul = mix(vec3(1.0), sh / max(0.12, luma(sh)), 0.55) * 0.78;
        float amt = clamp(P(13) * P(12) * (1.0 - fogged), 0.0, 1.0);
        // Faces turned from a low sun read as warm silhouettes: they take the shadow colour's hue at their own
        // brightness, so a backlit plant goes dusky instead of staying green.
        vec3 shadowed = mix(col * shadowMul, luma(col) * sh / max(0.12, luma(sh)) * 0.78, 0.6);
        // At night the light has to light a dark world, not just tint it: the faces it reaches are brought up
        // (exposure), the rest stay in the dark shadow colour.
        // The lift is strongest toward the light (a pool of light under the rune, the ring, the moon) and
        // saturates softly instead of clipping into a flat fill.
        vec3 litCol = col * sunMul * (1.0 + P(49) * (0.2 + 0.8 * pow(max(cosS, 0.0), 5.0)));
        litCol = mix(litCol, vec3(1.0) - exp(-litCol * 1.3), clamp(P(49), 0.0, 1.0));
        col = mix(col, mix(shadowed, litCol, lit), amt);
        // A thin warm rim where a silhouette meets the sky near a low sun.
        float edge = 0.0;
        edge = max(edge, isSky(dR) ? 1.0 : 0.0);
        edge = max(edge, isSky(dL) ? 1.0 : 0.0);
        edge = max(edge, isSky(dU) ? 1.0 : 0.0);
        edge = max(edge, isSky(dD) ? 1.0 : 0.0);
        // Only the land's skyline: clouds (far above the eye) keep plain edges.
        edge *= 1.0 - smoothstep(24.0, 48.0, dot(pos, P3(24)));
        col += mix(P3(41), sunCol, 0.35) * edge * P(23) * smoothstep(0.7, 0.99, cosS) * P(2) * (1.0 - fogged * 0.5);
        // At night the light's glow lines every skyline (the prototypes' lit horizon edges).
        col += mix(P3(41), sunCol, 0.5) * edge * P(50) * 0.55;
        // Rim light on the faces the light grazes: lit edges along walls, trunks and dunes in the light's colour
        // (the rune's gold, the ring's red-orange, the galaxy's silver).
        float facing = max(0.0, dot(n, sunV));
        float graze = pow(1.0 - abs(dot(n, dir)), 2.5);
        col += lightCol * facing * graze * P(47) * 0.55 * (1.0 - fogged * 0.7) * smoothstep(0.0, 0.3, P(12));
        flashGain = 0.3 + 0.7 * facing;
    }
    // A lightning flash lights the whole world for a moment, the faces toward the bolt the most.
    // It lifts the midtones (the dark world comes up, bright sand doesn't clip into a white sheet).
    col += (col * (1.0 - col) * 1.5 + sunCol * 0.03) * P(48) * flashGain;
    float phase = pow(max(cosS, 0.0), 6.0) * 0.45 + pow(max(cosS, 0.0), 40.0) * 0.35;
    vec3 scat = mix(P3(38), P3(41), clamp(phase * 1.4, 0.0, 1.0));
    // Night thins the scattering, unless the theme's own light (the rune, the moon) is lighting the fog.
    float amount = (1.0 - exp(-dist / fogFar * 2.0)) * P(16) * (0.12 + phase) * 0.6 * (1.0 - 0.5 * P(29) * (1.0 - P(12)));
    float h = dot(pos, P3(24));
    float haze = P(17) * exp(-max(0.0, h + 1.62) / 2.5) * (1.0 - exp(-dist / 18.0));
    col = mix(col, scat, clamp(amount + haze * 0.4, 0.0, 0.85));
    fragColor = vec4(col + shafts, 1.0);
}
"""),
    # What blooms: the bright parts at half resolution, with a soft knee, four taps against flicker.
    "sol_bright": (["In", "Params"], PARAMS + r"""
void main() {
    vec2 h = 0.5 / vec2(textureSize(InSampler, 0));
    vec3 c = (texture(InSampler, texCoord + vec2(-h.x, -h.y)).rgb + texture(InSampler, texCoord + vec2(h.x, -h.y)).rgb
            + texture(InSampler, texCoord + vec2(-h.x, h.y)).rgb + texture(InSampler, texCoord + vec2(h.x, h.y)).rgb) * 0.25;
    float t = P(19);
    float knee = t * 0.35 + 1e-4;
    float l = luma(c);
    float soft = clamp(l - t + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee);
    float k = max(soft, l - t) / max(l, 1e-4);
    fragColor = vec4(c * k, 1.0);
}
"""),
    # Dual-filter downsample.
    "sol_down": (["In"], r"""
void main() {
    vec2 h = 0.5 / vec2(textureSize(InSampler, 0));
    vec3 s = texture(InSampler, texCoord).rgb * 4.0
            + texture(InSampler, texCoord - h).rgb + texture(InSampler, texCoord + h).rgb
            + texture(InSampler, texCoord + vec2(h.x, -h.y)).rgb + texture(InSampler, texCoord - vec2(h.x, -h.y)).rgb;
    fragColor = vec4(s * 0.125, 1.0);
}
"""),
    # Dual-filter upsample, blended with this level.
    "sol_up": (["Up", "Cur"], r"""
void main() {
    vec2 h = 0.5 / vec2(textureSize(UpSampler, 0));
    vec3 s = texture(UpSampler, texCoord + vec2(-2.0 * h.x, 0.0)).rgb + texture(UpSampler, texCoord + vec2(2.0 * h.x, 0.0)).rgb
            + texture(UpSampler, texCoord + vec2(0.0, -2.0 * h.y)).rgb + texture(UpSampler, texCoord + vec2(0.0, 2.0 * h.y)).rgb
            + (texture(UpSampler, texCoord + vec2(-h.x, h.y)).rgb + texture(UpSampler, texCoord + vec2(h.x, h.y)).rgb
            + texture(UpSampler, texCoord + vec2(h.x, -h.y)).rgb + texture(UpSampler, texCoord + vec2(-h.x, -h.y)).rgb) * 2.0;
    vec3 cur = texture(CurSampler, texCoord).rgb;
    fragColor = vec4(mix(cur, s / 12.0, 0.55), 1.0);
}
"""),
    # The finished frame: bloom added in the theme's tint, the theme's grade, the vignette, a dither.
    "sol_grade": (["In", "Bloom", "Params", "Lut"], PARAMS + r"""
vec3 grade(vec3 c) {
    float b = c.b * 31.0;
    float b0 = floor(b);
    float b1 = min(b0 + 1.0, 31.0);
    float f = b - b0;
    vec2 rg = vec2(c.r * 31.0 + 0.5, c.g * 31.0 + 0.5);
    vec3 s0 = texture(LutSampler, vec2((b0 * 32.0 + rg.x) / 1024.0, rg.y / 32.0)).rgb;
    vec3 s1 = texture(LutSampler, vec2((b1 * 32.0 + rg.x) / 1024.0, rg.y / 32.0)).rgb;
    return mix(s0, s1, f);
}

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    vec3 bloom = texture(BloomSampler, texCoord).rgb;
    c += bloom * P3(44) * P(18);
    c = grade(clamp(c, 0.0, 1.0));
    vec2 d = (texCoord - 0.5) * vec2(P(28), 1.0) / max(1.0, P(28) * 0.75);
    float v = smoothstep(0.32, 0.92, length(d) * 1.25);
    c *= 1.0 - P(20) * v;
    float n = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    c += (n - 0.5) / 255.0;
    fragColor = vec4(c, 1.0);
}
"""),
    "sol_copy": (["In"], r"""
void main() {
    fragColor = vec4(texture(InSampler, texCoord).rgb, 1.0);
}
"""),
    # Motion blur: the frame mixed with the last one (which already holds the ones before it), by the amount in
    # the blur texture's red (Sol works it out from the frame time, so the trail lasts as long at any frame rate).
    "sol_blur_mix": (["In", "Prev", "Amount"], r"""
void main() {
    float k = texelFetch(AmountSampler, ivec2(0, 0), 0).r;
    vec3 cur = texture(InSampler, texCoord).rgb;
    vec3 prev = texture(PrevSampler, texCoord).rgb;
    fragColor = vec4(mix(cur, prev, k), 1.0);
}
"""),
}

# ---------------------------------------------------------------------------------------------------- the chains

IDENTITY = [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0]
PARAMS_TEX = ("sol_params", 64, 1, False)
LUT_TEX = ("sol_lut", 1024, 32, True)
BLACK_TEX = ("sol_black", 1, 1, False)
BLUR_TEX = ("sol_blur", 1, 1, False)

# Fixed bloom levels (a 1080p screen's half, then each half of that): post chains only size targets to the screen
# or to fixed sizes, and a blur this wide looks the same at any resolution.
LEVELS = [(960, 540), (480, 270), (240, 135), (120, 68), (60, 34), (30, 17)]
SHAFT_SIZE = (480, 270)


def chain(name):
    """A chain as a list of passes: (program, output, inputs) where an input is (sampler, source, kind, bilinear)
    and kind is 'target', 'depth' or 'texture' (then source is a texture tuple); and its internal targets."""
    T, D, X = "target", "depth", "texture"
    if name.startswith("sol_world"):
        high = name.endswith("high")
        passes = []
        targets = {"swap": None}
        if high:
            targets["mask"] = SHAFT_SIZE
            targets["shafts"] = SHAFT_SIZE
            passes.append(("sol_mask", "mask", [("In", "main", T, True), ("Depth", "main", D, False), ("Params", PARAMS_TEX, X, False)]))
            passes.append(("sol_rays", "shafts", [("In", "mask", T, True), ("Params", PARAMS_TEX, X, False)]))
            shafts = ("Shafts", "shafts", T, True)
        else:
            shafts = ("Shafts", BLACK_TEX, X, False)
        passes.append(("sol_world", "swap", [("In", "main", T, False), ("Depth", "main", D, False), ("Params", PARAMS_TEX, X, False), shafts]))
        passes.append(("sol_copy", "main", [("In", "swap", T, False)]))
        return passes, targets
    if name == "sol_motion_blur":
        # "prev" keeps the last frame: persistent from 1.21.6; 1.21.1's chain owns its targets anyway.
        passes = [("sol_blur_mix", "swap", [("In", "main", T, False), ("Prev", "prev", T, False), ("Amount", BLUR_TEX, X, False)]),
                  ("sol_copy", "prev", [("In", "swap", T, False)]),
                  ("sol_copy", "main", [("In", "swap", T, False)])]
        return passes, {"swap": None, "prev": "persistent"}
    # sol_grade
    targets = {"swap": None}
    for i, size in enumerate(LEVELS):
        targets["b%d" % i] = size
        if i < len(LEVELS) - 1:
            targets["u%d" % i] = size
    passes = [("sol_bright", "b0", [("In", "main", T, True), ("Params", PARAMS_TEX, X, False)])]
    for i in range(1, len(LEVELS)):
        passes.append(("sol_down", "b%d" % i, [("In", "b%d" % (i - 1), T, True)]))
    for i in range(len(LEVELS) - 2, -1, -1):
        up = "b%d" % (i + 1) if i == len(LEVELS) - 2 else "u%d" % (i + 1)
        passes.append(("sol_up", "u%d" % i, [("Up", up, T, True), ("Cur", "b%d" % i, T, False)]))
    passes.append(("sol_grade", "swap", [("In", "main", T, False), ("Bloom", "u0", T, True), ("Params", PARAMS_TEX, X, False),
                                          ("Lut", LUT_TEX, X, True)]))
    passes.append(("sol_copy", "main", [("In", "swap", T, False)]))
    return passes, targets


CHAINS = ["sol_world_medium", "sol_world_high", "sol_grade", "sol_motion_blur"]
# Eras whose post chains can keep a target from one frame to the next (1.21.2 to 1.21.5 can't: no motion blur).
PERSISTENT = ("e1", "e4", "e5", "e6")

# ---------------------------------------------------------------------------------------------------- per era


def frag_header(era, inputs):
    lines = []
    if era in ("e5",):
        lines.append("#version 330")
    elif era == "e6":
        lines += ["#version 330", "#extension GL_ARB_separate_shader_objects : require"]
    else:
        lines.append("#version 150")
    lines.append("")
    for i, s in enumerate(inputs):
        if era == "e1" and i == 0:
            lines.append("uniform sampler2D DiffuseSampler;")
            lines.append("#define %sSampler DiffuseSampler" % s)
        else:
            lines.append("uniform sampler2D %sSampler;" % s)
    lines.append("")
    if era == "e6":
        lines += ["layout(location = 0) in vec2 texCoord;", "", "layout(location = 0) out vec4 fragColor;"]
    else:
        lines += ["in vec2 texCoord;", "", "out vec4 fragColor;"]
    return "\n".join(lines) + "\n"


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)


def png(path, w, h, rgba):
    raw = b"".join(b"\x00" + bytes(rgba[y * w * 4:(y + 1) * w * 4]) for y in range(h))
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


def era_files(era):
    base = os.path.join(ROOT, era, "resources", "assets")
    ns = os.path.join(base, "cosmeticsmod")
    for prog, (inputs, body) in BODIES.items():
        text = frag_header(era, inputs) + body
        if era == "e1":
            # 1.21.1 looks for a chain's programs only under minecraft:shaders/program (names are unique: sol_*).
            mc = os.path.join(base, "minecraft")
            write(os.path.join(mc, "shaders", "program", prog + ".fsh"), text)
            samplers = [{"name": "DiffuseSampler" if i == 0 else s + "Sampler"} for i, s in enumerate(inputs)]
            write(os.path.join(mc, "shaders", "program", prog + ".json"), json.dumps({
                "blend": {"func": "add", "srcrgb": "one", "dstrgb": "zero"},
                "vertex": "screenquad", "fragment": prog,
                "attributes": ["Position"], "samplers": samplers,
                "uniforms": [{"name": "ProjMat", "type": "matrix4x4", "count": 16, "values": [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0]},
                             {"name": "OutSize", "type": "float", "count": 2, "values": [1.0, 1.0]}]}, indent=2) + "\n")
        elif era == "e2":
            write(os.path.join(ns, "shaders", "post", prog + ".fsh"), text)
            write(os.path.join(ns, "shaders", "post", prog + ".json"), json.dumps({
                "vertex": "minecraft:post/screenquad", "fragment": "cosmeticsmod:post/" + prog,
                "samplers": [{"name": s + "Sampler"} for s in inputs],
                "uniforms": [{"name": "ProjMat", "type": "matrix4x4", "count": 16, "values": [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0]},
                             {"name": "OutSize", "type": "float", "count": 2, "values": [1.0, 1.0]}]}, indent=2) + "\n")
        else:
            write(os.path.join(ns, "shaders", "post", prog + ".fsh"), text)
    for name in CHAINS:
        passes, targets = chain(name)
        if "persistent" in targets.values() and era not in PERSISTENT:
            continue
        if era == "e1":
            tlist = []
            for t, size in targets.items():
                tlist.append(t if size in (None, "persistent") else {"name": t, "width": size[0], "height": size[1]})
            plist = []
            for prog, out, inputs in passes:
                first = inputs[0]
                # 1.21.1 sets one filter per pass for every target it reads, so a pass that blurs or upsamples any
                # of its inputs reads them all linearly (the same-size screen reads the same either way).
                p = {"name": prog, "intarget": "minecraft:main" if first[1] == "main" else first[1],
                     "outtarget": "minecraft:main" if out == "main" else out,
                     "use_linear_filter": any(i[3] for i in inputs if i[2] != "texture")}
                aux = []
                for s, src, kind, bil in inputs[1:]:
                    if kind == "texture":
                        aux.append({"name": s + "Sampler", "id": src[0], "width": src[1], "height": src[2], "bilinear": src[3]})
                    elif kind == "depth":
                        aux.append({"name": s + "Sampler", "id": "minecraft:main:depth" if src == "main" else src + ":depth"})
                    else:
                        aux.append({"name": s + "Sampler", "id": "minecraft:main" if src == "main" else src})
                if aux:
                    p["auxtargets"] = aux
                plist.append(p)
            write(os.path.join(ns, "shaders", "post", name + ".json"), json.dumps({"targets": tlist, "passes": plist}, indent=2) + "\n")
        else:
            tmap = {}
            for t, size in targets.items():
                tmap[t] = {} if size is None else {"persistent": True} if size == "persistent" else {"width": size[0], "height": size[1]}
            plist = []
            for prog, out, inputs in passes:
                ins = []
                for s, src, kind, bil in inputs:
                    if kind == "texture":
                        ins.append({"sampler_name": s, "location": "cosmeticsmod:" + src[0], "width": src[1], "height": src[2], "bilinear": src[3]})
                    else:
                        i = {"sampler_name": s, "target": "minecraft:main" if src == "main" else src}
                        if kind == "depth":
                            i["use_depth_buffer"] = True
                        if bil:
                            i["bilinear"] = True
                        ins.append(i)
                p = {}
                if era == "e2":
                    p["program"] = "cosmeticsmod:post/" + prog
                else:
                    p["vertex_shader"] = "minecraft:post/screenquad" if era in ("e3", "e4") else "minecraft:core/screenquad"
                    p["fragment_shader"] = "cosmeticsmod:post/" + prog
                p["inputs"] = ins
                p["output"] = "minecraft:main" if out == "main" else out
                plist.append(p)
            write(os.path.join(ns, "post_effect", name + ".json"), json.dumps({"targets": tmap, "passes": plist}, indent=2) + "\n")
    if era == "e1":
        # 1.21.1 asks the resources for a chain's textures before using the one Sol registered under that name.
        for tex, w, h, _ in (PARAMS_TEX, LUT_TEX, BLACK_TEX, BLUR_TEX):
            png(os.path.join(base, "minecraft", "textures", "effect", tex + ".png"), w, h, [0, 0, 0, 255] * (w * h))
        # 1.21.1's position_tex_color drops anything under 10% alpha, which cuts the faint edge of every glow into
        # a hard disc: Sol draws its sprites with this copy of it that keeps them (registered by SolShaders).
        core = os.path.join(base, "cosmeticsmod", "shaders", "core")
        write(os.path.join(core, "sol_tex_color.json"), json.dumps({
            "vertex": "cosmeticsmod:sol_tex_color", "fragment": "cosmeticsmod:sol_tex_color",
            "samplers": [{"name": "Sampler0"}],
            "uniforms": [{"name": "ModelViewMat", "type": "matrix4x4", "count": 16, "values": IDENTITY},
                         {"name": "ProjMat", "type": "matrix4x4", "count": 16, "values": IDENTITY},
                         {"name": "ColorModulator", "type": "float", "count": 4, "values": [1.0, 1.0, 1.0, 1.0]}]},
            indent=2) + "\n")
        write(os.path.join(core, "sol_tex_color.vsh"), """#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
}
""")
        write(os.path.join(core, "sol_tex_color.fsh"), """#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
""")


if __name__ == "__main__":
    for era in ERAS:
        era_files(era)
    print("light pass written for", ", ".join(ERAS))
