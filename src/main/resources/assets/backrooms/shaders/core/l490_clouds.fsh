#version 150

// Level 490's clouds as volumes (client/Level490VolumeClouds), drawn in the sky pass before the
// terrain, so islands, chains and rain stay in front. For every pixel the view ray is followed
// through two slabs of cloud:
//  - the cloud sea, y 40..150: billowing heads with round tops, darker underneath;
//  - the storm, y 600..760: a ceiling hanging down in lumps over you.
// The density comes from tileable 3D noise (Noise: 64 slices of 64 x 64 packed 8 x 8; R the
// shapes, G the detail eating their edges), drifting with the wind. Light: the moon (a few looks
// toward it through the cloud), a dim ambient from above, lightning glowing inside the clouds round
// the latest strike. Far off everything fades into the haze. Output (colour x coverage, coverage),
// blended with ONE, ONE_MINUS_SRC_ALPHA.

uniform sampler2D Noise;
uniform mat4 ModelViewMat;   // camera rotation (the sky pass's matrix)
uniform mat4 InvProjMat;
uniform vec2 ScreenSize;
uniform vec3 Eye;
uniform vec3 Wind;           // how far the wind has carried the clouds (world blocks)
uniform float Time;
uniform vec3 MoonDir;
uniform float MoonLight;     // 0..1 how much of the moon shows through the storm right now
uniform vec3 HazeColor;
uniform vec4 Strike;         // the latest lightning: x, y, z, how bright (0 = none)
uniform float Flash;         // how much the lightning lights everything (0..1)

out vec4 fragColor;

const float SEA_BOTTOM = 40.0;
const float SEA_TOP = 150.0;
const float STORM_BOTTOM = 600.0;
const float STORM_TOP = 760.0;
const float FAR = 3600.0;
const int STEPS = 40;

// ---------------------------------------------------------------- noise

vec4 slice(float z, vec2 uv) {
    vec2 cell = vec2(mod(z, 8.0), floor(z / 8.0));
    // Kept half a texel inside each slice so the slices don't bleed into each other.
    vec2 inner = (fract(uv) * 63.0 + 0.5) / 64.0;
    return texture(Noise, (cell + inner) / 8.0);
}

// p in noise texels (the noise repeats every 64).
vec4 cloudNoise(vec3 p) {
    float z = mod(p.z, 64.0);
    float z0 = floor(z);
    float z1 = mod(z0 + 1.0, 64.0);
    vec2 uv = p.xy / 64.0;
    return mix(slice(z0, uv), slice(z1, uv), z - z0);
}

