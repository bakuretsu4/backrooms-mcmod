#include veil:space_helper
#include backrooms:level0_grid

// Level 0's finishing pass over the whole screen, drawn after the lamps
// (client/light/Level0LightRenderer). Veil adds its light buffer onto the picture
// (picture + light), and this pass REPLACES the light buffer, so it decides the final colour:
//     wanted = picture - removed bounce + lamps * corners, then shaped (see below)
//     light buffer = wanted - picture
// It reads the lamps drawn so far from a copy of the light buffer (LightSampler).
//  - Corners: lamp light is darkened in corners and at wall feet, from the wall map.
//  - Power: where the power is out (outages, the blackout wave, client/light/Level0Power) the
//    vanilla bounce dies with the lamps; corners darken it too. The bounce a pixel got is simply
//    the picture as Minecraft drew it (before Veil adds light it holds only the lightmap's light).
//  - Highlights: a soft shoulder, so walls right beside a panel peak around 0.85 on camera
//    instead of clipping to a flat white with a violet or green cast. All three colour channels
//    are scaled together, so hot wallpaper stays warm yellow. Glowing faces (the panel lenses)
//    are left alone: only they reach white.
//  - Darkness: the very darkest tones turn from the textures' brown toward the reference
//    footage's mauve, so darkness reads cold and empty instead of rust.
// The camcorder grades the picture afterwards (exposure, tints, contrast: AtmosphereGrade), so
// the shoulder and the dark tint work on where a pixel will END UP after that grade.

in vec2 texCoord;

uniform sampler2D AlbedoSampler;
uniform sampler2D NormalSampler;
uniform sampler2D DepthSampler;
uniform sampler2D MainSampler;
// The lamps drawn so far (a copy of Veil's light buffer).
uniform sampler2D LightSampler;
// Power over the area around you: 1 = lamps on, 0 = power out. One texel per 4 x 4 blocks.
uniform sampler2D PowerMap;
// xy = world x/z of the map's corner, z = its size in blocks, w = power beyond the map.
uniform vec4 PowerMapRect;
// x = corner darkening of the fill, y = of the lamp light, z = distance (blocks) where the
// corner darkening has faded out (the wall map ends there), w = distance from which the glow of
// ceiling panels follows the power map (nearer panels are switched dark one by one instead).
uniform vec4 Level0Shade;
// The camcorder's NightShot lifts every lightmap colour toward white by this much (0 when off).
// That lift is the camera's, not the lamps', so it is never taken away.
uniform float LightmapLift;
// The camcorder grade that follows: x = exposure, y = contrast (around 0.18); and its shadow tint.
uniform vec2 Grade;
uniform vec3 GradeShadowTint;
// x = shoulder knee, y = shoulder peak (brightest channel, on camera), z = dark tint strength,
// w = on-camera brightness below which the dark tint is complete (it fades out by 3x that).
uniform vec4 Level0Tone;

out vec4 fragColor;
// x = knee, y = cap of the lamp light reaching a surface (all lamps together, before its colour):
// one panel's pool (~0.95 right under it) passes untouched; where many panels overlap (bright
// districts) or a wall is right beside one, the sum eases toward the cap instead of piling up.
uniform vec2 Level0Irradiance;

// The reference's shadow colour (~18, 12, 13 on camera), scaled to brightness 1: mauve.
const vec3 MAUVE = vec3(1.35, 0.90, 0.98);

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float powerAt(vec2 xz) {
    vec2 mapUv = (xz - PowerMapRect.xy) / PowerMapRect.z;
    if (any(lessThan(mapUv, vec2(0.0))) || any(greaterThan(mapUv, vec2(1.0)))) {
        return PowerMapRect.w;
    }
    return texture(PowerMap, mapUv).r;
}

// Picture value -> brightness on camera after the grade, and back.
float toCamera(float v) {
    return (v * Grade.x - 0.18) * Grade.y + 0.18;
}

