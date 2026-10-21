#version 150

uniform sampler2D InSampler;
uniform sampler2D CellSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 s = texelFetch(CellSampler, ivec2(0, 0), 0);
    float cell = max(1.0, floor(s.r * 255.0 + 0.5));
    vec2 off = floor(s.gb * 255.0 + 0.5);
    vec2 size = vec2(textureSize(InSampler, 0));
    vec2 at = (floor((texCoord * size - off) / cell) + 0.5) * cell + off;
    fragColor = vec4(texture(InSampler, clamp(at, vec2(0.5), size - 0.5) / size).rgb, 1.0);
}
