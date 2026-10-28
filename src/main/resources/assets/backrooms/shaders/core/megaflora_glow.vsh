#version 150

// The Sun, its halo, the bubbles, the light beams and the far sunbeams (Level227Heavens):
// camera-facing quads, positions relative to the camera. Like the far tree: no far plane, and the
// same logarithmic depth, so the roots hide them where they cross.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;   // camera rotation
uniform mat4 ProjMat;
uniform float DepthRange;

out vec2 vUv;
out vec4 vColor;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    float depth = log2(1.0 + max(gl_Position.w, 1e-4)) / log2(1.0 + DepthRange);
    gl_Position.z = (depth * 2.0 - 1.0) * gl_Position.w;
    vUv = UV0;
    vColor = Color;
}
