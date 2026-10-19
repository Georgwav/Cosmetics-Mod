#version 150

uniform sampler2D DiffuseSampler;
#define InSampler DiffuseSampler
uniform sampler2D GlowSampler;
uniform sampler2D EdgeSampler;
uniform sampler2D TrailSampler;
uniform sampler2D DepthSampler;
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

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// Smooth value noise, 0 to 1.
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1.0, 0.0)), f.x), mix(hash2(i + vec2(0.0, 1.0)), hash2(i + vec2(1.0, 1.0)), f.x), f.y);
}


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

float handAt(vec2 uv) {
    return isSky(texture(DepthSampler, uv).r) ? 0.0 : 1.0;
}

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float hand = handAt(texCoord);
    vec3 g = texture(GlowSampler, texCoord).rgb;
    float t = P(22);
    float m = max(max(g.r, g.g), max(g.b, 1e-4));
    vec2 w = texCoord * vec2(P(28), 1.0);
    float pulse = 1.0 + P(84) * (0.18 * sin(t * 2.3) + 0.08 * sin(t * 5.1 + 1.3));
    float s = min(P(82), 2.0);
    if (P(88) > 0.5) {
        vec3 sm = P(90) > 0.0 ? texture(TrailSampler, texCoord).rgb
                : min(g, vec3(1.0)) * (0.7 + 0.6 * noise(w * 40.0 + vec2(0.0, -t * 1.8)));
        float d = max(max(sm.r, sm.g), max(sm.b, 1e-4));
        vec3 tint = d > 0.02 ? sm / d : m > 0.02 ? g / m : P3(76);
        // Raised first, so a thin sword glows as brightly at its edge as a whole arm does.
        float dd = min(1.0, d * 1.8);
        float a = (1.0 - exp(-dd * dd * 3.0 * pulse * s)) * (1.0 - hand);
        // Its own colour, saturated, only a little whiter where it's thickest.
        vec3 col = mix(tint, vec3(1.0), 0.18 * a * a);
        // Laid over what's behind (so it shows on bright skies), and glowing.
        c = mix(c, col, clamp(a * 0.4, 0.0, 1.0));
        c = 1.0 - (1.0 - c) * (1.0 - clamp(col * a * 0.75, 0.0, 1.0));
        vec3 edgeTint = m > 0.02 ? g / m : tint;
        c = mix(c, mix(edgeTint, vec3(1.0), 0.55), clamp(texture(EdgeSampler, texCoord).r * 0.9 * min(s, 1.0), 0.0, 1.0));
    } else {
        // Brightness falling off smoothly all the way out (no flat bright band round the hands), the hue kept.
        float k = 1.0 - exp(-m * 1.1 * pulse);
        k = k * k * mix(1.0, P(85), hand);
        vec3 col = mix(g / m, vec3(1.0), 0.25 * k * k * k);
        c = 1.0 - (1.0 - c) * (1.0 - clamp(col * k * s * 0.7, 0.0, 1.0));
        // The lines show in the glow round the hands, brightest partway out, not over the hand.
        if (P(87) > 0.0) {
            float band = smoothstep(0.03, 0.25, k) * (1.0 - smoothstep(0.55, 0.9, k)) * (1.0 - hand);
            c += mix(g / m, vec3(1.0), 0.35) * lines(w, t) * band * s * 0.85 * P(87);
        }
    }
    fragColor = vec4(c, 1.0);
}
