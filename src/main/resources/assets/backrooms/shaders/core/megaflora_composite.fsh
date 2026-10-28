#version 150

// Lays Level 227's far picture (the tree, roots, Sun and bubbles: FarColor / FarDepth) and the
// sky into the frame, among the real blocks already drawn (SceneDepth, a copy of their depth).
//
// For each pixel:
//  - the sky: bright sunlit haze overhead, the mist at the horizon, the black void below with a
//    pale mist floor far down; all through the same mist the terrain's fog uses;
//  - the far picture over the sky, but only beyond the loaded terrain (or above / below the world,
//    where there are no blocks): inside it, the real blocks are the tree;
//  - real blocks near the edge of the loaded terrain fade into that background, so the edge of the
//    world never shows; nearer blocks are left alone, and a real block in front of the far tree
//    always hides it.
// The output is (colour x coverage, coverage), blended over the frame with ONE, ONE_MINUS_SRC_ALPHA.

uniform sampler2D SceneDepth;
uniform sampler2D FarColor;
uniform sampler2D FarDepth;
uniform mat4 ModelViewMat;    // camera rotation
uniform mat4 InvProjMat;
uniform vec2 ScreenSize;
uniform vec4 FogColor;
uniform float FogStart;
uniform float FogEnd;
uniform vec3 Eye;
uniform float DepthRange;
uniform vec2 Loaded;          // x: radius of the loaded terrain
uniform float OpenAir;
uniform vec3 MistLow;
uniform vec3 MistHigh;
uniform vec3 Zenith;
uniform vec3 VoidColor;
uniform vec3 SunPos;
uniform vec4 LayerA;
uniform vec4 LayerB;
uniform vec4 LayerC;
uniform vec4 LayerD;
uniform vec4 SunScreen;      // where the Sun is on screen (uv), 1 if in front of you, look spacing (uv)
uniform float SunDepth;      // and how far it is (along the view axis)

out vec4 fragColor;

const float VOID_LINE = 30.0;
const float MIST_TOP = 9000.0;
const float SKY_DISTANCE = 60000.0;

// ---------------------------------------------------------------- atmosphere (same as megaflora_far)

float erfApprox(float x) {
    float t = 1.0 / (1.0 + 0.47047 * abs(x));
    float y = 1.0 - t * (0.3480242 + t * (-0.0958798 + t * 0.7478556)) * exp(-x * x);
    return x < 0.0 ? -y : y;
}

float layerDepth(vec4 L, float dy, float s0, float s1) {
    float len = s1 - s0;
    if (len <= 0.0) {
        return 0.0;
    }
    float ya = (Eye.y + dy * s0 - L.x) / L.y;
    float yb = (Eye.y + dy * s1 - L.x) / L.y;
    if (abs(yb - ya) < 0.2) {
        float m = 0.5 * (ya + yb);
        return L.z * len * exp(-m * m);
    }
    return L.z * L.y * 0.8862269 * (erfApprox(yb) - erfApprox(ya)) / dy;
}

float mistLength(float dy, float s0, float s1) {
    if (abs(dy) < 1e-5) {
        return (Eye.y > VOID_LINE && Eye.y < MIST_TOP) ? s1 - s0 : 0.0;
    }
    float sa = (VOID_LINE - Eye.y) / dy;
    float sb = (MIST_TOP - Eye.y) / dy;
    float lo = max(s0, min(sa, sb));
    float hi = min(s1, max(sa, sb));
    return max(hi - lo, 0.0);
}

// The mist's own colour at height y, darker the further below you (same as megaflora_far.vsh).
vec3 mistColor(float y) {
    vec3 c = mix(MistLow, FogColor.rgb, smoothstep(60.0, 320.0, y));
    return mix(c, MistHigh, smoothstep(700.0, 4000.0, y));
}

float belowShade(float drop) {
    return mix(1.0, 0.06, smoothstep(30.0, 320.0, drop));
}

vec3 mistAt(float y) {
    return mistColor(y) * belowShade(Eye.y - y);
}

