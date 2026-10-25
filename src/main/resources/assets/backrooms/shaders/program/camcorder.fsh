#version 150

// Camcorder look for the Backrooms, matched to the reference footage.
//
// Order of operations (each numbered step is below):
//  1. Lens: strong barrel distortion (k = 0.20 in half-height units), cropped exactly so the
//     corners of the screen land on the corners of the picture: no black corners, no vignette.
//  2. VHS tape transport (wobble, line jitter, tracking bar, tears) - ONLY in glitch moments:
//     danger, level transitions, low battery. Normally the picture is clean.
//  3. Rotational motion blur along the camera's turn this frame, with mild radial chromatic
//     aberration (red and green pushed apart toward the screen sides).
//  4. Fallback flashlight (only when Veil's real lights are not running): a dim, flat disc with a
//     hard-ish edge, lit by how far each pixel is.
//  5. Bloom: only near-white pixels (light tubes) glow; walls don't.
//  6. Grade: neutral, black point lifted to blue-violet #020005, faint magenta on neutrals.
//     Alarm = faint red wash, NightShot = green infrared, Lightning = white flash.
//  7. Glitch-only extras (grain, specks, scanlines), wake-up eyelids, transition static.
//  Dream (Poolrooms hallucination, uniform Dream 0..1) is woven into steps 2, 3, 5 and 6: slow
//  watery ripples + breathing zoom, chromatic shimmer, a faint ghost image when strong, and a
//  cyan/saturated grade. Every part is scaled by Dream, so Dream = 0 changes nothing at all.

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D Bloom1Sampler;
uniform sampler2D Bloom2Sampler;
uniform sampler2D Bloom3Sampler;
uniform vec2 InSize;

uniform float Seconds;    // wall-clock seconds
uniform float Danger;     // 0..1, Bacteria nearby / chasing
uniform float Battery;    // 0..1
uniform float NightShot;  // 0 or 1
uniform float Flashlight; // 0..1 fallback flashlight strength (0 while Veil lights are active)
uniform float FlashX;     // where the (lagging) beam points, offset from centre, texture units
uniform float FlashY;
uniform float Signal;     // 0..1, nearness of the exit (calms the picture)
uniform float Transition; // 1 -> 0 over the noclip / escape transition
uniform float Wake;       // 1 -> 0 while waking up on the Level 0 carpet
uniform float Alarm;      // 0..1 red emergency pulse
uniform float Flash;      // 0..1 lightning flash
uniform float BlurX;      // motion blur streak for this frame, texture units
uniform float BlurY;
uniform float Grain;      // base film grain (0 = none, like the reference)
uniform float Dream;      // 0..1 Poolrooms hallucination (0 = picture exactly unchanged)
uniform float PoolWater;  // 1 in the Poolrooms: make pool water vivid cyan
uniform float Interference; // 0..1 Level 9: a Watcher frying the electronics (0 = no change at all)
uniform float FogDrain;   // 1 in Level 9: colour drains out of distant things (0 = no change at all)
uniform float Near;
uniform float Far;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

// Barrel strength measured from the reference: a straight wall edge bows ~26 px at 480p.
const float LENS_K = 0.20;
// Chromatic aberration: offset = CA_STRENGTH * |x - 0.5| (so 0 in the middle, 0.15% at the sides).
const float CA_STRENGTH = 0.003;
// How much of each of the three bloom sizes is added back.
const float BLOOM_STRENGTH = 0.45;
// Unlit black is not pure black on the tape, it is a very dark blue-violet.
const vec3 BLACK_POINT = vec3(2.0, 0.0, 5.0) / 255.0;
// Neutral (grey/white) pixels lose ~2.8% green -> a faint magenta cast, e.g. 216/222/217 -> 216/216/217.
const float MAGENTA_TINT = 0.028;
// Motion blur taps (jittered per pixel, so few taps still look smooth).
const int BLUR_TAPS = 12;
// Fallback flashlight: a disc of radius 0.47 x screen height; inner 2/3 flat, outer 1/3 soft.
const float FLASH_RADIUS = 0.47;
// Brightness the flashlight lifts dark surfaces to, at the beam's centre right in front of you.
// With the falloff below this gives ~40/255 at 2 blocks and ~22/255 at 4 blocks: a dim torch.
const float FLASH_LEVEL = 0.22;
const float FLASH_FALLOFF_BLOCKS = 3.2;
const float FLASH_RANGE_BLOCKS = 25.0;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise1(float x) {
    float i = floor(x);
    float f = fract(x);
    return mix(hash12(vec2(i, 0.0)), hash12(vec2(i + 1.0, 0.0)), smoothstep(0.0, 1.0, f));
}

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float linearDepth(float d) {
    float z = d * 2.0 - 1.0;
    return (2.0 * Near * Far) / (Far + Near - z * (Far - Near));
}

