package io.iskopasi.shader_test

import androidx.compose.runtime.mutableStateOf

enum class Shaders(val shaderName: String) {
    BoxBlurShader("Box blur"),
    GoogleBlurShader("Google blur"),
}

class DrawerController {
    var currentShader = mutableStateOf(Shaders.BoxBlurShader)

    fun onShaderClick(shader: Shaders) {
        currentShader.value = shader
    }
}