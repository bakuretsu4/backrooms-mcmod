#version 150

// Eyes under the water (Poolrooms, Level 37): light that the rippling surface above focuses
// into the pool. Drawn over the finished picture with additive blending, so it only ever ADDS
// light:
//  - caustics: the moving web of light on every wall / floor / pillar below the water line,
//    scaled by how lit that surface already is (dark corners stay dark);
//  - light shafts: soft beams through the water between you and what you look at, made by
//    sampling a smooth, large noise (the ripple height) along your line of sight; it is
//    projected along the light direction, so equal values line up into beams.
// The underwater fog itself is vanilla's, with our colour and distance (UnderwaterView).

uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform sampler2D Ripples;
uniform sampler2D Caustics;
uniform mat4 ModelViewMat;   // camera rotation
uniform mat4 InvProjMat;
uniform vec2 ScreenSize;
uniform vec3 CameraLow;      // camera position wrapped every 4096 blocks
uniform float Time;
uniform float WaterLevel;    // height of the water surface above the eye (blocks)
uniform vec3 SunDir;         // direction the light travels (points down)
uniform vec3 SunColor;
uniform vec3 ShallowColor;
uniform float CausticStrength;
uniform float ShaftStrength;

out vec4 fragColor;

const float CAUSTIC_TILE = 4.0;
const float CAUSTIC_WARP_TILE = 8.0;
const float SHAFT_TILE = 16.0;
const int SHAFT_SAMPLES = 8;
const float SHAFT_RANGE = 20.0;

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec3 viewFromDepth(vec2 uv, float depth) {
    vec4 p = InvProjMat * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

// Where light reaching `world` (depth `h` under the surface) entered the water.
vec2 entryPoint(vec3 world, float h) {
    return world.xz - SunDir.xz * (h / max(-SunDir.y, 0.2));
}

// Same web as on the pool floor seen from above (pool_water.fsh), without the colour split.
float causticWeb(vec2 p) {
    vec2 warp = (texture(Ripples, p / CAUSTIC_WARP_TILE + vec2(Time * 0.035, -Time * 0.025)).rg * 2.0 - 1.0) * 0.05;
    vec2 uvA = p / CAUSTIC_TILE + vec2(Time * 0.012, Time * 0.007) + warp;
    vec2 uvB = vec2(p.x + p.y, p.y - p.x) / (CAUSTIC_TILE * 2.0) + vec2(-Time * 0.017, Time * 0.024);
    float a = texture(Caustics, uvA).r;
    return (a * 0.7 + min(a, texture(Caustics, uvB).r) * 1.2) * 0.55;
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float depth = texture(SceneDepth, uv).r;
    bool sky = depth >= 0.99999;
    vec3 rel = viewFromDepth(uv, depth) * mat3(ModelViewMat); // camera-relative world position
    float dist = sky ? 64.0 : length(rel);
    vec3 dir = rel / max(length(rel), 1.0e-4);
    vec3 scene = texture(SceneColor, uv).rgb;
    vec3 add = vec3(0.0);

    // Caustics on the surfaces under the water around you. Floors: the web projected along
    // the light; walls: spread across the wall (along it, and up), like in pool_water.fsh.
    vec3 n = normalize(cross(dFdx(rel), dFdy(rel)) + vec3(0.0, 1.0e-6, 0.0));
    float h = WaterLevel - rel.y;
    if (!sky && h > 0.0) {
        vec3 world = rel + CameraLow;
        vec2 p;
        float strength = 1.0;
        if (abs(n.y) > 0.5) {
            p = entryPoint(world, h);
        } else {
            vec2 along = normalize(vec2(-n.z, n.x) + vec2(1.0e-5));
            p = vec2(dot(world.xz, along), world.y * 1.3);
            strength = 0.7;
        }
        float fade = smoothstep(0.0, 0.3, h) * exp(-h * 0.16) * (1.0 - smoothstep(12.0, 26.0, dist));
        add += scene * causticWeb(p) * fade * strength * CausticStrength;
    }

    // Light shafts: march from the eye to the first thing hit (or the surface).
    float tMax = min(dist, SHAFT_RANGE);
    if (dir.y > 1.0e-3) {
        tMax = min(tMax, max(WaterLevel, 0.0) / dir.y);
    }
    // Per-pixel offset of the sample positions (interleaved gradient noise): smooth, no banding.
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float shaft = 0.0;
    for (int i = 0; i < SHAFT_SAMPLES; i++) {
        float t = (float(i) + jitter) / float(SHAFT_SAMPLES) * tMax;
        vec3 q = dir * t;
        float qh = max(WaterLevel - q.y, 0.0);
        vec2 e = entryPoint(q + CameraLow, qh);
        // The smooth ripple height (blue channel), not the thin web: soft beams, no shimmer.
        float beam = smoothstep(0.5, 0.85, texture(Ripples, e / SHAFT_TILE + vec2(Time * 0.008, Time * 0.005)).b);
        shaft += beam * exp(-qh * 0.12);
    }
    shaft *= tMax / float(SHAFT_SAMPLES) * 0.035;
    // Beams are only as bright as the room above the water.
    float roomLight = clamp(luma(textureLod(SceneColor, vec2(0.5, 0.75), 7.0).rgb) * 2.0, 0.03, 1.0);
    vec3 beamColor = mix(SunColor, ShallowColor, 0.35);
    add += beamColor * shaft * roomLight * ShaftStrength;

    fragColor = vec4(max(add, 0.0), 1.0);
}
