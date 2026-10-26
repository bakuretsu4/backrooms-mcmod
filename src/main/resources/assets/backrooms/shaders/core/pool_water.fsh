#version 150

// Pool water surface for the Poolrooms and Level 37.
//
// The surface is drawn after the whole room has been drawn and lit, so SceneColor/SceneDepth
// (a copy of the screen) show exactly what lies behind each water pixel. From that:
//  1. Ripples: two scrolling layers of a tileable normal map bend the surface.
//  2. Refraction: the floor behind is looked up a little off to the side, following the ripples.
//  3. Caustics: the moving web of light that a rippled surface focuses onto the pool floor,
//     projected along the light direction; it brightens the (already lit) floor, so dark rooms
//     stay dark.
//  4. Depth colour: the more water the line of sight crosses, the more the floor is tinted and
//     covered by the water colour (shallow -> deep). The water colour is lit by the blurred
//     brightness of the room around it (a mip level of the copy).
//  5. Reflection: a short ray march across the depth buffer finds the walls/pillars/ceiling the
//     surface mirrors; where it finds nothing, a tint of the level's ceiling colour is used.
//     Fresnel: almost no reflection looking straight down, mirror-like at grazing angles.
//  6. Sun glints (Level 37), a soft edge where the water meets walls and floor, and the fog.
// Looking up at the surface from under the water: the room above, bent, inside Snell's window,
// and a mirror of the pool (total internal reflection) outside it.

uniform sampler2D SceneColor;  // the lit scene behind the water (with mipmaps)
uniform sampler2D SceneDepth;  // its depth buffer
uniform sampler2D Ripples;     // tileable: rg = surface slope (0.5 = flat), b = height
uniform sampler2D Caustics;    // tileable caustic network (r)

uniform mat4 ModelViewMat;     // camera rotation
uniform mat4 ProjMat;
uniform mat4 InvProjMat;
uniform vec2 ScreenSize;
uniform vec3 CameraLow;        // camera position, wrapped every 4096 blocks (noise coordinates)
uniform float Time;

uniform vec4 ShallowColor;     // rgb + alpha in very shallow water
uniform vec4 DeepColor;        // rgb + alpha in deep water
uniform float DeepDistance;    // blocks of water until it reads deep
uniform vec3 ReflectColor;     // what the surface mirrors when the ray march misses
uniform float CausticStrength;
uniform float RippleStrength;
uniform float Refraction;
uniform vec3 SunDir;           // direction the light travels (points down)
uniform vec3 SunColor;
uniform float SunGlints;       // 0 = none, 1 = full
uniform float ClearDepth;      // blocks of water over which the water colour fades in
uniform float TintStrength;    // 0..1 how much the floor seen through the water is tinted
uniform float WaterBrightness; // overall brightness of the lit water

uniform float SceneFogStart;
uniform float SceneFogEnd;
uniform vec4 SceneFogColor;
uniform int SceneFogShape;

in vec3 relPos;
in vec3 viewPos;

out vec4 fragColor;