float remap(float v, float a, float b, float c, float d) {
    return c + (v - a) / (b - a) * (d - c);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// ---------------------------------------------------------------- the two layers

// Cloud density at world point p (0 = clear air).
float density(vec3 p, bool cheap) {
    vec3 q = p + Wind;
    if (p.y < 300.0) {
        float h = (p.y - SEA_BOTTOM) / (SEA_TOP - SEA_BOTTOM);
        if (h < 0.0 || h > 1.0) {
            return 0.0;
        }
        // Big heads (one noise tile per 900 blocks), more cloud where the broad noise says.
        vec4 n = cloudNoise(q * vec3(64.0 / 1200.0, 64.0 / 380.0, 64.0 / 1200.0));
        float cover = 0.9 + 0.2 * (cloudNoise(q * (64.0 / 4200.0)).b - 0.5);
        // Round tops: the heads narrow as they rise; a soft floor fading into the dark below.
        float shape = smoothstep(0.0, 0.25, h) * smoothstep(1.0, 0.45 + 0.35 * n.b, h);
        float d = clamp(remap(n.r * shape, 1.0 - cover, 1.0, 0.0, 1.0), 0.0, 1.0);
        if (!cheap && d > 0.0) {
            float detail = cloudNoise(q * (64.0 / 140.0) + vec3(0.0, Time * 0.4, 0.0)).g;
            d = clamp(remap(d, detail * 0.35, 1.0, 0.0, 1.0), 0.0, 1.0);
        }
        return d * 0.05;
    }
    float h = (p.y - STORM_BOTTOM) / (STORM_TOP - STORM_BOTTOM);
    if (h < 0.0 || h > 1.0) {
        return 0.0;
    }
    vec4 n = cloudNoise(q * vec3(64.0 / 1100.0, 64.0 / 420.0, 64.0 / 1100.0));
    // Thick above; underneath it hangs down in lumps.
    float shape = smoothstep(0.0, 0.35 + 0.4 * (1.0 - n.r), h) * smoothstep(1.0, 0.8, h);
    float d = clamp(remap(n.r * shape, 0.12, 1.0, 0.0, 1.0), 0.0, 1.0);
    if (!cheap && d > 0.0) {
        float detail = cloudNoise(q * (64.0 / 180.0)).g;
        d = clamp(remap(d, detail * 0.3, 1.0, 0.0, 1.0), 0.0, 1.0);
    }
    return d * 0.025;
}

// How much moonlight gets to p through the cloud toward the moon.
float moonlight(vec3 p) {
    float sum = 0.0;
    float step = 18.0;
    for (int i = 1; i <= 4; i++) {
        sum += density(p + MoonDir * step * float(i * i), true) * step * float(2 * i - 1);
    }
    return exp(-sum * 0.3);
}

// Follows the ray through the slab y0..y1; accumulates colour and coverage into acc.
void march(vec3 dir, float y0, float y1, float dither, inout vec4 acc) {
    float ta, tb;
    if (abs(dir.y) < 1e-4) {
        if (Eye.y < y0 || Eye.y > y1) {
            return;
        }
        ta = 0.0;
        tb = FAR;
    } else {
        float t0 = (y0 - Eye.y) / dir.y;
        float t1 = (y1 - Eye.y) / dir.y;
        ta = max(0.0, min(t0, t1));
        tb = min(FAR, max(t0, t1));
    }
    if (tb <= ta) {
        return;
    }
    // Long grazing paths get the same number of steps, spread out.
    float len = min(tb - ta, 1400.0);
    float dt = len / float(STEPS);
    float t = ta + dt * dither;
    float upLight = 0.05 + 0.05 * MoonLight;
    for (int i = 0; i < STEPS; i++) {
        if (acc.a > 0.97) {
            break;
        }
        vec3 p = Eye + dir * t;
        float d = density(p, false);
        if (d > 0.0) {
            float a = 1.0 - exp(-d * dt);
            float lit = moonlight(p);
            // Darker toward the bottom of each layer (less of the sky's light gets in there).
            float h = clamp((p.y - y0) / (y1 - y0), 0.0, 1.0);
            float amb = upLight * (0.2 + 1.1 * h);
            // Silver lining: brighter where you look toward the moon through the cloud.
            float phase = 0.6 + 0.9 * pow(max(dot(dir, MoonDir), 0.0), 6.0);
            vec3 col = vec3(0.62, 0.66, 0.78) * lit * phase * 1.3 * MoonLight + vec3(amb) * vec3(0.9, 0.93, 1.0);
            // Lightning lights the cloud round the strike from inside.
            if (Strike.w > 0.0) {
                float r = length(p.xz - Strike.xz);
                col += vec3(0.75, 0.78, 0.95) * Strike.w * exp(-r * r / (700.0 * 700.0)) * (0.6 + 0.4 * h);
            }
            col += vec3(0.20, 0.21, 0.26) * Flash;
            // Far off: into the haze.
            float haze = 1.0 - exp(-t / 3500.0);
            col = mix(col, HazeColor, haze);
            float w = a * (1.0 - acc.a) * (1.0 - 0.75 * haze * haze);
            acc.rgb += col * w;
            acc.a += w;
        }
        t += dt;
        if (t > tb) {
            break;
        }
    }
}

void main() {
    vec2 ndc = gl_FragCoord.xy / ScreenSize * 2.0 - 1.0;
    vec4 v = InvProjMat * vec4(ndc, 1.0, 1.0);
    vec3 viewDir = normalize(v.xyz / v.w);
    vec3 dir = normalize(transpose(mat3(ModelViewMat)) * viewDir);
    float dither = hash12(gl_FragCoord.xy + fract(Time * 7.0) * 57.0);
    vec4 acc = vec4(0.0);
    // Nearer layer first.
    if (dir.y < 0.0 || Eye.y < SEA_TOP) {
        march(dir, SEA_BOTTOM, SEA_TOP, dither, acc);
        march(dir, STORM_BOTTOM, STORM_TOP, dither, acc);
    } else {
        march(dir, STORM_BOTTOM, STORM_TOP, dither, acc);
        march(dir, SEA_BOTTOM, SEA_TOP, dither, acc);
    }
    fragColor = acc;
}
