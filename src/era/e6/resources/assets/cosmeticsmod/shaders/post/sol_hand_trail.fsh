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

// What the hands leave behind at a point of this frame: their glow, and faintly their own shape.
float leaves(vec2 uv) {
    if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) {
        return 0.0;
    }
    vec3 e = texture(EdgeSampler, uv).rgb;
    return e.b > 0.5 ? 0.25 : e.g;
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
    float trail = max(texture(PrevSampler, texCoord).g * P(89) - 0.5 / 255.0, 0.0);
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
            float s = float(k) / steps;
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
