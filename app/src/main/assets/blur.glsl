#extension GL_OES_EGL_image_external: require
         precision highp float;
uniform samplerExternalOES u_texture;
varying vec2 v_textureCoord;

uniform vec2 iResolution;
uniform int orientation;
uniform float iTime;
uniform float iRand;

//const vec3 offset = vec3(0.001, 0.0, -0.001);
const mat3 edge = mat3(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0) * 0.1111;
const mat3 gaussian_blur = mat3(1.0, 2.0, 1.0, 2.0, 4.0, 2.0, 1.0, 2.0, 1.0) * 0.0625;

void main() {
    vec4 value = vec4(0.0, 0.0, 0.0, 1.0);

    const int radius = 7;
    const float step = 0.002;

    for (int x = 0; x < radius; x++) {
        float xOffset = float(step * float(x));

        for (int y = 0; y < radius; y++) {
            float yOffset = float(step * float(y));

            vec2 coord = vec2(v_textureCoord.x + xOffset, v_textureCoord.y + yOffset);
            value += texture2D(u_texture, coord);
        }
    }
    value /= float(radius * radius);
    value.a = 1.0;

    //    gl_FragColor = texture2D(u_texture, v_textureCoord);
    gl_FragColor = value;
}