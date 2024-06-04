package io.iskopasi.shader_test.ui.composables

import android.content.Context
import android.os.Build
import android.view.SurfaceHolder
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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.iskopasi.shader_test.DrawerController
import io.iskopasi.shader_test.utils.camera_utils.AutoFitSurfaceView
import io.iskopasi.shader_test.utils.camera_utils.CameraController2
import io.iskopasi.shader_test.utils.camera_utils.InitCallback
import io.iskopasi.shader_test.utils.e
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun CameraView(controller: DrawerController) {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
//    var cameraController: Camera2Controller? = null
//    val view = remember {
//        GLSurfaceView(context).apply {
//            cameraController = Camera2Controller(this, isFront, lifecycleOwner)
//        }
//    }

    var cameraController: CameraController2? = null
    fun getLifecycleObserver() = object : DefaultLifecycleObserver {

        override fun onStart(owner: LifecycleOwner) {
            super.onStart(owner)
            cameraController?.onStart()
        }

        override fun onResume(owner: LifecycleOwner) {
            cameraController?.onResume()
        }

        override fun onPause(owner: LifecycleOwner) {
            cameraController?.onPause()
        }

        override fun onStop(owner: LifecycleOwner) {
            cameraController?.onStop()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            super.onDestroy(owner)
            cameraController?.onDestroy()
        }
    }

    fun getSurfaceCallback(view: AutoFitSurfaceView) = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            "--> surfaceCreated".e
            // To ensure that size is set, initialize camera in the view's thread
            view.post {
                cameraController = CameraController2(
                    isFront = false,
                    view.context.applicationContext,
                    view,
                    holder.surface
                ).apply {
                    addCallbackListener(object : InitCallback {
                        override fun onInitialized() {
                            super.onInitialized()
                        }
                    })
                }
            }
        }

        override fun surfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            "--> surfaceChanged".e
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            "--> surfaceChanged".e
            cameraController?.onSurfaceDestroyed()
        }
    }

    val view = remember {
        AutoFitSurfaceView(context).also { view ->
            view.holder.addCallback(getSurfaceCallback(view))
            lifecycleOwner.lifecycle.addObserver(getLifecycleObserver())
        }
    }


    AndroidView(
        factory = { view },
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                cameraController!!.startVideoRec(context)
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