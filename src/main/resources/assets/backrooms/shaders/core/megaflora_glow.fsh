#version 150

// The heavens of Level 227 (Level227Heavens). Each quad says what it is in its colour's alpha:
//  10 glare   the Sun's wide blinding glow through the mist (stronger the thicker the mist)
//  20 halo    a faint iridescent ring round it, shimmering slowly (red: a phase)
//  30 core    the Sun itself: a tiny disc of pure white
//  40 beam    a hair-thin light beam from the Sun to a bubble, pulses racing along it (red: a phase)
//  50 bubble  a sphere mirroring another level (Sampler0: that level's panorama), with an
//             iridescent soap-film rim and a highlight (red: a per-bubble offset)
//  60 shaft   a far sunbeam: a soft white column (red: how much of it shows)
// Output is premultiplied: glows add light (alpha 0), bubbles cover what's behind them.

uniform sampler2D Sampler0;
uniform vec4 FogColor;     // alpha = how much mist there is at most
uniform float Time;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

const float TAU = 6.2831853;

vec3 iridescent(float hue) {
    return 0.55 + 0.45 * cos(TAU * (hue + vec3(0.0, 0.33, 0.67)));
}

void main() {
    int kind = int(vColor.a * 255.0 + 0.5);
    float mist = FogColor.a;
    float through = 1.0 - mist * 0.75;
    float r = length(vUv);
    vec4 outColor;
    if (kind < 15) {
        float glow = 0.55 * exp(-r * 9.0) + 0.25 * exp(-r * 3.2) + 0.08 * (1.0 - r);
        glow *= (1.0 - smoothstep(0.8, 1.0, r)) * (0.7 + 0.5 * mist);
        vec3 c = mix(vec3(1.0, 1.0, 0.97), FogColor.rgb * 1.3, 0.2);
        outColor = vec4(c * glow, 0.0);
    } else if (kind < 25) {
        float ra = (r - 0.72) / 0.05;
        float rb = (r - 0.48) / 0.10;
        float ring = exp(-ra * ra) * 0.22 + exp(-rb * rb) * 0.08;
        float angle = atan(vUv.y, vUv.x);
        float shimmer = 0.8 + 0.2 * sin(angle * 11.0 - Time * 0.8 + vColor.r * TAU);
        vec3 c = iridescent(r * 3.0 + Time * 0.04 + 0.15 * sin(angle * 3.0 + Time * 0.3));
        outColor = vec4(c * ring * shimmer * through, 0.0);
    } else if (kind < 35) {
        float disc = 1.0 - smoothstep(0.30, 0.38, r);
        float corona = exp(-r * 6.0) * 0.6;
        // The disc itself burns through any mist at full white; only its corona is softened.
        outColor = vec4(vec3(disc + corona * (1.0 - mist * 0.5)), 0.0);
    } else if (kind < 45) {
        float along = vUv.y;
        float profile = exp(-vUv.x * vUv.x * 4.0);
        float pulse = 0.55 + 0.45 * sin(along * 60.0 - Time * 14.0 + vColor.r * TAU);
        vec3 c = mix(vec3(1.0, 0.98, 0.95), iridescent(along + vColor.r), along * 0.5);
        outColor = vec4(c * profile * pulse * 0.4 * (0.6 + 0.4 * along) * through, 0.0);
    } else if (kind < 55) {
        float r2 = dot(vUv, vUv);
        if (r2 > 1.0) {
            discard;
        }
        float nz = sqrt(1.0 - r2);
        vec3 n = vec3(vUv, nz);
        // What the sphere mirrors: the view reflected off it, looked up in the level's panorama,
        // slowly turning as the bubble spins.
        vec3 refl = vec3(2.0 * nz * n.x, 2.0 * nz * n.y, 2.0 * nz * nz - 1.0);
        float u = atan(refl.x, -refl.z) / TAU + 0.5 + vColor.r + Time * 0.03;
        float v = 0.5 - asin(clamp(refl.y, -1.0, 1.0)) / 3.1415927;
        vec3 seen = texture(Sampler0, vec2(u, v)).rgb;
        float fresnel = pow(1.0 - nz, 3.0);
        vec3 c = mix(seen * 0.95, iridescent(fresnel * 1.4 + Time * 0.05 + vColor.r), 0.35 + 0.5 * fresnel);
        c += vec3(pow(max(dot(n, normalize(vec3(-0.4, 0.6, 0.7))), 0.0), 40.0) * 0.8);
        c = mix(c, FogColor.rgb, mist * 0.3);
        float alpha = mix(0.55, 0.95, fresnel) * through * (1.0 - smoothstep(0.92, 1.0, r2));
        outColor = vec4(c * alpha, alpha);
    } else {
        // A soft glow, no hard core: brightest down the middle, fading smoothly to nothing at the
        // sides, and dissolving into the mist at its top and where it meets the ground.
        float profile = exp(-vUv.x * vUv.x * 3.5) * (1.0 - vUv.x * vUv.x);
        float ends = smoothstep(0.0, 0.25, vUv.y) * smoothstep(1.0, 0.9, vUv.y);
        float glow = profile * ends * vColor.r * 0.28 * through;
        outColor = vec4(vec3(1.0, 0.99, 0.94) * glow, 0.0);
    }
    fragColor = outColor;
}