// World size of one tile of each texture layer (every one divides 4096, so the wrap is seamless).
const float RIPPLE_TILE_A = 8.0;
const float RIPPLE_TILE_B = 4.0;
const float GLINT_TILE = 2.0;
const float CAUSTIC_TILE = 4.0;
const float CAUSTIC_WARP_TILE = 8.0;
// How steep the ripples are at RippleStrength 1.
const float RIPPLE_SLOPE = 0.22;
const int SSR_STEPS = 18;
const int SSR_REFINE = 5;

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// View-space position of the scene at screen position uv with depth-buffer value depth.
vec3 viewFromDepth(vec2 uv, float depth) {
    vec4 p = InvProjMat * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

// View space -> camera-relative world (ModelViewMat is a pure rotation: its inverse is its transpose).
vec3 viewToWorld(vec3 v) {
    return v * mat3(ModelViewMat);
}

// Surface normal (world space, pointing up) at world position p (xz).
vec3 surfaceNormal(vec2 p, float dist) {
    vec2 uvA = p / RIPPLE_TILE_A + vec2(Time * 0.021, Time * 0.013);
    // Layer B is turned 45 degrees (x+z, z-x) so the two layers never line up into a grid.
    vec2 uvB = vec2(p.x + p.y, p.y - p.x) / (RIPPLE_TILE_B * 2.0) + vec2(-Time * 0.019, Time * 0.028);
    vec2 sA = texture(Ripples, uvA).rg * 2.0 - 1.0;
    vec2 sB = texture(Ripples, uvB).rg * 2.0 - 1.0;
    sB = vec2(sB.x - sB.y, sB.x + sB.y) * 0.70710678; // back to world axes
    vec2 slope = (sA * 0.6 + sB * 0.5) * RIPPLE_SLOPE * RippleStrength;
    // Far away the ripples are smaller than a pixel: calm them so the water doesn't shimmer.
    slope /= 1.0 + dist * 0.035;
    return normalize(vec3(-slope.x, 1.0, -slope.y));
}

// The caustic web at pattern position p (in blocks).
vec3 causticWeb(vec2 p) {
    // The web wobbles: it is bent by a slowly scrolling copy of the ripples (the surface
    // above is what focuses it). A second, larger copy drifting another way marks where the
    // light focuses hardest (min of the two). Each colour channel of the main web is offset
    // a touch: the faint rainbow edge of real caustics.
    vec2 warp = (texture(Ripples, p / CAUSTIC_WARP_TILE + vec2(Time * 0.035, -Time * 0.025)).rg * 2.0 - 1.0) * 0.05;
    vec2 uvA = p / CAUSTIC_TILE + vec2(Time * 0.012, Time * 0.007) + warp;
    vec2 uvB = vec2(p.x + p.y, p.y - p.x) / (CAUSTIC_TILE * 2.0) + vec2(-Time * 0.017, Time * 0.024);
    vec2 split = vec2(0.003, 0.002);
    vec3 a = vec3(texture(Caustics, uvA + split).r, texture(Caustics, uvA).r, texture(Caustics, uvA - split).r);
    float b = texture(Caustics, uvB).r;
    return (a * 0.7 + min(a, vec3(b)) * 1.2) * 0.55;
}

// Caustics on a surface at world position `world` with normal `n`, `depth` blocks under water.
// Floors get the web projected along the light (it entered the water up-light); walls get it
// spread across the wall itself (along the wall, and up), so they show a web, not streaks.
vec3 causticsOn(vec3 world, vec3 n, float depth) {
    vec2 p;
    float strength = 1.0;
    if (abs(n.y) > 0.5) {
        p = world.xz - SunDir.xz * (depth / max(-SunDir.y, 0.2));
    } else {
        vec2 along = normalize(vec2(-n.z, n.x) + vec2(1.0e-5));
        p = vec2(dot(world.xz, along), world.y * 1.3);
        strength = 0.7;
    }
    // Right at the surface the light hasn't focused yet; deep down it spreads and fades.
    float fade = smoothstep(0.0, 0.3, depth) * exp(-depth * 0.16);
    return causticWeb(p) * fade * strength;
}

// Screen-space reflection: march from `origin` along `dir` (both view space) and return the
// colour of the first scene surface the ray passes behind (a = how much to trust it).
vec4 traceReflection(vec3 origin, vec3 dir) {
    float stepLen = 0.12 + 0.02 * length(origin);
    vec3 pos = origin;
    for (int i = 0; i < SSR_STEPS; i++) {
        vec3 prev = pos;
        pos += dir * stepLen;
        vec4 clip = ProjMat * vec4(pos, 1.0);
        if (clip.w <= 0.0) {
            break;
        }
        vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
            break;
        }
        float depth = texture(SceneDepth, uv).r;
        float sceneDist = -viewFromDepth(uv, depth).z;
        float behind = -pos.z - sceneDist;
        if (depth < 0.99999 && behind > 0.0 && behind < stepLen * 2.0 + 0.25) {
            // Halve the last step a few times to land on the surface.
            vec3 a = prev;
            vec3 b = pos;
            for (int j = 0; j < SSR_REFINE; j++) {
                vec3 m = (a + b) * 0.5;
                vec4 mc = ProjMat * vec4(m, 1.0);
                vec2 muv = mc.xy / mc.w * 0.5 + 0.5;
                float md = -viewFromDepth(muv, texture(SceneDepth, muv).r).z;
                if (-m.z > md) {
                    b = m;
                } else {
                    a = m;
                }
            }
            vec4 hc = ProjMat * vec4(b, 1.0);
            vec2 huv = clamp(hc.xy / hc.w * 0.5 + 0.5, 0.0, 1.0);
            float edge = smoothstep(0.0, 0.07, min(min(huv.x, 1.0 - huv.x), min(huv.y, 1.0 - huv.y)));
            float far = 1.0 - smoothstep(0.55, 1.0, float(i) / float(SSR_STEPS));
            return vec4(textureLod(SceneColor, huv, 1.0).rgb, edge * far);
        }
        stepLen *= 1.25;
    }
    return vec4(0.0);
}

