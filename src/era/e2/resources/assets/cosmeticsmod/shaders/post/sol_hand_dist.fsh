#version 150

uniform sampler2D DxSampler;
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
    float rise = min(16.0, here * 4.0);
    for (int i = 1; i <= 48; i++) {
        float dy = float(i) / 3.0;
        if (dy >= rise) {
            break;
        }
        float d = texture(DxSampler, texCoord - vec2(0.0, float(i) / 540.0)).r * 16.0 * 4.0;
        rise = min(rise, sqrt(d * d + dy * dy));
    }
    fragColor = vec4(best / 16.0, rise / 16.0, 0.0, 1.0);
}
