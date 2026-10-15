#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D UpSampler;
uniform sampler2D CurSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 h = 0.5 / vec2(textureSize(UpSampler, 0));
    vec3 s = texture(UpSampler, texCoord + vec2(-2.0 * h.x, 0.0)).rgb + texture(UpSampler, texCoord + vec2(2.0 * h.x, 0.0)).rgb
            + texture(UpSampler, texCoord + vec2(0.0, -2.0 * h.y)).rgb + texture(UpSampler, texCoord + vec2(0.0, 2.0 * h.y)).rgb
            + (texture(UpSampler, texCoord + vec2(-h.x, h.y)).rgb + texture(UpSampler, texCoord + vec2(h.x, h.y)).rgb
            + texture(UpSampler, texCoord + vec2(h.x, -h.y)).rgb + texture(UpSampler, texCoord + vec2(-h.x, -h.y)).rgb) * 2.0;
    vec3 cur = texture(CurSampler, texCoord).rgb;
    fragColor = vec4(mix(cur, s / 12.0, 0.55), 1.0);
}
