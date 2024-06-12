package io.iskopasi.shader_test.ui.composables

import android.content.Context
import android.os.Build
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoCameraBack
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.iskopasi.shader_test.DrawerController
import io.iskopasi.shader_test.utils.OrientationListener
import io.iskopasi.shader_test.utils.bg
import io.iskopasi.shader_test.utils.camera_utils.AutoFitSurfaceView
import io.iskopasi.shader_test.utils.camera_utils.CameraController2
import io.iskopasi.shader_test.utils.e
import io.iskopasi.shader_test.utils.rotation
import kotlinx.coroutines.delay
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

    val cameraController = CameraController2(
        isFront = false,
        context = context.applicationContext,
        orientation = context.rotation
    )

    val orientationListener: OrientationListener by lazy {
        "--> creating orientationListener".e
        object : OrientationListener(context) {
            override fun onSimpleOrientationChanged(orientation: Int, currentOrientation: Int) {
                cameraController.onOrientationChanged(orientation, currentOrientation, context)
            }
        }
    }

    fun getLifecycleObserver() = object : DefaultLifecycleObserver {

        override fun onStart(owner: LifecycleOwner) {
            cameraController.onStart()
            orientationListener.enable()
        }

        override fun onResume(owner: LifecycleOwner) {
            cameraController.onResume()
        }

        override fun onPause(owner: LifecycleOwner) {
            cameraController.onPause()
        }

        override fun onStop(owner: LifecycleOwner) {
            cameraController.onStop()
            orientationListener.disable()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            super.onDestroy(owner)
            cameraController.onDestroy()
        }
    }

    fun getSurfaceCallback(view: AutoFitSurfaceView) = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            "--> surfaceCreated".e
            // To ensure that size is set, initialize camera in the view's thread
            view.post {
                cameraController.init(holder.surface, view)
            }
        }

        override fun surfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            "--> surfaceDestroyed".e
            cameraController.onSurfaceDestroyed()
        }
    }

    val view = remember {
        AutoFitSurfaceView(context).also { view ->
            view.holder.addCallback(getSurfaceCallback(view))
            lifecycleOwner.lifecycle.addObserver(getLifecycleObserver())
        }
    }

    AndroidView(
        factory = {
            view
        },
        modifier = Modifier
            .fillMaxSize()
    )

    Box(modifier = Modifier.fillMaxHeight()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
        ) {
            TimerView(cameraController)
            Controls(cameraController, view)
        }
    }
}

@Composable
fun TimerView(cameraController: CameraController2) {
    Text(text = cameraController.timerValue.value)
}

@Composable
fun Controls(cameraController: CameraController2, view: AutoFitSurfaceView) {
    val ctx = LocalContext.current
    val shape = remember {
        RoundedCornerShape(16.dp)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .padding(bottom = 70.dp, start = 48.dp, end = 16.dp)
            .border(
                border = BorderStroke(1.dp, color = Color.DarkGray),
                shape = shape
            )
            .background(Color.DarkGray.copy(alpha = 0.5f), shape)
            .fillMaxWidth()
            .height(50.dp)
    ) {
        IconButton(
            onClick = {
                cameraController.isReadyToPhoto.value = false
                cameraController.takePhoto()
                bg {
                    delay(200L)
                    cameraController.isReadyToPhoto.value = true
                }
                view.blink()
            },
            enabled = cameraController.isReadyToPhoto.value
        ) {
            Icon(
                Icons.Rounded.PhotoCamera,
                "",
                tint = if (cameraController.isReadyToPhoto.value) Color.White else Color.Gray,
                modifier = Modifier.size(38.dp)
            )
        }
        IconButton(
            onClick = { cameraController.onChangeCamera(view) },
            enabled = cameraController.isInitialized.value
        ) {
            Icon(
                if (cameraController.isFrontState.value) Icons.Rounded.CameraFront
                else Icons.Rounded.PhotoCameraBack,
                "",
                tint = if (cameraController.isInitialized.value) Color.White else Color.Gray,
                modifier = Modifier.size(38.dp)
            )
        }
        IconButton(
            onClick = {
                cameraController.isReadyToVideo.value = false
                cameraController.startVideoRec(ctx.applicationContext)
                bg {
                    // Need at least a second of video to form a valid mp4
                    delay(1000L)
                    cameraController.isReadyToVideo.value = true
                }
            },
            enabled = cameraController.isInitialized.value && cameraController.isReadyToVideo.value
        ) {
            Icon(
                if (cameraController.recordingStarted.value) Icons.Rounded.Stop
                else Icons.Rounded.Videocam,
                "",
                tint = if (cameraController.isInitialized.value) if (cameraController.recordingStarted.value) Color.Red else Color.White else Color.Gray,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}

private fun AutoFitSurfaceView.blink() {
    post {
        background = android.graphics.Color.argb(150, 255, 255, 255).toDrawable()

        postDelayed({
            background = null
        }, 50L)
    }
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