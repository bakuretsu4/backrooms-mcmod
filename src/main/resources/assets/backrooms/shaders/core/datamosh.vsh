#version 150

// Full-screen quad for the datamosh pass: Position is already in clip space (-1..1).

in vec3 Position;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