// One colour sample with chromatic aberration: red pushed outward, green inward, blue in place.
vec3 sampleCA(vec2 uv, vec2 caOffset) {
    float r = texture(DiffuseSampler, clamp(uv + caOffset, 0.0, 1.0)).r;
    float g = texture(DiffuseSampler, clamp(uv - caOffset, 0.0, 1.0)).g;
    float b = texture(DiffuseSampler, uv).b;
    return vec3(r, g, b);
}

void main() {
    float t = Seconds;
    float aspect = InSize.x / InSize.y;
    float lowBattery = Battery < 0.15 ? 1.0 : 0.0;
    // 0 = clean picture. Danger, transitions and a dying battery make the tape misbehave.
    float glitch = clamp(Danger * 0.9 + Transition * 1.2 + lowBattery * 0.35 - Signal * 0.15, 0.0, 1.5);

    // 1. Lens. p is the output pixel in half-height units (y -1..1, x -aspect..aspect).
    //    Barrel: sample from p * (1 + k|p|^2). Dividing by the corner's factor (1 + k(aspect^2+1))
    //    maps the screen corner exactly onto the picture corner, so every pixel has image.
    vec2 p = (texCoord - 0.5) * 2.0 * vec2(aspect, 1.0);
    float crop = 1.0 / (1.0 + LENS_K * (aspect * aspect + 1.0));
    vec2 src = p * (1.0 + LENS_K * dot(p, p)) * crop;
    vec2 uv = src / vec2(aspect, 1.0) * 0.5 + 0.5;

    // 2. Tape transport, glitch moments only.
    if (glitch > 0.001) {
        float band = floor(uv.y * 300.0);
        uv.y += (noise1(t * 0.8) - 0.5) * 0.002 * glitch;
        uv.x += (hash12(vec2(band, floor(t * 30.0))) - 0.5) * 0.0015 * glitch * (1.0 + glitch * 6.0);
        float bar = smoothstep(0.06, 0.0, abs(uv.y - fract(t * 0.11))) * glitch;
        uv.x += bar * 0.02 * (hash12(vec2(band, t)) - 0.3);
        if (texCoord.y < 0.02 * glitch) {
            uv.x += 0.015 * sin(texCoord.y * 900.0 + t * 40.0) + 0.01; // head-switching noise
        }
        if (glitch > 0.25) {
            float tearRow = floor(uv.y * 36.0);
            float tear = step(1.0 - glitch * 0.09, hash12(vec2(tearRow, floor(t * 14.0))));
            uv.x += tear * (hash12(vec2(t, tearRow)) - 0.5) * 0.09 * glitch;
        }
    }
    // Dream: looking at the world through slowly moving water.
    float dream = clamp(Dream, 0.0, 1.0);
    if (dream > 0.0) {
        // Gentle "breathing": a slow zoom by at most ~1.2%, only ever inward (no gaps at the edges).
        float breathe = 1.0 - dream * 0.012 * (0.5 + 0.5 * sin(t * 0.8));
        uv = (uv - 0.5) * breathe + 0.5;
        // Slow wavy refraction: two soft ripples per axis, at most ~0.25% of the screen.
        vec2 ripple = vec2(sin(uv.y * 9.0 + t * 1.1) + 0.5 * sin(uv.y * 17.0 - t * 0.7),
                           cos(uv.x * 8.0 + t * 0.9) + 0.5 * cos(uv.x * 15.0 + t * 1.3));
        uv += ripple * 0.0017 * dream;
    }
    // Waking up: everything swims.
    uv += vec2(sin(t * 1.7 + uv.y * 6.0), cos(t * 1.3 + uv.x * 5.0)) * 0.006 * Wake;
    // Watcher interference: horizontal bands of the picture tear sideways, more the closer it is.
    if (Interference > 0.0) {
        float row = floor(texCoord.y * 90.0);
        float slot = floor(t * 20.0);
        float tearOn = step(1.0 - Interference * 0.3, hash12(vec2(row, slot)));
        uv.x += tearOn * (hash12(vec2(slot, row + 7.0)) - 0.5) * 0.12 * Interference;
        uv.x += (noise1(t * 3.0 + texCoord.y * 40.0) - 0.5) * 0.004 * Interference; // fine wobble
    }
    uv = clamp(uv, vec2(0.0), vec2(1.0));

    // 3. Motion blur + chromatic aberration.
    vec2 fromCentre = uv - 0.5;
    float centreDist = length(fromCentre);
    vec2 radial = centreDist > 1.0e-5 ? fromCentre / centreDist : vec2(0.0);
    vec2 caOffset = radial * CA_STRENGTH * abs(uv.x - 0.5) * (1.0 + glitch * 2.0);
    if (dream > 0.0) {
        // Dream: soft chromatic shimmer, the colour fringes slowly breathe and wander.
        caOffset += radial * 0.0025 * dream * (0.6 + 0.4 * sin(t * 1.3 + centreDist * 12.0))
                + vec2(sin(t * 0.6), cos(t * 0.5)) * 0.0006 * dream;
    }

    vec2 blurVec = vec2(BlurX, BlurY);
    float wakeBlur = Wake * 0.012; // out-of-focus eyes while waking up
    vec3 col;
    if (length(blurVec * InSize) < 0.75 && wakeBlur <= 0.0) {
        col = sampleCA(uv, caOffset); // camera still: one sharp sample
    } else {
        // Taps spread evenly along the streak, shifted by a per-pixel random amount so the gaps
        // between taps turn into fine noise instead of visible copies.
        float jitter = hash12(gl_FragCoord.xy + fract(t) * 61.0);
        vec3 acc = vec3(0.0);
        for (int i = 0; i < BLUR_TAPS; i++) {
            float f = (float(i) + jitter) / float(BLUR_TAPS);
            float angle = float(i) * 2.39996; // golden angle spiral for the wake-up defocus
            vec2 defocus = vec2(cos(angle) / aspect, sin(angle)) * wakeBlur * sqrt(f);
            acc += sampleCA(clamp(uv + blurVec * (f - 0.5) + defocus, 0.0, 1.0), caOffset);
        }
        col = acc / float(BLUR_TAPS);
    }

    // 4. Fallback flashlight. It lifts dark surfaces toward a target brightness that depends on
    //    distance, keeping texture detail by dividing by the local average brightness.
    if (Flashlight > 0.0) {
        float depth = texture(DepthSampler, uv).r;
        float dist = linearDepth(depth);
        float sky = step(0.99999, depth);
        // The disc is drawn in output screen space (heights = 1) so it stays a circle.
        vec2 beamCentre = vec2(0.5) + vec2(FlashX, FlashY) / crop;
        float r = length((texCoord - beamCentre) * vec2(aspect, 1.0));
        float disc = smoothstep(FLASH_RADIUS, FLASH_RADIUS * (2.0 / 3.0), r); // zero spill outside
        float hotspot = 1.0 + 0.3 * smoothstep(FLASH_RADIUS * 0.3, 0.0, r);   // faint 14-degree core
        float d = dist / FLASH_FALLOFF_BLOCKS;
        float atten = smoothstep(FLASH_RANGE_BLOCKS, FLASH_RANGE_BLOCKS * 0.72, dist) / (1.0 + d * d);
        float target = FLASH_LEVEL * disc * hotspot * atten * (1.0 - sky) * Flashlight;
        vec2 o = oneTexel * 6.0;
        float localLum = 0.25 * (luma(texture(DiffuseSampler, uv + vec2(o.x, o.y)).rgb)
                + luma(texture(DiffuseSampler, uv - vec2(o.x, o.y)).rgb)
                + luma(texture(DiffuseSampler, uv + vec2(o.x, -o.y)).rgb)
                + luma(texture(DiffuseSampler, uv - vec2(o.x, -o.y)).rgb));
        localLum = mix(luma(col), localLum, 0.6);
        col *= clamp(target / max(localLum, 0.004), 1.0, 40.0);
    }

    // Dream, strong: a faint double image drifts in and out every ~17 seconds.
    float ghost = smoothstep(0.6, 1.0, dream) * pow(max(sin(t * 0.37), 0.0), 3.0) * 0.18;
    if (ghost > 0.001) {
        vec2 ghostUv = clamp(uv + vec2(0.012 * sin(t * 0.23), 0.006 * cos(t * 0.31)), 0.0, 1.0);
        col = mix(col, texture(DiffuseSampler, ghostUv).rgb, ghost);
    }

    // 5. Bloom from the three blur sizes. Only light fixtures made it into these buffers.
    col += (texture(Bloom1Sampler, uv).rgb + texture(Bloom2Sampler, uv).rgb + texture(Bloom3Sampler, uv).rgb) * BLOOM_STRENGTH;
    col = clamp(col, 0.0, 1.0);

    // 6. Grade.
    // Level 9 fog drains the colour: near things keep it, beyond ~25 blocks they fade toward grey.
    if (FogDrain > 0.0) {
        float sceneDepth = texture(DepthSampler, uv).r;
        float dist = sceneDepth >= 0.99999 ? 1000.0 : linearDepth(sceneDepth);
        float drain = FogDrain * smoothstep(8.0, 28.0, dist) * 0.92;
        col = mix(col, vec3(luma(col)), drain);
    }
    if (Alarm > 0.0) {
        float l = luma(col);
        col = mix(col, vec3(l * 1.2 + 0.02, l * 0.25, l * 0.22), Alarm * 0.35);
    }
    if (PoolWater > 0.0) {
        // Poolrooms: pool water reads vivid cyan in the footage even in dim rooms (Veil's lights
        // don't reach translucent water). Only pixels that are already cyan-ish get lifted.
        float cyan = clamp((min(col.g, col.b) - col.r) * 3.0, 0.0, 1.0)
                * (1.0 - clamp(abs(col.g - col.b) * 3.0, 0.0, 1.0));
        vec3 vivid = vec3(col.r * 0.55, col.g * 1.45 + 0.025, col.b * 1.45 + 0.035);
        col = clamp(mix(col, vivid, cyan * PoolWater), 0.0, 1.0);
    }
    if (NightShot > 0.5) {
        float l = pow(luma(col), 0.85) * 1.1;
        col = vec3(l * 0.42, l, l * 0.48);
    }
    if (dream > 0.0) {
        // Dream grade: a little more saturated, drifting toward pool-water cyan, soft glow on highlights.
        float drift = 0.5 + 0.5 * sin(t * 0.25);
        float dl = luma(col);
        col = max(mix(vec3(dl), col, 1.0 + dream * (0.15 + 0.15 * drift)), 0.0);
        col *= mix(vec3(1.0), vec3(0.93, 1.02, 1.06), dream * (0.6 + 0.4 * drift));
        col += vec3(0.55, 0.85, 1.0) * smoothstep(0.55, 1.0, dl) * 0.05 * dream;
        col = clamp(col, 0.0, 1.0);
    }
    // Faint magenta on neutrals: the less saturated a pixel, the more green it loses.
    float hi = max(col.r, max(col.g, col.b));
    float lo = min(col.r, min(col.g, col.b));
    float saturation = (hi - lo) / max(hi, 1.0e-4);
    col.g *= 1.0 - MAGENTA_TINT * (1.0 - smoothstep(0.05, 0.3, saturation));
    // Lift the black point to #020005 (pure black becomes that colour, white stays white).
    col = BLACK_POINT + col * (1.0 - BLACK_POINT);
    // Lightning: the whole frame blows out white for an instant.
    col = mix(col, vec3(1.0), clamp(Flash, 0.0, 1.0) * 0.85);

    // 7. Grain and tape damage: off on a clean picture.
    float grainAmount = Grain + glitch * 0.10 + NightShot * 0.06 + Wake * 0.03;
    if (grainAmount > 0.0) {
        float scale = max(InSize.y / 720.0, 1.0);
        float grain = hash12(floor(gl_FragCoord.xy / scale) + fract(t * 7.13) * vec2(431.0, 177.0)) - 0.5;
        col += grain * grainAmount * (0.4 + luma(col));
    }
    if (glitch > 0.001) {
        col *= 1.0 - 0.06 * glitch * (0.5 + 0.5 * sin(uv.y * 300.0 * 6.2831853)); // scanlines
        col += step(0.9997, hash12(gl_FragCoord.xy + floor(t * 30.0) * 17.0)) * glitch; // white dropout specks
        if (texCoord.y < 0.02 * glitch) {
            col = mix(col, vec3(hash12(gl_FragCoord.xy + t)), 0.3);
        }
    }
    // Watcher interference: burnt colours, coloured static and whole lines dropping out.
    if (Interference > 0.0) {
        float k = Interference;
        // Colour burn: reds blow out, blues sink, the picture tints sick orange-magenta.
        vec3 burnt = vec3(col.r * 1.3 + 0.03, col.g * 0.78, col.b * 0.55 + col.r * 0.2);
        col = mix(col, burnt, k * 0.6);
        // Coloured static: each channel gets its own noise, so it sparkles red/green/blue.
        vec2 cell = floor(gl_FragCoord.xy / max(InSize.y / 360.0, 1.0)) + fract(t * 11.3) * vec2(173.0, 311.0);
        vec3 staticRgb = vec3(hash12(cell), hash12(cell + 17.0), hash12(cell + 41.0)) - 0.5;
        col += staticRgb * 0.45 * k * k;
        // Dropouts: a few whole lines flash white or black for a frame.
        float line = floor(texCoord.y * 240.0);
        float drop = step(1.0 - 0.05 * k, hash12(vec2(line, floor(t * 24.0))));
        col = mix(col, vec3(step(0.5, hash12(vec2(line, t)))), drop * 0.85);
    }

    // Eyelids: opening slowly, with a couple of heavy blinks.
    if (Wake > 0.0) {
        float blink = smoothstep(0.02, 0.0, abs(fract(Wake * 2.3) - 0.5) - 0.42);
        float open = clamp(1.0 - Wake * 1.15, 0.0, 1.0) * (1.0 - blink * 0.8);
        float lid = smoothstep(open * 0.62 + 0.02, open * 0.62 - 0.12, abs(texCoord.y - 0.5));
        col *= lid * (1.0 - Wake * 0.5);
    }

    if (Transition > 0.0) {
        float staticNoise = hash12(gl_FragCoord.xy * 0.5 + t * 91.0);
        float roll = step(0.5, fract(texCoord.y * 3.0 + t * 2.0));
        col = mix(col, vec3(staticNoise * (0.6 + 0.4 * roll)), clamp(Transition * 1.1, 0.0, 0.9));
    }

    if (lowBattery > 0.5) {
        col *= 1.0 - step(0.965, hash12(vec2(floor(t * 12.0), 3.0))) * 0.55;
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
