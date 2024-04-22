package io.iskopasi.shader_test

import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.iskopasi.shader_test.utils.Shaders

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class DrawerController : ViewModel() {
    var currentShader = mutableStateOf(Shaders.entries.first())

    //        var currentShader = mutableStateOf(Shaders.GlitchShader)
    val bitmap = mutableStateOf(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
    val bitmapBig = mutableStateOf(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))

    fun onShaderClick(shader: Shaders) {
        currentShader.value = shader
    }
}