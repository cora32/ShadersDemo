package io.iskopasi.shader_test.utils

import android.os.Build
import androidx.annotation.RequiresApi
import org.intellij.lang.annotations.Language

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
enum class Shaders(val shaderName: String, val shaderHolder: RuntimeShaderHolder) {
    BoxBlurShader("Box blur", RuntimeShaderHolder(BLUR_SHADER)),
    GoogleBlurShader("Google blur", EmptyShader()),
    GradientBorderShader(
        "Gradient border",
        GradientBorderRuntimeShaderHolder(GRADIENT_BORDER_SHADER)
    ),
    TestShader("Test shader", TestRuntimeShaderHolder(CUSTOM_SHADER)),
    BlurBorderShader(
        "Blur + border shader",
        ChainShaderHolder(listOf(BoxBlurShader, GradientBorderShader))
    ),
}


@Language("AGSL")
val CUSTOM_SHADER = """
            uniform shader inputShader;
            uniform float2 iResolution;
//            layout(color) uniform half4 color;
//            layout(color) uniform half4 color2;
        
            half4 main(in float2 fragCoord) {
                vec4 color1 = inputShader.eval(vec2(0, iResolution.y / 2));
                vec4 color2 = inputShader.eval(vec2(iResolution.x, iResolution.y / 2));
                
                float2 uv = fragCoord/iResolution.xy;
                float mixValue = distance(uv, vec2(0.5, 0));                
                return mix(color1, color2, mixValue);
            }
""".trimIndent()

@Language("AGSL")
val GRADIENT_BORDER_SHADER = """
            uniform shader inputShader;
            uniform float2 iResolution;
            uniform int lensWidth;
            const vec4 divider = vec4(20);
            
//            vec4 getCircle(vec2 position, vec4 color, float size)
//            {
//                float circle = sqrt(pow(position.x,2.0) + pow(position.y,2.0));
//                circle = smoothstep(size, size+0.003, 1.0-circle);
//                
//                return color * circle;
//            }

           float2 circleXY(float r, float angle) {
                float rads = radians(angle);
                return float2(r * cos(rads), -r * sin(rads));
           }
           
            vec4 circle(vec2 fragCoordxy, vec2 center, float radius, vec3 color) {
            	float d = length(center - fragCoordxy) - radius;
            	float t = saturate(d);
            	return vec4(color, 1);
            }
            
            vec4 getArcAvgColorI(float radius2, float2 center) {
                vec4 result = vec4(0, 0, 0, 0);
                
                for(int i = 0; i < 90; i += 2) {
                    float2 p1 = circleXY(radius2, float(i)) + center;
                    vec4 sample = inputShader.eval(vec2(p1.x, p1.y));
                    result += sample;
                }
                
                result /= divider;
                
                return vec4(result.rgb, 1);
            }
            
            vec4 getArcAvgColorII(float radius2, float2 center) {
                vec4 result = vec4(0, 0, 0, 0);
                
                for(int i = 90; i < 180; i += 2) {
                    float2 p1 = circleXY(radius2, float(i)) + center;
                    vec4 sample = inputShader.eval(vec2(p1.x, p1.y));
                    result += sample;
                }
                
                result /= divider;
                
                return vec4(result.rgb, 1);
            }
            
            vec4 getArcAvgColorIII(float radius2, float2 center) {
                vec4 result = vec4(0, 0, 0, 0);
                
                for(int i = 180; i < 270; i += 2) {
                    float2 p1 = circleXY(radius2, float(i)) + center;
                    vec4 sample = inputShader.eval(vec2(p1.x, p1.y));
                    result += sample;
                }
                
                result /= divider;
                
                return vec4(result.rgb, 1);
            }
            
            vec4 getArcAvgColorIV(float radius2, float2 center) {
                vec4 result = vec4(0, 0, 0, 0);
                
                for(int i = 270; i < 360; i += 2) {
                    float2 p1 = circleXY(radius2, float(i)) + center;
                    vec4 sample = inputShader.eval(vec2(p1.x, p1.y));
                    result += sample;
                }
                
                result /= divider;
                
                return vec4(result.rgb, 1);
            }
            
            half4 main(in float2 fragCoord) {
                vec2 center = iResolution.xy * 0.5;
                float radius = 0.45 * iResolution.y;
                float radius2 = 0.47 * iResolution.y;
            
//                float2 p1 = circleXY(radius2, 45.0) + center;
//                float2 p2 = circleXY(radius2, 135) + center;
//                float2 p3 = circleXY(radius2, 225) + center;
//                float2 p4 = circleXY(radius2, 315) + center;
                
                vec4 colorI = getArcAvgColorI(radius2, center);
                vec4 colorII = getArcAvgColorII(radius2, center);
                vec4 colorIII = getArcAvgColorIII(radius2, center);
                vec4 colorIV = getArcAvgColorIV(radius2, center);
                
//                vec4 colorI = vec4(1, 0,0,1);
//                vec4 colorII = vec4(1, 1,0,1);
//                vec4 colorIII = vec4(0, 0,1,1);
//                vec4 colorIV = vec4(0, 1,1,1);
                
                float2 uv = fragCoord/iResolution.xy;
                vec2 u_c = vec2(0.5, 0.5);
                float distanceFromLight = length(uv - u_c);
                float ItoIII = distance(uv, vec2(0, 1));
                float IItoIV = distance(uv, vec2(1, 1));
                vec4 borderGradientColor = mix(
                                                mix(colorIII, colorI, ItoIII),
                                                mix(colorIV, colorII, IItoIV),  
                                                uv.x);
                
                // Border
                vec4 layer1 = circle(fragCoord.xy, center, radius, borderGradientColor.rgb);
                 
                // Underlying picture 
                vec4 layer2 = inputShader.eval(fragCoord);
                
//                return layer1;
                return mix(layer1, layer2, 1 - distanceFromLight);
            }
""".trimIndent()


