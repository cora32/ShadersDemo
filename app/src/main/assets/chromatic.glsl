#extension GL_OES_EGL_image_external: require
         precision highp float;
uniform samplerExternalOES u_texture;
varying vec2 v_textureCoord;

uniform vec2 iResolution;
uniform int orientation;
uniform float iTime;
uniform float iRand;
const mat3 edge = mat3(30, -5, -10, -15, -1, -12, -5, -10, 21);
const mat3 edge2 = mat3(-8, 0, -1, 0, 0, 0, 0, 1, 5);
const int radius = 3;
const vec3 offset = vec3(0.005, 0.001, -0.001);
const vec3 offset2 = vec3(0.003, 0.001, 0);

void main() {
    vec4 value = vec4(0.0, 0.0, 0.0, 1.0);
    vec4 value2 = vec4(0.0, 0.0, 0.0, 1.0);

    for (int x = 0; x < radius; x++) {
        for (int y = 0; y < radius; y++) {
            vec2 coord = vec2(v_textureCoord.x + offset[x], v_textureCoord.y + offset[y]);
            value += texture2D(u_texture, coord) * float(edge[x][y]);

            coord = vec2(v_textureCoord.x + offset2[x], v_textureCoord.y + offset2[y]);
            value2 += texture2D(u_texture, coord) * float(edge2[x][y]);
        }
    }
    value.a = 1.0;
    value2.a = 1.0;
    value.r *= 2.0;
    value2.b *= 2.0;

    gl_FragColor = vec4(value.r, 0.0, value2.b, 1.0);

}