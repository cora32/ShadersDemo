package io.iskopasi.shader_test.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import android.hardware.camera2.params.DynamicRangeProfiles
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.CamcorderProfile
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.ConditionVariable
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.ExecutorCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.iskopasi.shader_test.BuildConfig
import io.iskopasi.shader_test.ui.composables.MyGLRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


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
    private var _mediaRecorder: MediaRecorder? = null
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

    private fun _createCaptureSession(surfaces: List<Surface>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            "--> Creating getCameraCallback".e
            _cameraDevice!!.createCaptureSession(
                SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    surfaces.map { OutputConfiguration(it) },
//                            listOf(
//                                OutputConfiguration(surface).apply {
//                                    // Works for SurfaceView but not for GLSurfaceView
////                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
////                                    mirrorMode = OutputConfiguration.MIRROR_MODE_V
////                                }
//                                },
////                                OutputConfiguration(sur!!),
//                                OutputConfiguration(_mediaRecorder!!.surface),
////                                OutputConfiguration(_imageReader!!.surface)
//                            ),
                    ExecutorCompat.create(_handler!!),
                    getCameraCaptureCallback()
                )
            )
        } else {
            _cameraDevice!!.createCaptureSession(
//                        listOf(surface, _imageReader!!.surface),
//                        listOf(surface, _mediaRecorder!!.surface),
                surfaces,
                getCameraCaptureCallback(),
                _handler
            )
        }
    }

    private fun getCameraCallback(surfaces: List<Surface>) =
        object : CameraDevice.StateCallback() {
            @RequiresApi(Build.VERSION_CODES.R)
            override fun onOpened(cameraDevice: CameraDevice) {
                _cameraDevice = cameraDevice

                _createCaptureSession(surfaces)
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
        if (!checkPermissions(context)) {
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
//        createMediaRecorder(context, "0")
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

//                    createMediaRecorder(context, cameraId)
//                    createImageReader()
                    cameraManager.openCamera(
                        cameraId,
                        getCameraCallback(listOf(surface)),
                        _handler
                    )
                }
        }
    }

    var sur: Surface? = null

    private fun _createMR(surface: Surface): MediaRecorder {
        "--> Creating MR in thread: ${Thread.currentThread().name}".e


//        _mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            MediaRecorder(context)
//        } else {
//            MediaRecorder()
//        }

        return MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
            val outputFile = File(context.filesDir, "SV_${sdf.format(Date())}.mp4")
            setOutputFile(outputFile.absolutePath)

            "--> Writing to ${outputFile.absolutePath}".e

            CamcorderProfile.get(CamcorderProfile.QUALITY_1080P).let { profile ->
                "--> profile.videoFrameRate: ${profile.videoFrameRate}".e
                "--> profile.videoFrameWidth: ${profile.videoFrameWidth} profile.videoFrameHeight: ${profile.videoFrameHeight}".e
                "--> profile.videoBitRate: ${profile.videoBitRate} ".e
                "--> profile.audioBitRate: ${profile.audioBitRate} ".e
                "--> profile.audioSampleRate: ${profile.audioSampleRate} ".e

                setVideoFrameRate(profile.videoFrameRate)
                setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight)
//                setVideoSize(720, 480)
                setVideoEncodingBitRate(profile.videoBitRate)
                setAudioEncodingBitRate(profile.audioBitRate)
                setAudioSamplingRate(profile.audioSampleRate)
            }


//            if (args.useHardware) {
//                if (orientation == 90 || orientation == 270) {
//                    width = args.height
//                    height = args.width
//                }
//                orientationHint = 0
//            }

//            setOnInfoListener { mr, what, extra -> "--> EVENT: mr: $mr what: $what extra: $extra".e }

//            MediaCodec.createPersistentInputSurface()
//            sur = MediaCodec.createPersistentInputSurface()
//            "surface: ${this@Camera2Controller.surface} ${this@Camera2Controller.surface.toString()}".e
//            setInputSurface(this@Camera2Controller.surface)
            "--> Creating setInputSurface".e
            setInputSurface(sur!!)
            setOrientationHint(0)
