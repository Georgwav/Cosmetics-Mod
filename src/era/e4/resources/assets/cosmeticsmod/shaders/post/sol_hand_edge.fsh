#version 150

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D DistSampler;
uniform sampler2D ParamsSampler;

in vec2 texCoord;

out vec4 fragColor;

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
    // Rows of 540 are 2 px at 1080p; measured from texel centres, so half a texel nearer the edge.
    return max(vec2(0.0), d * 32.0 - 1.0);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
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
        // The core, then a glow a good deal dimmer than it, falling off fast and gone by 10 px (times the size).
        float core = near > 0.0 ? 1.0 : min(1.0, edge * 0.5);
        float fade = 0.55 * exp(-max(dist - 2.0, 0.0) / (2.5 * size)) * (1.0 - smoothstep(6.0 * size, 10.0 * size, dist));
        rim = hand > 0.5 ? 0.0 : max(core, fade);
        // The rising glow: strong for its first third, then fading; its reach wavers, rising, so its top flickers.
        vec2 w = texCoord * vec2(P(28), 1.0) * 1080.0;
        float reach = min(24.0, 13.0 * size) * (0.8 + 0.45 * noise(vec2(w.x / 16.0, w.y / 22.0 - P(22) * 2.2)));
        // The field stops at 32 px (no hand that near below): no glow there.
        float rising = (1.0 - smoothstep(0.3 * reach, reach, dists.y)) * step(dists.y, 30.0);
        green = hand > 0.5 ? (out1 > 0.0 ? 0.5 : 0.0) : rising * rising;
    } else {
        float f = 1.0 - smoothstep(0.0, min(24.0, 12.0 * size), dist);
        rim = hand > 0.5 ? 0.0 : f * f * 0.8;
        green = 0.0;
    }
    // The frame itself is only its first input (1.21.1 binds a pass's first input as its colour).
    fragColor = vec4(rim, green, hand, max(texture(InSampler, texCoord).a, 1.0));
}
