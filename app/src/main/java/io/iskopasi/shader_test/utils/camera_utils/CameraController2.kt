@file:Suppress("DEPRECATION")

package io.iskopasi.shader_test.utils.camera_utils

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.DynamicRangeProfiles
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.CamcorderProfile
import android.media.MediaScannerConnection
import android.os.Build
import android.os.ConditionVariable
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.ExecutorCompat
import io.iskopasi.shader_test.utils.bg
import io.iskopasi.shader_test.utils.cameraManager
import io.iskopasi.shader_test.utils.createFile
import io.iskopasi.shader_test.utils.e
import io.iskopasi.shader_test.utils.saveToDcim
import io.iskopasi.shader_test.utils.share
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

interface InitCallback {
    fun onInitialized() {

    }
}

class CameraController2(
    isFront: Boolean,
    context: Context,
    view: AutoFitSurfaceView,
    surface: Surface,
    orientation: Int
) {
    var isInitialized: Boolean = false
    private var previewSize: Size
    private var device: CameraDevice? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    @Volatile
    private var recordingStarted = false

    @Volatile
    private var recordingComplete = false
    private var recordingStartMillis = 0L
    private var width = 0
    private var height = 0
    private var bitrate = 0
    private val fps = 30
    private var audioBitrate = 0
    private var audioSampleRate = 0
    private var initCallback: InitCallback? = null
    private var mOrientation: Int = orientation

    private val RECORDER_VIDEO_BITRATE: Int = 10_000_000
    private val MIN_REQUIRED_RECORDING_TIME_MILLIS: Long = 1000L
    private val videoCodec = EncoderWrapper.VIDEO_CODEC_ID_H264
    private val dynamicRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        DynamicRangeProfiles.STANDARD
    else
        DynamicRangeProfiles.PUBLIC_MAX

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private var characteristics: CameraCharacteristics
    private lateinit var encoder: EncoderWrapper
    private lateinit var session: CameraCaptureSession
    private lateinit var pipeline: Pipeline
    private lateinit var outputFile: File
    private lateinit var recordRequest: CaptureRequest

    /** Condition variable for blocking until the recording completes */
    private val cvRecordingStarted = ConditionVariable(false)

    init {
        isInitialized = false
        startThread()

        val cameraId = getCameraId(
            context,
            if (isFront) CameraMetadata.LENS_FACING_FRONT else CameraMetadata.LENS_FACING_BACK
        )

        characteristics = getCharacteristics(context, cameraId)

        // Selects appropriate preview size and configures view finder
        previewSize = getPreviewOutputSize(
            view.display, characteristics, SurfaceHolder::class.java
        )
        "View finder size: ${view.width} x ${view.height}".e
        "Selected preview size: $previewSize".e
        view.setAspectRatio(previewSize.width, previewSize.height)

        bg {
            setRecorderParams(cameraId)
            initializePipeline(context, previewSize, view, surface = surface)
            initializeCamera(context, cameraId)

            initCallback?.onInitialized()
            isInitialized = true
        }
    }

    fun addCallbackListener(listener: InitCallback?) {
        initCallback = listener
    }

    fun onStart() {
        "--> onStart".e
    }

    fun onResume() {
        "--> onResume".e
    }

    fun onPause() {
        "--> onPause".e
    }

    fun onStop() {
        isInitialized = false
        "--> onStop".e

        try {
            session.stopRepeating()
            session.close()
            device?.close()
        } catch (exc: Throwable) {
            "$exc".e
        }
        stopThread()
        pipeline.cleanup()

        removeEmptyFile()
    }

    fun onDestroy() {
        "--> onDestroy".e
        pipeline.clearFrameListener()
        pipeline.cleanup()
        stopThread()
        encoderSurface.release()
    }

    private fun removeEmptyFile() {
        outputFile.apply {
            if (exists()) {
                if (length() == 0L) {
                    delete()
                }
            }
        }
    }

    private fun setRecorderParams(cameraId: String) {
        val quality = CamcorderProfile.QUALITY_1080P

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            CamcorderProfile.getAll(cameraId, quality)?.apply {
                videoProfiles.first().let { profile ->
                    width = profile.width
                    height = profile.height
                    // fps = profile.frameRate
                    bitrate = profile.bitrate

                    "--> Setting buffer sizes: ${width}x$height"
                }

                audioProfiles.first().let { profile ->
                    audioBitrate = profile.bitrate
                    audioSampleRate = profile.sampleRate
                }
            }
        } else {
            CamcorderProfile.get(quality)?.let { profile ->
                width = profile.videoFrameWidth
                height = profile.videoFrameHeight
                bitrate = profile.videoBitRate
                audioBitrate = profile.audioBitRate
                audioSampleRate = profile.audioSampleRate
            }
        }
    }

    private fun startThread() {
        cameraThread = HandlerThread("CameraThread").apply { start() }
        cameraHandler = Handler(cameraThread!!.looper)
    }

    fun onSurfaceDestroyed() {
        pipeline.destroyWindowSurface()
    }

    private fun stopThread() {
        cameraThread?.quitSafely()
        try {
            cameraThread?.join()
            cameraHandler?.looper?.quitSafely()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } finally {
            cameraThread = null
            cameraHandler = null
        }
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

    private fun getCharacteristics(context: Context, cameraId: String) =
        context.cameraManager!!.getCameraCharacteristics(cameraId)

    private fun getCameraId(context: Context, lensFacing: Int): String =
        context.cameraManager!!
            .let { cameraManager ->
                cameraManager.cameraIdList.first {
                    cameraManager
                        .getCameraCharacteristics(it)
                        .get(CameraCharacteristics.LENS_FACING) == lensFacing
                }
            }

    private fun isCurrentlyRecording(): Boolean {
        return recordingStarted && !recordingComplete
    }


    /**
     * Creates a [CameraCaptureSession] with the dynamic range profile set.
     */
    @SuppressLint("WrongConstant")
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
//                    setPhysicalCameraId("4")
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
            /** Called after all captures have completed - shut down the encoder */
            override fun onClosed(session: CameraCaptureSession) {
                if (!recordingCompleteOnClose or !isCurrentlyRecording()) {
                    return
                }

                stopRecording()
            }
        }

        setupSessionWithDynamicRangeProfile(device, targets, handler, stateCallback)
    }

    private fun stopRecording() {
        recordingComplete = true
        pipeline.stopRecording()
//        cvRecordingComplete.open()
    }

    private suspend fun initializeCamera(context: Context, cameraId: String) {
        device = openCamera(context, cameraId)

        val previewTargets = pipeline.getPreviewTargets()

        // Start a capture session using our open camera and list of Surfaces where frames will go
        session = createCaptureSession(
            device!!,
            previewTargets,
            cameraHandler!!,
            recordingCompleteOnClose = true
        )

        recordRequest = pipeline.createRecordRequest(session, false)

        // Sends the capture request as frequently as possible until the session is torn down or
        //  session.stopRepeating() is called
        session.setRepeatingRequest(recordRequest, null, cameraHandler)
    }

    private fun initializePipeline(
        context: Context,
        previewSize: Size,
        view: SurfaceView,
        surface: Surface
    ) {
        outputFile = context.applicationContext.createFile("mp4")

        encoder = EncoderWrapper(
            width,
            height,
            bitrate,
            fps,
            dynamicRange,
            mOrientation,
            outputFile,
            useMediaRecorder = true,
            videoCodec,
            context.applicationContext,
            audioBitrate,
            audioSampleRate,
        )

        pipeline = HardwarePipeline(
            width,
            height,
            fps,
            false,
            2,
            dynamicRange,
            characteristics,
            encoder,
            view,
            orientation = mOrientation
        )
        pipeline.setPreviewSize(previewSize)
        pipeline.createResources(surface)
    }

    /**
     * Setup a [Surface] for the encoder
     */
    private val encoderSurface: Surface by lazy {
        encoder.getInputSurface()
    }

    fun startVideoRec(context: Context) = bg {
        if (!isInitialized) {
            "--> CameraController not initialized yet".e
            return@bg
        }

        if (!recordingStarted) {
            "--> Starting recording".e
            recordingStartMillis = System.currentTimeMillis()

            encoder.setInitialOrientation(mOrientation)
            pipeline.setInitialOrientation(mOrientation)
            pipeline.actionDown(encoderSurface)

            // Finalizes encoder setup and starts recording
            recordingStarted = true
            encoder.start()
            cvRecordingStarted.open()
            pipeline.startRecording()
        } else {
            cvRecordingStarted.block()

            /* Wait for at least one frame to process so we don't have an empty video */
            encoder.waitForFirstFrame()

//            session.stopRepeating()
//            session.close()

//            pipeline.clearFrameListener()

            // Requires recording of at least MIN_REQUIRED_RECORDING_TIME_MILLIS
            val elapsedTimeMillis = System.currentTimeMillis() - recordingStartMillis
            if (elapsedTimeMillis < MIN_REQUIRED_RECORDING_TIME_MILLIS) {
                delay(MIN_REQUIRED_RECORDING_TIME_MILLIS - elapsedTimeMillis)
            }

            stopRecording()

            /* Wait until the session signals onReady */
//            cvRecordingComplete.block()

            // Unlocks screen rotation after recording finished
//            requireActivity().requestedOrientation =
//                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

//            pipeline.cleanup()

            recordingStarted = false
            "--> Recording stopped. Output file: $outputFile".e
            if (encoder.shutdown()) {
                // Broadcasts the media file to the rest of the system
                MediaScannerConnection.scanFile(
                    context, arrayOf(outputFile.absolutePath), null, null
                )

                if (outputFile.exists()) {
                    outputFile.share(context)
                    outputFile.saveToDcim(context)
//                    outputFile.play(context)
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

            // Resetting MediaRecorder with new file
            outputFile = context.applicationContext.createFile("mp4")
            encoder.setOutputFile(outputFile)
        }
    }

    fun onOrientationChanged(orientation: Int, currentOrientation: Int) {
        "--> Setting orientation: $mOrientation".e
        when (currentOrientation) {
            Surface.ROTATION_0 -> mOrientation = 0
            Surface.ROTATION_90 -> mOrientation = 90
            Surface.ROTATION_180 -> mOrientation = 180
            Surface.ROTATION_270 -> mOrientation = 270
        }

        if (isInitialized) {
            pipeline.setOrientation(mOrientation)
        }
    }
}