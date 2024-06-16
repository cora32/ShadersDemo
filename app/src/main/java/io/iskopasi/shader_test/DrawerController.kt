package io.iskopasi.shader_test

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.iskopasi.shader_test.utils.Shaders
import io.iskopasi.shader_test.utils.bg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class DrawerController : ViewModel() {
    var currentShader = mutableStateOf(Shaders.entries.first())
    var currentShaderFlow: MutableStateFlow<Shaders> = MutableStateFlow(Shaders.entries.first())
    var cameraEnabled = mutableStateOf(false)

    val bitmap = mutableStateOf(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
    val bitmapBig = mutableStateOf(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))

    fun onShaderClick(shader: Shaders) {
        currentShader.value = shader
        currentShaderFlow.update {
            shader
        }
    }

    fun onCameraViewSwitch(enabled: Boolean) {
        cameraEnabled.value = enabled
    }

    fun onShaderUpdate(listener: (Shaders) -> Unit) {
        bg {
            currentShaderFlow.onEach {
                listener(it)
            }.collect()
        }
    }
}