vec3 atmosphere(vec3 surf, vec3 dir, float dist) {
    float s0 = min(Loaded.x, dist);
    float s1 = dist;
    float open = mistLength(dir.y, s0, s1) / OpenAir;
    float ta = layerDepth(LayerA, dir.y, s0, s1);
    float tb = layerDepth(LayerB, dir.y, s0, s1);
    float tc = layerDepth(LayerC, dir.y, s0, s1);
    float td = layerDepth(LayerD, dir.y, s0, s1);
    float tau = open + ta + tb + tc + td;
    float mid = 0.5 * (s0 + min(s1, s0 + 5000.0));
    vec3 medium = mistAt(Eye.y + dir.y * mid) * open
            + mistAt(LayerA.x) * (LayerA.w * ta) + mistAt(LayerB.x) * (LayerB.w * tb)
            + mistAt(LayerC.x) * (LayerC.w * tc) + mistAt(LayerD.x) * (LayerD.w * td);
    medium /= max(tau, 1e-5);
    float toward = max(dot(dir, normalize(SunPos - Eye)), 0.0);
    medium += MistHigh * (pow(toward, 6.0) * 0.32 + pow(toward, 48.0) * 0.6);
    vec3 c = mix(medium, surf, exp(-tau));
    // The near mist, thinned for the sky as for the far tree (megaflora_far.fsh). Looking down,
    // the view leaves the forest's mist within a few blocks of the branch and falls into black:
    // less of it, and what there is lies in the tree's shadow.
    float f = (dist <= FogStart ? 0.0 : (dist < FogEnd ? smoothstep(FogStart, FogEnd, dist) : 1.0)) * 0.55;
    f *= mix(1.0, 0.3, smoothstep(0.05, 0.55, -dir.y));
    return mix(c, FogColor.rgb * belowShade(-dir.y * 2000.0), f * FogColor.a);
}

// The empty sky in direction dir: what lies at the end of the ray (sunlit haze up, black void
// down) seen through all the mist on the way.
vec3 skyColor(vec3 dir) {
    vec3 end = dir.y >= 0.0 ? mix(MistHigh, Zenith, sqrt(dir.y)) : VoidColor;
    return atmosphere(end, dir, SKY_DISTANCE);
}

// How much of the Sun's disc is open sky right now (no root, branch or block in front), from
// a few looks at the far picture and the scene round its place on screen.
float sunVisible() {
    // Only while the Sun itself is on screen: off screen the looks would land on the screen's edge.
    if (SunScreen.z < 0.5 || any(lessThan(SunScreen.xy, vec2(0.0))) || any(greaterThan(SunScreen.xy, vec2(1.0)))) {
        return 0.0;
    }
    float open = 0.0;
    float range = log2(1.0 + DepthRange);
    for (int i = 0; i < 9; i++) {
        vec2 o = vec2(float(i % 3) - 1.0, float(i / 3) - 1.0) * SunScreen.w * vec2(ScreenSize.y / ScreenSize.x, 1.0);
        vec2 p = SunScreen.xy + o;
        float fd = texture(FarDepth, p).r;
        bool clear = texture(SceneDepth, p).r >= 1.0 && (fd >= 1.0 || exp2(fd * range) - 1.0 > SunDepth * 0.97);
        open += clear ? 1.0 : 0.0;
    }
    return open / 9.0;
}

