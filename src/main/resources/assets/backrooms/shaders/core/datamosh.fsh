#version 150

// The datamosh of the noclip transition: the picture behaves like a video stream whose decoder has
// lost its keyframes. Run once per "decoder step" (about 20 a second, whatever the frame rate).
//
// Mode 0, lag: the live picture at a collapsing bitrate (each 16 px macroblock keeps less and less
//   detail, colours band).
// Mode 1, mosh: what was on screen stays, but every macroblock is moved along the camera's motion
//   (P-frames applied to the wrong picture), the new picture's changes are added on top of it
//   (ghost edges of the new place showing through the old one), and more and more blocks are coded
//   fresh from the new picture until it has taken over.

uniform sampler2D PrevOut;   // what the decoder showed last step
uniform sampler2D Scene;     // the game's fresh frame
uniform sampler2D PrevScene; // the game's frame at the previous step

uniform vec2 ScreenSize;
uniform float Mode;          // 0 = lag, 1 = mosh
uniform float Progress;      // 0..1 through the current mode
uniform float Seed;          // decoder step number
uniform vec2 Shift;          // how far the picture moved since the last step (pixels; camera turning)
uniform float BlockSize;     // macroblock size in pixels

out vec4 fragColor;

float hash(vec2 p, float s) {
    return fract(sin(dot(p, vec2(127.1, 311.7)) + s * 74.7) * 43758.5453);
}

vec3 fetch(sampler2D tex, vec2 px) {
    px = clamp(px, vec2(0.5), ScreenSize - 0.5);
    return texture(tex, px / ScreenSize).rgb;
}

void main() {
    vec2 px = gl_FragCoord.xy;
    vec2 block = floor(px / BlockSize);
    vec2 local = px - block * BlockSize;

    // Bitrate: how many cells of detail a macroblock keeps across (1, 2, 4, 8 or 16), and how
    // many steps each colour channel has. Falls during the lag, recovers at the end of the mosh.
    float quality = Mode < 0.5 ? mix(16.0, 2.0, Progress) : mix(4.0, 16.0, smoothstep(0.55, 1.0, Progress));
    float cells = exp2(floor(log2(max(quality, 1.0))));
    float cell = BlockSize / cells;
    vec2 at = block * BlockSize + (floor(local / cell) + 0.5) * cell;
    float levels = Mode < 0.5 ? mix(64.0, 12.0, Progress) : mix(20.0, 96.0, smoothstep(0.4, 1.0, Progress));

    vec3 col;
    if (Mode < 0.5) {
        col = fetch(Scene, at);
    } else {
        // Each block moves with the camera (a bit more or less than its neighbours, like the
        // uneven motion vectors of real footage) plus a wobble of its own that changes every few steps.
        float group = floor(Seed / 3.0);
        vec2 wobble = (vec2(hash(block, group), hash(block + 17.0, group)) - 0.5) * BlockSize * 0.6;
        vec2 v = Shift * mix(2.5, 1.2, Progress) * (0.7 + 0.6 * hash(block + 3.0, 0.0)) + wobble * (1.0 - Progress);
        vec3 moved = fetch(PrevOut, at - v);
        // The residual: how the new picture changed, applied to the wrong one.
        vec3 residual = fetch(Scene, at) - fetch(PrevScene, at - Shift);
        col = moved + residual * (0.6 + 0.4 * Progress);
        // Intra refresh: blocks coded fresh from the new picture, rarer at first, then most of them.
        if (hash(block + 31.0, Seed) < 0.02 + 0.45 * Progress * Progress) {
            col = fetch(Scene, at);
        }
    }
    col = floor(clamp(col, 0.0, 1.0) * levels + 0.5) / levels;
    fragColor = vec4(col, 1.0);
}
