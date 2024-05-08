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
import android.view.OrientationEventListener
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.ExecutorCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner


class Camera2Impl() : DefaultLifecycleObserver {
    private lateinit var _cameraThread: HandlerThread
    private lateinit var _handler: Handler

    private lateinit var session: CameraCaptureSession
    private lateinit var cameraDevice: CameraDevice
    private lateinit var cameraCharacteristic: CameraCharacteristics

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

    private fun closeCamera() {
        session.close()
        cameraDevice.close()
    }

    private fun createCameraThread() {
        _cameraThread = HandlerThread("Camera thread").apply { start() }
        _handler = Handler(_cameraThread.looper)
    }

    private fun getJpegOrientation(
        orientation: Int
    ): Int {
        var deviceOrientation = orientation
        if (deviceOrientation == OrientationEventListener.ORIENTATION_UNKNOWN) return 0
        val sensorOrientation =
            cameraCharacteristic.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

        // Round device orientation to a multiple of 90
        deviceOrientation = (deviceOrientation + 45) / 90 * 90

        // Reverse device orientation for front-facing cameras
        deviceOrientation = -deviceOrientation

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        return (sensorOrientation + deviceOrientation + 360) % 360
    }

    fun getCameraCaptureCallback(surface: Surface, rotation: Int) =
        object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                // Build request to the camera device to start streaming data into surface
                val request =
                    session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                        set(
                            CaptureRequest.JPEG_ORIENTATION,
                            getJpegOrientation(rotation)
                        )
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
            @RequiresApi(Build.VERSION_CODES.R)
            override fun onOpened(cameraDevice: CameraDevice) {
                val rotation = ContextCompat.getDisplayOrDefault(context).rotation

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    cameraDevice.createCaptureSession(
                        SessionConfiguration(
                            SessionConfiguration.SESSION_REGULAR,
                            listOf(OutputConfiguration(surface)),
                            ExecutorCompat.create(_handler),
                            getCameraCaptureCallback(surface, rotation)
                        )
                    )
                } else {
                    cameraDevice.createCaptureSession(
                        listOf(surface),
                        getCameraCaptureCallback(surface, rotation),
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

    fun bind(context: Context, surface: Surface) {
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

                    cameraCharacteristic = cameraManager.getCameraCharacteristics(cameraId)

                    cameraManager.openCamera(
                        cameraId,
                        getCameraCallback(context, surface),
                        _handler
                    )
                }
        }
    }
}

fun Surface.bindCamera(context: Context, lifecycleOwner: LifecycleOwner): Surface {
    Camera2Impl().bind(context, this)

    return this
}