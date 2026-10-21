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
  sol_pixel                           last, under the quick menu only: the world in the pixel UI's pixels (whole
                                      screen pixels per 2 design px, lined up with the wheel's own grid).
  sol_hand_glow                       after the hand, before the grade: a thin bright rim round the first-person
                                      hands and held item, fading out within about 10 px at 1080p (a distance field
                                      on a 960 x 540 grid), coloured from a blurred quarter-resolution mask, with a
                                      motion trail of the rim (kept frame to frame); or a soft glow.
"""
import json
import os
import struct
import zlib

ROOT = "src/era"
ERAS = ["e1", "e2", "e3", "e4", "e5", "e6"]

# ---------------------------------------------------------------------------------------------------- GLSL bodies

PARAMS = r"""
// Sol's per-frame values: 128 floats, each one's IEEE bits in a texel's bytes (r = lowest).
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
    // A depth the projection can't give (a range mismatch) reads as the end of the world, not a million blocks away.
    float d = depth * P(8) + P(9);
    return d > 1e-6 ? 1.0 / d : max(16.0, P(30));
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
    vec3 base = col;
    // A theme that dims its own day dims the world (the sky and the light it adds stay bright).
    col *= P(58);
    vec3 pos = viewAt(texCoord, depth);
    float dist = length(pos);
    // Where the depth buffer's land ends while far land goes on (P(59) blocks; along the view axis to the game's far
    // plane with Voxy, whose land past it reads as sky, or out from the eye to the last chunks with Distant Horizons,
    // whose land never reaches it): Sol's light on the land fades out before it, so that land and the far land after
    // it meet without a line. Without either, the game's land ends well short of it.
    float reach = (P(60) > 0.5 ? -pos.z : dist) / max(16.0, P(59));
    float landFade = 1.0 - smoothstep(0.55, 0.97, reach);
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
        // And only at the end of the game's own land (its chunks reach out to their corners, 1.42 render distances): a
        // hill, a player or a tree in front of farther land or of the sky keeps a plain edge. Voxy's land past the
        // game's far plane reads as sky in the depth buffer: where its land crosses that plane (a few render
        // distances out) and round its hills in front of it, no line is drawn on the land.
        float atEnd = dist / max(16.0, P(30));
        edge *= smoothstep(0.82, 0.94, atEnd) * (1.0 - smoothstep(1.45, 1.6, atEnd));
        col += mix(P3(41), sunCol, 0.35) * edge * P(23) * smoothstep(0.7, 0.99, cosS) * P(2) * (1.0 - fogged * 0.5);
        // At night the light's glow lines every skyline (the prototypes' lit horizon edges).
        col += mix(P3(41), sunCol, 0.5) * edge * P(50) * 0.55;
        // Rim light on the faces the light grazes: lit edges along walls, trunks and dunes in the light's colour
        // (the rune's gold, the ring's red-orange, the galaxy's silver).
        float facing = max(0.0, dot(n, sunV));
        float graze = pow(1.0 - abs(dot(n, dir)), 2.5);
        col += lightCol * facing * graze * P(47) * 0.55 * (1.0 - fogged * 0.7) * smoothstep(0.0, 0.3, P(12));
        flashGain = 0.3 + 0.7 * facing;
        // A second light where the theme's own is something else (the moon under the rune, the aurora, the
        // galaxy): silver on the faces turned toward it, a little everywhere.
        if (P(70) > 0.0) {
            float xk = (max(0.0, dot(n, normalize(P3(64)))) * 0.72 + 0.07) * P(70);
            col += col * P3(67) * xk * 1.4 * (1.0 - fogged);
        }
    } else if (P(70) > 0.0) {
        col += col * P3(67) * P(70) * 0.25 * (1.0 - fogged);
    }
    // Day for night: the dim world turns blue, its shadows lifted to a blue-violet floor (never flat black), the
    // nearest blocks the most so you can still play; warm lights (torches, lava, windows) keep their colour.
    float nk = P(71);
    if (nk > 0.0) {
        float l = luma(col);
        float warm = clamp((col.r - col.b) * 3.0, 0.0, 1.0) * smoothstep(0.2, 0.55, l);
        col = mix(col, l * vec3(0.78, 0.9, 1.22), nk * 0.55 * (1.0 - smoothstep(0.12, 0.7, l)) * (1.0 - warm));
        float near = 1.0 - smoothstep(4.0, 36.0, dist);
        col += P3(72) * nk * (0.35 + 0.65 * near) * (1.0 - smoothstep(0.0, 0.3, l)) * (1.0 - fogged * 0.6);
    }
    // A lightning flash lights the whole world for a moment, the faces toward the bolt the most.
    // It lifts the midtones (the dark world comes up, bright sand doesn't clip into a white sheet).
    col += (col * (1.0 - col) * 1.5 + sunCol * 0.03) * P(48) * flashGain;
    float phase = pow(max(cosS, 0.0), 6.0) * 0.45 + pow(max(cosS, 0.0), 40.0) * 0.35;
    vec3 scat = mix(P3(38), P3(41), clamp(phase * 1.4, 0.0, 1.0));
    // Night thins the scattering, unless the theme's own light (the rune, the moon) is lighting the fog.
    float amount = (1.0 - exp(-dist / fogFar * 2.0)) * P(16) * (0.12 + phase) * 0.6 * (1.0 - 0.5 * P(29) * (1.0 - P(12)));
    float h = clamp(dot(pos, P3(24)), -256.0, 256.0);
    // Haze lies low over the land: thickest near sea level, thinning over some 20 blocks of height in the world,
    // so it has no edge of its own (measured from your feet it drew a hard line across the land at your height).
    float haze = P(17) * exp(-max(0.0, h + P(31) - 2.0) / 20.0) * (1.0 - exp(-dist / 18.0));
    col = mix(col, scat, clamp(amount + haze * 0.4, 0.0, 0.85));
    fragColor = vec4(mix(base, col, landFade) + shafts, 1.0);
}
"""),
    # What blooms: the bright parts at half resolution, with a soft knee, four taps against flicker.
    "sol_bright": (["In", "Params"], PARAMS + r"""
