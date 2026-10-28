#include veil:space_helper
#include backrooms:level0_grid

// One Level 0 lamp lighting the pixels inside its reach (client/light/Level0LightRenderer).
//
// Brightness: the surface's own colour (albedo) times the lamp colour, falling off as
// (1 - distance / reach)^3, times how squarely the surface faces the lamp. A little light still
// reaches surfaces that face sideways to it (WRAP), so walls beside a lamp aren't cut off hard.
// Shadows come from the wall map (backrooms:level0_grid): a wall, post or door jamb between the
// pixel and the lamp blocks it completely, block-exact.

in vec3 lightPos;
in vec3 lightColor;
in float radius;
in float occluded;

uniform sampler2D AlbedoSampler;
uniform sampler2D NormalSampler;
uniform sampler2D DepthSampler;
uniform vec2 ScreenSize;

out vec4 fragColor;

#define WRAP 0.2
#define SHEEN 0.03

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
    float facing = clamp(dot(normal, offset / max(dist, 1.0e-4)), 0.0, 1.0);
    float s = 1.0 - dist / radius;
    float light = (facing + WRAP) / (1.0 + WRAP) * s * s * s;
    if (occluded > 0.5 && light > 0.0005) {
        // Bright parts get the soft-edged two-ray shadow; the faint tail of the reach (most of
        // the pixels a lamp covers) one ray, which halves the cost where nobody sees the edge.
        vec3 from = pos + normal * 0.03;
        light *= light > 0.03 ? level0Shadow(from, lightPos) : level0Ray(from, lightPos);
    }
    vec3 c = light * lightColor;
    fragColor = vec4(albedo.rgb * c * (1.0 - SHEEN) + c * SHEEN, 1.0);
}
