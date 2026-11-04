#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in ivec2 UV2;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;
uniform float GameTime;
// x: how far the blades reach from the camera, y: where they start to thin out (blocks).
uniform vec2 Meadow;
// 1 for the meadow (wind, and sinking into the lawn past Meadow.y); 0 for the dark rooms'
// glowing vines, which hang still from the ceiling and simply stop at Meadow.x.
uniform float Sway;

out float vertexDistance;
out vec4 vertexColor;
out vec3 viewPos;
// How much of this vertex still shows (1 inside the reach, 0 past it): the fragment shader drops
// what is gone, so a vine (which can't sink into the lawn) ends there too.
out float shownPart;

// The Backrooms (Level 369's meadow up close, client/Level369Grass): real grass blades and
// flowers, one mesh per 16 x 16 tile. Position is relative to the tile's corner on the lawn
// (x and z a multiple of 16, y the lawn's top), so it is exactly what the block meadow's cutout
// shader sees relative to its chunk section, minus the lawn's height: the wind below is that
// shader's, wave for wave, and both meadows sway as one. ChunkOffset puts the tile relative to
// the camera, as for chunks. Color.a is the blade's rank: past Meadow.y the meadow thins out
// toward Meadow.x by sinking its highest-ranked blades into the lawn, while the block meadow
// rises in their place (rendertype_cutout_mipped.vsh).
const float TAU = 6.2831853;
const float SINK = 1.5;

void main() {
    vec3 pos = Position;
    vec3 color = Color.rgb;
    float dist = length((Position + ChunkOffset).xz);
    float density = 1.0 - smoothstep(Meadow.y, Meadow.x, dist);
    float shown = clamp((density * 1.2 - Color.a) / 0.2, 0.0, 1.0);
    pos.y -= (1.0 - shown) * SINK * Sway;
    shownPart = shown;
    float h = pos.y;
    if (h > 0.02 && Sway > 0.5) {
        float along = (Position.x + Position.z) / 8.0;
        float across = (Position.x - Position.z) / 16.0;
        float gust = 0.6 + 0.4 * sin(TAU * (GameTime * 37.0 + across));
        float wave = sin(TAU * (GameTime * 360.0 - along)) * 0.75 + sin(TAU * (GameTime * 150.0 + Position.x / 16.0)) * 0.25;
        float bend = (0.16 + 0.14 * gust * wave) * h * h;
        float flutter = 0.015 * h * sin(TAU * (GameTime * 900.0 + (Position.x + Position.z * 2.0) / 4.0));
        pos.x += bend * 0.8 + flutter;
        pos.z += bend * 0.6 - flutter * 0.5;
        pos.y -= 0.5 * bend * bend / max(h, 0.1);
        color *= 1.0 + 0.12 * gust * wave * min(h, 1.0);
    }
    pos += ChunkOffset;
    vec4 view = ModelViewMat * vec4(pos, 1.0);
    gl_Position = ProjMat * view;
    viewPos = view.xyz;

    // For Veil's lights, the unlit colour (the surface's direction is worked out per pixel in
    // the fragment shader).
    // #veil:albedo
    vec4 albedo = vec4(color, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = albedo * minecraft_sample_lightmap(Sampler2, UV2);
}
