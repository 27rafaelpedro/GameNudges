#version 330

uniform sampler2D InSampler;
in vec2 texCoord;

layout(std140) uniform SaturationConfig {
    float SaturationMultiplier;
};

out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    vec3 adjusted = mix(vec3(luminance), color.rgb, SaturationMultiplier);
    fragColor = vec4(adjusted, color.a);
}
