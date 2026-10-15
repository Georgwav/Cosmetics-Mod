#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 h = 0.5 / vec2(textureSize(InSampler, 0));
    vec3 s = texture(InSampler, texCoord).rgb * 4.0
            + texture(InSampler, texCoord - h).rgb + texture(InSampler, texCoord + h).rgb
            + texture(InSampler, texCoord + vec2(h.x, -h.y)).rgb + texture(InSampler, texCoord - vec2(h.x, -h.y)).rgb;
    fragColor = vec4(s * 0.125, 1.0);
}
