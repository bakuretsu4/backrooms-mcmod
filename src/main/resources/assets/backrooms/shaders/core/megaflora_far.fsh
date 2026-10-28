#version 150

// The far tree of Level 227: the trunk, every branch, the canopy, moss curtains and the roots,
// seen from hundreds to thousands of blocks away through the mist.
//
// Each surface is painted by its material (vertex colour alpha, see Level227FarMesh):
//  - bark: dark wet wood with long grain, darker wet streaks, moss on everything facing up and in
//    big drifts down the trunk;
//  - roots: the same wood without moss, greyer, with pale lichen;
//  - leaves: clumps of dark and bright greens with sunlit sprays on top; the edges of each mass are
//    ragged (pixels near the silhouette are cut away in a leafy pattern) so it reads as foliage,
//    not as a smooth ball;
//  - moss curtains: a sheet cut into hanging strands of different lengths;
//  - cut faces of broken branches: growth rings.
// Then the mist: the same near mist as the terrain's fog (FogStart/End/Color with its alpha, set by
// Level227Atmosphere), and beyond the loaded terrain the open air's slow haze and the mist layers.
// The depth (set by the vertex shader) is logarithmic out to 65,536 blocks, so far things hide
// each other properly even 10,000 blocks away.

uniform vec4 FogColor;
uniform float FogStart;
uniform float FogEnd;
uniform vec2 Loaded;          // x: radius of the loaded terrain; y: margin inside it where we draw nothing
uniform vec3 SunPos;
uniform vec3 LightColor;

in vec3 vWorld;
in vec3 vRel;
in vec4 vColor;
in vec3 vNormal;
in vec4 vHaze;                // the open air's haze between the loaded terrain and here: colour, what's left

out vec4 fragColor;

// Darker the further below you (same as megaflora_far.vsh / megaflora_composite.fsh).
float belowShade(float drop) {
    return mix(1.0, 0.06, smoothstep(30.0, 320.0, drop));
}

// What reaches the eye from something of colour `surf` at `dist`.
vec3 atmosphere(vec3 surf, float dist) {
    // Beyond the loaded terrain: the open air and the layers (worked out per corner, megaflora_far.vsh).
    vec3 c = mix(vHaze.rgb, surf, vHaze.a);
    // The near mist: the terrain's fog at the edge of the loaded terrain (so the seam with the real
    // blocks doesn't show), thinning to about half further out so the colossal shapes keep their
    // dark silhouettes; and darker below you, so the tree under your branch sinks into black.
    float f = dist <= FogStart ? 0.0 : (dist < FogEnd ? smoothstep(FogStart, FogEnd, dist) : 1.0);
    float amount = FogColor.a * mix(1.0, 0.55, smoothstep(Loaded.x, Loaded.x * 3.0, dist));
    return mix(c, FogColor.rgb * belowShade(-vRel.y), f * amount);
}

// ---------------------------------------------------------------- noise

float hash3(vec3 p) {
    uvec3 q = uvec3(ivec3(floor(p)) + ivec3(1048576));
    uint h = q.x * 1597334677u ^ q.y * 3812015801u ^ q.z * 2798796415u;
    h = (h ^ (h >> 16u)) * 2246822519u;
    h ^= h >> 13u;
    return float(h & 16777215u) * (1.0 / 16777215.0);
}

float vnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash3(i);
    float b = hash3(i + vec3(1.0, 0.0, 0.0));
    float c = hash3(i + vec3(0.0, 1.0, 0.0));
    float d = hash3(i + vec3(1.0, 1.0, 0.0));
    float e = hash3(i + vec3(0.0, 0.0, 1.0));
    float g = hash3(i + vec3(1.0, 0.0, 1.0));
    float h = hash3(i + vec3(0.0, 1.0, 1.0));
    float k = hash3(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(a, b, f.x), mix(c, d, f.x), f.y), mix(mix(e, g, f.x), mix(h, k, f.x), f.y), f.z);
}

// ---------------------------------------------------------------- materials

