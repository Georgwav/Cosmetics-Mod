#version 150

uniform sampler2D DiffuseSampler;
#define InSampler DiffuseSampler
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
    // A depth the projection can't give (a range mismatch) reads as the end of the world, not a million blocks away.
    float d = depth * P(8) + P(9);
    return d > 1e-6 ? 1.0 / d : max(16.0, P(30));
}

vec3 viewAt(vec2 uv, float depth) {
    float z = linearDepth(depth);
    return vec3((uv * 2.0 - 1.0) * vec2(P(6), P(7)) * z, -z);
}

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
