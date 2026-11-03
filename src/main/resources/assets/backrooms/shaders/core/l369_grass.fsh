#version 150

#moj_import <fog.glsl>

uniform mat4 ModelViewMat;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec3 viewPos;

out vec4 fragColor;

void main() {
    // For Veil's lights: the blade's own face, turned toward the camera (both sides are drawn)
    // and, if that is its underside, mirrored to the top (thin leaves and petals let the light
    // through), then leaning a little up so a light overhead still grazes it. Unused without Veil.
    vec3 up = mat3(ModelViewMat) * vec3(0.0, 1.0, 0.0);
    vec3 face = normalize(cross(dFdx(viewPos), dFdy(viewPos)));
    if (dot(face, viewPos) > 0.0) {
        face = -face;
    }
    face -= 2.0 * min(dot(face, up), 0.0) * up;
    // #veil:normal
    vec3 surfaceNormal = normalize(face + 0.5 * up);

    fragColor = linear_fog(vertexColor * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
}
