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

void main() {
    // The outline, its glow and the hands' shape where they are now, over what's left of them from before (less a
    // half step, so an 8-bit target fades all the way out).
    vec3 e = texture(EdgeSampler, texCoord).rgb;
    vec3 p = max(texture(PrevSampler, texCoord).rgb * P(89) - 0.5 / 255.0, 0.0);
    fragColor = vec4(max(p, e), 1.0);
}