float fromCamera(float v) {
    return ((v - 0.18) / Grade.y + 0.18) / Grade.x;
}

// Soft highlight shoulder on camera: straight up to the knee, then easing toward the peak.
float shoulder(float v) {
    float knee = Level0Tone.x;
    float room = Level0Tone.y - knee;
    return v <= knee ? v : knee + room * (1.0 - exp(-(v - knee) / room));
}

void main() {
    vec2 uv = texCoord;
    vec3 lamps = texture(LightSampler, uv).rgb;
    vec4 albedo = texture(AlbedoSampler, uv);
    float depth = texture(DepthSampler, uv).r;
    if (albedo.a == 0.0 || depth >= 1.0) {
        fragColor = vec4(lamps, 1.0); // nothing of ours here: keep the light as it is
        return;
    }
    vec3 pos = screenToWorldSpace(uv, depth).xyz;
    vec3 normal = normalize((VeilCamera.IViewMat * vec4(texture(NormalSampler, uv).xyz, 0.0)).xyz);
    vec3 scene = texture(MainSampler, uv).rgb;
    // The lightmap's light on this pixel (the NightShot lift taken off), and how bright the
    // lightmap colour itself was (picture / surface colour, by brightness).
    vec3 fill = max(scene - albedo.rgb * LightmapLift, vec3(0.0));
    float lightmap = luma(fill) / max(luma(albedo.rgb), 0.02) / max(1.0 - LightmapLift, 0.01);
    float dist = length(pos - VeilCamera.CameraPosition);
    float power = powerAt(pos.xz);

    if (lightmap > 0.45) {
        // A glowing face (full-bright). Ceiling panels far away go dark with the power; exit
        // signs and other wall lamps are on batteries and keep glowing. No shoulder: lenses
        // may be white.
        float keepFill = normal.y < -0.5 ? mix(1.0, power, smoothstep(Level0Shade.w - 4.0, Level0Shade.w + 4.0, dist)) : 1.0;
        fragColor = vec4(lamps - fill * (1.0 - keepFill), 1.0);
        return;
    }

    float fade = smoothstep(Level0Shade.z, Level0Shade.z * 0.75, dist);
    float ao = level0Ao(pos + normal * 0.03, normal);
    float keepFill = power * mix(1.0, ao, Level0Shade.x * fade);
    float keepLight = mix(1.0, ao, Level0Shade.y * fade);
    // Too much lamp light piling up on one surface eases toward a cap (same colour).
    float reaching = luma(lamps) / max(luma(albedo.rgb), 0.02);
    float capKnee = Level0Irradiance.x;
    float capRoom = Level0Irradiance.y - capKnee;
    if (reaching > capKnee) {
        lamps *= (capKnee + capRoom * (1.0 - exp(-(reaching - capKnee) / capRoom))) / reaching;
    }
    vec3 wanted = scene - fill * (1.0 - keepFill) + lamps * keepLight;

    // Highlights: squeeze the brightest channel on camera, scale all three by the same amount.
    float top = max(wanted.r, max(wanted.g, wanted.b));
    float topCam = toCamera(top);
    if (topCam > Level0Tone.x) {
        wanted *= max(fromCamera(shoulder(topCam)), 0.0) / max(top, 1.0e-4);
    }

    // Darkness: below a faint grey, ease the colour toward mauve (same brightness on camera; the
    // grade's own shadow tint is divided out so it lands where we want).
    float y = luma(wanted);
    float dark = (1.0 - smoothstep(Level0Tone.w, Level0Tone.w * 3.0, toCamera(y))) * Level0Tone.z;
    if (dark > 0.0) {
        vec3 tinted = y * MAUVE / max(GradeShadowTint, vec3(0.05));
        tinted *= y / max(luma(tinted), 1.0e-5);
        wanted = mix(wanted, tinted, dark);
    }

    fragColor = vec4(wanted - scene, 1.0);
}
