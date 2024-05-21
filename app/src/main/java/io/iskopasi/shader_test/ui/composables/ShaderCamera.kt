package io.iskopasi.shader_test.ui.composables

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Build
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.iskopasi.shader_test.DrawerController
import io.iskopasi.shader_test.utils.Camera2Controller
import io.iskopasi.shader_test.utils.bindCamera
import io.iskopasi.shader_test.utils.getSurface
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun CameraView(controller: DrawerController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isFront = false
    var cameraController: Camera2Controller? = null
    var renderer: MyGLRenderer? = null
    val view = remember {
        GLSurfaceView(context).apply {
            renderer = getSurface(isFront)
            val surface = Surface(renderer!!.mSurfaceTexture)
            cameraController = surface.bindCamera(
                context,
                lifecycleOwner,
                isFront
            )
        }
    }

//    val view = SurfaceView(context).apply {
//        holder.surface.bindCamera(context, lifecycleOwner)
//    }

    AndroidView(
        factory = { view },
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                cameraController!!.snapshot(context)
                renderer!!.takeScreenshot()
            }
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun CameraViewX(controller: DrawerController) {
    val lensFacing = CameraSelector.LENS_FACING_BACK
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val preview = Preview.Builder().build()
    val previewView = remember {
        PreviewView(context)
    }
    val cameraxSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraxSelector, preview)
        preview.setSurfaceProvider(previewView.surfaceProvider)
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { cameraProvider ->
            cameraProvider.addListener({
                continuation.resume(cameraProvider.get())
            }, ContextCompat.getMainExecutor(this))
        }
    }