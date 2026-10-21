#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D DistSampler;
uniform sampler2D ParamsSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

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
    // A depth the projection can't give (a range mismatch) reads as the end of the world, not a million blocks away.
    float d = depth * P(8) + P(9);
    return d > 1e-6 ? 1.0 / d : max(16.0, P(30));
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
        if (P(88) > 1.5 && P(88) < 2.5 && hand < 0.5) {
            // The golden glow: soft light all round the hands, reaching further up as warm light rises (P(114)),
            // fading smoothly with no edge of its own; the fields stop at 32 px across and 48 px rising.
            float halo = exp(-dist / (9.0 * size)) * (1.0 - smoothstep(24.0, 31.0, dist));
            float up = exp(-dists.y / (12.0 * size * max(P(114), 0.05))) * (1.0 - smoothstep(36.0, 46.0, dists.y));
            green = max(halo, up * 0.85 * min(P(114), 1.0));
        }
        if (P(88) > 3.5) {
            // Bands: the ramp's four tones in stepped rings round the hands, 4.5 px each at 1080p (times the size, the
            // four within the field's 30 px), as 1, 0.75, 0.5 and 0.25; no inner edge.
            float w = min(4.5 * size, 7.5);
            float b = floor(dist / w);
            rim = hand > 0.5 || b > 3.0 ? 0.0 : 1.0 - b * 0.25;
            green = 0.0;
        }
    } else {
        float f = 1.0 - smoothstep(0.0, min(24.0, 12.0 * size), dist);
        rim = hand > 0.5 ? 0.0 : f * f * 0.8;
        green = 0.0;
    }
    // The frame itself is only its first input (1.21.1 binds a pass's first input as its colour).
    fragColor = vec4(rim, green, hand, max(texture(InSampler, texCoord).a, 1.0));
}
