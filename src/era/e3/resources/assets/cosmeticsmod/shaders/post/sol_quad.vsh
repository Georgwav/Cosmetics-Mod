#version 150

in vec4 Position;

uniform mat4 ProjMat;
uniform vec2 OutSize;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.2, 1.0);
    texCoord = Position.xy + (OutSize.x < 0.0 ? ProjMat[0][0] : 0.0);
}
