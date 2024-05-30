package io.iskopasi.shader_test.utils.camera_utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.DynamicRangeProfiles
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.MediaScannerConnection
import android.os.ConditionVariable
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.ExecutorCompat
import io.iskopasi.shader_test.BuildConfig
import io.iskopasi.shader_test.utils.bg
import io.iskopasi.shader_test.utils.e
import io.iskopasi.shader_test.utils.main
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CameraController2 {
    private var device: CameraDevice? = null
    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private val RECORDER_VIDEO_BITRATE: Int = 10_000_000
    private val MIN_REQUIRED_RECORDING_TIME_MILLIS: Long = 1000L

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private lateinit var characteristics: CameraCharacteristics
    private lateinit var encoder: EncoderWrapper
    private lateinit var session: CameraCaptureSession
    private lateinit var pipeline: Pipeline
    private lateinit var outputFile: File

    /** Orientation of the camera as 0, 90, 180, or 270 degrees */
    private val orientation: Int by lazy {
        characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
    }

    private val fps = 30
    private val videoCodec = EncoderWrapper.VIDEO_CODEC_ID_H264
    private val dynamicRange = DynamicRangeProfiles.STANDARD

    @Volatile
    private var recordingStarted = false

    @Volatile
    private var recordingComplete = false

    /** Requests used for preview only in the [CameraCaptureSession] */
    private val previewRequest: CaptureRequest? by lazy {
        pipeline.createPreviewRequest(session, false)
    }

    /** Requests used for preview and recording in the [CameraCaptureSession] */
    private val recordRequest: CaptureRequest by lazy {
        pipeline.createRecordRequest(session, false)
    }

    /** Condition variable for blocking until the recording completes */
    private val cvRecordingStarted = ConditionVariable(false)
    private val cvRecordingComplete = ConditionVariable(false)

    /** Creates a [File] named with the current date and time */
    private fun createFile(context: Context, extension: String): File {
        val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
        return File(context.filesDir, "shadertoy_${sdf.format(Date())}.$extension")
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

                recordingComplete = true
                pipeline.stopRecording()
                cvRecordingComplete.open()
            }
        }

        setupSessionWithDynamicRangeProfile(device, targets, handler, stateCallback)
    }

    private fun initializeCamera(context: Context, cameraId: String) = main {
        "--> Initializing camera...".e
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
            session.setRepeatingRequest(recordRequest, null, cameraHandler)
        } else {
            session.setRepeatingRequest(previewRequest!!, null, cameraHandler)
        }
    }

    fun start(
        view: AutoFitSurfaceView
    ) {
        val context = view.context

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
                val cameraId = getCameraId(context, CameraMetadata.LENS_FACING_FRONT)

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

                    outputFile = createFile(context, ".mp4")
                    encoder = createEncoder(
                        previewSize.width,
                        previewSize.height,
                        orientation,
                        outputFile,
                        context
                    )
                    pipeline = HardwarePipeline(
                        previewSize.width,
                        previewSize.height,
                        fps,
                        false,
                        0,
                        dynamicRange,
                        characteristics,
                        encoder,
                        view
                    )
                    pipeline.setPreviewSize(previewSize)
                    pipeline.createResources(holder.surface)

                    initializeCamera(context, cameraId)
                }
            }
        })
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

                session.close()
                session = createCaptureSession(
                    device!!, recordTargets, cameraHandler,
                    recordingCompleteOnClose = true
                )

                session.setRepeatingRequest(
                    recordRequest,
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

            session.stopRepeating()
            session.close()

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

val Context.cameraManager: CameraManager?
    get() = ContextCompat.getSystemService(this, CameraManager::class.java)