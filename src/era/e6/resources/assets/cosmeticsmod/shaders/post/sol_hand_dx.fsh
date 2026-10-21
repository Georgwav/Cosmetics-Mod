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
