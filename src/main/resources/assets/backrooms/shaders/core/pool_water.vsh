#version 150

// Pool water surface (Poolrooms, Level 37). Vertices are stored relative to a mesh origin near
// the camera; MeshOffset (origin - camera) turns them into camera-relative positions, which is
// all the fragment shader needs (ModelViewMat is only the camera rotation).

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 MeshOffset;

out vec3 relPos;   // world position minus camera position
out vec3 viewPos;  // the same point in view space

void main() {
    relPos = Position + MeshOffset;
    vec4 view = ModelViewMat * vec4(relPos, 1.0);
    viewPos = view.xyz;
    gl_Position = ProjMat * view;
}
