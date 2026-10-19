#version 330

uniform sampler2D GlowSampler;
uniform sampler2D MaskSampler;
uniform sampler2D PrevSampler;
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

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// Smooth value noise, 0 to 1.
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1.0, 0.0)), f.x), mix(hash2(i + vec2(0.0, 1.0)), hash2(i + vec2(1.0, 1.0)), f.x), f.y);
}


void main() {
    vec2 px = 1.0 / vec2(textureSize(PrevSampler, 0));
    float scale = max(1.0, float(textureSize(PrevSampler, 0).y) / 720.0);
    float t = P(22);
    float frames = clamp(P(91) * 60.0, 0.0, 4.0);
    vec2 w = texCoord * vec2(P(28), 1.0);
    vec2 curl = vec2(noise(w * 7.0 + vec2(t * 0.6, 0.0)), noise(w * 7.0 + vec2(4.7, t * 0.5))) - 0.5;
    vec2 from = texCoord - (vec2(0.0, 0.45) + curl * 1.2) * px * scale * frames;
    vec2 d = px * scale * 1.6;
    vec3 prev = texture(PrevSampler, from).rgb * 0.36
            + (texture(PrevSampler, from + vec2(d.x, 0.0)).rgb + texture(PrevSampler, from - vec2(d.x, 0.0)).rgb
            + texture(PrevSampler, from + vec2(0.0, d.y)).rgb + texture(PrevSampler, from - vec2(0.0, d.y)).rgb) * 0.16;
    // Less a half step, so an 8-bit target fades all the way out.
    prev = max(prev * P(89) - 0.5 / 255.0, 0.0);
    // Mostly from round the hands' edge: what's inside them gives off a little, so a swing leaves smoke, not a copy.
    vec3 mk = texture(MaskSampler, texCoord).rgb;
    float inside = max(max(mk.r, mk.g), mk.b) > 0.02 ? 1.0 : 0.0;
    vec3 now = min(texture(GlowSampler, texCoord).rgb, vec3(1.0)) * (1.0 - 0.5 * inside)
            * (0.7 + 0.6 * noise(w * 40.0 + vec2(0.0, -t * 1.8)));
    fragColor = vec4(max(prev, now), 1.0);
}
