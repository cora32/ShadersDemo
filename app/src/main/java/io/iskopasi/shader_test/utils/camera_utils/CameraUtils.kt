package io.iskopasi.shader_test.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.DynamicRangeProfiles
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.os.ExecutorCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.iskopasi.shader_test.ui.composables.MyGLRenderer
import io.iskopasi.shader_test.utils.CameraUtils.getCameraId
import io.iskopasi.shader_test.utils.camera_utils.EncoderWrapper
import io.iskopasi.shader_test.utils.camera_utils.cameraManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val RECORDER_VIDEO_BITRATE: Int = 10_000_000
private val fps = 30
private val videoCodec = EncoderWrapper.VIDEO_CODEC_ID_H264
private val dynamicRange = DynamicRangeProfiles.STANDARD

class Camera2Controller(
    private val view: GLSurfaceView,
    private val isFront: Boolean,
    lifecycleOwner: LifecycleOwner
) :
    DefaultLifecycleObserver {
    private lateinit var renderer: MyGLRenderer
    private lateinit var cameraCharacteristic: CameraCharacteristics
    private lateinit var encoder: EncoderWrapper
    private lateinit var _cameraThread: HandlerThread
    private lateinit var _handler: Handler
    private lateinit var session: CameraCaptureSession
    private var device: CameraDevice? = null
    private var _imageReader: ImageReader? = null

    //    private val previewRequest: CaptureRequest? by lazy {
//        pipeline.createPreviewRequest(session, false)
//    }
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
    private val context by lazy {
        view.context
    }

    init {
        createCameraThread()
        cameraCharacteristic = CameraUtils.getCameraCharacteristic(
            context,
            if (isFront) CameraMetadata.LENS_FACING_FRONT else CameraMetadata.LENS_FACING_BACK
        )
        encoder = createEncoder()
        renderer = createRenderer(isFront)

        lifecycleOwner.lifecycle.addObserver(this)
        openCamera()
    }

    private fun startPreview(previewStabilization: Boolean) {
        val request = session.device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            // Add the preview surface target
            addTarget(renderer.getRecordTarget())

            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(fps, fps))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                set(
                    CaptureRequest.SENSOR_PIXEL_MODE,
                    CaptureRequest.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION
                )
            }

            if (previewStabilization) {
                set(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                )
            }
        }.build()

        "--> startPreview $session".e
        session.setRepeatingRequest(request, null, null)
    }

    private fun closeCamera() {
        session?.close()
        device?.close()
    }

    override fun onResume(owner: LifecycleOwner) {
        createCameraThread()
        if (device != null) {
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
//            _cameraThread = null
//            _handler = null
        }
    }

    private fun createCameraThread() {
        _cameraThread = HandlerThread("Camera thread").apply { start() }
        _handler = Handler(_cameraThread!!.looper)
    }

//    fun getCameraCaptureCallback() =
//        object : CameraCaptureSession.StateCallback() {
//            override fun onConfigured(session: CameraCaptureSession) {
//                this@Camera2Controller.session = session
//
//                // Build request to the camera device to start streaming data into surface
//                val request =
//                    session.device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
////                        set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
////                        set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY)
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                            set(
//                                CaptureRequest.SENSOR_PIXEL_MODE,
//                                CaptureRequest.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION
//                            )
//                        }
//                        addTarget(surface)
//                    }.build()
//
//                // Request data endlessly
//                session.setRepeatingRequest(request, null, null)
//            }
//
//            override fun onConfigureFailed(session: CameraCaptureSession) {
//                "Camera config failed".e
//            }
//        }

//    private fun getCameraCallback() =
//        object : CameraDevice.StateCallback() {
//            @RequiresApi(Build.VERSION_CODES.R)
//            override fun onOpened(cameraDevice: CameraDevice) {
//                device = cameraDevice
//
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//                    cameraDevice.createCaptureSession(
//                        SessionConfiguration(
//                            SessionConfiguration.SESSION_REGULAR,
//                            listOf(
//                                OutputConfiguration(surface).apply {
//                                    // Works for SurfaceView but not for GLSurfaceView
////                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
////                                    mirrorMode = OutputConfiguration.MIRROR_MODE_V
////                                }
//                                },
////                                OutputConfiguration(_imageReader!!.surface)
//                            ),
//                            ExecutorCompat.create(_handler!!),
//                            getCameraCaptureCallback()
//                        )
//                    )
//                } else {
//                    cameraDevice.createCaptureSession(
////                        listOf(surface, _imageReader!!.surface),
//                        listOf(surface),
//                        getCameraCaptureCallback(),
//                        _handler
//                    )
//                }
//            }
//
//            override fun onDisconnected(camera: CameraDevice) {
//                camera.close()
//            }
//
//            override fun onError(camera: CameraDevice, error: Int) {
//                val errorMsg = when (error) {
//                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
//                    ERROR_CAMERA_DISABLED -> "Device policy"
//                    ERROR_CAMERA_IN_USE -> "Camera in use"
//                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
//                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
//                    else -> "Unknown"
//                }
//
//                "Error: $camera $error $errorMsg".e
//            }
//        }


//    fun bind(
//        lifecycleOwner: LifecycleOwner
//    ): Camera2Controller {
//        if (ActivityCompat.checkSelfPermission(
//                context,
//                Manifest.permission.CAMERA
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            "Permission check failed.".e
//            return this
//        }
//
//        lifecycleOwner.lifecycle.addObserver(this)
//        openCamera()
//
////        val rotation = ContextCompat.getDisplayOrDefault(context).rotation
//
//        return this
//    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        bg {
            val cameraId = getCameraId(
                context,
                if (isFront) CameraMetadata.LENS_FACING_FRONT else CameraMetadata.LENS_FACING_BACK
            )

            device = getCameraDevice(cameraId)

            session = createCaptureSession(
                device!!,
                listOf(renderer.getRecordTarget()),
                _handler,
                recordingCompleteOnClose = true
            )

            "--> got session $session".e
            startPreview(previewStabilization = false)

            // Sends the capture request as frequently as possible until the session is torn down or
            //  session.stopRepeating() is called
//            if (previewRequest == null) {
//                session.setRepeatingRequest(recordRequest, null, _handler)
//            } else {
//                session.setRepeatingRequest(previewRequest!!, null, _handler)
//            }
        }
