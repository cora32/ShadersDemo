/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.iskopasi.shader_test.utils.camera_utils

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.opengl.EGL14
import android.opengl.GLES30
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceView
import io.iskopasi.shader_test.utils.bg
import io.iskopasi.shader_test.utils.e
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.random.Random


class HardwarePipeline(
    width: Int,
    height: Int,
    fps: Int,
    filterOn: Boolean,
    transfer: Int,
    dynamicRange: Long,
    characteristics: CameraCharacteristics,
    encoder: EncoderWrapper,
    viewFinder: SurfaceView,
    orientation: Int,
) : Pipeline(
    width, height, fps, filterOn, dynamicRange, characteristics, encoder, viewFinder
) {
    private val renderThread: HandlerThread by lazy {
        val renderThread = HandlerThread("Camera2Video.RenderThread ${Random.nextInt()}")
        renderThread.start()
        renderThread
    }

    private val renderHandler = RenderHandler(
        renderThread.getLooper(),
        width,
        height,
        fps,
        filterOn,
        transfer,
        dynamicRange,
        encoder,
        viewFinder,
        orientation
    )

    private fun stopThread() {
        renderThread.quitSafely()
        try {
            renderThread.join()
            renderThread.looper?.quitSafely()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    override fun createRecordRequest(
        session: CameraCaptureSession, previewStabilization: Boolean
    ): CaptureRequest {
        return renderHandler.createRecordRequest(session, previewStabilization)
    }

    override fun startRecording() {
        renderHandler.startRecording()
    }

    override fun stopRecording() {
        renderHandler.stopRecording()
    }

    override fun destroyWindowSurface() {
        renderHandler.sendMessage(
            renderHandler.obtainMessage(
                RenderHandler.MSG_DESTROY_WINDOW_SURFACE
            )
        )
        renderHandler.waitDestroyWindowSurface()
    }

    override fun setPreviewSize(previewSize: Size) {
        renderHandler.setPreviewSize(previewSize)
    }

    override fun createResources(surface: Surface) {
        renderHandler.sendMessage(
            renderHandler.obtainMessage(
                RenderHandler.MSG_CREATE_RESOURCES, 0, 0, surface
            )
        )
    }

    override fun getPreviewTargets(): List<Surface> {
        return renderHandler.getTargets()
    }

    override fun getRecordTargets(): List<Surface> {
        return renderHandler.getTargets()
    }

    override fun actionDown(encoderSurface: Surface) {
        renderHandler.sendMessage(
            renderHandler.obtainMessage(
                RenderHandler.MSG_ACTION_DOWN, 0, 0, encoderSurface
            )
        )
    }

    override fun actionTakePhoto(imageReaderSurface: Surface) {
        renderHandler.sendMessage(
            renderHandler.obtainMessage(
                RenderHandler.MSG_ACTION_TAKE_PHOTO, 0, 0, imageReaderSurface
            )
        )
    }

    override fun setOrientation(orientation: Int) {
        "--> Setting pipeline orientation: $orientation".e
        renderHandler.sendMessage(
            renderHandler.obtainMessage(
                RenderHandler.MSG_ON_SET_ORIENTATION, 0, 0, orientation
            )
        )
    }

    override fun setInitialOrientation(orientation: Int) {
        "--> Setting initial pipeline orientation: $orientation".e
        renderHandler.sendMessage(
            renderHandler.obtainMessage(
                RenderHandler.MSG_ON_SET_INITIAL_ORIENTATION, 0, 0, orientation
            )
        )
    }

    override fun clearFrameListener() {
        renderHandler.sendMessage(
            renderHandler.obtainMessage(
                RenderHandler.MSG_CLEAR_FRAME_LISTENER
            )
        )
        renderHandler.waitClearFrameListener()
    }

    override fun cleanup() {
        renderHandler.sendMessage(
            renderHandler.obtainMessage(
                RenderHandler.MSG_CLEANUP
            )
        )
        bg {
            renderHandler.waitCleanup()
            stopThread()
        }
    }


    companion object {
        val TAG = HardwarePipeline::class.java.simpleName

        /** Check if OpenGL failed, and throw an exception if so */
        fun checkGlError(op: String) {
            val error = GLES30.glGetError()
            if (error != GLES30.GL_NO_ERROR) {
                val msg = op + ": glError 0x" + Integer.toHexString(error)
                Log.e(TAG, msg)
                throw RuntimeException(msg)
            }
        }

        fun checkEglError(op: String) {
            val eglError = EGL14.eglGetError()
            if (eglError != EGL14.EGL_SUCCESS) {
                val msg = op + ": eglError 0x" + Integer.toHexString(eglError)
                Log.e(TAG, msg)
                throw RuntimeException(msg);
            }
        }
    }
}

class ShaderProgram(
    private val id: Int,
    private val vPositionLoc: Int,
    private val texMatrixLoc: Int,
    private var uMVPMatrixHandle: Int,
    private val iTimeHandle: Int,
    private val iRandHandle: Int,
    private val orientationLoc: Int,
) {

    //Handle to vertex attributes
//    private val mvpMatrix = FloatArray(16)
    private var timeProgress = 0f
//    private val rotationAngle = 0f
//    private var takeScreenshot = false

    private fun floatBufferFromArray(array: FloatArray): FloatBuffer {
        val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(array.size * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        val floatBuffer: FloatBuffer = byteBuffer.asFloatBuffer()
        floatBuffer.put(array)
        byteBuffer.position(0)
        floatBuffer.position(0)
        return floatBuffer
    }

    fun setData(vertexCoords: FloatArray, orientation: Int) {
        GLES30.glEnableVertexAttribArray(vPositionLoc)
        HardwarePipeline.checkGlError("glEnableVertexAttribArray")
        GLES30.glVertexAttribPointer(
            vPositionLoc,
            2,
            GLES30.GL_FLOAT,
            false,
            8,
            floatBufferFromArray(vertexCoords)
        )
        HardwarePipeline.checkGlError("glVertexAttribPointer")

        // Rotate front camera
//        Matrix.setIdentityM(mvpMatrix, 0)
//        HardwarePipeline.checkGlError("setIdentityM")
//        Matrix.rotateM(mvpMatrix, 0, rotationAngle, 0.0f, 0.0f, 1.0f)
//        HardwarePipeline.checkGlError("rotateM")
//        GLES30.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
//        HardwarePipeline.checkGlError("uMVPMatrixHandle")


        GLES30.glUniform1f(iTimeHandle, timeProgress++)
        HardwarePipeline.checkGlError("iTimeHandle")
        GLES30.glUniform1i(orientationLoc, orientation)
        HardwarePipeline.checkGlError("orientationLoc")
        GLES30.glUniform1f(iRandHandle, Random.nextFloat())
        HardwarePipeline.checkGlError("iRandHandle")
    }

    fun setTexMatrix(texMatrix: FloatArray) {
        GLES30.glUniformMatrix4fv(texMatrixLoc, 1, false, texMatrix, 0)
        HardwarePipeline.checkGlError("glUniformMatrix4fv")
    }

    fun useProgram() {
        GLES30.glUseProgram(id)
        HardwarePipeline.checkGlError("glUseProgram")
    }
}
