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
import android.os.PowerManager
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.ExecutorCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.iskopasi.shader_test.utils.bg
import io.iskopasi.shader_test.utils.createFile
import io.iskopasi.shader_test.utils.e
import io.iskopasi.shader_test.utils.main
import io.iskopasi.shader_test.utils.saveToDcim
import io.iskopasi.shader_test.utils.share
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CameraController2(val isFront: Boolean, lifecycleOwner: LifecycleOwner) :
    DefaultLifecycleObserver {
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

    private val RECORDER_VIDEO_BITRATE: Int = 10_000_000
    private val MIN_REQUIRED_RECORDING_TIME_MILLIS: Long = 1000L
    private val fps = 30
    private val videoCodec = EncoderWrapper.VIDEO_CODEC_ID_H264
    private val dynamicRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        DynamicRangeProfiles.STANDARD
    else
        DynamicRangeProfiles.PUBLIC_MAX

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private lateinit var characteristics: CameraCharacteristics
    private lateinit var encoder: EncoderWrapper
    private lateinit var session: CameraCaptureSession
    private lateinit var pipeline: Pipeline
    private lateinit var outputFile: File
    private lateinit var recordRequest: CaptureRequest

    /** Orientation of the camera as 0, 90, 180, or 270 degrees */
    private val orientation: Int by lazy {
        characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
    }

    /** Condition variable for blocking until the recording completes */
    private val cvRecordingStarted = ConditionVariable(false)
    private val cvRecordingComplete = ConditionVariable(false)

    init {
        startThread()

        lifecycleOwner.lifecycle.addObserver(this)
    }

    private fun startThread() {
        cameraThread = HandlerThread("CameraThread").apply { start() }
        cameraHandler = Handler(cameraThread!!.looper)
    }

    override fun onResume(owner: LifecycleOwner) {
    }

    override fun onPause(owner: LifecycleOwner) {
        "--> onPause".e
//        pipeline.cleanup()
    }

    override fun onStop(owner: LifecycleOwner) {
        "--> onStop".e
        super.onStop(owner)

        try {
            device?.close()
            session.stopRepeating()
        } catch (exc: Throwable) {
            "$exc".e
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        pipeline.clearFrameListener()
        pipeline.cleanup()
        stopThread()
        encoderSurface.release()
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
        cvRecordingComplete.open()
    }

    private fun initializeCamera(context: Context, cameraId: String) = main {
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

    fun start(
        view: AutoFitSurfaceView
    ): CameraController2 {
        val context = view.context.applicationContext
        view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                pipeline.destroyWindowSurface()
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {
                val cameraId = getCameraId(
                    context,
                    if (isFront) CameraMetadata.LENS_FACING_FRONT else CameraMetadata.LENS_FACING_BACK
                )

                characteristics = getCharacteristics(context, cameraId)

                // To ensure that size is set, initialize camera in the view's thread
                view.post {
                    // Selects appropriate preview size and configures view finder
                    val previewSize = getPreviewOutputSize(
                        view.display, characteristics, SurfaceHolder::class.java
                    )
                    "View finder size: ${view.width} x ${view.height}".e
                    "Selected preview size: $previewSize".e
                    view.setAspectRatio(previewSize.width, previewSize.height)

                    CamcorderProfile.get(CamcorderProfile.QUALITY_1080P).let { profile ->
                        width = profile.videoFrameWidth
                        height = profile.videoFrameHeight
                    }

                    initializePipeline(context, previewSize, view, surface = holder.surface)
                    initializeCamera(context, cameraId)
                }
            }
        })

        return this
    }

    private fun initializePipeline(
        context: Context,
        previewSize: Size,
        view: SurfaceView,
        surface: Surface
    ) {
        encoder = createEncoder(
            width,
            height,
            RECORDER_VIDEO_BITRATE,
            fps,
            dynamicRange,
            orientation,
            useMediaRecorder = true,
            videoCodec,
            context.applicationContext
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
            view
        )
        pipeline.setPreviewSize(previewSize)
        pipeline.createResources(surface)
    }

    private fun createEncoder(
        width: Int,
        height: Int,
        recorderVideoBitrate: Int,
        fps: Int,
        dynamicRange: Long,
        orientation: Int,
        useMediaRecorder: Boolean,
        videoCodec: Int,
        context: Context
    ): EncoderWrapper {
        outputFile = context.applicationContext.createFile("mp4")
        "--> Created output file: ${outputFile.absoluteFile}".e

        return EncoderWrapper(
            width,
            height,
            recorderVideoBitrate,
            fps,
            dynamicRange,
            orientation,
            outputFile,
            useMediaRecorder = useMediaRecorder,
            videoCodec,
            context.applicationContext
        )
    }

    /**
     * Setup a [Surface] for the encoder
     */
    private val encoderSurface: Surface by lazy {
        encoder.getInputSurface()
    }

    fun startVideoRec(context: Context) = bg {
        if (!recordingStarted) {
            "--> Starting recording".e
            recordingStartMillis = System.currentTimeMillis()
            // Prevents screen rotation during the video recording
//            requireActivity().requestedOrientation =
//                ActivityInfo.SCREEN_ORIENTATION_LOCKED

            pipeline.actionDown(encoderSurface)

            // Finalizes encoder setup and starts recording
            recordingStarted = true
            encoder.start()
            cvRecordingStarted.open()
            pipeline.startRecording()
        } else {
            "--> Stopping recording".e
            cvRecordingStarted.block()
            "--> Stopping recording 1".e

            /* Wait for at least one frame to process so we don't have an empty video */
            encoder.waitForFirstFrame()
            "--> Stopping recording 2".e

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
            cvRecordingComplete.block()
            "--> Stopping recording 3".e

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

            outputFile = context.applicationContext.createFile("mp4")
            encoder.setOutputFile(outputFile)
        }
    }
}

val Context.cameraManager: CameraManager?
    get() = ContextCompat.getSystemService(this, CameraManager::class.java)
val Context.powerManager: PowerManager?
    get() = ContextCompat.getSystemService(this, PowerManager::class.java)
val Context.windowManager: WindowManager?
    get() = ContextCompat.getSystemService(this, WindowManager::class.java)
