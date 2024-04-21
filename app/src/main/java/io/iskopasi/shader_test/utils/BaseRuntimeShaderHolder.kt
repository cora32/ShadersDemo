package io.iskopasi.shader_test.utils

import android.graphics.RenderEffect
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

    open fun compose(): RenderEffect = RenderEffect
        .createRuntimeShaderEffect(
            runtimeShader,
            "inputShader"
        )
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

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class ChainShaderHolder(val shaderList: List<Shaders>) :
    RuntimeShaderHolder("") {

    override fun setParams(width: Float, height: Float) {
        for (shader in shaderList) {
            shader.shaderHolder.setParams(width, height)
        }
    }

    override fun compose(): RenderEffect {
        var effect: RenderEffect = shaderList.first().shaderHolder.compose()

        for (i in 1..<shaderList.size) {
//        for (i in shaderList.size - 2 downTo 0) {
            val nextEffect = shaderList[i].shaderHolder.compose()
            effect = RenderEffect.createChainEffect(nextEffect, effect)
        }

        return effect
    }
}