//            context.cameraManager
//                ?.let { cameraManager ->
//                    // Select front-facing camera
////                    val (cameraId, camCharacteristic) = CameraUtils.getCameraCharacteristic(
////                        context,
////                        if (isFront) CameraMetadata.LENS_FACING_FRONT else CameraMetadata.LENS_FACING_BACK
////                    )
////
////                    cameraCharacteristic = camCharacteristic
////                    listSupportedSized()
//
////                    createImageReader()
//                }
//        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCameraDevice(cameraId: String): CameraDevice = suspendCoroutine { cont ->
        val cb =
            object : CameraDevice.StateCallback() {
                @RequiresApi(Build.VERSION_CODES.R)
                override fun onOpened(cameraDevice: CameraDevice) = cont.resume(cameraDevice)
//                {

//                    device = cameraDevice
//
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//                        cameraDevice.createCaptureSession(
//                            SessionConfiguration(
//                                SessionConfiguration.SESSION_REGULAR,
//                                listOf(
//                                    OutputConfiguration(surface).apply {
//                                        // Works for SurfaceView but not for GLSurfaceView
////                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
////                                    mirrorMode = OutputConfiguration.MIRROR_MODE_V
////                                }
//                                    },
////                                OutputConfiguration(_imageReader!!.surface)
//                                ),
//                                ExecutorCompat.create(_handler!!),
//                                getCameraCaptureCallback()
//                            )
//                        )
//                    } else {
//                        cameraDevice.createCaptureSession(
////                        listOf(surface, _imageReader!!.surface),
//                            listOf(surface),
//                            getCameraCaptureCallback(),
//                            _handler
//                        )
//                    }
//                }

                override fun onDisconnected(camera: CameraDevice) {
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

        context.cameraManager!!.openCamera(
            cameraId,
            cb,
            _handler
        )
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
            device!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequestBuilder.addTarget(_imageReader!!.surface)

        val rotation = ContextCompat.getSystemService(context, WindowManager::class.java)!!
            .defaultDisplay.rotation
        captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, orientations.get(rotation))
        session!!.capture(captureRequestBuilder.build(), captureCallback, null)
    }

//    private fun listSupportedSized() {
//        val scmap = cameraCharacteristic.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
////      val previewSizes = scmap?.getOutputSizes(ImageReader::class.java)
//        scmap?.getOutputSizes(SurfaceTexture::class.java)?.forEach { s ->
//            "-->> ${s.width} x ${s.height}".e
//        }
//    }

