package io.iskopasi.shader_test

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import org.intellij.lang.annotations.Language

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
enum class Shaders(val shaderName: String, val shaderHolder: RuntimeShaderHolder) {
    BoxBlurShader("Box blur", BoxBlurRuntimeShaderHolder(RuntimeShader(BLUR_SHADER))),
    GoogleBlurShader("Google blur", NativeBlurShaderHolder(null)),
    TestShader("Test shader", TestRuntimeShaderHolder(RuntimeShader(CUSTOM_SHADER))),
}

open class RuntimeShaderHolder(open val shader: RuntimeShader?) {
    open fun setParams(params: Map<String, Any>) {
    }

}


data class NativeBlurShaderHolder(override val shader: RuntimeShader?) :
    RuntimeShaderHolder(shader) {

}

data class TestRuntimeShaderHolder(override val shader: RuntimeShader) :
    RuntimeShaderHolder(shader) {
    override fun setParams(params: Map<String, Any>) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val width = params["width"] as Float
            val height = params["height"] as Float
//            val color1 = params["color"] as android.graphics.Color
//            val color2 = params["color2"] as android.graphics.Color

//            shader.setInputShader("inputShader", shader)
            shader.setFloatUniform("iResolution", width, height)
            shader.setColorUniform("color", android.graphics.Color.BLUE)
            shader.setColorUniform("color2", android.graphics.Color.CYAN)
        }
    }
}

data class BoxBlurRuntimeShaderHolder(override val shader: RuntimeShader) :
    RuntimeShaderHolder(shader) {
    override fun setParams(params: Map<String, Any>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val width = params["width"] as Float
            val height = params["height"] as Float

            shader.setInputShader("inputShader", shader)
            shader.setFloatUniform("iResolution", width, height)
        }
    }
}


@Language("AGSL")
val CUSTOM_SHADER = """
            uniform shader inputShader;
            uniform float2 iResolution;
            layout(color) uniform half4 color;
            layout(color) uniform half4 color2;
        
            half4 main(in float2 fragCoord) {
                float2 uv = fragCoord/iResolution.xy;
        
                float mixValue = distance(uv, vec2(0.5, 1));
                return mix(color, color2, mixValue);
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