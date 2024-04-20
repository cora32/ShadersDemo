package io.iskopasi.shader_test.utils

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
open class RuntimeShaderHolder(private val shader: String) {
    val runtimeShader by lazy { RuntimeShader(shader) }

    open fun setParams(width: Float, height: Float) {
        runtimeShader.setInputShader("inputShader", runtimeShader)
        runtimeShader.setFloatUniform("iResolution", width, height)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class EmptyShader :
    RuntimeShaderHolder("")

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class GradientBorderRuntimeShaderHolder(shader: String) :
    RuntimeShaderHolder(shader) {

    fun setParams(width: Float, height: Float, lensWidth: Int) {
        super.setParams(width, height)

        runtimeShader.setIntUniform("lensWidth", lensWidth)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class TestRuntimeShaderHolder(shader: String) :
    RuntimeShaderHolder(shader) {

    fun setParams(width: Float, height: Float, thirdParam: Int) {
        super.setParams(width, height)

//        runtimeShader.setColorUniform("color", android.graphics.Color.BLUE)
//        runtimeShader.setColorUniform("color2", android.graphics.Color.CYAN)
//        runtimeShader.setIntUniform("thirdParam", thirdParam)
    }
}
