package io.iskopasi.shader_test.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.ExecutorCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.iskopasi.shader_test.ui.composables.MyGLRenderer

class Camera2Controller(
    private val context: Context,
    private val surface: Surface,
    private val isFront: Boolean
) :
    DefaultLifecycleObserver {
    private var _cameraThread: HandlerThread? = null
    private var _handler: Handler? = null
    private var _session: CameraCaptureSession? = null
    private var _cameraDevice: CameraDevice? = null
    private var _imageReader: ImageReader? = null
    private lateinit var cameraCharacteristic: CameraCharacteristics

    private val captureCallback = object : CaptureCallback() {
        override fun onCaptureStarted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            timestamp: Long,
            frameNumber: Long
        ) {
            "--> onCaptureStarted".e
            super.onCaptureStarted(session, request, timestamp, frameNumber)

        }

        override fun onReadoutStarted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            timestamp: Long,
            frameNumber: Long
        ) {
            "--> onReadoutStarted".e
            super.onReadoutStarted(session, request, timestamp, frameNumber)
        }

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            "--> onCaptureProgressed".e
            super.onCaptureProgressed(session, request, partialResult)
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            "--> onCaptureCompleted".e
            super.onCaptureCompleted(session, request, result)
        }

        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: CaptureFailure
        ) {
            "--> onCaptureFailed".e
            super.onCaptureFailed(session, request, failure)
        }

        override fun onCaptureSequenceCompleted(
            session: CameraCaptureSession,
            sequenceId: Int,
            frameNumber: Long
        ) {
            "--> onCaptureSequenceCompleted".e
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber)
        }

        override fun onCaptureSequenceAborted(session: CameraCaptureSession, sequenceId: Int) {
            "--> onCaptureSequenceAborted".e
            super.onCaptureSequenceAborted(session, sequenceId)
        }

        override fun onCaptureBufferLost(
            session: CameraCaptureSession,
            request: CaptureRequest,
            target: Surface,
            frameNumber: Long
        ) {
            "--> onCaptureBufferLost".e
            super.onCaptureBufferLost(session, request, target, frameNumber)
        }
    }

    init {
        createCameraThread()
    }

    private fun closeCamera() {
        _session?.close()
        _cameraDevice?.close()
    }

    override fun onResume(owner: LifecycleOwner) {
        createCameraThread()
        if (_cameraDevice != null) {
            openCamera()
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        closeCamera()

        _cameraThread?.quitSafely()
        try {
            _cameraThread?.join()
            _handler?.looper?.quitSafely()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } finally {
            _cameraThread = null
            _handler = null
        }
    }

    private fun createCameraThread() {
        _cameraThread = HandlerThread("Camera thread").apply { start() }
        _handler = Handler(_cameraThread!!.looper)
    }

    fun getCameraCaptureCallback() =
        object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                _session = session

                // Build request to the camera device to start streaming data into surface
                val request =
                    session.device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
//                        set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
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

    private fun getCameraCallback() =
        object : CameraDevice.StateCallback() {
            @RequiresApi(Build.VERSION_CODES.R)
            override fun onOpened(cameraDevice: CameraDevice) {
                _cameraDevice = cameraDevice

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    cameraDevice.createCaptureSession(
                        SessionConfiguration(
                            SessionConfiguration.SESSION_REGULAR,
                            listOf(
                                OutputConfiguration(surface).apply {
                                    // Works for SurfaceView but not for GLSurfaceView
//                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                                    mirrorMode = OutputConfiguration.MIRROR_MODE_V
//                                }
                                },
//                                OutputConfiguration(_imageReader!!.surface)
                            ),
                            ExecutorCompat.create(_handler!!),
                            getCameraCaptureCallback()
                        )
                    )
                } else {
                    cameraDevice.createCaptureSession(
//                        listOf(surface, _imageReader!!.surface),
                        listOf(surface),
                        getCameraCaptureCallback(),
                        _handler
                    )
                }
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                val errorMsg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }

                "Error: $camera $error $errorMsg".e
            }
        }


    fun bind(
        lifecycleOwner: LifecycleOwner
    ): Camera2Controller {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            "Permission check failed.".e
            return this
        }

        lifecycleOwner.lifecycle.addObserver(this)
        openCamera()

//        val rotation = ContextCompat.getDisplayOrDefault(context).rotation

        return this
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        bg {
            ContextCompat.getSystemService(context, CameraManager::class.java)
                ?.let { cameraManager ->
                    // Select front-facing camera
                    val (cameraId, camCharacteristic) = CameraUtils.getCameraCharacteristic(
                        context,
                        if (isFront) CameraMetadata.LENS_FACING_FRONT else CameraMetadata.LENS_FACING_BACK
                    )

                    cameraCharacteristic = camCharacteristic
//                    listSupportedSized()

                    createImageReader()
                    cameraManager.openCamera(
                        cameraId,
                        getCameraCallback(),
                        _handler
                    )
                }
        }
    }

    private fun createImageReader() {
        val previewSize =
            cameraCharacteristic.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                .getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.height * it.width }!!
        _imageReader = ImageReader.newInstance(
            previewSize.width,
            previewSize.height,
            ImageFormat.JPEG,
            1
        ).apply {
            setOnImageAvailableListener(
                { reader ->
                    reader.acquireLatestImage().use {
                        "---> Saving image.... ${it.timestamp} ${Thread.currentThread().name}".e
                        it.saveToFile(context)
                        "---> Got image! ${it.timestamp} ${Thread.currentThread().name}".e
                    }
                },
                _handler
            )
        }
    }

    fun snapshot(context: Context) {
        val orientations: SparseIntArray = SparseIntArray(4).apply {
            append(Surface.ROTATION_0, 0)
            append(Surface.ROTATION_90, 90)
            append(Surface.ROTATION_180, 180)
            append(Surface.ROTATION_270, 270)
        }

        val captureRequestBuilder =
            _cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequestBuilder.addTarget(_imageReader!!.surface)

        val rotation = ContextCompat.getSystemService(context, WindowManager::class.java)!!
            .defaultDisplay.rotation
        captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, orientations.get(rotation))
        _session!!.capture(captureRequestBuilder.build(), captureCallback, null)
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

    fun getMaxSizeBack(context: Context): Size =
        getCameraCharacteristic(context, CameraMetadata.LENS_FACING_BACK).second
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            .getOutputSizes(SurfaceTexture::class.java)!!
            .first()

    fun getMaxSizeFront(context: Context): Size =
        getCameraCharacteristic(context, CameraMetadata.LENS_FACING_FRONT).second
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            .getOutputSizes(SurfaceTexture::class.java)!!
            .first()
}

@RequiresApi(Build.VERSION_CODES.R)
fun GLSurfaceView.getSurface(isFront: Boolean = false): MyGLRenderer {
//            val size = CameraUtils.getMaxSizeFront(context)
    val size = CameraUtils.getMaxSizeBack(context)
    val bounds = ContextCompat.getSystemService(context, WindowManager::class.java)!!
        .currentWindowMetrics
        .bounds

    val renderer = MyGLRenderer(
        this,
        size,
        bounds.width(),
        bounds.height(),
        isFront
    )
    setEGLContextClientVersion(2)
    setRenderer(renderer)
    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

    return renderer
}

fun Surface.bindCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    isFront: Boolean
): Camera2Controller = Camera2Controller(context, this, isFront).bind(lifecycleOwner)