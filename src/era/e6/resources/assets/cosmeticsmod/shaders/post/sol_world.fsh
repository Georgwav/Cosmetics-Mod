#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D ParamsSampler;
uniform sampler2D ShaftsSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// Sol's per-frame values: 64 floats, each one's IEEE bits in a texel's bytes (r = lowest).
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

void main() {
    vec3 col = texture(InSampler, texCoord).rgb;
    float depth = texture(DepthSampler, texCoord).r;
    vec3 sunCol = P3(32);
    vec3 shafts = P(11) >= 2.0 ? texture(ShaftsSampler, texCoord).rgb * sunCol * P(21) : vec3(0.0);
    if (isSky(depth)) {
        fragColor = vec4(col + shafts, 1.0);
        return;
    }
    vec3 pos = viewAt(texCoord, depth);
    float dist = length(pos);
    vec3 dir = pos / max(dist, 1e-4);
    vec3 sunV = normalize(P3(3));
    float cosS = dot(dir, sunV);
    float fogFar = max(1.0, P(15));
    float fogged = smoothstep(P(14), fogFar, dist);
    if (P(11) >= 2.0) {
        vec2 px = 1.0 / vec2(textureSize(DepthSampler, 0));
        float dR = texture(DepthSampler, texCoord + vec2(px.x, 0.0)).r;
        float dL = texture(DepthSampler, texCoord - vec2(px.x, 0.0)).r;
        float dU = texture(DepthSampler, texCoord + vec2(0.0, px.y)).r;
        float dD = texture(DepthSampler, texCoord - vec2(0.0, px.y)).r;
        vec3 pR = viewAt(texCoord + vec2(px.x, 0.0), dR);
        vec3 pL = viewAt(texCoord - vec2(px.x, 0.0), dL);
        vec3 pU = viewAt(texCoord + vec2(0.0, px.y), dU);
        vec3 pD = viewAt(texCoord - vec2(0.0, px.y), dD);
        // The neighbour on the same surface (the smaller step) on each axis, so edges don't smear the normal.
        vec3 ddx = abs(pR.z - pos.z) < abs(pos.z - pL.z) ? pR - pos : pos - pL;
        vec3 ddy = abs(pU.z - pos.z) < abs(pos.z - pD.z) ? pU - pos : pos - pD;
        vec3 n = normalize(cross(ddx, ddy));
        if (dot(n, dir) > 0.0) {
            n = -n;
        }
        // Wrapped light: ground and anything open to the sky catches a low sun (and the bright sky round it);
        // only faces turned away from it fall into the shadow colour.
        float lit = smoothstep(-0.45, 0.25, dot(n, sunV) + 0.35 * max(0.0, dot(n, P3(24))));
        vec3 sunMul = sunCol / max(0.25, luma(sunCol)) * 1.32;
        vec3 sh = P3(35);
        vec3 shadowMul = mix(vec3(1.0), sh / max(0.12, luma(sh)), 0.55) * 0.78;
        float amt = clamp(P(13) * P(12) * (1.0 - fogged), 0.0, 1.0);
        // Faces turned from a low sun read as warm silhouettes: they take the shadow colour's hue at their own
        // brightness, so a backlit plant goes dusky instead of staying green.
        vec3 shadowed = mix(col * shadowMul, luma(col) * sh / max(0.12, luma(sh)) * 0.78, 0.6);
        col = mix(col, mix(shadowed, col * sunMul, lit), amt);
        // A thin warm rim where a silhouette meets the sky near a low sun.
        float edge = 0.0;
        edge = max(edge, isSky(dR) ? 1.0 : 0.0);
        edge = max(edge, isSky(dL) ? 1.0 : 0.0);
        edge = max(edge, isSky(dU) ? 1.0 : 0.0);
        edge = max(edge, isSky(dD) ? 1.0 : 0.0);
        col += mix(P3(41), sunCol, 0.35) * edge * P(23) * smoothstep(0.7, 0.99, cosS) * P(2) * (1.0 - fogged * 0.5);
    }
    float phase = pow(max(cosS, 0.0), 6.0) * 0.45 + pow(max(cosS, 0.0), 40.0) * 0.35;
    vec3 scat = mix(P3(38), P3(41), clamp(phase * 1.4, 0.0, 1.0));
    float amount = (1.0 - exp(-dist / fogFar * 2.0)) * P(16) * (0.12 + phase) * 0.6 * (1.0 - 0.5 * P(29));
    float h = dot(pos, P3(24));
    float haze = P(17) * exp(-max(0.0, h + 1.62) / 2.5) * (1.0 - exp(-dist / 18.0));
    col = mix(col, scat, clamp(amount + haze * 0.4, 0.0, 0.85));
    fragColor = vec4(col + shafts, 1.0);
}
