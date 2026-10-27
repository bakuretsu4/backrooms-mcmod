#version 150

// Soft particles (steam, dust): lit by the world's light like any particle (the light level
// comes in UV2 and is looked up in the lightmap), positions relative to the camera.

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // Spherical fog (shape 0) uses the straight distance, cylindrical (1) the larger of the
    // horizontal distance and the height difference.
    vertexDistance = FogShape == 0 ? length(Position) : max(length(Position.xz), abs(Position.y));
    texCoord0 = UV0;
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
}
