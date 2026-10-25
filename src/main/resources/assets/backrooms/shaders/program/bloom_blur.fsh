#version 150

// Bloom, step 2: one direction of a 9-tap Gaussian blur. The post chain runs it horizontally then
// vertically at three spacings (1.5, 4.5, 12 px at 1080p), each feeding the next, which gives a
// tight glow, a medium one and a wide soft one. The final pass adds all three back.

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform vec2 BlurDir;  // (1,0) horizontal or (0,1) vertical
uniform float Spacing; // pixels between taps at 1080p; scaled with the window height

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    // Gaussian weights for offsets 0..4 (sigma ~1.75 taps), summing to 1 over -4..4.
    float w[5] = float[](0.2270, 0.1946, 0.1216, 0.0541, 0.0162);
    vec2 stride = BlurDir * oneTexel * Spacing * max(InSize.y / 1080.0, 0.5);
    vec3 sum = texture(DiffuseSampler, texCoord).rgb * w[0];
    for (int i = 1; i < 5; i++) {
        vec2 o = stride * float(i);
        sum += (texture(DiffuseSampler, texCoord + o).rgb + texture(DiffuseSampler, texCoord - o).rgb) * w[i];
    }
    fragColor = vec4(sum, 1.0);
}
