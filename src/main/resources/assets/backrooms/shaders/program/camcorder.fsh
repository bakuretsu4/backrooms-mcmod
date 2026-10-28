#version 150

// Camcorder look for the Backrooms, matched to the reference footage.
//
// Order of operations (each numbered step is below):
//  1. Lens: very gentle barrel distortion (k = 0.04 in half-height units), cropped exactly so the
//     corners of the screen land on the corners of the picture: no black corners, no vignette.
//     (Stronger barrel magnified the middle and stretched the edges, so everything seemed to
//     happen in the centre of the screen.)
//  2. VHS tape transport: a barely-there line jitter; wobble, tracking bar and tears in glitch moments
//     (danger, transitions, low battery); white dropout specks and the head-switching strip only in heavy ones.
//  3. Rotational motion blur along the camera's turn this frame. Chromatic aberration (red and
//     green pushed apart) only while the tape misbehaves (danger, low sanity, glitches,
//     transitions) or a Smiler is close: a clean picture has no colour fringes.
//  4. Fallback flashlight (only when Veil's real lights are not running): a dim, flat disc with a
//     hard-ish edge, lit by how far each pixel is.
//  5. Bloom: only near-white pixels (light tubes) glow; walls don't. Then the level's soft glow:
//     everything bright bleeds a little into its surroundings (old optics), all over the picture.
//  6. Atmosphere and grade, the same everywhere on screen (driven by AtmosphereGrade per level):
//     contact shadows in corners and where walls meet floors (screen-space ambient occlusion),
//     coloured haze that thickens with real distance, exposure/contrast/saturation, split-toned
//     shadows and highlights, a lifted tinted black, and a very slow auto-iris "breath".
//     Alarm = faint red wash, NightShot = green infrared, Lightning = white flash.
//  7. Glitch-only extras (grain, specks, scanlines), transition static.
//  Dream (Poolrooms hallucination, uniform Dream 0..1) is woven into steps 2, 3, 5 and 6: slow
//  watery ripples + breathing zoom, chromatic shimmer, a faint ghost image when strong, and a
//  cyan/saturated grade. Every part is scaled by Dream, so Dream = 0 changes nothing at all.
//  Sanity (uniform Sanity 0..1, 0 = sane) adds a little tape jitter (step 2), a slight
//  desaturation and edge darkening that pulses with the heartbeat (step 6). Sanity = 0, SanityPulse
//  and SanityGlitch = 0 change nothing at all.
//  Underwater (uniform 0..1, eyes in the pool water of the Poolrooms / Level 37) makes the picture
//  sway gently (step 2) and flicker faintly with the light on the surface (step 6); 0 = unchanged.
//  The pool water itself is drawn by the mod's own water renderer before this effect runs.

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
uniform float Alarm;      // 0..1 red emergency pulse
uniform float Flash;      // 0..1 lightning flash
uniform float BlurX;      // motion blur streak for this frame, texture units
uniform float BlurY;
uniform float Grain;      // base film grain (0 = none, like the reference)
uniform float Dream;      // 0..1 Poolrooms hallucination (0 = picture exactly unchanged)
uniform float Underwater; // 0..1 eyes under the pool water (0 = picture exactly unchanged)
uniform float Interference; // 0..1 Level 9: a Watcher frying the electronics (0 = no change at all)
uniform float FogDrain;   // 1 in Level 9: colour drains out of distant things (0 = no change at all)
uniform float Sanity;     // 0..1 how broken your mind is (0 = picture exactly unchanged)
uniform float SanityPulse;  // 0..1 heartbeat pulse for the edge darkening (only matters when Sanity > 0)
uniform float SanityGlitch; // 0..1 a tiny tape glitch when a hallucination vanishes (0 = no change)
uniform float Chroma;       // 0..1 a Smiler close by: strong colour fringing (0 = no change at all)
uniform float BloomStrength; // 1 = glow on; 0 on LOW quality, whose post chain skips the blur passes
// The level's mood (AtmosphereGrade). Defaults leave the picture as it was.
uniform float Exposure;     // overall brightness multiplier
uniform float Contrast;     // around mid-grey
uniform float Saturation;   // 1 = unchanged, 0 = black and white
uniform float ShadowR;      // tint multiplier for the dark parts
uniform float ShadowG;
uniform float ShadowB;
uniform float HighlightR;   // tint multiplier for the bright parts
uniform float HighlightG;
uniform float HighlightB;
uniform float LiftR;        // the darkest colour the tape shows (black is never quite black)
uniform float LiftG;
uniform float LiftB;
uniform float HazeR;        // colour of the air
uniform float HazeG;
uniform float HazeB;
uniform float HazeStart;    // blocks before the haze begins
uniform float HazeDensity;  // how fast it thickens per block
uniform float HazeMax;      // the most it can cover (0..1), so far walls never vanish entirely
uniform float ContactShadow; // 0..1 strength of the corner / contact shadows
uniform float Glow;         // 0..1 soft glow around bright areas
uniform float Breath;       // tiny slow exposure drift (0.01 = 1%)
uniform float Near;
uniform float Far;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

