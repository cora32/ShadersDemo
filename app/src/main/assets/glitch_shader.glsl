#extension GL_OES_EGL_image_external: require
         precision highp float;
uniform samplerExternalOES u_texture;
varying vec2 v_textureCoord;

uniform vec2 iResolution;
uniform int orientation;
uniform float iTime;
uniform float iRand;
//const mat3 edge = mat3(3, -6, -1, -4, 16, -4, -1, -6, 3);
//const mat3 edge = mat3(3, -12, -4, -4, 28, -4, 2, -9, -2);
const mat3 edge = mat3(3, -12, -4, -4, 34, -4, -5, -9, -2);
//const mat3 edge = mat3(7, -10, -8, -4, 20, -4, -2, -6, 3);
const mat3 non = mat3(1, 1, 1, 1, 1, 1, 1, 1, 1);
const int radius = 3;
const float PHI = 1.61803398874989484820459;  // Φ = Golden Ratio

float rand(vec2 fragCoord) {
    return fract(sin(dot(fragCoord, vec2(12.9898, 78.233))) * 43758.5453);
}

float gold_noise(in vec2 xy, in float seed) {
    return fract(tan(distance(xy * PHI, xy) * seed) * xy.x);
}

highp float rand2(vec2 co)
{
    highp float a = 12.9898;
    highp float b = 78.233;
    highp float c = 43758.5453;
    highp float dt = dot(co.xy, vec2(a, b));
    highp float sn = mod(dt, 3.14);
    return fract(sin(sn) * c);
}

bool isRed(vec4 value) {
    return value.r > 0.8;
}

bool isGreen(vec4 value) {
    return value.g > 0.8;
}

bool isBlue(vec4 value) {
    return value.b > 0.8;
}

bool isYellow(vec4 value) {
    return value.r > 0.8
    && value.g > 0.8;
}

void main() {
    vec4 value = vec4(0.0, 0.0, 0.0, 1.0);
    //    float r0 = rand2(v_textureCoord + iTime * 0.1);
    //    float r1 = gold_noise(v_textureCoord, r0);
    float yRow = 0.0;

    if (orientation == 0) {
        yRow = -1.0 * floor(v_textureCoord.y * 10000.0);
    } else if (orientation == 90) {
        yRow = -1.0 * floor(v_textureCoord.x * 10000.0);
    } else if (orientation == 180) {
        yRow = floor(v_textureCoord.y * 10000.0);
    } else if (orientation == 270) {
        yRow = floor(v_textureCoord.x * 10000.0);
    }

    if (mod(yRow + iTime, 4.0) == 0.0) {
        float r0 = rand2(v_textureCoord + iTime);

        int r1Sign = mod(floor(r0 * 100.0), 2.0) == 0.0 ? -1 : 1;
        int r2Sign = mod(floor(r0 * 100.0), 3.0) == 0.0 ? -1 : 1;
        int r3Sign = mod(floor(r0 * 100.0), 5.0) == 0.0 ? -1 : 1;

        int r1 = int(rand(v_textureCoord + iTime) * 100.0) * r1Sign;
        int r2 = int(rand(v_textureCoord + iTime + float(r1)) * 10.0) * r2Sign;
        int r3 = int(rand(v_textureCoord + iTime + float(r2)) * 100.0) * r3Sign;

        float os0 = -0.01;
        float os1 = 0.0;
        float os2 = 0.01;
        int redFlag = 0;

        // Calculate offsets for edge detector
        if (iRand > 0.98) {
            //            os0 = -0.0101 - rand(v_textureCoord + iRand) / 10000.0;
            //            os1 = 0.01 + rand(v_textureCoord + iRand * 2.0) / 10000.0;
            //            os2 = 0.015 + rand(v_textureCoord + iRand * 5.0) / 100.0;
            os0 = -0.0101;
            os1 = 0.001 + (rand(v_textureCoord + iRand * 2.0) / 5000.0) * float(r1Sign);
            os2 = 0.0015;

            if (iRand > 0.99) {
                os0 = -0.0201 - rand(v_textureCoord + iRand) / 100.0;
                os1 = 0.04 + rand(v_textureCoord + iRand * 2.0) / 100.0;
            }

            redFlag = 1;
        }

        vec3 offset = vec3(os0, os1, os2);

        // Sort of edge detection
        for (int x = 0; x < radius; x++) {
            for (int y = 0; y < radius; y++) {
                vec2 coord = vec2(v_textureCoord.x + offset[x], v_textureCoord.y + offset[y]);
                value += texture2D(u_texture, coord) * float(edge[x][y]);
            }
        }
        value /= 2.0;
        value.a = 1.0;

        // Removing brightness
        if (
        value.r > 0.7 &&
        value.g > 0.7 &&
        value.b > 0.7
        ) {
            value.r = 0.2;
            //            value.g = 0.6;
            value.b = 0.2;

            if (redFlag == 1) {
                value.r = 0.9;
                value.b = 0.2;
            }
        }

        vec4 nValue = texture2D(u_texture, v_textureCoord);
        bool isRed = isRed(nValue);
        bool isGreen = isGreen(nValue);
        bool isBlue = isBlue(nValue);
        bool isYellow = isYellow(nValue);
        // Increasing green component
        if (
        value.r > 0.2 &&
        value.g > 0.2 &&
        value.b > 0.2
        ) {
            value.g = 1.0 - r0 / 10.0;
        }

        if (isRed) {
            value.r += 0.4;
        }
        //        if (isGreen) {
        //            value.g += 0.5;
        //        }
        if (isBlue) {
            value.b += 0.5;
        }
        if (isYellow) {
            value.r += 0.3;
            value.g += 0.3;
        }

        //        value = texture2D(u_texture, v_textureCoord);
    }
    //    value = texture2D(u_texture, v_textureCoord);
    gl_FragColor = value;
}