@Language("AGSL")
val BLUR_SHADER = """
            uniform shader inputShader;
            uniform float2 iResolution;

    const vec3 offset = vec3(-1, 0, 1);
    const mat3 box_blur = mat3(1, 1, 1, 1, 1, 1, 1, 1, 1) * 0.1111;
    const mat3 gaussian_blur = mat3(1, 2, 1, 2, 4, 2, 1, 2, 1) * 0.0625;
    const mat3 sharpen = mat3(0, -1, 0, -1, 5, -1, 0, -1, 0);

    vec4 main(in float2  coords) { 
    // Normalized pixel coordinates (from 0 to 1)
//        vec2 uv = coords / iResolution.xy;
        vec4 currValue = vec4(0);
//
//const int radius = 3;
//
//    for(int x = 0; x < radius; x++) {
//        for(int y = 0; y < radius; y++) {
//    //            vec2 offset = vec2(x, y) / iResolution.xy;
////                currValue += (inputShader.eval(coords + vec2(offset[x], offset[y])) * box_blur[x][y]);
////                currValue += (inputShader.eval(vec2(coords.x + offset[x], coords.y + offset[y])) * box_blur[x][y]);
//                currValue += (inputShader.eval(vec2(coords.x + offset[x], coords.y + offset[y])) * gaussian_blur[x][y]);
//        }
//    }
//        currValue /= 9.0;
//
    const int radius = 20;
    const int rStart = int(-radius / 2);
//
    for(int x = 0; x < radius; x++) {
        float xOffset = float(rStart + x);

        for(int y = 0; y < radius; y++) {
            float yOffset = float(rStart + y);         
            currValue += (inputShader.eval(vec2(coords.x + xOffset, coords.y + yOffset)));
        }
    }
        currValue /= float(radius * radius);

        return currValue;
    }
""".trimIndent()