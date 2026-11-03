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
// Level 369's near meadow (client/Level369Grass): x how far its blades reach from the camera,
// y how wide the hand-over is; 0 while it isn't drawn.
uniform vec2 MeadowFade;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

// The Backrooms (Level 369's meadow): vanilla's shader, plus wind. Only the meadow's grass and
// flowers move: their block colour is a marker, white with green one step down (255, 254, 255),
// which nothing else uses. Every vertex is bent by the square of its height above the lawn, so
// the roots stay put and each plane and blade (cut in two at half height, see
// tools/anomaly_assets/level369.py _meadow) curves like a real blade. The whole field leans one
// way with the wind; waves roll across it, bending the grass further as they pass and catching
// the light on its tips, now and then in a stronger gust. Position is relative to the 16-block
// chunk section, and the lawn's top is Level 369's floor y41, nine blocks into its section
// (Level369Plan.FEET_Y). Every wave repeats a whole number of times in 16 blocks and in
// GameTime's 20-minute cycle, so nothing jumps at a section edge or when the cycle wraps.
// Near the camera Level369Grass draws the meadow as real blades (same wind, see
// assets/backrooms/shaders/core/l369_grass.vsh); there these plants sink into the lawn, rising
// again over the hand-over ring while the blades thin out. Position + ChunkOffset is the vertex
// relative to the camera.
const float TAU = 6.2831853;
const float LAWN_Y = 9.0;

bool meadow(vec4 c) {
    return c.r > 0.998 && c.b > 0.998 && abs(c.g - 254.0 / 255.0) < 0.002;
}

void main() {
    vec3 pos = Position;
    vec4 color = Color;
    if (meadow(Color)) {
        color.g = 1.0;
        if (MeadowFade.x > 0.0) {
            float keep = smoothstep(MeadowFade.x - MeadowFade.y, MeadowFade.x, length((Position + ChunkOffset).xz));
            pos.y -= (1.0 - keep) * 1.3;
        }
        float h = pos.y - LAWN_Y;
        if (h > 0.02) {
            float along = (Position.x + Position.z) / 8.0;
            float across = (Position.x - Position.z) / 16.0;
            float gust = 0.6 + 0.4 * sin(TAU * (GameTime * 37.0 + across));
            float wave = sin(TAU * (GameTime * 360.0 - along)) * 0.75 + sin(TAU * (GameTime * 150.0 + Position.x / 16.0)) * 0.25;
            float bend = (0.16 + 0.14 * gust * wave) * h * h;
            float flutter = 0.015 * h * sin(TAU * (GameTime * 900.0 + (Position.x + Position.z * 2.0) / 4.0));
            pos.x += bend * 0.8 + flutter;
            pos.z += bend * 0.6 - flutter * 0.5;
            pos.y -= 0.5 * bend * bend / max(h, 0.1);
            color.rgb *= 1.0 + 0.12 * gust * wave * min(h, 1.0);
        }
    }
    pos += ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
