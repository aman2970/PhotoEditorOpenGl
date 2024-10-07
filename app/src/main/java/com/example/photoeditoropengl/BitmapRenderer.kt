package com.example.photoeditoropengl

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class BitmapRenderer(private val context: Context) : GLSurfaceView.Renderer {
    private var rotationAngle = 0f
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var scaleFactor = 1.0f
    private var translateX = 0.0f
    private var translateY = 0.0f
    private var isUsingTint = false
    private var isUsingGrayscale = false
    @Volatile private var shouldSaveScreenshot = false

    private val vertices = floatArrayOf(
        -0.5f, -0.5f, 0.0f,  // Bottom-left
        0.5f, -0.5f, 0.0f,   // Bottom-right
        -0.5f, 0.5f, 0.0f,   // Top-left
        0.5f, 0.5f, 0.0f     // Top-right
    )

    private val textureCoordinates = floatArrayOf(
        0.0f, 1.0f,  // Bottom-left
        1.0f, 1.0f,  // Bottom-right
        0.0f, 0.0f,  // Top-left
        1.0f, 0.0f   // Top-right
    )

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var textureBuffer: FloatBuffer
    private var textureId = 0
    private var program = 0
    private val tintColor = floatArrayOf(1.0f, 0.0f, 0.0f, 0.5f)

    private var backgroundColor = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f)

    fun setBackgroundColor(r: Float, g: Float, b: Float, a: Float) {
        backgroundColor[0] = r
        backgroundColor[1] = g
        backgroundColor[2] = b
        backgroundColor[3] = a
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(backgroundColor[0], backgroundColor[1], backgroundColor[2], backgroundColor[3])
        initializeBuffers()
        loadTexture()
        initializeShaderProgram()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glClearColor(backgroundColor[0], backgroundColor[1], backgroundColor[2], backgroundColor[3])
        GLES20.glUseProgram(program)

        setUpModelViewMatrix()
        setUpShaderAttributes()
        drawImage()

        if (shouldSaveScreenshot) {
            saveScreenshot()
            shouldSaveScreenshot = false
        }
    }

    private fun initializeBuffers() {
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer.position(0)

        textureBuffer = ByteBuffer.allocateDirect(textureCoordinates.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(textureCoordinates)
        textureBuffer.position(0)
    }

    private fun loadTexture() {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_emoji)
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        textureId = textureIds[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
    }

    private fun initializeShaderProgram() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().apply {
            GLES20.glAttachShader(this, vertexShader)
            GLES20.glAttachShader(this, fragmentShader)
            GLES20.glLinkProgram(this)
        }
    }

    private fun setUpModelViewMatrix() {
        val modelViewMatrix = FloatArray(16)
        Matrix.setIdentityM(modelViewMatrix, 0)
        Matrix.translateM(modelViewMatrix, 0, translateX, translateY, 0f)
        Matrix.scaleM(modelViewMatrix, 0, scaleFactor, scaleFactor, 1.0f)
        Matrix.rotateM(modelViewMatrix, 0, rotationAngle, 0f, 0f, 1f)

        val matrixHandle = GLES20.glGetUniformLocation(program, "u_Matrix")
        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, modelViewMatrix, 0)
    }

    private fun setUpShaderAttributes() {
        val positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        val texCoordHandle = GLES20.glGetAttribLocation(program, "a_TexCoord")
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

        val textureHandle = GLES20.glGetUniformLocation(program, "u_Texture")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureHandle, 0)

        val tintHandle = GLES20.glGetUniformLocation(program, "u_TintColor")
        GLES20.glUniform4fv(tintHandle, 1, tintColor, 0)

        val isUsingTintHandle = GLES20.glGetUniformLocation(program, "u_IsUsingTint")
        GLES20.glUniform1i(isUsingTintHandle, if (isUsingTint) 1 else 0)

        val isUsingGrayscaleHandle = GLES20.glGetUniformLocation(program, "u_IsUsingGrayscale")
        GLES20.glUniform1i(isUsingGrayscaleHandle, if (isUsingGrayscale) 1 else 0)
    }

    private fun drawImage() {
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun rotateImage() {
        rotationAngle = (rotationAngle + 90f) % 360f
    }

    fun increaseImageSize() {
        scaleFactor *= 1.5f
    }

    fun requestScreenshot() {
        shouldSaveScreenshot = true
    }

    fun moveRight() {
        translateX += 0.1f
    }

    fun moveLeft() {
        translateX -= 0.1f
    }

    fun moveUp() {
        translateY += 0.1f
    }

    fun moveDown() {
        translateY -= 0.1f
    }

    fun toggleTint() {
        isUsingTint = !isUsingTint
    }

    fun toggleGrayscale() {
        isUsingGrayscale = !isUsingGrayscale
    }

    fun setTintColor(r: Float, g: Float, b: Float, a: Float) {
        tintColor[0] = r
        tintColor[1] = g
        tintColor[2] = b
        tintColor[3] = a
    }

    private fun saveScreenshot() {
        val buffer = IntArray(viewportWidth * viewportHeight)
        val byteBuffer = IntBuffer.wrap(buffer)
        byteBuffer.position(0)

        GLES20.glReadPixels(0, 0, viewportWidth, viewportHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, byteBuffer)

        val bitmap = Bitmap.createBitmap(viewportWidth, viewportHeight, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(IntBuffer.wrap(buffer))

        val invertedBitmap = Bitmap.createBitmap(viewportWidth, viewportHeight, Bitmap.Config.ARGB_8888)
        for (y in 0 until viewportHeight) {
            for (x in 0 until viewportWidth) {
                invertedBitmap.setPixel(x, viewportHeight - y - 1, bitmap.getPixel(x, y))
            }
        }

        saveImageToGallery(invertedBitmap)
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "screenshot_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.flush()
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)

                Log.d("Screenshot", "Saved screenshot to Pictures.")
            } catch (e: Exception) {
                Log.e("Screenshot", "Error saving screenshot: ${e.message}")
            }
        } ?: Log.e("Screenshot", "Failed to create MediaStore entry.")
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    companion object {
        private const val vertexShaderCode = """
            uniform mat4 u_Matrix; 
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;

            void main() {
                gl_Position = u_Matrix * a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        private const val fragmentShaderCode = """
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform sampler2D u_Texture;
            uniform vec4 u_TintColor;
            uniform int u_IsUsingTint;
            uniform int u_IsUsingGrayscale;

            void main() {
                vec4 texColor = texture2D(u_Texture, v_TexCoord);
                
                if (u_IsUsingGrayscale == 1) {
                    float gray = dot(texColor.rgb, vec3(0.299, 0.587, 0.114));
                    texColor = vec4(gray, gray, gray, texColor.a);
                }
                
                if (u_IsUsingTint == 1) {
                    gl_FragColor = mix(texColor, u_TintColor, u_TintColor.a);
                } else {
                    gl_FragColor = texColor;
                }
            }
        """
    }
}