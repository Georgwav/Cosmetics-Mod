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
    // Pixel light reads the quarter-size shafts texel by texel: light in visible steps.
    vec2 ss = vec2(textureSize(ShaftsSampler, 0));
    vec2 suv = P(30) > 0.5 ? (floor(texCoord * ss) + 0.5) / ss : texCoord;
    vec3 shafts = P(11) >= 2.0 ? texture(ShaftsSampler, suv).rgb * sunCol * P(21) : vec3(0.0);
    if (isSky(depth)) {
        // A lightning flash lights the clouds from inside.
        fragColor = vec4(col + shafts + sunCol * P(48) * 0.22, 1.0);
        return;
    }
    // A theme that dims its own day dims the world (the sky and the light it adds stay bright).
    col *= P(58);
    vec3 pos = viewAt(texCoord, depth);
    float dist = length(pos);
    vec3 dir = pos / max(dist, 1e-4);
    vec3 sunV = normalize(P3(3));
    float cosS = dot(dir, sunV);
    float fogFar = max(1.0, P(15));
    float fogged = smoothstep(P(14), fogFar, dist);
    // How much a lightning flash brightens this pixel (faces toward the bolt more, when the normals are known).
    float flashGain = 0.9;
    // The aurora's and the nebula's light: two colours sweeping over the ground in slow bands round the eye.
    vec3 lightCol = sunCol;
    float bandLift = 1.0;
    if (P(54) > 0.0) {
        vec3 east = P3(55);
        vec3 north = cross(P3(24), east);
        float ang = atan(dot(pos, north), dot(pos, east));
        float k = 0.5 + 0.5 * sin(ang * 3.0 + P(22) * 0.22 + 0.08 * dist);
        lightCol = mix(sunCol, P3(51), k * P(54));
        bandLift = 1.0 + 0.35 * P(54) * sin(ang * 5.0 - P(22) * 0.3 + 0.05 * dist);
    }
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
        vec3 sunMul = lightCol / max(0.25, luma(lightCol)) * 1.32 * bandLift;
        vec3 sh = P3(35);
        vec3 shadowMul = mix(vec3(1.0), sh / max(0.12, luma(sh)), 0.55) * 0.78;
        float amt = clamp(P(13) * P(12) * (1.0 - fogged), 0.0, 1.0);
        // Faces turned from a low sun read as warm silhouettes: they take the shadow colour's hue at their own
        // brightness, so a backlit plant goes dusky instead of staying green.
        vec3 shadowed = mix(col * shadowMul, luma(col) * sh / max(0.12, luma(sh)) * 0.78, 0.6);
        // At night the light has to light a dark world, not just tint it: the faces it reaches are brought up
        // (exposure), the rest stay in the dark shadow colour.
        // The lift is strongest toward the light (a pool of light under the rune, the ring, the moon) and
        // saturates softly instead of clipping into a flat fill.
        vec3 litCol = col * sunMul * (1.0 + P(49) * (0.2 + 0.8 * pow(max(cosS, 0.0), 5.0)));
        litCol = mix(litCol, vec3(1.0) - exp(-litCol * 1.3), clamp(P(49), 0.0, 1.0));
        col = mix(col, mix(shadowed, litCol, lit), amt);
        // A thin warm rim where a silhouette meets the sky near a low sun.
        float edge = 0.0;
        edge = max(edge, isSky(dR) ? 1.0 : 0.0);
        edge = max(edge, isSky(dL) ? 1.0 : 0.0);
        edge = max(edge, isSky(dU) ? 1.0 : 0.0);
        edge = max(edge, isSky(dD) ? 1.0 : 0.0);
        // Only the land's skyline: clouds (far above the eye) keep plain edges.
        edge *= 1.0 - smoothstep(24.0, 48.0, dot(pos, P3(24)));
        col += mix(P3(41), sunCol, 0.35) * edge * P(23) * smoothstep(0.7, 0.99, cosS) * P(2) * (1.0 - fogged * 0.5);
        // At night the light's glow lines every skyline (the prototypes' lit horizon edges).
        col += mix(P3(41), sunCol, 0.5) * edge * P(50) * 0.55;
        // Rim light on the faces the light grazes: lit edges along walls, trunks and dunes in the light's colour
        // (the rune's gold, the ring's red-orange, the galaxy's silver).
        float facing = max(0.0, dot(n, sunV));
        float graze = pow(1.0 - abs(dot(n, dir)), 2.5);
        col += lightCol * facing * graze * P(47) * 0.55 * (1.0 - fogged * 0.7) * smoothstep(0.0, 0.3, P(12));
        flashGain = 0.3 + 0.7 * facing;
    }
    // A lightning flash lights the whole world for a moment, the faces toward the bolt the most.
    // It lifts the midtones (the dark world comes up, bright sand doesn't clip into a white sheet).
    col += (col * (1.0 - col) * 1.5 + sunCol * 0.03) * P(48) * flashGain;
    float phase = pow(max(cosS, 0.0), 6.0) * 0.45 + pow(max(cosS, 0.0), 40.0) * 0.35;
    vec3 scat = mix(P3(38), P3(41), clamp(phase * 1.4, 0.0, 1.0));
    // Night thins the scattering, unless the theme's own light (the rune, the moon) is lighting the fog.
    float amount = (1.0 - exp(-dist / fogFar * 2.0)) * P(16) * (0.12 + phase) * 0.6 * (1.0 - 0.5 * P(29) * (1.0 - P(12)));
    float h = dot(pos, P3(24));
    float haze = P(17) * exp(-max(0.0, h + 1.62) / 2.5) * (1.0 - exp(-dist / 18.0));
    col = mix(col, scat, clamp(amount + haze * 0.4, 0.0, 0.85));
    fragColor = vec4(col + shafts, 1.0);
}
