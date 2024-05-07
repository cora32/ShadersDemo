package io.iskopasi.shader_test.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.SurfaceView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.ExecutorCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner


class Camera2Impl : DefaultLifecycleObserver {
    private lateinit var _cameraThread: HandlerThread
    private lateinit var _handler: Handler

//    class ReduceComplexComponent : LifecycleObserver{
//
//        registerLifecycle(lifecycle : Lifecycle){
//            lifecycle.addObserver(this)
//        }
//
//        @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
//        fun resume() {
//            Log.d("OnResume","ON_RESUME")
//        }
//
//        @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
//        fun pause() {
//            Log.d("onPause","ON_PAUSE")
//        }
//    }

    init {
        createCameraThread()
    }

    private fun createCameraThread() {
        _cameraThread = HandlerThread("Camera thread").apply { start() }
        _handler = Handler(_cameraThread.looper)
    }

    fun getCameraCaptureCallback(surface: Surface) = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            // Build request to the camera device to start streaming data into surface
            val request = session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                addTarget(surface)
            }.build()

            // Request data endlessly
            session.setRepeatingRequest(request, null, null)
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            "Camera config failed".e
        }
    }

    private fun getCameraCallback(context: Context, surface: Surface) =
        object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    camera.createCaptureSession(
                        SessionConfiguration(
                            SessionConfiguration.SESSION_REGULAR,
                            listOf(OutputConfiguration(surface)),
                            ExecutorCompat.create(_handler),
                            getCameraCaptureCallback(surface)
                        )
                    )
                } else {
                    camera.createCaptureSession(
                        listOf(surface),
                        getCameraCaptureCallback(surface),
                        _handler
                    )
                }
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                "Error: $camera $error".e
                camera.close()
            }
        }

    override fun onResume(owner: LifecycleOwner) {
        "-->> onResume".e
        createCameraThread()
    }

    override fun onPause(owner: LifecycleOwner) {
        "-->> onPause".e
        _cameraThread.quitSafely()
        try {
            _cameraThread.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun bind(context: Context, surfaceView: SurfaceView) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            "Permission check failed.".e
            return
        }

        bg {
            ContextCompat.getSystemService(context, CameraManager::class.java)
                ?.let { cameraManager ->
                    // Select front-facing camera
                    val cameraId = cameraManager.cameraIdList.first {
                        cameraManager
                            .getCameraCharacteristics(it)
                            .get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT
                    }

                    cameraManager.openCamera(
                        cameraId,
                        getCameraCallback(context, surfaceView.holder.surface),
                        _handler
                    )
                }
        }
    }
}

fun SurfaceView.bindCamera(context: Context, lifecycleOwner: LifecycleOwner): SurfaceView {
    Camera2Impl().bind(context, this)

    return this
}