package io.iskopasi.shader_test.ui.composables

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES20.glGetUniformLocation
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Size
import io.iskopasi.shader_test.utils.e
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


class MyGLRenderer(
    private var mGLSurfaceView: GLSurfaceView,
    size: Size,
    private val width: Int,
    private val height: Int
) : GLSurfaceView.Renderer,
    OnFrameAvailableListener {
    //Texture ID of camera image
    private var textureId: Int = 0
    var mSurfaceTexture: SurfaceTexture? = null
    private val COORDS_PER_VERTEX = 2
    private val TEXTURE_COORDS_PER_VERTEX = 2

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

    // fragment shader
    private var fragmentShaderCode2 = """
         #extension GL_OES_EGL_image_external : require
         precision mediump float;
         uniform samplerExternalOES u_texture;
         varying vec2 v_textureCoord;
         
         void main() {
           gl_FragColor = texture2D(u_texture, v_textureCoord);
           gl_FragColor.r = 1.0;
         }
         """.trim()
    private var fragmentShaderCode = """
         #extension GL_OES_EGL_image_external : require
         precision mediump float;
         uniform samplerExternalOES u_texture;
         varying vec2 v_textureCoord;
         
         
        uniform float iTime;
//        uniform float progress;
//        uniform shader inputShader;
        uniform vec2 iResolution;
    //    const vec3 offset = vec3(-15, 0, 13);
    //    const mat3 edge = mat3(-1, -1, -2, -3, 15, -3, -2, -1, -1);
        const mat3 edge = mat3(3, -3, -1, -4, 16, -4, -1, -3, 3);
        const mat3 non = mat3(1, 1, 1, 1, 1, 1, 1, 1, 1);
        const int radius = 3;
        
        float rand(vec2 fragCoord){
            return fract(sin(dot(fragCoord, vec2(12.9898, 78.233))) * 43758.5453);
        }
         
         void main() {
            vec4 value = vec4(0, 0, 0, 1);
            float r0 = rand(v_textureCoord + iTime);
            float randFloat = r0 * 300.0;
            int r1Sign = 1;
            int r2Sign = 1;
            int r3Sign = 1;


            if(mod(r0, 2.0) == 0.0) r1Sign = -1;
            if(mod(r0, 3.0) == 0.0) r2Sign = -1;
            if(mod(r0, 5.0) == 0.0) r3Sign = -1;
         
        // Line shift value
        int lineG = 0;
//        if(v_textureCoord.y > randFloat && v_textureCoord.y < (randFloat + 15.0)) {
//            lineG = -200;
//        }

        int r1 = int(rand(v_textureCoord + iTime) * 100.0) * r1Sign;
        int r2 = int(rand(v_textureCoord + iTime + float(r1)) * 10.0) * r2Sign;
        int r3 = int(rand(v_textureCoord + iTime + float(r2)) * 100.0) * r3Sign;
        vec3 offset = vec3(-15 + r1 + lineG, r2 + lineG, 13 + r3 + lineG);
        
        // Sort of edge detection
        for(int x = 0; x < radius; x++) {
            for(int y = 0; y < radius; y++) {
                vec2 coord = vec2(v_textureCoord.x + offset[x], v_textureCoord.y + offset[y]);
                value += texture2D(u_texture, coord) * float(edge[x][y]);
            }
        }
        value /= 9.0;
        value.a = 1.0;
        
        // Removing brightness
        if(
            value.r > 0.5 && 
            value.g > 0.5 && 
            value.b > 0.5 
            ) {
                value.r = 0.0;
                value.g = 0.0;
                value.b = 0.0;
            }
            
        // Increasing green component
        if(
            value.r > 0.2 && 
            value.g > 0.2 && 
            value.b > 0.2 
            ) {
                value.r -= 0.1;
                value.g += (r0 * float(r1Sign) * 2.0);
                value.b -= 0.1;
            } else {
                value.r -= 0.1;
                value.b -= 0.1;
            }
        gl_FragColor = value;
//        return value;
//           gl_FragColor = texture2D(u_texture, v_textureCoord);
//           gl_FragColor.r = 1.0;
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
    private var iResolutionHandle = 0
    private var iTimeHandle = 0
    private val mvpMatrix = FloatArray(16)

    init {
        textureId = createTexture()
        mSurfaceTexture = SurfaceTexture(textureId)
        "---> MyGLRenderer setting size: ${size.width}, ${size.height}".e
        mSurfaceTexture?.setDefaultBufferSize(size.width, size.height)
        mSurfaceTexture?.setOnFrameAvailableListener(this)
    }

    /**
     * Initialize OpenGL and load the vertex shader and fragment shader. By compiling and linking the shader, creating the shader program and getting a handle to the vertex attribute.
     */
    override fun onSurfaceCreated(p0: GL10?, p1: EGLConfig?) {
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
        iResolutionHandle = glGetUniformLocation(programId, "iResolution")
        iTimeHandle = glGetUniformLocation(programId, "iTime")
        //Use shader program
        GLES20.glUseProgram(programId)
    }

    override fun onSurfaceChanged(p0: GL10?, p1: Int, p2: Int) {
        //Respond to GLSurfaceView size changes here, such as updating the viewport size, etc.
        GLES20.glViewport(0, 0, p1, p2)
    }

    /**
     * Draw each frame, perform actual drawing operations here, such as clearing the screen, drawing textures, etc.
     */
    override fun onDrawFrame(p0: GL10?) {
        // Rotate front camera
        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.rotateM(mvpMatrix, 0, 270f, 0.0f, 0.0f, 1.0f)
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)

        GLES20.glUniform2iv(iResolutionHandle, 1, intArrayOf(width, height), 0)
        GLES20.glUniform1f(iTimeHandle, 1f)

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
            floatBufferFromArray(TEXTURE_COORDS_ORIG)
        )
        GLES20.glEnableVertexAttribArray(textureCoordHandle)
        // Activate texture unit 0 and bind the current texture to the external OES texture target
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        //Draw the primitives of the triangle strip
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COORDS.size / COORDS_PER_VERTEX)
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
}