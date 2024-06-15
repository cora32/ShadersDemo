#extension GL_OES_EGL_image_external: require
         precision highp float;
uniform samplerExternalOES u_texture;
varying vec2 v_textureCoord;

uniform vec2 iResolution;
uniform int orientation;
uniform float iTime;
uniform float iRand;

void main() {
    gl_FragColor = texture2D(u_texture, v_textureCoord);
}