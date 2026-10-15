#version 150

uniform sampler2D DiffuseSampler;
#define InSampler DiffuseSampler

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 h = 0.5 / vec2(textureSize(InSampler, 0));
    vec3 s = texture(InSampler, texCoord).rgb * 4.0
            + texture(InSampler, texCoord - h).rgb + texture(InSampler, texCoord + h).rgb
            + texture(InSampler, texCoord + vec2(h.x, -h.y)).rgb + texture(InSampler, texCoord - vec2(h.x, -h.y)).rgb;
    fragColor = vec4(s * 0.125, 1.0);
}
