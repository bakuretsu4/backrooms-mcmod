#version 150

// Bloom, step 1: keep only pixels that are (nearly) pure white - in practice the light fixtures,
// which render full-bright. Lit walls (#CCB633 is ~0.69 luminance) stay out, so they never haze.
// Four slightly spread taps average out single-pixel sparkle before blurring.

uniform sampler2D DiffuseSampler;
uniform float Threshold; // luminance where glow starts (0.92); fully on at 1.0

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

vec3 bright(vec2 uv) {
    vec3 c = texture(DiffuseSampler, uv).rgb;
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    return c * smoothstep(Threshold, 1.0, l);
}

void main() {
    vec2 o = oneTexel * 0.75;
    vec3 s = bright(texCoord + vec2(-o.x, -o.y)) + bright(texCoord + vec2(o.x, -o.y))
           + bright(texCoord + vec2(-o.x, o.y)) + bright(texCoord + vec2(o.x, o.y));
    fragColor = vec4(s * 0.25, 1.0);
}
