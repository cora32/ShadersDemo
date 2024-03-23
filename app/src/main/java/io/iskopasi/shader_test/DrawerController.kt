package io.iskopasi.shader_test

import android.graphics.Picture
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.iskopasi.shader_test.utils.Shaders

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class DrawerController : ViewModel() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    var currentShader = mutableStateOf(Shaders.entries.first())
    val picture = mutableStateOf(Picture())

    fun onShaderClick(shader: Shaders) {
        currentShader.value = shader
    }
}