// Barrel strength: enough for a camcorder lens feel, gentle enough that the picture stays even.
const float LENS_K = 0.04;
// Chromatic aberration from a misbehaving tape: zero on a clean picture (the owner found the
// always-on fringe on every edge too much), it only appears with the tape damage that danger,
// low sanity, glitches and transitions add (see caOffset in main). This is its size per unit of
// that extra damage: a monster nearby (+0.3) gives a faint fringe (about 2/3 of the old resting one).
const float CA_STRENGTH = 0.0016;
const float CA_PER_DAMAGE = 2.5;
// Worn tape, always on but faint: a hint of sideways line jitter and faint grain. (Stronger made
// the picture look busy and smeared.)
const float TAPE_BASE = 0.12;
// How much of each of the three bloom sizes is added back.
const float BLOOM_STRENGTH = 0.45;
// (The darkest colour now comes from the level's Lift: by default the tape's blue-violet #020005.)
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

// Distance (blocks) of the surface seen at uv; very far for the sky.
float sceneDistance(vec2 uv) {
    float d = texture(DepthSampler, uv).r;
    if (d >= 0.99999) {
        return 1000.0;
    }
    float z = d * 2.0 - 1.0;
    return (2.0 * Near * Far) / (Far + Near - z * (Far - Near));
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
    // A monster nearby only roughs the tape up a little (max ~0.3): enough to warn you, not enough
    // to hide what you're looking at. Level transitions keep the full-strength glitch.
    float glitch = clamp(Danger * 0.3 + Transition * 1.2 + lowBattery * 0.35 - Signal * 0.15 + SanityGlitch * 0.6, 0.0, 1.5);
    // The worn-tape look (faint line jitter, grain) never goes away; glitch moments add to it,
    // and only they bring colour fringes. The head-switching strip, tracking bar, tears, specks
    // and scanlines are glitch-only.
    // Low sanity wears the tape a little more (adds to tape only, never to the glitch extras).
    float sanity = clamp(Sanity, 0.0, 1.0);
    float tape = clamp(TAPE_BASE + glitch + sanity * 0.2, 0.0, 1.5);
    // Only the big glitches (noclip transitions, a dying battery) get the noisy extras: white
    // dropout specks and the head-switching strip. A monster nearby (glitch up to 0.3) does not.
    float heavy = max(glitch - 0.3, 0.0);

    // 1. Lens. p is the output pixel in half-height units (y -1..1, x -aspect..aspect).
    //    Barrel: sample from p * (1 + k|p|^2). Dividing by the corner's factor (1 + k(aspect^2+1))
    //    maps the screen corner exactly onto the picture corner, so every pixel has image.
    vec2 p = (texCoord - 0.5) * 2.0 * vec2(aspect, 1.0);
    float crop = 1.0 / (1.0 + LENS_K * (aspect * aspect + 1.0));
    vec2 src = p * (1.0 + LENS_K * dot(p, p)) * crop;
    vec2 uv = src / vec2(aspect, 1.0) * 0.5 + 0.5;

    // 2. Tape transport. A faint line jitter is always there (tape); the head-switching strip at
    //    the bottom, the rolling tracking bar and tears only show in glitch moments.
    float band = floor(uv.y * 300.0);
    uv.y += (noise1(t * 0.8) - 0.5) * 0.002 * glitch;
    uv.x += (hash12(vec2(band, floor(t * 30.0))) - 0.5) * 0.0015 * tape * (1.0 + tape * 6.0);
    if (texCoord.y < 0.02 * heavy) {
        uv.x += 0.015 * sin(texCoord.y * 900.0 + t * 40.0) + 0.01; // head-switching noise
    }
    if (glitch > 0.001) {
        float bar = smoothstep(0.06, 0.0, abs(uv.y - fract(t * 0.11))) * glitch;
        uv.x += bar * 0.02 * (hash12(vec2(band, t)) - 0.3);
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
    // Under water: the picture sways gently, as if seen through moving water. A tiny zoom-in
    // first, so the sway never pulls an edge into view.
    float under = clamp(Underwater, 0.0, 1.0);
    if (under > 0.0) {
        uv = (uv - 0.5) * (1.0 - 0.006 * under) + 0.5;
        uv += vec2(sin(uv.y * 11.0 + t * 1.7) + 0.5 * sin(uv.y * 23.0 - t * 2.3),
                   cos(uv.x * 9.0 + t * 1.4) + 0.5 * cos(uv.x * 19.0 + t * 2.1)) * 0.0011 * under;
    }
    // Watcher interference: horizontal bands of the picture tear sideways, more the closer it is.
    if (Interference > 0.0) {
        float row = floor(texCoord.y * 90.0);
        float slot = floor(t * 20.0);
        float tearOn = step(1.0 - Interference * 0.12, hash12(vec2(row, slot)));
        uv.x += tearOn * (hash12(vec2(slot, row + 7.0)) - 0.5) * 0.05 * Interference;
        uv.x += (noise1(t * 3.0 + texCoord.y * 40.0) - 0.5) * 0.004 * Interference; // fine wobble
    }
    uv = clamp(uv, vec2(0.0), vec2(1.0));

    // 3. Motion blur + chromatic aberration.
    vec2 fromCentre = uv - 0.5;
    float centreDist = length(fromCentre);
    vec2 radial = centreDist > 1.0e-5 ? fromCentre / centreDist : vec2(0.0);
    // VHS chroma recorded out of step with the picture: the colour sits a hair to the side of the
    // edges (a horizontal shift) plus a touch of radial lens fringe. Only the tape damage on top of
    // the resting wear counts, so a clean picture has no fringes at all.
    float tapeDamage = max(tape - TAPE_BASE, 0.0);
    vec2 caOffset = (vec2(CA_STRENGTH * 0.4, 0.0) + radial * CA_STRENGTH * centreDist * 1.4) * (tapeDamage * CA_PER_DAMAGE);
    if (Chroma > 0.0) {
        // Smiler close by: the colours tear apart, worst at the edges, wobbling a little.
        caOffset += radial * Chroma * (0.003 + 0.012 * centreDist) * (0.85 + 0.15 * sin(t * 17.0));
    }
    if (dream > 0.0) {
        // Dream: soft chromatic shimmer, the colour fringes slowly breathe and wander.
        caOffset += radial * 0.0025 * dream * (0.6 + 0.4 * sin(t * 1.3 + centreDist * 12.0))
                + vec2(sin(t * 0.6), cos(t * 0.5)) * 0.0006 * dream;
    }

    vec2 blurVec = vec2(BlurX, BlurY);
    vec3 col;
    if (length(blurVec * InSize) < 0.75) {
        col = sampleCA(uv, caOffset); // camera still: one sharp sample
    } else {
        // Taps spread evenly along the streak, shifted by a per-pixel random amount so the gaps
        // between taps turn into fine noise instead of visible copies.
        float jitter = hash12(gl_FragCoord.xy + fract(t) * 61.0);
        vec3 acc = vec3(0.0);
        for (int i = 0; i < BLUR_TAPS; i++) {
            float f = (float(i) + jitter) / float(BLUR_TAPS);
            acc += sampleCA(clamp(uv + blurVec * (f - 0.5), 0.0, 1.0), caOffset);
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

    // 6a. Contact shadows (screen-space ambient occlusion), the same all over the picture. For each
    //     of eight directions we look a little way to both sides. On a flat surface 1/distance
    //     changes evenly across the screen, so the two neighbours average out to the middle; in an
    //     inside corner (wall meets floor, a room's corners, under a desk) both neighbours are
    //     closer, so the middle is darkened a little. Big jumps (an object in front of a far wall)
    //     fade out of it. The directions are fixed and every term is smooth: random per-pixel
    //     directions with a hard cut-off made grainy, crawling speckle on walls near a corner.
    float dist0 = sceneDistance(uv);
    if (ContactShadow > 0.0 && dist0 < 48.0) {
        // About half a block on screen at this distance (1.4 ~ 2 * tan(35 degrees) for a 70 degree view).
        float radiusPx = clamp(0.5 * InSize.y / (1.4 * dist0), 3.0, 40.0);
        float inv0 = 1.0 / dist0;
        float occ = 0.0;
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.3926991; // 22.5 degree steps (both sides cover 360)
            vec2 o = vec2(cos(a), sin(a)) * radiusPx * (0.7 + 0.3 * float(i & 1)) / InSize;
            float d1 = sceneDistance(clamp(uv + o, 0.0, 1.0));
            float d2 = sceneDistance(clamp(uv - o, 0.0, 1.0));
            float valid = smoothstep(1.5, 0.6, abs(d1 - dist0)) * smoothstep(1.5, 0.6, abs(d2 - dist0));
            float concave = (0.5 * (1.0 / d1 + 1.0 / d2) - inv0) / inv0;
            occ += smoothstep(0.0, 0.18, concave) * valid;
        }
        occ /= 8.0;
        col *= 1.0 - ContactShadow * occ * smoothstep(48.0, 24.0, dist0);
    }

    // 5. Bloom from the three blur sizes. Only light fixtures made it into these buffers.
    col += (texture(Bloom1Sampler, uv).rgb + texture(Bloom2Sampler, uv).rgb + texture(Bloom3Sampler, uv).rgb) * BLOOM_STRENGTH * BloomStrength;
    col = clamp(col, 0.0, 1.0);

    // 6b. Glow: bright areas bleed softly into their neighbourhood (old, slightly hazy optics), and
    //     the whole picture is a touch softer. Two rings of samples around every pixel, so it is the
    //     same everywhere on screen.
    if (Glow > 0.0) {
        vec2 gr = vec2(0.016 / aspect, 0.016);
        vec3 acc = vec3(0.0);
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.7853982 + 0.39;
            vec2 dir = vec2(cos(a), sin(a));
            acc += texture(DiffuseSampler, clamp(uv + dir * gr, 0.0, 1.0)).rgb;
            acc += texture(DiffuseSampler, clamp(uv + dir * gr * 2.3, 0.0, 1.0)).rgb;
        }
        vec3 soft = acc / 16.0;
        col += max(soft - 0.32, 0.0) * (Glow * 0.9);
        col = mix(col, max(col, soft), Glow * 0.25);
        col = clamp(col, 0.0, 1.0);
    }

    // 6c. Haze: the air between you and what you see has a colour, and the further away a surface
    //     is, the more of that air there is. Real distance, so it is even across the screen.
    if (HazeMax > 0.0) {
        float haze = HazeMax * (1.0 - exp(-max(dist0 - HazeStart, 0.0) * HazeDensity));
        col = mix(col, vec3(HazeR, HazeG, HazeB), haze);
    }

    // 6d. The level's grade: exposure (drifting very slowly, like an auto-iris that never settles),
    //     shadows and highlights tinted separately, saturation, then contrast around a dark mid-grey
    //     (so dark rooms don't get their shadows crushed).
    col *= Exposure * (1.0 + Breath * (0.6 * sin(t * 0.31) + 0.4 * sin(t * 0.73 + 1.3)));
    float gradeLuma = luma(col);
    col *= mix(vec3(ShadowR, ShadowG, ShadowB), vec3(HighlightR, HighlightG, HighlightB), smoothstep(0.04, 0.55, gradeLuma));
    col = mix(vec3(luma(col)), col, Saturation);
    col = clamp((col - 0.18) * Contrast + 0.18, 0.0, 1.0);

    // 6e. Event overlays on top of the level's grade, so their colours stay true.
    // Level 9 fog drains the colour: near things keep it, beyond ~25 blocks they fade toward grey.
    if (FogDrain > 0.0) {
        float drain = FogDrain * smoothstep(8.0, 28.0, dist0) * 0.92;
        col = mix(col, vec3(luma(col)), drain);
    }
    if (Alarm > 0.0) {
        float l = luma(col);
        col = mix(col, vec3(l * 1.2 + 0.02, l * 0.25, l * 0.22), Alarm * 0.35);
    }
    if (under > 0.0) {
        // Under water: the light from the rippling surface breathes a little.
        col *= 1.0 + under * 0.035 * (noise1(t * 2.6) - 0.5 + 0.5 * (noise1(t * 5.3 + 11.0) - 0.5));
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
    if (sanity > 0.0) {
        // Low sanity: colour slowly drains away, and the edges of the picture close in, throbbing
        // with your heartbeat (strongest in the corners, nothing in the middle).
        col = mix(col, vec3(luma(col)), sanity * 0.3);
        float edge = smoothstep(0.45, 1.35, length((texCoord - 0.5) * 2.0));
        col *= 1.0 - sanity * (0.4 + 0.2 * clamp(SanityPulse, 0.0, 1.0)) * edge;
    }
    // Faint magenta on neutrals: the less saturated a pixel, the more green it loses.
    float hi = max(col.r, max(col.g, col.b));
    float lo = min(col.r, min(col.g, col.b));
    float saturation = (hi - lo) / max(hi, 1.0e-4);
    col.g *= 1.0 - MAGENTA_TINT * (1.0 - smoothstep(0.05, 0.3, saturation));
    // Lift the black point to the level's darkest colour (pure black becomes it, white stays white).
    vec3 lift = vec3(LiftR, LiftG, LiftB);
    col = lift + col * (1.0 - lift);
    // Lightning: the whole frame blows out white for an instant.
    col = mix(col, vec3(1.0), clamp(Flash, 0.0, 1.0) * 0.85);

    // 7. Grain and tape damage.
    float grainAmount = Grain + tape * 0.10 + NightShot * 0.06;
    if (grainAmount > 0.0) {
        float scale = max(InSize.y / 720.0, 1.0);
        float grain = hash12(floor(gl_FragCoord.xy / scale) + fract(t * 7.13) * vec2(431.0, 177.0)) - 0.5;
        col += grain * grainAmount * (0.4 + luma(col));
    }
    // White dropout specks: only in heavy glitches (a clean picture has none).
    col += step(0.9998, hash12(gl_FragCoord.xy + floor(t * 30.0) * 17.0)) * heavy;
    if (texCoord.y < 0.02 * heavy) {
        col = mix(col, vec3(hash12(gl_FragCoord.xy + t)), 0.3); // head-switching static strip
    }
    if (glitch > 0.001) {
        col *= 1.0 - 0.06 * glitch * (0.5 + 0.5 * sin(uv.y * 300.0 * 6.2831853)); // scanlines
    }
    // Watcher interference: burnt colours, coloured static and whole lines dropping out.
    if (Interference > 0.0) {
        float k = Interference;
        // Colour burn: reds blow out, blues sink, the picture tints sick orange-magenta.
        vec3 burnt = vec3(col.r * 1.3 + 0.03, col.g * 0.78, col.b * 0.55 + col.r * 0.2);
        col = mix(col, burnt, k * 0.35);
        // Coloured static: each channel gets its own noise, so it sparkles red/green/blue.
        vec2 cell = floor(gl_FragCoord.xy / max(InSize.y / 360.0, 1.0)) + fract(t * 11.3) * vec2(173.0, 311.0);
        vec3 staticRgb = vec3(hash12(cell), hash12(cell + 17.0), hash12(cell + 41.0)) - 0.5;
        col += staticRgb * 0.15 * k * k;
        // Dropouts: a few whole lines flash white or black for a frame.
        float line = floor(texCoord.y * 240.0);
        float drop = step(1.0 - 0.015 * k, hash12(vec2(line, floor(t * 24.0))));
        col = mix(col, vec3(step(0.5, hash12(vec2(line, t)))), drop * 0.45);
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
