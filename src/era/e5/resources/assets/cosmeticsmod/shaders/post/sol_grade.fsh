#version 330

uniform sampler2D InSampler;
uniform sampler2D BloomSampler;
uniform sampler2D ParamsSampler;
uniform sampler2D LutSampler;

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

vec3 grade(vec3 c) {
    float b = c.b * 31.0;
    float b0 = floor(b);
    float b1 = min(b0 + 1.0, 31.0);
    float f = b - b0;
    vec2 rg = vec2(c.r * 31.0 + 0.5, c.g * 31.0 + 0.5);
    vec3 s0 = texture(LutSampler, vec2((b0 * 32.0 + rg.x) / 1024.0, rg.y / 32.0)).rgb;
    vec3 s1 = texture(LutSampler, vec2((b1 * 32.0 + rg.x) / 1024.0, rg.y / 32.0)).rgb;
    return mix(s0, s1, f);
}

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    vec3 bloom = texture(BloomSampler, texCoord).rgb;
    c += bloom * P3(44) * P(18);
    c = grade(clamp(c, 0.0, 1.0));
    vec2 d = (texCoord - 0.5) * vec2(P(28), 1.0) / max(1.0, P(28) * 0.75);
    float v = smoothstep(0.32, 0.92, length(d) * 1.25);
    c *= 1.0 - P(20) * v;
    float n = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    c += (n - 0.5) / 255.0;
    fragColor = vec4(c, 1.0);
}
