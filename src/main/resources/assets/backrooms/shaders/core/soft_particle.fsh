#version 150

// Unlike the vanilla particle shader this keeps faint pixels (vanilla throws away everything
// below 10% opacity), so a wisp of steam can fade out smoothly instead of crumbling into specks.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.004) {
        discard;
    }
    float fog = vertexDistance <= FogStart ? 0.0 : vertexDistance < FogEnd ? smoothstep(FogStart, FogEnd, vertexDistance) : 1.0;
    fragColor = vec4(mix(color.rgb, FogColor.rgb, fog * FogColor.a), color.a);
}