vec3 bark(vec3 w, vec3 n, bool mossy, float dist) {
    float grain = vnoise(w * vec3(0.06, 0.015, 0.06));
    // Five-block detail is less than a pixel beyond ~900 blocks: skip it there (no shimmer, cheaper).
    float fine = dist < 900.0 ? vnoise(w * 0.21) : 0.5;
    vec3 c = vColor.rgb * (0.72 + 0.56 * grain) * (0.88 + 0.24 * fine);
    // Wet streaks running down the wood.
    c *= 1.0 - 0.28 * smoothstep(0.62, 0.9, vnoise(w * vec3(0.11, 0.008, 0.11)));
    if (mossy) {
        float moss = smoothstep(0.25, 0.8, n.y + 0.45 * (vnoise(w * 0.03) - 0.5));
        moss = max(moss, 0.55 * smoothstep(0.6, 0.82, vnoise(w * vec3(0.018, 0.005, 0.018))));
        c = mix(c, vec3(0.15, 0.26, 0.08) * (0.8 + 0.4 * fine), moss * 0.85);
    } else {
        c = mix(c, vec3(0.45, 0.47, 0.42), 0.3 * smoothstep(0.65, 0.9, vnoise(w * 0.05)));
    }
    return c;
}

void main() {
    // Inside the loaded terrain the real blocks are the tree; don't even shade.
    float across = length(vRel.xz);
    if (vWorld.y > -8.0 && vWorld.y < 776.0 && across < Loaded.x - Loaded.y) {
        discard;
    }
    vec3 n = normalize(vNormal);
    float dist = length(vRel);
    vec3 dir = vRel / max(dist, 1e-3);
    vec3 w = vWorld;
    int material = int(vColor.a * 255.0 + 0.5);
    vec3 albedo;
    if (material >= 230) {
        albedo = bark(w, n, true, dist);
    } else if (material >= 180) {
        albedo = bark(w, n, false, dist);
    } else if (material >= 125) {
        // Ragged leafy silhouette: near the outline, cut away in a clumpy pattern (beyond ~1500
        // blocks the pattern would be finer than a pixel and only shimmer, so the cut fades out).
        float rim = 1.0 - abs(dot(n, dir)) * (1.0 + smoothstep(1000.0, 1500.0, dist));
        if (vnoise(w * 0.11 + 7.0) < smoothstep(0.55, 0.97, rim) * 0.8) {
            discard;
        }
        float clump = vnoise(w * 0.07) * 0.6 + (dist < 900.0 ? vnoise(w * 0.21) : 0.5) * 0.4;
        albedo = vColor.rgb * (0.62 + 0.76 * clump);
        albedo = mix(albedo, vec3(0.27, 0.40, 0.13), smoothstep(0.62, 0.95, clump) * max(n.y, 0.0) * 0.6);
        albedo *= 0.75 + 0.25 * smoothstep(-0.4, 0.4, n.y);
    } else if (material >= 75) {
        // Strands: across the sheet, each 1.3 blocks wide, of its own length; some missing.
        vec3 along = normalize(cross(vec3(0.0, 1.0, 0.0), n) + vec3(1e-4));
        float strand = floor(dot(w, along) / 1.3);
        float len = 0.3 + 0.7 * hash3(vec3(strand, 17.0, floor(vColor.g * 255.0)));
        if (vColor.r > len || hash3(vec3(strand, 3.0, 5.0)) < 0.22) {
            discard;
        }
        albedo = vec3(0.12, 0.21, 0.08) * (0.7 + 0.6 * vColor.g) * (1.0 - 0.4 * vColor.r);
    } else {
        float rad = vColor.r;
        float ring = fract(rad * 16.0 + 0.35 * vnoise(w * 0.15));
        albedo = mix(vec3(0.52, 0.40, 0.26), vec3(0.34, 0.24, 0.15), smoothstep(0.35, 0.65, ring));
        albedo = mix(albedo, vec3(0.16, 0.11, 0.08), smoothstep(0.9, 0.97, rad));
    }
    // Soft misty daylight: mostly from above, a little from the Sun's side, dark from the void below.
    float sky = 0.30 + 0.70 * pow(clamp(n.y * 0.5 + 0.5, 0.0, 1.0), 1.3);
    float sun = max(dot(n, normalize(SunPos - w)), 0.0) * 0.18;
    vec3 lit = albedo * LightColor * (sky + sun);
    fragColor = vec4(atmosphere(lit, dist), 1.0);
}
