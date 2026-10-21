#version 150

in vec4 Position;

uniform mat4 ProjMat;
uniform vec2 OutSize;

out vec2 texCoord;

void main() {
    vec2 uv = Position.xy / OutSize;
    gl_Position = vec4(uv * 2.0 - 1.0, 0.2, 1.0);
    texCoord = uv + (OutSize.x < 0.0 ? ProjMat[0][0] : 0.0);
}