void main() {
    vec2 h = 0.5 / vec2(textureSize(InSampler, 0));
    vec3 c = (texture(InSampler, texCoord + vec2(-h.x, -h.y)).rgb + texture(InSampler, texCoord + vec2(h.x, -h.y)).rgb
            + texture(InSampler, texCoord + vec2(-h.x, h.y)).rgb + texture(InSampler, texCoord + vec2(h.x, h.y)).rgb) * 0.25;
    float t = P(19);
    // At night warm lights (torches, lanterns, lava, windows) bloom against the cold.
    t = mix(t, t * 0.6, P(75) * clamp((c.r - c.b) * 2.5, 0.0, 1.0));
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
    # The hand glow's source: the hands and the held item (all that was drawn after the game cleared the depth for
    # them) in the glow's colour, at a quarter of the screen.
    "sol_hand_mask": (["In", "Depth", "Params"], PARAMS + DEPTH + r"""
vec3 rainbow(float h) {
    return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
}

void main() {
    // Four taps over this texel's part of the screen: soft edges, no shimmer as the hand sways.
    vec2 h = vec2(0.25 / 480.0, 0.25 / 270.0);
    float cover = 0.0;
    vec3 item = vec3(0.0);
    for (int i = 0; i < 4; i++) {
        vec2 uv = texCoord + vec2((i == 0 || i == 2) ? -h.x : h.x, i < 2 ? -h.y : h.y);
        float on = isSky(texture(DepthSampler, uv).r) ? 0.0 : 1.0;
        cover += on * 0.25;
        item += texture(InSampler, uv).rgb * on * 0.25;
    }
    if (cover <= 0.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    float t = P(22);
    int mode = int(P(83) + 0.5);
    vec3 col;
    if (mode == 1) {
        // The item's own colours, brightened so a dark sword glows too.
        vec3 c = item / cover;
        col = mix(c / max(max(max(c.r, c.g), c.b), 0.08), P3(76), 0.2);
    } else if (mode == 2) {
        col = rainbow(fract(texCoord.x * 0.7 - texCoord.y * 0.4 + t * 0.12));
    } else {
        // The two colours flowing through each other.
        float f = 0.5 + 0.5 * sin(texCoord.x * 7.0 + texCoord.y * 4.0 - t * 1.7 + sin(texCoord.y * 5.0 + t * 0.9) * 1.6);
        col = mix(P3(76), P3(79), f);
    }
    fragColor = vec4(col * cover, 1.0);
}
"""),
    # The hand glow's downsample: thirteen taps over two texels of the level above (a wider reach than the bloom's),
    # so the glow spreads far from the hands.
    "sol_glow_down": (["In"], r"""
void main() {
    vec2 t = 1.0 / vec2(textureSize(InSampler, 0));
    vec3 inner = texture(InSampler, texCoord + t * vec2(-1.0, -1.0)).rgb + texture(InSampler, texCoord + t * vec2(1.0, -1.0)).rgb
            + texture(InSampler, texCoord + t * vec2(-1.0, 1.0)).rgb + texture(InSampler, texCoord + t * vec2(1.0, 1.0)).rgb;
    vec3 axis = texture(InSampler, texCoord + t * vec2(0.0, -2.0)).rgb + texture(InSampler, texCoord + t * vec2(-2.0, 0.0)).rgb
            + texture(InSampler, texCoord + t * vec2(2.0, 0.0)).rgb + texture(InSampler, texCoord + t * vec2(0.0, 2.0)).rgb;
    vec3 corner = texture(InSampler, texCoord + t * vec2(-2.0, -2.0)).rgb + texture(InSampler, texCoord + t * vec2(2.0, -2.0)).rgb
            + texture(InSampler, texCoord + t * vec2(-2.0, 2.0)).rgb + texture(InSampler, texCoord + t * vec2(2.0, 2.0)).rgb;
    vec3 c = texture(InSampler, texCoord).rgb;
    fragColor = vec4(inner * 0.125 + axis * 0.0625 + corner * 0.03125 + c * 0.125, 1.0);
}
"""),
    # The hand glow's upsample: the wider level added onto this one (not blended), so every level counts: the colours
    # round the hands spread out, for the rim to take the colour of what it's round.
    "sol_glow_up": (["Up", "Cur", "Params"], PARAMS + r"""
void main() {
    vec2 h = 0.5 / vec2(textureSize(UpSampler, 0));
    vec3 s = texture(UpSampler, texCoord + vec2(-2.0 * h.x, 0.0)).rgb + texture(UpSampler, texCoord + vec2(2.0 * h.x, 0.0)).rgb
            + texture(UpSampler, texCoord + vec2(0.0, -2.0 * h.y)).rgb + texture(UpSampler, texCoord + vec2(0.0, 2.0 * h.y)).rgb
            + (texture(UpSampler, texCoord + vec2(-h.x, h.y)).rgb + texture(UpSampler, texCoord + vec2(h.x, h.y)).rgb
            + texture(UpSampler, texCoord + vec2(h.x, -h.y)).rgb + texture(UpSampler, texCoord + vec2(-h.x, -h.y)).rgb) * 2.0;
    fragColor = vec4(s / 12.0 + texture(CurSampler, texCoord).rgb * 0.45, 1.0);
}
"""),
    # How far each texel of a 960 x 540 grid is from the hands (the hand glow's fade, Georg's picture). First along its
    # row: the nearest hand within 16 rows' worth, in rows (so it's the same on any window shape), over 16.
    "sol_hand_dx": (["In", "Depth", "Params"], PARAMS + DEPTH + r"""
float handAt(vec2 uv) {
    return isSky(texture(DepthSampler, uv).r) ? 0.0 : 1.0;
}

void main() {
    float unit = P(28) * 540.0 / 960.0;
    float best = 16.0;
    if (handAt(texCoord) > 0.5) {
        best = 0.0;
    } else {
        for (int i = 1; i <= 32; i++) {
            float d = float(i) * unit;
            if (d >= 16.0) {
                break;
            }
            vec2 o = vec2(float(i) / 960.0, 0.0);
            if (handAt(texCoord + o) > 0.5 || handAt(texCoord - o) > 0.5) {
                best = d;
                break;
            }
        }
    }
    // The frame itself is only its first input (1.21.1 binds a pass's first input as its colour).
    fragColor = vec4(best / 16.0, 0.0, 0.0, max(texture(InSampler, texCoord).a, 1.0));
}
"""),
    # Then down its column: red, the true distance to the nearest hand (rows, over 16), from each row's nearest along
    # it. Green, the rising glow's distance (Clarity's look): only to hands below (every second row, up to 72), a row up
    # counting a third and a row across four, so it reaches about twelve times as far up as to the side (rows, over 24).
    "sol_hand_dist": (["Dx", "Params"], PARAMS + r"""
void main() {
    float here = texture(DxSampler, texCoord).r * 16.0;
    float best = here;
    for (int i = 1; i <= 16; i++) {
        float dy = float(i);
        if (dy >= best) {
            break;
        }
        vec2 o = vec2(0.0, dy / 540.0);
        float d = min(texture(DxSampler, texCoord + o).r, texture(DxSampler, texCoord - o).r) * 16.0;
        best = min(best, sqrt(d * d + dy * dy));
    }
    float rise = min(24.0, here * 4.0);
    for (int i = 1; i <= 36; i++) {
        float dy = float(i) * 2.0 / 3.0;
        if (dy >= rise) {
            break;
        }
        float d = texture(DxSampler, texCoord - vec2(0.0, float(i) * 2.0 / 540.0)).r * 16.0 * 4.0;
        rise = min(rise, sqrt(d * d + dy * dy));
    }
    fragColor = vec4(best / 16.0, rise / 24.0, 0.0, 1.0);
}
"""),
    # The hand glow (Clarity's look, from Georg's picture), at full resolution. Red: the outline round the hands, full
    # from their edge to 2 px out (at 1080p; scaled by the window's height), fading to nothing by 10 px (times the
    # size setting, up to 3x), from the distance field (the golden style only has it when asked for); or, for the soft
    # style, a softer fade with no bright core, capped at 24 px. Green: inside the hands, a 1 px edge just inside them
    # at half strength (with the outline); outside, for the golden style, its light, all round them and reaching further
    # up, fading smoothly (times the size). Blue: the hands themselves (never filled).
    "sol_hand_edge": (["In", "Depth", "Dist", "Params"], PARAMS + DEPTH + r"""
float handAt(vec2 uv) {
    return isSky(texture(DepthSampler, uv).r) ? 0.0 : 1.0;
}

// The distance field (both distances) read with bilinear filtering by hand (1.21.1 filters every input of a pass
// alike, and the depth must stay unfiltered): in 1080p pixels.
vec2 distAt(vec2 uv) {
    vec2 size = vec2(textureSize(DistSampler, 0));
    vec2 p = uv * size - 0.5;
    vec2 f = fract(p);
    vec2 b = (floor(p) + 0.5) / size;
    vec2 t = 1.0 / size;
    vec2 d = mix(mix(texture(DistSampler, b).rg, texture(DistSampler, b + vec2(t.x, 0.0)).rg, f.x),
            mix(texture(DistSampler, b + vec2(0.0, t.y)).rg, texture(DistSampler, b + t).rg, f.x), f.y);
    // Rows of 540 are 2 px at 1080p (the first over 16 rows, the rising one over 24); measured from texel centres, so
    // half a texel nearer the edge.
    return max(vec2(0.0), d * vec2(32.0, 48.0) - 1.0);
}


void main() {
    float hand = handAt(texCoord);
    vec2 px = 1.0 / vec2(textureSize(DepthSampler, 0));
    float s = float(textureSize(DepthSampler, 0).y) / 1080.0;
    float near = 0.0, edge = 0.0, out1 = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.7853982 + 0.39;
        vec2 d = vec2(cos(a), sin(a)) * px;
        near += handAt(texCoord + d * max(1.0, 2.0 * s));
        edge += handAt(texCoord + d * max(1.5, 2.8 * s));
        out1 += 1.0 - handAt(texCoord + d * max(1.0, s));
    }
    float size = clamp(P(86), 1.0, 3.0);
    vec2 dists = distAt(texCoord);
    float dist = dists.x;
    float rim, green;
    if (P(88) > 0.5) {
        // The outline: the core, then a glow a good deal dimmer than it, falling off fast and gone by 10 px (times
        // the size).
        float core = near > 0.0 ? 1.0 : min(1.0, edge * 0.5);
        float fade = 0.55 * exp(-max(dist - 2.0, 0.0) / (2.5 * size)) * (1.0 - smoothstep(6.0 * size, 10.0 * size, dist));
        // On its own, or in the golden light only when asked for (P(117)).
        rim = hand > 0.5 ? 0.0 : max(core, fade) * P(117);
        green = hand > 0.5 && out1 > 0.0 ? 0.5 * P(117) : 0.0;
        if (P(88) > 1.5 && hand < 0.5) {
            // The golden glow: soft light all round the hands, reaching further up as warm light rises (P(114)),
            // fading smoothly with no edge of its own; the fields stop at 32 px across and 48 px rising.
            float halo = exp(-dist / (9.0 * size)) * (1.0 - smoothstep(24.0, 31.0, dist));
            float up = exp(-dists.y / (12.0 * size * max(P(114), 0.05))) * (1.0 - smoothstep(36.0, 46.0, dists.y));
            green = max(halo, up * 0.85 * min(P(114), 1.0));
        }
    } else {
        float f = 1.0 - smoothstep(0.0, min(24.0, 12.0 * size), dist);
        rim = hand > 0.5 ? 0.0 : f * f * 0.8;
        green = 0.0;
    }
    // The frame itself is only its first input (1.21.1 binds a pass's first input as its colour).
    fragColor = vec4(rim, green, hand, max(texture(InSampler, texCoord).a, 1.0));
}
"""),
    # The motion trail (Clarity's look): last frame's trail fading (by the frame's time, so it lasts as long at any frame
    # rate), and the whole path each hand took since then filled in. For each hand that moved (HandMotion: a turn, a
    # stretch and a shift from where things are now to where they were), the pixel is followed back along that path,
    # turning as the hand turned, in steps of a few pixels; at each step, what the hand leaves there (its glow, and
    # faintly its own shape) counts, faded more the further back it is, as long as that point really was part of this
    # hand the frame before (so a still hand isn't dragged along by the other one). So a swing leaves one smooth
    # golden smear, not a copy per frame. Red: what the hands leave this frame (checked against next frame); green: the
    # trail.
    "sol_hand_trail": (["Edge", "Prev", "Params"], PARAMS + r"""
// What the hands leave behind at a point of this frame: their glow or outline, and faintly their own shape.
float leaves(vec2 uv) {
    if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) {
        return 0.0;
    }
    vec3 e = texture(EdgeSampler, uv).rgb;
    return e.b > 0.5 ? 0.1 : max(e.g, e.r);
}

// The trail so far round a point, a little blurred: each frame softens what's older, so a swing fades out as one
// blurred streak of light instead of keeping each frame's edges.
float trailBefore(vec2 uv) {
    vec2 t = 1.25 / vec2(textureSize(PrevSampler, 0));
    float c = texture(PrevSampler, uv).g * 0.36;
    c += (texture(PrevSampler, uv + vec2(t.x, 0.0)).g + texture(PrevSampler, uv - vec2(t.x, 0.0)).g
            + texture(PrevSampler, uv + vec2(0.0, t.y)).g + texture(PrevSampler, uv - vec2(0.0, t.y)).g) * 0.11;
    c += (texture(PrevSampler, uv + t).g + texture(PrevSampler, uv - t).g
            + texture(PrevSampler, uv + vec2(t.x, -t.y)).g + texture(PrevSampler, uv - vec2(t.x, -t.y)).g) * 0.05;
    return c;
}

// What they left at a point the frame before.
float left(vec2 uv) {
    if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) {
        return 0.0;
    }
    return texture(PrevSampler, uv).r;
}

mat2 turn(float a) {
    return mat2(cos(a), sin(a), -sin(a), cos(a));
}

void main() {
    float own = leaves(texCoord);
    // Less a half step, so an 8-bit target fades all the way out.
    float trail = max(trailBefore(texCoord) * P(89) - 0.5 / 255.0, 0.0);
    // Where along the path each step samples, shifted a little per pixel: the gaps between steps on a fast swing turn
    // into a fine grain the next frame's blur smooths away, not copies of the hand. Interleaved gradient noise: its
    // grain is even, so a small blur takes it out (a hash's clumps showed as specks).
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    vec2 asp = vec2(P(28), 1.0);
    vec2 q = texCoord * asp;
    for (int h = 0; h < 2; h++) {
        int o = 92 + h * 11;
        if (P(o) < 0.5) {
            continue;
        }
        float a = P(o + 1);
        mat2 stretch = mat2(P(o + 2), P(o + 3), P(o + 4), P(o + 5));
        vec2 was = vec2(P(o + 6), P(o + 7));
        vec2 mid = vec2(P(o + 8), P(o + 9));
        mat2 back = turn(a) * stretch;
        float steps = P(o + 10);
        for (int k = 1; k < 32; k++) {
            if (float(k) >= steps) {
                break;
            }
            // s of the way from where it was (0) to now (1): the point of this frame that was here then.
            float s = (float(k) - jitter + 0.5) / steps;
            mat2 m = turn(a * (1.0 - s)) * (stretch * (1.0 - s) + mat2(s));
            float det = m[0][0] * m[1][1] - m[1][0] * m[0][1];
            if (abs(det) < 1e-5) {
                continue;
            }
            vec2 r = q - mix(was, mid, s);
            vec2 x = mid + vec2(m[1][1] * r.x - m[1][0] * r.y, m[0][0] * r.y - m[0][1] * r.x) / det;
            // That point, where it was the frame before: it must have been this hand's then too.
            if (left((was + back * (x - mid)) / asp) < 0.12) {
                continue;
            }
            trail = max(trail, leaves(x / asp) * mix(P(89), 1.0, s));
        }
    }
    fragColor = vec4(own, max(trail, own), 0.0, 1.0);
}
"""),
    # The hand glow (Clarity's look, from Georg's picture): golden light round the hands and what they hold, deep gold
    # where it fades out and pale bright gold at the hands, then a thin pale line round them; brightest at
    # the fist and dimmer where the arm leaves the screen; the hands themselves flat, with their 1 px inner edge; the
    # motion trail (the path they took, filled with their glow). Each is laid over what's behind (so the hue holds on
    # any sky), then added with a soft clamp that keeps the hue, so no channel clips unless the colour itself is white.
    # The soft style can have lines of light rising through its fade.
    "sol_hand_glow": (["In", "Glow", "Edge", "Trail", "Params"], PARAMS + r"""
float hash(float n) {
    return fract(sin(n * 91.345) * 47453.21);
}

// Thin lines of light moving up the screen in lanes, waving as they go; most lanes have one.
float lines(vec2 w, float t) {
    float lx = w.x * 30.0;
    float lane = floor(lx);
    float r = hash(lane), r2 = hash(lane + 17.0);
    float x = fract(lx) - 0.5 + sin(w.y * 8.0 + t * 1.8 + r * 6.28) * 0.2;
    float y = 1.0 - fract(w.y * (0.9 + r2 * 0.8) - t * (0.3 + r * 0.4) + r * 5.0);
    float body = smoothstep(0.35, 0.82, y) * (1.0 - smoothstep(0.82, 0.86, y));
    float thin = 1.0 - smoothstep(0.02, 0.08, abs(x));
    return body * thin * step(0.3, r2);
}

// Unchanged up to 0.8; past it the whole colour is scaled so its brightest channel eases towards 1 without reaching
// it, which keeps the hue (gold stays gold rather than going yellow, then white).
vec3 soft(vec3 x) {
    float m = max(max(x.r, x.g), x.b);
    return m > 0.8 ? x * ((0.8 + 0.2 * (1.0 - exp((0.8 - m) / 0.2))) / m) : x;
}

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    vec3 e = texture(EdgeSampler, texCoord).rgb;
    vec3 g = texture(GlowSampler, texCoord).rgb;
    float m = max(max(g.r, g.g), max(g.b, 1e-4));
    vec3 col = m > 0.02 ? g / m : P3(76);
    float t = P(22);
    float s = min(P(82), 2.0) * (1.0 + P(84) * 0.12 * sin(t * 2.3));
    // The arms come in from the bottom and the sides: full at the fist, 60% where they leave the screen.
    float fromEdge = min(texCoord.y, min(texCoord.x, 1.0 - texCoord.x) * P(28));
    float grade = mix(0.6, 1.0, smoothstep(0.02, 0.35, fromEdge));
    // Green is the inner edge inside the hands and the rising glow outside them; with the trail, what the hands left
    // behind on their way here.
    float inner = e.b > 0.5 ? e.g : 0.0;
    float glow = e.b > 0.5 ? 0.0 : e.g;
    if (P(90) > 0.0) {
        glow = max(glow, texture(TrailSampler, texCoord).g * (1.0 - e.b) * P(90));
    }
    float lit = clamp(glow * grade * s, 0.0, 1.5);
    // Light, not paint: added onto what's behind (dimmed a little where it's strong, so it reads on a bright sky too),
    // a deeper shade of the colour where it fades out (deep amber for gold), the colour itself, then a pale bright
    // shade of it where it's strongest, at the hands (cream for gold). Both come from the colour, so white stays white.
    vec3 outer = col * mix(vec3(1.0), col, P(116));
    vec3 hot = mix(col, vec3(1.0), 0.75 * P(115));
    vec3 light = mix(mix(outer, col, smoothstep(0.0, 0.45, lit)), hot, smoothstep(0.55, 1.0, lit));
    c = soft(c * (1.0 - 0.45 * min(lit, 1.0)) + light * lit * 1.15);
    // The outline: pale in the golden light (cream for gold), nearly the colour itself on its own.
    float k = clamp((e.r + inner) * grade * s, 0.0, 1.5);
    vec3 pale = mix(col, vec3(1.0), P(118));
    c = mix(c, pale, clamp(k * 0.6, 0.0, 0.8));
    c = soft(c + pale * k * 0.3);
    // The lines show in the soft style's fade, not over the hand.
    if (P(87) > 0.0 && P(88) < 0.5) {
        float band = smoothstep(0.05, 0.3, e.r) * (1.0 - smoothstep(0.6, 0.9, e.r)) * (1.0 - e.b);
        c = soft(c + col * lines(texCoord * vec2(P(28), 1.0), t) * band * s * 0.6 * P(87));
    }
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
    # The world under the quick menu in the pixel UI's pixels: each cell (its size in screen pixels in the cell
    # texture's red, the grid's offset in its green and blue) takes the colour at its middle.
    "sol_pixel": (["In", "Cell"], r"""
void main() {
    vec4 s = texelFetch(CellSampler, ivec2(0, 0), 0);
    float cell = max(1.0, floor(s.r * 255.0 + 0.5));
    vec2 off = floor(s.gb * 255.0 + 0.5);
    vec2 size = vec2(textureSize(InSampler, 0));
    vec2 at = (floor((texCoord * size - off) / cell) + 0.5) * cell + off;
    fragColor = vec4(texture(InSampler, clamp(at, vec2(0.5), size - 0.5) / size).rgb, 1.0);
}
"""),
}

# ---------------------------------------------------------------------------------------------------- the chains

IDENTITY = [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0]
PARAMS_TEX = ("sol_params", 128, 1, False)
LUT_TEX = ("sol_lut", 1024, 32, True)
BLACK_TEX = ("sol_black", 1, 1, False)
BLUR_TEX = ("sol_blur", 1, 1, False)
PIXEL_TEX = ("sol_pixel", 1, 1, False)

# Fixed bloom levels (a 1080p screen's half, then each half of that): post chains only size targets to the screen
# or to fixed sizes, and a blur this wide looks the same at any resolution.
LEVELS = [(960, 540), (480, 270), (240, 135), (120, 68), (60, 34), (30, 17)]
SHAFT_SIZE = (480, 270)
# The hand glow's levels: its mask at a quarter of a 1080p screen, then each half of that.
HAND_SIZES = [(480, 270), (240, 135), (120, 68), (60, 34), (30, 17)]
# The hand glow's distance field: 540 rows, so a row is 2 px at 1080p and the fade scales with the window's height.
DIST_SIZE = (960, 540)


def chain(name, era=None):
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
    if name == "sol_hand_glow":
        # The mask at a quarter of the screen, blurred down four levels and added back up: a wide, soft falloff.
        targets = {"swap": None, "hmask": HAND_SIZES[0], "hu0": HAND_SIZES[0]}
        for i in range(1, len(HAND_SIZES)):
            targets["hg%d" % i] = HAND_SIZES[i]
            if i < len(HAND_SIZES) - 1:
                targets["hu%d" % i] = HAND_SIZES[i]
        level = lambda i: "hmask" if i == 0 else "hg%d" % i
        passes = [("sol_hand_mask", "hmask", [("In", "main", T, False), ("Depth", "main", D, False), ("Params", PARAMS_TEX, X, False)])]
        for i in range(1, len(HAND_SIZES)):
            passes.append(("sol_glow_down", level(i), [("In", level(i - 1), T, True)]))
        for i in range(len(HAND_SIZES) - 2, -1, -1):
            up = level(i + 1) if i == len(HAND_SIZES) - 2 else "hu%d" % (i + 1)
            passes.append(("sol_glow_up", "hu%d" % i, [("Up", up, T, True), ("Cur", level(i), T, False), ("Params", PARAMS_TEX, X, False)]))
        # The distance to the hands on a 960 x 540 grid (along the rows, then down the columns), for the rim's fade.
        targets["hdx"] = DIST_SIZE
        targets["hdist"] = DIST_SIZE
        passes.append(("sol_hand_dx", "hdx", [("In", "main", T, False), ("Depth", "main", D, False), ("Params", PARAMS_TEX, X, False)]))
        passes.append(("sol_hand_dist", "hdist", [("Dx", "hdx", T, False), ("Params", PARAMS_TEX, X, False)]))
        targets["hedge"] = None
        passes.append(("sol_hand_edge", "hedge", [("In", "main", T, False), ("Depth", "main", D, False), ("Dist", "hdist", T, False),
                                                  ("Params", PARAMS_TEX, X, False)]))
        if era is None or era in PERSISTENT:
            # The trail lives in a target kept from frame to frame (not on 1.21.2 to 1.21.5, which can't).
            targets["htrail"] = "persistent"
            targets["htrailn"] = None
            passes.append(("sol_hand_trail", "htrailn", [("Edge", "hedge", T, False), ("Prev", "htrail", T, False),
                                                         ("Params", PARAMS_TEX, X, False)]))
            passes.append(("sol_copy", "htrail", [("In", "htrailn", T, False)]))
            trail = "htrailn"
        else:
            trail = "hedge"
        passes.append(("sol_hand_glow", "swap", [("In", "main", T, False), ("Glow", "hu0", T, True), ("Edge", "hedge", T, False),
                                                  ("Trail", trail, T, False), ("Params", PARAMS_TEX, X, False)]))
        passes.append(("sol_copy", "main", [("In", "swap", T, False)]))
        return passes, targets
    if name == "sol_motion_blur":
        # "prev" keeps the last frame: persistent from 1.21.6; 1.21.1's chain owns its targets anyway.
        passes = [("sol_blur_mix", "swap", [("In", "main", T, False), ("Prev", "prev", T, False), ("Amount", BLUR_TEX, X, False)]),
                  ("sol_copy", "prev", [("In", "swap", T, False)]),
                  ("sol_copy", "main", [("In", "swap", T, False)])]
        return passes, {"swap": None, "prev": "persistent"}
    if name == "sol_pixel":
        passes = [("sol_pixel", "swap", [("In", "main", T, False), ("Cell", PIXEL_TEX, X, False)]),
                  ("sol_copy", "main", [("In", "swap", T, False)])]
        return passes, {"swap": None}
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


CHAINS = ["sol_world_medium", "sol_world_high", "sol_grade", "sol_motion_blur", "sol_hand_glow", "sol_pixel"]
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
                "vertex": "sol_quad", "fragment": prog,
                "attributes": ["Position"], "samplers": samplers,
                "uniforms": [{"name": "ProjMat", "type": "matrix4x4", "count": 16, "values": [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0]},
                             {"name": "OutSize", "type": "float", "count": 2, "values": [1.0, 1.0]}]}, indent=2) + "\n")
        elif era == "e2":
            write(os.path.join(ns, "shaders", "post", prog + ".fsh"), text)
            write(os.path.join(ns, "shaders", "post", prog + ".json"), json.dumps({
                "vertex": "cosmeticsmod:post/sol_quad", "fragment": "cosmeticsmod:post/" + prog,
                "samplers": [{"name": s + "Sampler"} for s in inputs],
                "uniforms": [{"name": "ProjMat", "type": "matrix4x4", "count": 16, "values": [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0]},
                             {"name": "OutSize", "type": "float", "count": 2, "values": [1.0, 1.0]}]}, indent=2) + "\n")
        else:
            write(os.path.join(ns, "shaders", "post", prog + ".fsh"), text)
    for name in CHAINS:
        passes, targets = chain(name, era)
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
                    p["vertex_shader"] = "cosmeticsmod:post/sol_quad" if era in ("e3", "e4") else "minecraft:core/screenquad"
                    p["fragment_shader"] = "cosmeticsmod:post/" + prog
                p["inputs"] = ins
                p["output"] = "minecraft:main" if out == "main" else out
                plist.append(p)
            write(os.path.join(ns, "post_effect", name + ".json"), json.dumps({"targets": tmap, "passes": plist}, indent=2) + "\n")
    if era in ("e1", "e2"):
        # 1.21.1 to 1.21.4: the game draws each pass's quad in its target's pixels under a projection for the whole
        # screen, so a pass into a smaller fixed-size target (the bloom levels, the light shafts, the hand glow) filled
        # only its corner. This quad fills its own target at any size; it keeps the game's uniforms in use so the game
        # finds them.
        quad = """#version 150

in vec4 Position;

uniform mat4 ProjMat;
uniform vec2 OutSize;

out vec2 texCoord;

void main() {
    vec2 uv = Position.xy / OutSize;
    gl_Position = vec4(uv * 2.0 - 1.0, 0.2, 1.0);
    texCoord = uv + (OutSize.x < 0.0 ? ProjMat[0][0] : 0.0);
}
"""
        if era == "e1":
            write(os.path.join(base, "minecraft", "shaders", "program", "sol_quad.vsh"), quad)
        else:
            write(os.path.join(ns, "shaders", "post", "sol_quad.vsh"), quad)
    if era in ("e3", "e4"):
        # 1.21.5 to 1.21.8: the game's screen quad is sized by a projection for the whole screen, so a pass into a
        # smaller fixed-size target (the bloom levels, the light shafts, the hand glow) filled only its corner. This
        # quad fills its own target at any size; it keeps the game's uniforms in use so the game finds them.
        if era == "e3":
            head = "#version 150\n\nin vec4 Position;\n\nuniform mat4 ProjMat;\nuniform vec2 OutSize;\n"
        else:
            head = ("#version 150\n\n#moj_import <minecraft:projection.glsl>\n\nin vec4 Position;\n\n"
                    "layout(std140) uniform SamplerInfo {\n    vec2 OutSize;\n    vec2 InSize;\n};\n")
        write(os.path.join(ns, "shaders", "post", "sol_quad.vsh"), head + """
out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.2, 1.0);
    texCoord = Position.xy + (OutSize.x < 0.0 ? ProjMat[0][0] : 0.0);
}
""")
    if era == "e1":
        # 1.21.1 asks the resources for a chain's textures before using the one Sol registered under that name.
        for tex, w, h, _ in (PARAMS_TEX, LUT_TEX, BLACK_TEX, BLUR_TEX, PIXEL_TEX):
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
