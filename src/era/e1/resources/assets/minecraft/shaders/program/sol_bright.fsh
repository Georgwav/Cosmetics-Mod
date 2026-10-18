#version 150

uniform sampler2D DiffuseSampler;
#define InSampler DiffuseSampler
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
    vec2 h = 0.5 / vec2(textureSize(InSampler, 0));
    vec3 c = (texture(InSampler, texCoord + vec2(-h.x, -h.y)).rgb + texture(InSampler, texCoord + vec2(h.x, -h.y)).rgb
            + texture(InSampler, texCoord + vec2(-h.x, h.y)).rgb + texture(InSampler, texCoord + vec2(h.x, h.y)).rgb) * 0.25;
    float t = P(19);
    // At night warm lights (torches, lanterns, lava, windows) bloom against the cold.
    t = mix(t, t * 0.6, P(75) * clamp((c.r - c.b) * 2.5, 0.0, 1.0));
    float knee = t * 0.35 + 1e-4;
    float l = luma(c);
    float soft = clamp(l - t + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee);
    float k = max(soft, l - t) / max(l, 1e-4);
    fragColor = vec4(c * k, 1.0);
}