// ---------------------------------------------------------------- compositing

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec2 ndc = uv * 2.0 - 1.0;
    float sceneDepth = texture(SceneDepth, uv).r;
    float farDepth = texture(FarDepth, uv).r;
    mat3 toWorld = transpose(mat3(ModelViewMat));
    vec4 fp = InvProjMat * vec4(ndc, 1.0, 1.0);
    vec3 viewDir = normalize(fp.xyz / fp.w);

    // The far picture here: how far, and whether it may show in front of real terrain. (Inside the
    // world it only exists from 40 blocks inside the loaded edge on, see megaflora_far.fsh, and is
    // never half-faded: it keeps only its nearest surface, so a faded surface would open a hole to
    // the sky instead of showing the far tree behind it.)
    bool hasFar = farDepth < 1.0;
    float farZ = 1e9;
    float showFront = 0.0;
    if (hasFar) {
        // The far picture's depth is logarithmic (megaflora_far.vsh): back to blocks.
        farZ = exp2(farDepth * log2(1.0 + DepthRange)) - 1.0;
        vec3 fw = toWorld * (viewDir * (farZ / max(-viewDir.z, 1e-4)));
        float y = Eye.y + fw.y;
        float outside = max(smoothstep(768.0, 792.0, y), smoothstep(0.0, -24.0, y));
        showFront = max(smoothstep(Loaded.x - 40.0, Loaded.x - 8.0, length(fw.xz)), outside);
    }

    // The real terrain here: how far, how much it fades into the background near the edge of the
    // loaded chunks, and how much its fog should darken because it lies below you.
    bool terrain = sceneDepth < 1.0;
    float terrainZ = 1e9;
    float fadeOut = 1.0;
    float darken = 0.0;
    if (terrain) {
        vec4 tp = InvProjMat * vec4(ndc, sceneDepth * 2.0 - 1.0, 1.0);
        vec3 tv = tp.xyz / tp.w;
        vec3 tw = toWorld * tv;
        terrainZ = -tv.z;
        // Only where the far tree carries on right there (the same branch beyond the edge, about as
        // far as the block or a little behind): a real tree taller than the far canopy, or one with
        // a far surface well in front of it, must not fade into a sky-coloured hole.
        float carriesOn = hasFar ? smoothstep(terrainZ - 60.0, terrainZ - 20.0, farZ)
                * smoothstep(terrainZ + 200.0, terrainZ + 40.0, farZ) : 0.0;
        fadeOut = smoothstep(Loaded.x - 40.0, Loaded.x - 8.0, length(tw.xz)) * carriesOn;
        // The terrain's fog (one colour for the whole level) is the forest's lit mist; under your
        // branch that mist is in shadow, so take back the part of the fog that should be dark.
        float d = length(tv);
        float f = d <= FogStart ? 0.0 : (d < FogEnd ? smoothstep(FogStart, FogEnd, d) : 1.0);
        darken = f * FogColor.a * (1.0 - belowShade(-tw.y));
    }
    float front = terrain && hasFar ? smoothstep(terrainZ * 1.002, terrainZ * 0.985, farZ) : 0.0;

    // The Sun seen through a gap: its glare blooms over everything round it (the eye's own glare).
    vec2 fromSun = (uv - SunScreen.xy) * vec2(ScreenSize.x / ScreenSize.y, 1.0);
    float r = length(fromSun);
    float bloom = r < 0.9 ? sunVisible() * (0.9 * exp(-r * 60.0) + 0.35 * exp(-r * 9.0) + 0.12 * exp(-r * 2.5)) : 0.0;
    vec3 glare = vec3(1.0, 0.99, 0.95) * bloom * (1.0 - 0.4 * FogColor.a);

    if (terrain && fadeOut <= 0.0 && front <= 0.0 && darken <= 0.002 && bloom <= 0.002) {
        discard; // a real block, well inside the loaded terrain, in front of everything far
    }

    vec4 picture = texture(FarColor, uv);
    vec3 sky = skyColor(toWorld * viewDir);
    // The background: the far picture over the sky.
    vec3 background = picture.rgb + sky * (1.0 - picture.a);
    // What's left of the terrain after its dark fog: kept = (1 - darken).
    float kept = (1.0 - darken) * (1.0 - fadeOut);
    // Far tree behind the terrain: the terrain fades into the background near the edge.
    vec4 behind = vec4(background * fadeOut, 1.0 - kept);
    // Far tree in front of the terrain (only near the edge / outside the world); where the faded
    // block lets through, the far surface itself shows (never the sky: we can't see past it).
    float cover = picture.a * showFront;
    vec4 inFront = vec4(picture.rgb * showFront + background * fadeOut * (1.0 - cover), 1.0 - kept * (1.0 - cover));
    vec4 outColor = terrain ? mix(behind, inFront, front) : vec4(background, 1.0);
    fragColor = vec4(outColor.rgb + glare, outColor.a);
}
