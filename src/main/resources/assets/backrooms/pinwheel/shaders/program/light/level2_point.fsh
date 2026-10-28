#include veil:space_helper
#include backrooms:level0_grid

// One Level 2 lamp lighting the pixels inside its reach (client/light/Level0LightRenderer, which
// draws both levels' lamps). Same light as Veil's own point lights, so the corridor keeps its look:
// the surface colour (albedo) times the lamp colour, falling off as (1 - distance / reach)^3, times
// how squarely the surface faces the lamp (with Veil's 0.4 wrap, so walls beside a lamp aren't cut
// off hard). Two things are ours:
//  - Shadows from the wall map (backrooms:level0_grid, built for Level 2 from y36 up): a wall,
//    pier or bulkhead between the pixel and the lamp blocks it, block-exact, cheaply.
//  - Gloss: a small highlight, the lamp's reflection, on surfaces the gloss map marks as damp,
//    wet or metal (client/light/Level2Gloss). Blinn-Phong around the half-way direction between
//    the lamp and your eye. Wet surfaces reflect much more at a grazing angle (Fresnel), so looking
//    down the corridor the wet patches catch the lamps as streaks; metal tints the highlight with
//    its own colour. No screen-space reflections: one extra texture read per lit pixel.

in vec3 lightPos;
in vec3 lightColor;
in float radius;
in float occluded;

uniform sampler2D AlbedoSampler;
uniform sampler2D NormalSampler;
uniform sampler2D DepthSampler;
uniform vec2 ScreenSize;

// The gloss classes of the blocks in the wall map's window: red = low bit, green = high bit, one
// bit per block height (same window, wrapping and base y as WallGrid).
uniform usampler2D GlossGrid;
// x, y = damp strength, sharpness; z, w = wet strength, sharpness.
uniform vec4 GlossDampWet;
// x, y = metal strength, sharpness; z = how far the highlight reaches (exponent of the falloff);
// w = how much of the wet highlight stays when you look straight down at it (Fresnel floor).
uniform vec4 GlossMetal;

out vec4 fragColor;

#define WRAP 0.4
#define SHEEN 0.05

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// 0 matte, 1 damp, 2 wet, 3 metal: the class of the block containing p (0 outside the window).
int glossClass(vec3 p) {
    ivec3 c = ivec3(floor(p));
    ivec2 rel = c.xz - ivec2(WallGridInfo.xz);
    int bit = c.y - int(WallGridInfo.y);
    if (rel.x < 0 || rel.y < 0 || rel.x >= WALL_GRID_SIZE || rel.y >= WALL_GRID_SIZE || bit < 0 || bit > 31) {
        return 0;
    }
    uvec2 g = texelFetch(GlossGrid, c.xz & ivec2(WALL_GRID_SIZE - 1), 0).rg;
    return int((g.r >> uint(bit)) & 1u) | (int((g.g >> uint(bit)) & 1u) << 1);
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec4 albedo = texture(AlbedoSampler, uv);
    if (albedo.a == 0.0) {
        discard;
    }
    float depth = texture(DepthSampler, uv).r;
    vec3 pos = screenToWorldSpace(uv, depth).xyz;
    vec3 offset = lightPos - pos;
    float dist = length(offset);
    if (dist >= radius) {
        discard;
    }
    vec3 normalVS = texture(NormalSampler, uv).xyz;
    vec3 normal = normalize((VeilCamera.IViewMat * vec4(normalVS, 0.0)).xyz);
    vec3 toLight = offset / max(dist, 1.0e-4);
    float nl = dot(normal, toLight);
    float s = 1.0 - dist / radius;
    float light = (clamp(nl, 0.0, 1.0) + WRAP) / (1.0 + WRAP) * s * s * s;

    // Gloss: only for surfaces facing the lamp.
    float spec = 0.0;
    vec3 specColor = lightColor;
    if (nl > 0.0) {
        int cls = glossClass(pos - normal * 0.02);
        if (cls != 0) {
            vec3 toEye = normalize(VeilCamera.CameraPosition - pos);
            vec3 halfway = normalize(toLight + toEye);
            float nh = max(dot(normal, halfway), 0.0);
            float nv = clamp(dot(normal, toEye), 0.0, 1.0);
            float strength;
            float sharp;
            if (cls == 1) {
                strength = GlossDampWet.x;
                sharp = GlossDampWet.y;
                // The concrete's own grain breaks the sheen up (lighter grain shines a bit more).
                strength *= 0.6 + 0.8 * clamp(luma(albedo.rgb) * 2.0, 0.0, 1.0);
            } else if (cls == 2) {
                strength = GlossDampWet.z;
                sharp = GlossDampWet.w;
                float fresnel = 0.04 + 0.96 * pow(1.0 - nv, 5.0);
                strength *= mix(GlossMetal.w, 1.0, fresnel);
            } else {
                strength = GlossMetal.x;
                sharp = GlossMetal.y;
                // Metal reflects in its own colour (copper orange, steel grey-blue).
                specColor = lightColor * mix(vec3(1.0), albedo.rgb / max(luma(albedo.rgb), 0.04), 0.7);
            }
            // (sharp + 2) / 8 keeps a tight highlight about as bright overall as a broad one.
            // Reflections fall off more slowly than the light itself, so they reach a bit further.
            spec = strength * pow(nh, sharp) * (sharp + 2.0) * 0.125 * pow(s, GlossMetal.z) * smoothstep(0.0, 0.15, nl);
        }
    }

    if (occluded > 0.5 && (light > 0.0005 || spec > 0.0005)) {
        // Bright parts get the soft-edged two-ray shadow; the faint tail of the reach (most of
        // the pixels a lamp covers) one ray, which halves the cost where nobody sees the edge.
        vec3 from = pos + normal * 0.03;
        float shadow = light > 0.03 ? level0Shadow(from, lightPos) : level0Ray(from, lightPos);
        light *= shadow;
        spec *= shadow;
    }
    vec3 c = light * lightColor;
    fragColor = vec4(albedo.rgb * c * (1.0 - SHEEN) + c * SHEEN + spec * specColor, 1.0);
}
