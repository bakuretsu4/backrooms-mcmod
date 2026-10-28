#version 150

// The far tree of Level 227 (see megaflora_far.fsh). Positions are world coordinates; the
// camera offset is taken here in float, precise to a tiny fraction of a block at these distances.
//
// Sway: the colossal tree flexes. Far from you (from Sway.y blocks on) its outer limbs and the
// root tips drift slowly, up to Sway.x blocks at the very tips. Near the loaded terrain nothing
// moves, so the far tree always lines up with the real blocks there.
//
// Haze: the open air's slow haze and the mist layers between the edge of the loaded terrain and
// each corner are worked out here (the haze changes slowly across a triangle, and per corner is
// far cheaper than per pixel); the fragment shader adds the near mist. megaflora_composite.fsh
// paints the sky with the same sums, so far things fade into exactly the sky behind them.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;   // camera rotation
uniform mat4 ProjMat;
uniform vec4 FogColor;
uniform vec3 Eye;
uniform float Time;
uniform vec2 Sway;
uniform float DepthRange;
uniform vec2 Loaded;          // x: radius of the loaded terrain
uniform float OpenAir;        // the open air dims light by e over this many blocks
uniform vec3 MistLow;         // mist colour down toward the void ...
uniform vec3 MistHigh;        // ... and high up among the roots, lit by the Sun
uniform vec3 SunPos;
uniform vec4 LayerA;          // mist layers: height, half-thickness, density per block, brightness
uniform vec4 LayerB;
uniform vec4 LayerC;
uniform vec4 LayerD;

out vec3 vWorld;
out vec3 vRel;
out vec4 vColor;
out vec3 vNormal;
out vec4 vHaze;

const float VOID_LINE = 30.0;   // no mist below this: the black void
const float MIST_TOP = 9000.0;  // nor above the Sun

// Depth for distances out to DepthRange blocks: logarithmic, so near and far both keep fine
// steps (a 24-bit depth buffer resolves about a millionth of the distance anywhere). It is
// computed per corner, so the GPU can still skip hidden pixels before shading them.
float logDepth(float w) {
    return log2(1.0 + max(w, 1e-4)) / log2(1.0 + DepthRange);
}

// ---------------------------------------------------------------- atmosphere (same as composite)

float erfApprox(float x) {
    float t = 1.0 / (1.0 + 0.47047 * abs(x));
    float y = 1.0 - t * (0.3480242 + t * (-0.0958798 + t * 0.7478556)) * exp(-x * x);
    return x < 0.0 ? -y : y;
}

// How much mist band L lies along the view ray between distances s0 and s1 (dy: the ray's rise).
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

// The part of the ray between s0 and s1 that runs through mist (not the void below, not above the Sun).
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

// The mist's own colour at height y: dark toward the void, the fog colour among the branches,
// pale and sunlit high up among the roots ...
vec3 mistColor(float y) {
    vec3 c = mix(MistLow, FogColor.rgb, smoothstep(60.0, 320.0, y));
    return mix(c, MistHigh, smoothstep(700.0, 4000.0, y));
}

// ... and darker the further it lies below you: the light comes from above, and under your own
// branch the mist sits in the tree's shadow, fading into the black void.
float belowShade(float drop) {
    return mix(1.0, 0.06, smoothstep(30.0, 320.0, drop));
}

vec3 mistAt(float y) {
    return mistColor(y) * belowShade(Eye.y - y);
}

// The haze from the edge of the loaded terrain out to `dist` along `dir`: its colour, and how
// much of what lies behind it still shows.
vec4 haze(vec3 dir, float dist) {
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
    // The mist glows toward the Sun (light scattered forward by the droplets).
    float toward = max(dot(dir, normalize(SunPos - Eye)), 0.0);
    medium += MistHigh * (pow(toward, 6.0) * 0.32 + pow(toward, 48.0) * 0.6);
    return vec4(medium, exp(-tau));
}

void main() {
    vec3 rel = Position - Eye;
    float dist = length(rel);
    // How far out on the tree this point is: away from the trunk, or up the roots.
    float reach = length(Position.xz) + max(Position.y - 768.0, 0.0);
    float k = Sway.x * smoothstep(Sway.y, Sway.y * 2.5, dist) * clamp(reach / 6000.0, 0.0, 1.4);
    float ang = atan(Position.z, Position.x);
    vec3 drift = vec3(sin(Time * 0.071 + reach * 0.00061 + ang * 2.0),
                      0.4 * sin(Time * 0.053 + reach * 0.00093 + ang),
                      cos(Time * 0.064 + reach * 0.00077 + ang * 3.0));
    rel += drift * k;
    int material = int(Color.a * 255.0 + 0.5);
    if (material > 75 && material < 125) {
        // Moss curtains stir at their lower ends.
        rel.xz += Color.r * 1.6 * vec2(sin(Time * 0.7 + Position.x * 0.031 + Position.z * 0.017),
                                       cos(Time * 0.55 + Position.z * 0.029));
    }
    vec4 view = ModelViewMat * vec4(rel, 1.0);
    gl_Position = ProjMat * view;
    // No far plane: the tree is thousands of blocks away, far past the game's own.
    gl_Position.z = (logDepth(gl_Position.w) * 2.0 - 1.0) * gl_Position.w;
    float d = max(length(rel), 1e-3);
    vHaze = haze(rel / d, d);
    vWorld = Eye + rel;
    vRel = rel;
    vColor = Color;
    vNormal = Normal;
}