float fresnel(float cosine) {
    return 0.02 + 0.98 * pow(1.0 - clamp(cosine, 0.0, 1.0), 5.0);
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float surfDist = length(viewPos);
    vec3 V = -relPos / max(surfDist, 1.0e-4);   // towards the eye
    vec2 p = relPos.xz + CameraLow.xz;           // world xz (wrapped) for the textures
    vec3 N = surfaceNormal(p, surfDist);
    // Only the ripple part of the normal moves things on screen (the flat "up" part must not).
    vec3 bend = mat3(ModelViewMat) * (N - vec3(0.0, 1.0, 0.0));
    bool fromBelow = relPos.y > 0.0;             // the surface is above the eye

    // What lies straight behind this pixel, and how much water is in between.
    float depth0 = texture(SceneDepth, uv).r;
    vec3 back0 = viewFromDepth(uv, depth0);
    float thick0 = depth0 >= 0.99999 ? 64.0 : max(length(back0) - surfDist, 0.0);
    // Rough surface direction of what's behind (from how its position changes between pixels);
    // must be worked out here, outside any branch, for the screen derivatives to be valid.
    vec3 back0Rel = viewToWorld(back0);
    vec3 backNormal = normalize(cross(dFdx(back0Rel), dFdy(back0Rel)) + vec3(0.0, 1.0e-6, 0.0));

    // Refraction: deeper water and nearer water shift the floor further.
    vec2 uvR = uv + bend.xy * Refraction * 0.09 * clamp(thick0, 0.0, 1.5) / (1.0 + 0.12 * surfDist);
    uvR = clamp(uvR, vec2(0.001), vec2(0.999));
    float depthR = texture(SceneDepth, uvR).r;
    vec3 backR = viewFromDepth(uvR, depthR);
    if (depthR < 0.99999 && length(backR) < surfDist) {
        // That spot is something in FRONT of the water (a leg, a pillar): don't pull it in.
        uvR = uv;
        depthR = depth0;
        backR = back0;
    }
    vec3 behind = texture(SceneColor, uvR).rgb;

    vec3 col;
    float edge;
    if (!fromBelow) {
        float thick = depthR >= 0.99999 ? 64.0 : max(length(backR) - surfDist, 0.0);
        vec3 backRel = viewToWorld(backR);       // camera-relative world position of the floor
        float depthBelow = max(relPos.y - backRel.y, 0.0);

        // 3. Caustics on the floor / lower walls (faded out far away, where the fine web would
        //    only flicker between pixels).
        float causticFar = 1.0 - smoothstep(14.0, 36.0, surfDist);
        behind *= 1.0 + causticsOn(backRel + CameraLow, backNormal, depthBelow) * CausticStrength * causticFar;

        // 4. Depth colour.
        float deep = 1.0 - exp(-thick / max(DeepDistance, 0.1) * 1.6);
        vec3 waterColor = mix(ShallowColor.rgb, DeepColor.rgb, smoothstep(0.0, 1.0, deep));
        float cover = mix(ShallowColor.a, DeepColor.a, deep) * smoothstep(0.0, max(ClearDepth, 0.05), thick);
        float roomLight = clamp(luma(textureLod(SceneColor, uvR, 5.0).rgb) * 1.7, 0.02, 1.25);
        vec3 tint = ShallowColor.rgb / max(max(ShallowColor.r, ShallowColor.g), max(ShallowColor.b, 0.001));
        vec3 seen = behind * mix(vec3(1.0), tint, clamp(thick * 0.45, 0.0, 0.9) * TintStrength);
        vec3 under = mix(seen, waterColor * roomLight, cover) * WaterBrightness;

        // 5. Reflection.
        vec3 R = reflect(-V, N);
        R.y = max(R.y, 0.02);
        vec4 hit = traceReflection(viewPos, mat3(ModelViewMat) * normalize(R));
        float above = clamp(luma(textureLod(SceneColor, vec2(uv.x, min(uv.y + 0.3, 0.98)), 6.0).rgb) * 1.4, 0.0, 1.2);
        vec3 reflection = mix(ReflectColor * above, hit.rgb, hit.a);
        // A little more than Schlick's fresnel: the ripples tilt part of the surface toward you.
        col = mix(under, reflection, min(fresnel(dot(N, V)) * 1.3, 1.0));

        // 6. Sun glints where a sunbeam lands on this patch of water (Level 37).
        if (SunGlints > 0.0) {
            vec2 g = texture(Ripples, p / GLINT_TILE + vec2(Time * 0.05, -Time * 0.04)).rg * 2.0 - 1.0;
            vec3 Ng = normalize(N + vec3(-g.x, 0.0, -g.y) * 0.12);
            float sunlit = smoothstep(0.35, 0.8, luma(textureLod(SceneColor, uv, 2.5).rgb));
            float glint = pow(max(dot(reflect(-V, Ng), -SunDir), 0.0), 420.0);
            col += SunColor * glint * 6.0 * SunGlints * sunlit;
        }

        // Soft edge: where the floor or a wall is right at the surface, fade into it (no seam).
        edge = smoothstep(0.0, 0.06, relPos.y - back0Rel.y) * smoothstep(0.0, 0.05, thick0);
    } else {
        // Looking up from under the water.
        vec3 I = -V;                                  // eye -> surface, pointing up
        vec3 Nd = -N;                                 // the underside faces down
        vec3 T = refract(I, Nd, 1.333);
        vec4 hit = traceReflection(viewPos, mat3(ModelViewMat) * reflect(I, Nd));
        // The pool mirrored in the surface; where the ray march finds nothing, a blurred look at
        // the pool walls further down the screen, hazed by the underwater fog.
        vec3 poolBelow = textureLod(SceneColor, vec2(uv.x, max(uv.y - 0.25, 0.02)), 5.0).rgb;
        vec3 inside = mix(mix(SceneFogColor.rgb, poolBelow, 0.6), hit.rgb, hit.a);
        if (dot(T, T) < 1.0e-6) {
            col = inside;                             // outside Snell's window: a perfect mirror
        } else {
            float F = fresnel(T.y);
            vec3 tint = ShallowColor.rgb / max(max(ShallowColor.r, ShallowColor.g), max(ShallowColor.b, 0.001));
            col = mix(behind * mix(vec3(1.0), tint, 0.25), inside, F);
        }
        edge = smoothstep(0.0, 0.05, thick0);
    }

    // Fade into the unrefracted scene at the very edge.
    col = mix(texture(SceneColor, uv).rgb, col, edge);

    // Fog, exactly like vanilla's terrain fog for this frame.
    float fogDist = SceneFogShape == 0 ? length(relPos) : max(length(relPos.xz), abs(relPos.y));
    if (fogDist > SceneFogStart) {
        float fog = fogDist < SceneFogEnd ? smoothstep(SceneFogStart, SceneFogEnd, fogDist) : 1.0;
        col = mix(col, SceneFogColor.rgb, fog * SceneFogColor.a);
    }

    fragColor = vec4(max(col, 0.0), 1.0);
}
