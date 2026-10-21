#version 330

uniform sampler2D InSampler;
uniform sampler2D GlowSampler;
uniform sampler2D EdgeSampler;
uniform sampler2D TrailSampler;
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

// The Sol FX ramp's tones of a colour (as Ramp does it): 0 core (60% toward white), 1 main, 2 shade (35% toward
// black), 3 deep (60% toward black).
vec3 tone(vec3 col, int i) {
    return i == 0 ? mix(col, vec3(1.0), 0.6) : i == 1 ? col : i == 2 ? col * 0.65 : col * 0.4;
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// The stars look (the End portal's idea, in the theme's tones): the hands and what they hold as a window onto a deep
// sky with a faint nebula in two stepped shades, and four layers of stars drifting each its own way, the nearer ones
// bigger (2 x 2 for the brightest), faster and brighter, twinkling in steps. A texel is 2 px at 1080p; at most one star
// per 6 x 6 texels per layer.
vec3 stars(vec3 c, vec3 col, float t) {
    vec2 size = vec2(textureSize(InSampler, 0));
    float texel = max(1.0, floor(size.y / 540.0 + 0.5));
    vec2 q = floor(texCoord * size / texel);
    // The deep sky, lighter where the hand's own shading is, so its shape still reads.
    float neb = sin(q.x * 0.045 + t * 0.15) + sin(q.y * 0.06 - t * 0.11 + q.x * 0.02);
    vec3 sky = mix(tone(col, 3) * 0.55, tone(col, 2) * 0.7, step(0.9, neb)) * (0.65 + 0.6 * luma(c));
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float a = hash(fi + 3.0) * 6.2832;
        vec2 dir = vec2(cos(a), sin(a));
        float zoom = 1.0 + (3.0 - fi) * 0.5;
        vec2 p = floor(q / zoom + dir * t * (1.2 + fi * 1.1) + fi * 37.0);
        vec2 cell = floor(p / 6.0);
        float r = hash2(cell + fi * 11.0);
        if (r < 0.35) {
            continue;
        }
        vec2 at = cell * 6.0 + floor(vec2(hash2(cell + 1.7), hash2(cell + 4.3)) * 5.0);
        vec2 d = p - at;
        float big = i >= 2 && r > 0.85 ? 1.0 : 0.0;
        if (d.x < 0.0 || d.y < 0.0 || d.x > big || d.y > big) {
            continue;
        }
        float tw = ceil((0.6 + 0.4 * sin(t * (1.5 + r * 2.0) + r * 40.0)) * 3.0) / 3.0;
        int k = r > 0.85 ? 0 : r > 0.6 ? 1 : 2;
        sky = mix(sky, tone(col, k), tw * (0.7 + 0.1 * fi));
    }
    return sky;
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
    if (P(88) > 3.5) {
        // Bands: each ring its tone, laid on flat (no light, no smooth fade); the trail steps down the same tones as
        // it fades.
        float k = e.r;
        if (P(90) > 0.0) {
            k = max(k, texture(TrailSampler, texCoord).g * (1.0 - e.b) * P(90) * 0.9);
        }
        if (k > 0.1) {
            int b = int(clamp(floor((1.0 - k) * 4.0 + 0.001), 0.0, 3.0));
            float a = clamp(s, 0.0, 1.0) * grade * (1.0 - float(b) * 0.1);
            c = mix(c, tone(col, b), a);
        }
        fragColor = vec4(c, 1.0);
        return;
    }
    if (P(88) > 2.5 && e.b > 0.5) {
        c = mix(c, stars(c, col, t), clamp(s, 0.0, 1.0));
    }
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
