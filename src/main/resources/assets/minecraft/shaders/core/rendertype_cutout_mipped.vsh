#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;
uniform float GameTime;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

// The Backrooms (Level 369's meadow): vanilla's shader, plus a breeze. Only the meadow's grass
// and flowers sway: their block colour is a marker, white with green one step down (255, 254,
// 255), which nothing else uses. Their roots stay put (a plant's foot is always on a whole block
// height, its top never is); the tops lean in slow travelling waves across the field, now and
// then in a stronger gust. Position is relative to the 16-block chunk section and every wave
// repeats a whole number of times in 16 blocks and in GameTime's 20-minute cycle, so nothing
// jumps at a section edge or when the cycle wraps.
const float TAU = 6.2831853;

bool meadow(vec4 c) {
    return c.r > 0.998 && c.b > 0.998 && abs(c.g - 254.0 / 255.0) < 0.002;
}

void main() {
    vec3 pos = Position;
    vec4 color = Color;
    if (meadow(Color)) {
        color.g = 1.0;
        if (fract(Position.y) > 0.02) {
            float along = (Position.x + Position.z) / 16.0;
            float across = (Position.x - Position.z) / 16.0;
            float gust = 0.55 + 0.45 * sin(TAU * (GameTime * 37.0 + across));
            float wave = sin(TAU * (GameTime * 400.0 + along)) * 0.7 + sin(TAU * (GameTime * 170.0 + Position.x / 8.0)) * 0.3;
            float lean = 0.055 * gust * wave;
            pos.x += lean;
            pos.z += lean * 0.6 + 0.012 * sin(TAU * (GameTime * 290.0 + Position.z / 8.0));
        }
    }
    pos += ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
