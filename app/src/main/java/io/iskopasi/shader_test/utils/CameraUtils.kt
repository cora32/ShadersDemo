package io.iskopasi.shader_test.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
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
import android.util.Size
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.ExecutorCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner


class Camera2Impl : DefaultLifecycleObserver {
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

    fun getCameraCaptureCallback(surface: Surface) =
        object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                // Build request to the camera device to start streaming data into surface
                val request =
                    session.device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                        set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
//                        set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            set(
                                CaptureRequest.SENSOR_PIXEL_MODE,
                                CaptureRequest.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION
                            )
                        }
                        addTarget(surface)
                    }.build()

                // Request data endlessly
                session.setRepeatingRequest(request, null, null)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                "Camera config failed".e
            }
        }

    private fun getCameraCallback(surface: Surface) =
        object : CameraDevice.StateCallback() {
            @RequiresApi(Build.VERSION_CODES.R)
            override fun onOpened(cameraDevice: CameraDevice) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    cameraDevice.createCaptureSession(
                        SessionConfiguration(
                            SessionConfiguration.SESSION_REGULAR,
                            listOf(OutputConfiguration(surface).apply {
                                // Works for SurfaceView but not for GLSurfaceView
//                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                                    mirrorMode = OutputConfiguration.MIRROR_MODE_V
//                                }
                            }),
                            ExecutorCompat.create(_handler),
                            getCameraCaptureCallback(surface)
                        )
                    )
                } else {
                    cameraDevice.createCaptureSession(
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

    fun bind(context: Context, surface: Surface) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            "Permission check failed.".e
            return
        }

//        val rotation = ContextCompat.getDisplayOrDefault(context).rotation
        bg {
            ContextCompat.getSystemService(context, CameraManager::class.java)
                ?.let { cameraManager ->
                    // Select front-facing camera
                    val (cameraId, camCharacteristic) = CameraUtils.getCameraCharacteristic(
                        context,
                        CameraMetadata.LENS_FACING_FRONT
                    )

                    cameraCharacteristic = camCharacteristic
//                    listSupportedSized()

                    cameraManager.openCamera(
                        cameraId,
                        getCameraCallback(surface),
                        _handler
                    )
                }
        }
    }

    private fun listSupportedSized() {
        val scmap = cameraCharacteristic.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
//      val previewSizes = scmap?.getOutputSizes(ImageReader::class.java)
        scmap?.getOutputSizes(SurfaceTexture::class.java)?.forEach { s ->
            "-->> ${s.width} x ${s.height}".e
        }
    }
}

object CameraUtils {
    fun getCameraCharacteristic(
        context: Context,
        lensFacing: Int
    ): Pair<String, CameraCharacteristics> =
        ContextCompat.getSystemService(context, CameraManager::class.java)!!
            .let { cameraManager ->
                cameraManager.cameraIdList.first {
                    cameraManager
                        .getCameraCharacteristics(it)
                        .get(CameraCharacteristics.LENS_FACING) == lensFacing
                }.let { cameraId ->
                    Pair(cameraId, cameraManager.getCameraCharacteristics(cameraId))
                }
            }

    fun getMaxSizeFront(context: Context): Size =
        getCameraCharacteristic(context, CameraMetadata.LENS_FACING_FRONT).second
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            .getOutputSizes(SurfaceTexture::class.java)!!
            .first()
}

fun Surface.bindCamera(context: Context, lifecycleOwner: LifecycleOwner): Surface {
    Camera2Impl().bind(context, this)

    return this
}