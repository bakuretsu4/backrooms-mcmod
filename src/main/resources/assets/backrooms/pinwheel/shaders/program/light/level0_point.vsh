#veil:buffer veil:camera VeilCamera

// One Level 0 lamp, drawn as a box around its reach (see client/light/Level0LightRenderer).
// Same per-lamp data layout as Veil's own point lights.

layout (location = 0) in vec3 Position;
layout (location = 1) in vec3 LightPosition;
layout (location = 2) in vec3 Color;
layout (location = 3) in vec3 Settings; // reach, shadows on (1) / off (0), unused

out vec3 lightPos;
out vec3 lightColor;
out float radius;
out float occluded;

void main() {
    vec3 size = Position * Settings.x;
    gl_Position = VeilCamera.ProjMat * (VeilCamera.ViewMat * vec4(LightPosition - VeilCamera.CameraPosition, 1.0) + vec4(size, 0.0));
    lightPos = LightPosition;
    lightColor = Color;
    radius = Settings.x;
    occluded = Settings.y;
}