//            try {
//                prepare()
//                "MediaRecorder is ready".e
////                release()
//            } catch (e: Exception) {
//                "MediaRecorder is not ready: $e".e
//                e.printStackTrace()
//            }
        }
    }

    private fun createMediaRecorder(context: Context, cameraId: String) {
        sur = MediaCodec.createPersistentInputSurface()
        _createMR(sur!!).apply {
            prepare()
            release()
        }
        _mediaRecorder = _createMR(sur!!).apply {
            "--> Preparing MediaRecorder...".e
            prepare()
            "--> MediaRecorder prepared".e
        }
    }

    private var isRecording = false
    fun startVideoRec(context: Context) {
        "--> Starting video rec in ${Thread.currentThread().name}"

        if (isRecording) {
            _mediaRecorder?.stop()
            _mediaRecorder?.reset()
        } else {
            _session!!.close()

            createMediaRecorder(context, "0")
            _createCaptureSession(listOf(surface, sur!!))

            _mediaRecorder?.setOnInfoListener { mr, what, extra ->
                "--> INFO: $mr $what $extra".e
            }

            _mediaRecorder?.start()
        }

        isRecording = !isRecording

        "--> isRecording: $isRecording".e
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

class Cam2 {
    private var device: CameraDevice? = null
    private var surface: Surface? = null
    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private val RECORDER_VIDEO_BITRATE: Int = 10_000_000
    private val MIN_REQUIRED_RECORDING_TIME_MILLIS: Long = 1000L

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private lateinit var characteristics: CameraCharacteristics
    private lateinit var encoder: EncoderWrapper

    /** Orientation of the camera as 0, 90, 180, or 270 degrees */
    private val orientation: Int by lazy {
        characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
    }

    /** Creates a [File] named with the current date and time */
    private fun createFile(context: Context, extension: String): File {
        val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
        return File(context.filesDir, "sdf123VID_${sdf.format(Date())}.$extension")
    }

    @SuppressLint("MissingPermission")
    suspend fun openCamera(context: Context, cameraId: String):
            CameraDevice = suspendCancellableCoroutine { cont ->
        ContextCompat.getSystemService(context, CameraManager::class.java)?.apply {
            val cb = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    "--> Camera device acquired ${camera.id}".e
                    cont.resume(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    "--> Camera onDisconnected".e
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    val msg = when (error) {
                        ERROR_CAMERA_DEVICE -> "Fatal (device)"
                        ERROR_CAMERA_DISABLED -> "Device policy"
                        ERROR_CAMERA_IN_USE -> "Camera in use"
                        ERROR_CAMERA_SERVICE -> "Fatal (service)"
                        ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                        else -> "Unknown"
                    }
                    val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                    exc.printStackTrace()
                    "--> onError: $msg".e
                    if (cont.isActive) cont.resumeWithException(exc)
                }
            }

            openCamera(cameraId, cb, cameraHandler)
        }
    }

    fun getCameraManager(context: Context) =
        ContextCompat.getSystemService(context, CameraManager::class.java)

    fun getCameraCharacteristic(
        context: Context,
        lensFacing: Int
    ): Pair<String, CameraCharacteristics> =
        getCameraManager(context)!!
            .let { cameraManager ->
                cameraManager.cameraIdList.first {
                    cameraManager
                        .getCameraCharacteristics(it)
                        .get(CameraCharacteristics.LENS_FACING) == lensFacing
                }.let { cameraId ->
                    Pair(cameraId, cameraManager.getCameraCharacteristics(cameraId))
                }
            }

    private fun createEncoder(
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
            width, height, RECORDER_VIDEO_BITRATE, fps,
            dynamicRange,
            orientationHint,
            outputFile,
            useMediaRecorder,
            videoCodec,
            context.applicationContext
        )
    }

    val fps = 30
    val videoCodec = EncoderWrapper.VIDEO_CODEC_ID_H264
    val dynamicRange = DynamicRangeProfiles.STANDARD
    private var session: CameraCaptureSession? = null
    private lateinit var pipeline: Pipeline

    private fun getCharacteristics(context: Context, cameraId: String) =
        getCameraManager(context)!!.getCameraCharacteristics(cameraId)

    private fun getCameraId(context: Context, lensFacing: Int): String =
        getCameraManager(context)!!
            .let { cameraManager ->
                cameraManager.cameraIdList.first {
                    cameraManager
                        .getCameraCharacteristics(it)
                        .get(CameraCharacteristics.LENS_FACING) == lensFacing
                }
            }


    @Volatile
    private var recordingStarted = false

    @Volatile
    private var recordingComplete = false

    /** Requests used for preview only in the [CameraCaptureSession] */
    private val previewRequest: CaptureRequest? by lazy {
        pipeline.createPreviewRequest(session!!, false)
    }

    /** Requests used for preview and recording in the [CameraCaptureSession] */
    private val recordRequest: CaptureRequest by lazy {
        pipeline.createRecordRequest(session!!, false)
    }

    private fun isCurrentlyRecording(): Boolean {
        return recordingStarted && !recordingComplete
    }

    /** Condition variable for blocking until the recording completes */
    private val cvRecordingStarted = ConditionVariable(false)
    private val cvRecordingComplete = ConditionVariable(false)

    private lateinit var outputFile: File


    /**
     * Creates a [CameraCaptureSession] with the dynamic range profile set.
     */
    private fun setupSessionWithDynamicRangeProfile(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler,
        stateCallback: CameraCaptureSession.StateCallback
    ): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val outputConfigs = mutableListOf<OutputConfiguration>()
            for (target in targets) {
                val outputConfig = OutputConfiguration(target)
                outputConfig.dynamicRangeProfile = dynamicRange
                outputConfigs.add(outputConfig)
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

    /**
     * Creates a [CameraCaptureSession] and returns the configured session (as the result of the
     * suspend coroutine)
     */
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
                if (!recordingCompleteOnClose or !isCurrentlyRecording()) {
                    return
                }

                recordingComplete = true
                pipeline.stopRecording()
                cvRecordingComplete.open()
            }
        }

        setupSessionWithDynamicRangeProfile(device, targets, handler, stateCallback)
    }

    fun start(
        context: Context,
        surface: Surface,
        glView: GLSurfaceView
    ) {
        val size = CameraUtils.getMaxSizeBack(context)
        val bounds = ContextCompat.getSystemService(context, WindowManager::class.java)!!
            .currentWindowMetrics
            .bounds
        val width = bounds.width()
        val height = bounds.height()
        "--> width: $width height: $height".e

        outputFile = createFile(context, ".mp4")

        "--> Creating cameraId...".e
        val cameraId = getCameraId(context, CameraMetadata.LENS_FACING_FRONT)

        characteristics = getCharacteristics(context, cameraId)

        "--> Creating encoder...".e
        encoder = createEncoder(width, height, orientation, outputFile, context)

        "--> Creating HardwarePipeline...".e
        pipeline = HardwarePipeline(
            width,
            height,
            fps,
            false,
            0,
            dynamicRange,
            characteristics,
            encoder,
            glView
        )
        "--> setPreviewSize...".e
        pipeline.setPreviewSize(size)
        "--> createResources...".e
        pipeline.createResources(surface)

        "--> Initializing camera...".e
        main {
            device = openCamera(context, cameraId)

            "--> getPreviewTargets...".e
            val previewTargets = pipeline.getPreviewTargets()
            "--> previewTargets: $previewTargets".e

            // Start a capture session using our open camera and list of Surfaces where frames will go
            session = createCaptureSession(
                device!!,
                previewTargets,
                cameraHandler,
                recordingCompleteOnClose = true
            )

            // Sends the capture request as frequently as possible until the session is torn down or
            //  session.stopRepeating() is called
            if (previewRequest == null) {
                session!!.setRepeatingRequest(recordRequest, null, cameraHandler)
            } else {
                session!!.setRepeatingRequest(previewRequest!!, null, cameraHandler)
            }
        }
    }

    /**
     * Setup a [Surface] for the encoder
     */
    private val encoderSurface: Surface by lazy {
        encoder.getInputSurface()
    }

    fun startVideoRec(context: Context) = bg {
        if (!recordingStarted) {
            // Prevents screen rotation during the video recording
//            requireActivity().requestedOrientation =
//                ActivityInfo.SCREEN_ORIENTATION_LOCKED

            pipeline.actionDown(encoderSurface)

            // Finalizes encoder setup and starts recording
            recordingStarted = true
            encoder.start()
            cvRecordingStarted.open()
            pipeline.startRecording()


            // Start recording repeating requests, which will stop the ongoing preview
            //  repeating requests without having to explicitly call
            //  `session.stopRepeating`
            if (previewRequest != null) {
                val recordTargets = pipeline.getRecordTargets()

                session!!.close()
                session = createCaptureSession(
                    device!!, recordTargets, cameraHandler,
                    recordingCompleteOnClose = true
                )

                session!!.setRepeatingRequest(recordRequest,
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            if (isCurrentlyRecording()) {
                                encoder.frameAvailable()
                            }
                        }
                    }, cameraHandler
                )
            }
        } else {
            cvRecordingStarted.block()

            /* Wait for at least one frame to process so we don't have an empty video */
            encoder.waitForFirstFrame()

            session!!.stopRepeating()
            session!!.close()

            pipeline.clearFrameListener()
//            fragmentBinding.captureButton.setOnTouchListener(null)

            // Set color to GRAY and hide timer when recording stops
//            fragmentBinding.captureButton.post {
//                fragmentBinding.captureButton.background =
//                    context?.let {
//                        ContextCompat.getDrawable(it,
//                            R.drawable.ic_shutter_normal)
//                    }
//                fragmentBinding.captureTimer?.visibility = View.GONE
//                fragmentBinding.captureTimer?.stop()
//            }

            /* Wait until the session signals onReady */
            cvRecordingComplete.block()

            // Unlocks screen rotation after recording finished
//            requireActivity().requestedOrientation =
//                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

            // Requires recording of at least MIN_REQUIRED_RECORDING_TIME_MILLIS
//            val elapsedTimeMillis = System.currentTimeMillis() - recordingStartMillis
//            if (elapsedTimeMillis < MIN_REQUIRED_RECORDING_TIME_MILLIS) {
//                delay(MIN_REQUIRED_RECORDING_TIME_MILLIS - elapsedTimeMillis)
//            }

            delay(1000L)

            pipeline.cleanup()

            "--> Recording stopped. Output file: $outputFile".e

            if (encoder.shutdown()) {
                // Broadcasts the media file to the rest of the system
                MediaScannerConnection.scanFile(
                    context, arrayOf(outputFile.absolutePath), null, null
                )

                if (outputFile.exists()) {
                    // Launch external activity via intent to play video recorded using our provider
                    ContextCompat.startActivity(context, Intent().apply {
                        action = Intent.ACTION_VIEW
                        type = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(outputFile.extension)
                        val authority = "${BuildConfig.APPLICATION_ID}.provider"
                        data = FileProvider.getUriForFile(context, authority, outputFile)
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }, null)
                } else {
                    // TODO:
                    //  1. Move the callback to ACTION_DOWN, activating it on the second press
                    //  2. Add an animation to the button before the user can press it again
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context, "error_file_not_found",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } else {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context, "err",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
//            Handler(Looper.getMainLooper()).post {
//                navController.popBackStack()
//            }
        }
    }
}