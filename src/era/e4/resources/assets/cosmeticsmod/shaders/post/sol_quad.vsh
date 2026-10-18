#version 150

#moj_import <minecraft:projection.glsl>

in vec4 Position;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.2, 1.0);
    texCoord = Position.xy + (OutSize.x < 0.0 ? ProjMat[0][0] : 0.0);
}
