#version 330

uniform sampler2D InSampler;
uniform sampler2D PrevSampler;
uniform sampler2D AmountSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float k = texelFetch(AmountSampler, ivec2(0, 0), 0).r;
    vec3 cur = texture(InSampler, texCoord).rgb;
    vec3 prev = texture(PrevSampler, texCoord).rgb;
    fragColor = vec4(mix(cur, prev, k), 1.0);
}
