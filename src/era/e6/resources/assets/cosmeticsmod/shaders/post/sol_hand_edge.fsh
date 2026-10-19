#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
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
    return 1.0 / max(1e-6, depth * P(8) + P(9));
}

vec3 viewAt(vec2 uv, float depth) {
    float z = linearDepth(depth);
    return vec3((uv * 2.0 - 1.0) * vec2(P(6), P(7)) * z, -z);
}

float handAt(vec2 uv) {
    return isSky(texture(DepthSampler, uv).r) ? 0.0 : 1.0;
}

void main() {
    float hand = handAt(texCoord);
    vec2 px = 1.0 / vec2(textureSize(DepthSampler, 0));
    float scale = max(1.0, float(textureSize(DepthSampler, 0).y) / 720.0) * (0.6 + 0.8 * P(86));
    float covA = 0.0, cov0 = 0.0, cov1 = 0.0, cov2 = 0.0, cov3 = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.7853982 + 0.2;
        vec2 d = vec2(cos(a), sin(a)) * px * scale;
        covA += handAt(texCoord + d * 1.2) * 0.125;
        cov0 += handAt(texCoord + d * 2.6) * 0.125;
        cov1 += handAt(texCoord + d * 4.0) * 0.125;
        cov2 += handAt(texCoord + d * 6.5) * 0.125;
        cov3 += handAt(texCoord + d * 9.5) * 0.125;
    }
    float line = hand > 0.5 ? 0.0 : min(1.0, max(covA, cov0) * 3.0);
    float band = hand > 0.5 ? 0.0
            : max(max(line, min(1.0, cov1 * 2.5) * 0.75), max(min(1.0, cov2 * 2.5) * 0.35, min(1.0, cov3 * 2.5) * 0.12));
    float inner = 0.0;
    // The frame itself is only its first input (1.21.1 binds a pass's first input as its colour).
    fragColor = vec4(line, band, inner, max(texture(InSampler, texCoord).a, 1.0));
}
