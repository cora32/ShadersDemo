package io.iskopasi.shader_test.ui.composables

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES20.glGetUniformLocation
import android.opengl.GLException
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Size
import io.iskopasi.shader_test.utils.e
import io.iskopasi.shader_test.utils.loadShader
import io.iskopasi.shader_test.utils.saveToFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.random.Random


class MyGLRenderer(
    private var mGLSurfaceView: GLSurfaceView,
    size: Size,
    private val width: Int,
    private val height: Int,
    private val isFront: Boolean
) : GLSurfaceView.Renderer,
    OnFrameAvailableListener {
    var mSurfaceTexture: SurfaceTexture? = null
    private var timeProgress = 0f

    //Texture ID of camera image
    private var textureId: Int = 0
    private val COORDS_PER_VERTEX = 2
    private val TEXTURE_COORDS_PER_VERTEX = 2

    //    private val rotationAngle = if (isFront) 90f else -90f
    private val rotationAngle = 90f
    private var takeScreenshot = false

    //vertex shader
    private var vertexShaderCode = """
         uniform mat4 uMVPMatrix;
         attribute vec4 a_position;
         attribute vec2 a_textureCoord;
         varying vec2 v_textureCoord;
         
         void main() {
           gl_Position = uMVPMatrix * a_position;
           v_textureCoord = a_textureCoord;
         }
         """.trim()

    //Vertex coordinate data, indicating the position and size of the preview image.
    private val VERTEX_COORDS = floatArrayOf(
        -1.0f, -1.0f,
        1.0f, -1.0f,
        -1.0f, 1.0f,
        1.0f, 1.0f
    )

    //Texture coordinate data represents the mapping relationship of the camera image in the preview area.
    private val TEXTURE_COORDS_MIRRORED = floatArrayOf(
        0f, 0f,
        1f, 0f,
        0f, 1f,
        1f, 1f
    )
    private val TEXTURE_COORDS_ORIG = floatArrayOf(
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f
    )

    //The ID of the shader program
    private var programId = 0

    //Handle to vertex attributes
    private var positionHandle = 0
    private var textureCoordHandle = 0
    private var uMVPMatrixHandle = 0
    private var iTimeHandle = 0
    private var iRandHandle = 0
    private val mvpMatrix = FloatArray(16)

    init {
        textureId = createTexture()
        mSurfaceTexture = SurfaceTexture(textureId)
        "---> MyGLRenderer setting size: ${size.width}, ${size.height}".e
        mSurfaceTexture?.setDefaultBufferSize(size.width, size.height)
//        mSurfaceTexture?.setDefaultBufferSize(2400, 1080)
        mSurfaceTexture?.setOnFrameAvailableListener(this)
    }

    /**
     * Initialize OpenGL and load the vertex shader and fragment shader. By compiling and linking the shader, creating the shader program and getting a handle to the vertex attribute.
     */
    override fun onSurfaceCreated(p0: GL10?, p1: EGLConfig?) {
        val fragmentShaderCode = mGLSurfaceView.context.loadShader("glitch_shader2.glsl")

        // Initialize the OpenGL environment here, such as creating textures, shader programs, etc.
        //Set the color value when clearing the color buffer to black
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        //Load vertex shader and fragment shader
        val vertexShader: Int = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader: Int = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        // Create a shader program and bind the vertex shader and fragment shader to it
        programId = GLES20.glCreateProgram()
        GLES20.glAttachShader(programId, vertexShader)
        GLES20.glAttachShader(programId, fragmentShader)
        // Link the shader program and check whether the link is successful
        GLES20.glLinkProgram(programId)
        // Get the position of the vertex coordinate attribute and texture coordinate attribute
        positionHandle = GLES20.glGetAttribLocation(programId, "a_position")
        textureCoordHandle = GLES20.glGetAttribLocation(programId, "a_textureCoord")
        uMVPMatrixHandle = glGetUniformLocation(programId, "uMVPMatrix")
        iTimeHandle = glGetUniformLocation(programId, "iTime")
        iRandHandle = glGetUniformLocation(programId, "iRand")
        //Use shader program
        GLES20.glUseProgram(programId)
    }

    override fun onSurfaceChanged(p0: GL10?, p1: Int, p2: Int) {
        //Respond to GLSurfaceView size changes here, such as updating the viewport size, etc.
        GLES20.glViewport(0, 0, p1, p2)
    }

    @Throws(OutOfMemoryError::class)
    fun createBitmapFromGLSurface(x: Int, y: Int, w: Int, h: Int, gl: GL10): Bitmap? {
        val bitmapBuffer = IntArray(w * h)
        val bitmapSource = IntArray(w * h)
        val intBuffer = IntBuffer.wrap(bitmapBuffer)
        intBuffer.position(0)

        try {
            gl.glReadPixels(x, y, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer)
            var offset1: Int
            var offset2: Int
            for (i in 0 until h) {
                offset1 = i * w
                offset2 = (h - i - 1) * w
                for (j in 0 until w) {
                    val texturePixel = bitmapBuffer[offset1 + j]
                    val blue = (texturePixel shr 16) and 0xff
                    val red = (texturePixel shl 16) and 0x00ff0000
                    val pixel = (texturePixel and -0xff0100) or red or blue
                    bitmapSource[offset2 + j] = pixel
                }
            }
        } catch (e: GLException) {
            return null
        }

        return Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Draw each frame, perform actual drawing operations here, such as clearing the screen, drawing textures, etc.
     */
    override fun onDrawFrame(p0: GL10?) {
        // Rotate front camera
        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.rotateM(mvpMatrix, 0, rotationAngle, 0.0f, 0.0f, 1.0f)
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)

        GLES20.glUniform1f(iTimeHandle, timeProgress++)
        GLES20.glUniform1f(iRandHandle, Random.nextFloat())

        //Update texture image
        mSurfaceTexture?.updateTexImage()
        // Clear the color buffer
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        //Set the vertex coordinate attribute and enable it
        GLES20.glVertexAttribPointer(
            positionHandle,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            floatBufferFromArray(VERTEX_COORDS)
        )
        GLES20.glEnableVertexAttribArray(positionHandle)
        //Set texture coordinate properties and enable
        GLES20.glVertexAttribPointer(
            textureCoordHandle,
            TEXTURE_COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
//            floatBufferFromArray(if (isFront) TEXTURE_COORDS_MIRRORED else TEXTURE_COORDS_ORIG)
            floatBufferFromArray(TEXTURE_COORDS_ORIG)
        )
        GLES20.glEnableVertexAttribArray(textureCoordHandle)
        // Activate texture unit 0 and bind the current texture to the external OES texture target
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        //Draw the primitives of the triangle strip
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COORDS.size / COORDS_PER_VERTEX)

        if (takeScreenshot) {
            takeScreenshot = false
            makeCringeshot()
        }
    }

    /**
     * Create camera texture
     */
    private fun createTexture(): Int {
        //Create an array to store texture IDs
        val textureIds = IntArray(1)
        // Generate a texture object and store the texture ID into an array
        GLES20.glGenTextures(1, textureIds, 0)
        // Bind the current texture to the OpenGL ES texture target (external OES texture)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureIds[0])
        //Set the wrapping mode of the texture S axis to GL_CLAMP_TO_EDGE, that is,
        // the texture coordinates beyond the boundary will be intercepted to the texels on the boundary
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        //Set the wrapping mode of the texture T axis to GL_CLAMP_TO_EDGE
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        //Set the texture reduction filter to GL_NEAREST, which uses nearest neighbor sampling for texture reduction.
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST
        )
        //Set the texture amplification filter to GL_NEAREST
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_NEAREST
        )
        return textureIds[0]
    }

    /**
     * Load the shader, accept the shader type and shader code as parameters, and return the ID of the compiled shader object
     * @param type shader type, such as GLES20.GL_VERTEX_SHADER or GLES20.GL_FRAGMENT_SHADER
     * @param shaderCode shader code
     * @return shader ID
     */
    private fun loadShader(type: Int, shaderCode: String): Int {
        //Create a new shader object
        val shader = GLES20.glCreateShader(type)
        //Load the shader code into the shader object
        GLES20.glShaderSource(shader, shaderCode)
        //Compile shader
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun floatBufferFromArray(array: FloatArray): FloatBuffer? {
        val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(array.size * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        val floatBuffer: FloatBuffer = byteBuffer.asFloatBuffer()
        floatBuffer.put(array)
        floatBuffer.position(0)
        return floatBuffer
    }

    override fun onFrameAvailable(p0: SurfaceTexture?) {
        // Called back when a new frame is available from the camera, some processing can be done here
        mGLSurfaceView.requestRender()
    }

    fun takeScreenshot() {
        takeScreenshot = true
    }

    private fun makeCringeshot() {
        // TODO: replace cringeshot with ImageReader + GL
        val screenshotSize = width * height
        ByteBuffer.allocateDirect(screenshotSize * 4).apply {
            order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(
                0,
                0,
                width,
                height,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                this
            )

            val pixelsBuffer = IntArray(screenshotSize).let { pixelsBuffer ->
                asIntBuffer()[pixelsBuffer]

                for (i in 0 until screenshotSize) {
                    // The alpha and green channels' positions are preserved while the red and blue are swapped
                    pixelsBuffer[i] =
                        ((pixelsBuffer[i] and -0xff0100)) or ((pixelsBuffer[i] and 0x000000ff) shl 16) or ((pixelsBuffer[i] and 0x00ff0000) shr 16)
                }

                pixelsBuffer
            }

            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixelsBuffer, screenshotSize - width, -width, 0, 0, width, height)
                saveToFile(mGLSurfaceView.context)
            }
        }
    }
}
