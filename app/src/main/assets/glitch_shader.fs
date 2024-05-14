#extension GL_OES_EGL_image_external : require
         precision mediump float;
         uniform samplerExternalOES u_texture;
         varying vec2 v_textureCoord;


        uniform float iTime;
//        uniform float progress;
//        uniform shader inputShader;
        uniform vec2 iResolution;
    //    const vec3 offset = vec3(-15, 0, 13);
    //    const mat3 edge = mat3(-1, -1, -2, -3, 15, -3, -2, -1, -1);
        const mat3 edge = mat3(3, -3, -1, -4, 16, -4, -1, -3, 3);
        const mat3 non = mat3(1, 1, 1, 1, 1, 1, 1, 1, 1);
        const int radius = 3;

        float rand(vec2 fragCoord){
            return fract(sin(dot(fragCoord, vec2(12.9898, 78.233))) * 43758.5453);
        }

         void main() {
            vec4 value = vec4(0, 0, 0, 1);
            float r0 = rand(v_textureCoord + iTime);
            float randFloat = r0 * 300.0;
            int r1Sign = 1;
            int r2Sign = 1;
            int r3Sign = 1;


            if(mod(r0, 2.0) == 0.0) r1Sign = -1;
            if(mod(r0, 3.0) == 0.0) r2Sign = -1;
            if(mod(r0, 5.0) == 0.0) r3Sign = -1;

        // Line shift value
        int lineG = 0;
//        if(v_textureCoord.y > randFloat && v_textureCoord.y < (randFloat + 15.0)) {
//            lineG = -200;
//        }

        int r1 = int(rand(v_textureCoord + iTime) * 100.0) * r1Sign;
        int r2 = int(rand(v_textureCoord + iTime + float(r1)) * 10.0) * r2Sign;
        int r3 = int(rand(v_textureCoord + iTime + float(r2)) * 100.0) * r3Sign;
        vec3 offset = vec3(-15 + r1 + lineG, r2 + lineG, 13 + r3 + lineG);

        // Sort of edge detection
        for(int x = 0; x < radius; x++) {
            for(int y = 0; y < radius; y++) {
                vec2 coord = vec2(v_textureCoord.x + offset[x], v_textureCoord.y + offset[y]);
                value += texture2D(u_texture, coord) * float(edge[x][y]);
            }
        }
        value /= 9.0;
        value.a = 1.0;

        // Removing brightness
        if(
            value.r > 0.5 &&
            value.g > 0.5 &&
            value.b > 0.5
            ) {
                value.r = 0.0;
                value.g = 0.0;
                value.b = 0.0;
            }

        // Increasing green component
        if(
            value.r > 0.2 &&
            value.g > 0.2 &&
            value.b > 0.2
            ) {
                value.r -= 0.1;
                value.g += (r0 * float(r1Sign) * 2.0);
                value.b -= 0.1;
            } else {
                value.r -= 0.1;
                value.b -= 0.1;
            }
        gl_FragColor = value;
//        return value;
//           gl_FragColor = texture2D(u_texture, v_textureCoord);
//           gl_FragColor.r = 1.0;
         }