//    private val orientation: Int by lazy {
//        cameraCharacteristic.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
//    }

    private fun createEncoder(): EncoderWrapper {
        val outputFile = CameraUtils.createFile(context, ".mp4")
        val previewSize = CameraUtils.getMaxSizeBack(context)
        "--> previewSize: $previewSize".e

        val orientation = cameraCharacteristic.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
        return CameraUtils.createEncoder(
            previewSize.width,
            previewSize.height,
            orientation,
            outputFile,
            context
        )
    }

    private fun createRenderer(isFront: Boolean = false): MyGLRenderer {
//            val size = CameraUtils.getMaxSizeFront(context)
//        val bounds = ContextCompat.getSystemService(context, WindowManager::class.java)!!
//            .currentWindowMetrics
//            .bounds

        val previewSize = CameraUtils.getMaxSizeBack(context)

        val renderer = MyGLRenderer(
            view,
            previewSize,
            isFront,
            encoder
        )

        view.setEGLContextClientVersion(2)
        view.setRenderer(renderer)
        view.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        return renderer
    }

    var recordingStarted = false
    fun startVideoRec(context: Context) {
        if (recordingStarted) {
            // Stop recording
            recordingStarted = false

        } else {
            // Start recording
            recordingStarted = true

            renderer.actionDown(encoder.getInputSurface())
            encoder.start()
            renderer.startRecording()


//            val recordTargets = renderer.getRecordTarget()
//
//            session.close()
//            session = createCaptureSession(
//                device!!, listOf(recordTargets), _handler,
//                recordingCompleteOnClose = true
//            )

//            session.setRepeatingRequest(
//                recordRequest,
//                object : CameraCaptureSession.CaptureCallback() {
//                    override fun onCaptureCompleted(
//                        session: CameraCaptureSession,
//                        request: CaptureRequest,
//                        result: TotalCaptureResult
//                    ) {
//                        if (isCurrentlyRecording()) {
//                            encoder.frameAvailable()
//                        }
//                    }
//                }, _handler
//            )
        }
    }
}

private suspend fun createCaptureSession(
    device: CameraDevice,
    targets: List<Surface>,
    handler: Handler,
    recordingCompleteOnClose: Boolean
): CameraCaptureSession = suspendCoroutine { cont ->
    val stateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

        override fun onConfigureFailed(session: CameraCaptureSession) {
            val exc = RuntimeException("Camera ${device.id} session configuration failed")
            exc.printStackTrace()
            cont.resumeWithException(exc)
        }

        /** Called after all captures have completed - shut down the encoder */
        override fun onClosed(session: CameraCaptureSession) {
//            if (!recordingCompleteOnClose or !isCurrentlyRecording()) {
//                return
//            }
//
//            recordingComplete = true
//            pipeline.stopRecording()
//            cvRecordingComplete.open()
        }
    }

    setupSessionWithDynamicRangeProfile(device, targets, handler, stateCallback)
}


private fun setupSessionWithDynamicRangeProfile(
    device: CameraDevice,
    targets: List<Surface>,
    handler: Handler,
    stateCallback: CameraCaptureSession.StateCallback
): Boolean {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val outputConfigs = targets.map {
            OutputConfiguration(it).apply {
                dynamicRangeProfile = dynamicRange
            }
        }

        val sessionConfig = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            outputConfigs, ExecutorCompat.create(handler), stateCallback
        )
//            if (android.os.Build.VERSION.SDK_INT >=
//                android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
//                && args.colorSpace != ColorSpaceProfiles.UNSPECIFIED) {
//                sessionConfig.setColorSpace(ColorSpace.Named.values()[args.colorSpace])
//            }
        device.createCaptureSession(sessionConfig)
        return true
    } else {
        device.createCaptureSession(targets, stateCallback, handler)
        return false
    }
}

object CameraUtils {
    fun getCameraId(
        context: Context,
        lensFacing: Int
    ): String =
        context.cameraManager!!
            .let { cameraManager ->
                cameraManager.cameraIdList.first {
                    cameraManager
                        .getCameraCharacteristics(it)
                        .get(CameraCharacteristics.LENS_FACING) == lensFacing
                }
            }

    fun getCameraCharacteristic(
        context: Context,
        lensFacing: Int
    ): CameraCharacteristics =
        context.cameraManager!!.getCameraCharacteristics(getCameraId(context, lensFacing))

    fun getMaxSizeBack(context: Context): Size =
        getCameraCharacteristic(context, CameraMetadata.LENS_FACING_BACK)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            .getOutputSizes(SurfaceTexture::class.java)!!
            .first()

    fun getMaxSizeFront(context: Context): Size =
        getCameraCharacteristic(context, CameraMetadata.LENS_FACING_FRONT)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            .getOutputSizes(SurfaceTexture::class.java)!!
            .first()


    fun createFile(context: Context, extension: String): File {
        val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
        return File(context.filesDir, "shadertoy_${sdf.format(Date())}.$extension")
    }

    fun createEncoder(
        w: Int,
        h: Int,
        orientation: Int,
        outputFile: File,
        context: Context
    ): EncoderWrapper {
        var width = w
        var height = h
        var orientationHint = orientation

        val useHardware = true
        if (useHardware) {
            if (orientation == 90 || orientation == 270) {
                width = h
                height = w
            }
            orientationHint = 0
        }

        val useMediaRecorder = true

        return EncoderWrapper(
            width, height,
            RECORDER_VIDEO_BITRATE,
            fps,
            dynamicRange,
            orientationHint,
            outputFile,
            useMediaRecorder,
            videoCodec,
            context.applicationContext
        )
    }

}