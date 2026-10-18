#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 t = 1.0 / vec2(textureSize(InSampler, 0));
    vec3 inner = texture(InSampler, texCoord + t * vec2(-1.0, -1.0)).rgb + texture(InSampler, texCoord + t * vec2(1.0, -1.0)).rgb
            + texture(InSampler, texCoord + t * vec2(-1.0, 1.0)).rgb + texture(InSampler, texCoord + t * vec2(1.0, 1.0)).rgb;
    vec3 axis = texture(InSampler, texCoord + t * vec2(0.0, -2.0)).rgb + texture(InSampler, texCoord + t * vec2(-2.0, 0.0)).rgb
            + texture(InSampler, texCoord + t * vec2(2.0, 0.0)).rgb + texture(InSampler, texCoord + t * vec2(0.0, 2.0)).rgb;
    vec3 corner = texture(InSampler, texCoord + t * vec2(-2.0, -2.0)).rgb + texture(InSampler, texCoord + t * vec2(2.0, -2.0)).rgb
            + texture(InSampler, texCoord + t * vec2(-2.0, 2.0)).rgb + texture(InSampler, texCoord + t * vec2(2.0, 2.0)).rgb;
    vec3 c = texture(InSampler, texCoord).rgb;
    fragColor = vec4(inner * 0.125 + axis * 0.0625 + corner * 0.03125 + c * 0.125, 1.0);
}
