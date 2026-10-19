#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D EdgeSampler;
uniform sampler2D PrevSampler;
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

// What the hands leave behind at a point of this frame: their own shape, filled, and their glow.
float leaves(vec2 uv) {
    if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) {
        return 0.0;
    }
    vec3 e = texture(EdgeSampler, uv).rgb;
    return e.b > 0.5 ? 0.85 : e.g;
}

void main() {
    // Less a half step, so an 8-bit target fades all the way out.
    float trail = max(texture(PrevSampler, texCoord).g * P(89) - 0.5 / 255.0, 0.0);
    vec2 asp = vec2(P(28), 1.0);
    for (int h = 0; h < 2; h++) {
        int o = 92 + h * 10;
        if (P(o) < 0.5) {
            continue;
        }
        mat2 a = mat2(P(o + 1), P(o + 2), P(o + 3), P(o + 4));
        vec2 t = vec2(P(o + 5), P(o + 6));
        vec2 mine = vec2(P(o + 7), P(o + 8)) * asp;
        vec2 other = vec2(P(102 - h * 10 + 7), P(102 - h * 10 + 8)) * asp;
        float steps = P(o + 9);
        for (int k = 1; k < 24; k++) {
            if (float(k) >= steps) {
                break;
            }
            // s of the way from where it was (0) to now (1): the point of this frame that was here then.
            float s = float(k) / steps;
            mat2 m = a * (1.0 - s) + mat2(s);
            float det = m[0][0] * m[1][1] - m[1][0] * m[0][1];
            if (abs(det) < 1e-6) {
                continue;
            }
            vec2 r = texCoord - t * (1.0 - s);
            vec2 q = vec2(m[1][1] * r.x - m[1][0] * r.y, m[0][0] * r.y - m[0][1] * r.x) / det;
            // Only this hand's: nearer its anchor than the other hand's.
            if (distance(q * asp, mine) > distance(q * asp, other)) {
                continue;
            }
            trail = max(trail, leaves(q) * mix(P(89), 1.0, s));
        }
    }
    fragColor = vec4(0.0, trail, 0.0, 1.0);
}
