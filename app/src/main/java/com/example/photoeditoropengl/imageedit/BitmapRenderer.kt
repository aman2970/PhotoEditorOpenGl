package com.example.photoeditoropengl.imageedit

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
import com.example.photoeditoropengl.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
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
    private var framebuffer = 0
    private var framebufferTexture = 0
    private var depthRenderbuffer = 0
    @Volatile private var shouldSaveScreenshot = false
    @Volatile private var isProcessingScreenshot = false


    private var doodlePaths = mutableListOf<FloatArray>()
    private lateinit var doodleBuffer: FloatBuffer


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
        viewportWidth = width
        viewportHeight = height
        GLES20.glViewport(0, 0, width, height)

        initializeFramebuffer()

    }

    private fun initializeFramebuffer() {
        if (framebuffer != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        }
        if (framebufferTexture != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(framebufferTexture), 0)
        }
        if (depthRenderbuffer != 0) {
            GLES20.glDeleteRenderbuffers(1, intArrayOf(depthRenderbuffer), 0)
        }

        val fboIds = IntArray(1)
        GLES20.glGenFramebuffers(1, fboIds, 0)
        framebuffer = fboIds[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)

        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        framebufferTexture = textureIds[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, framebufferTexture)

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, viewportWidth, viewportHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        val depthRenderBufferIds = IntArray(1)
        GLES20.glGenRenderbuffers(1, depthRenderBufferIds, 0)
        depthRenderbuffer = depthRenderBufferIds[0]
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depthRenderbuffer)
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, viewportWidth, viewportHeight)

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, framebufferTexture, 0)
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, depthRenderbuffer)

        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e("BitmapRenderer", "Framebuffer is not complete: $status")
            when (status) {
                GLES20.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT -> Log.e("BitmapRenderer", "GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT")
                GLES20.GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS -> Log.e("BitmapRenderer", "GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS")
                GLES20.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT -> Log.e("BitmapRenderer", "GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT")
                GLES20.GL_FRAMEBUFFER_UNSUPPORTED -> Log.e("BitmapRenderer", "GL_FRAMEBUFFER_UNSUPPORTED")
            }
            throw RuntimeException("Framebuffer is not complete: $status")
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glClearColor(backgroundColor[0], backgroundColor[1], backgroundColor[2], backgroundColor[3])
        GLES20.glUseProgram(program)

        setUpModelViewMatrix()
        setUpShaderAttributes()
        drawImage()


        if (doodlePaths.isNotEmpty()) {
            drawDoodle()
        }

        if (shouldSaveScreenshot && !isProcessingScreenshot) {
            isProcessingScreenshot = true
            saveScreenshot()
            shouldSaveScreenshot = false
            isProcessingScreenshot = false
        }
    }

    fun updateDoodlePath(path: List<FloatArray>) {
        doodlePaths = path.toMutableList()
        val doodleVertices = FloatArray(doodlePaths.size * 2)
        for ((i, point) in doodlePaths.withIndex()) {
            doodleVertices[i * 2] = point[0]
            doodleVertices[i * 2 + 1] = point[1]
        }
        doodleBuffer = ByteBuffer.allocateDirect(doodleVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(doodleVertices)
        doodleBuffer.position(0)
    }

    private fun drawDoodle() {
        GLES20.glUseProgram(program)

        GLES20.glLineWidth(5.0f)

        val colorHandle = GLES20.glGetUniformLocation(program, "u_Color")
        GLES20.glUniform4f(colorHandle, 1.0f, 0.0f, 0.0f, 1.0f)


        val positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, doodleBuffer)

        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, doodlePaths.size)

        GLES20.glDisableVertexAttribArray(positionHandle)
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
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_pic)
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
        if (!isProcessingScreenshot) {
            shouldSaveScreenshot = true
        }
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
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        setUpModelViewMatrix()
        setUpShaderAttributes()
        drawImage()

        val buffer = ByteBuffer.allocateDirect(viewportWidth * viewportHeight * 4)
        buffer.order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, viewportWidth, viewportHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)

        buffer.rewind()
        val bitmap = Bitmap.createBitmap(viewportWidth, viewportHeight, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        val flippedBitmap = Bitmap.createBitmap(viewportWidth, viewportHeight, Bitmap.Config.ARGB_8888)
        for (y in 0 until viewportHeight) {
            for (x in 0 until viewportWidth) {
                flippedBitmap.setPixel(x, viewportHeight - y - 1, bitmap.getPixel(x, y))
            }
        }

        saveImageToGallery(flippedBitmap)

        bitmap.recycle()
        flippedBitmap.recycle()

        screenshotSavedListener?.invoke()

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
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

    private var screenshotSavedListener: (() -> Unit)? = null

    fun setScreenshotSavedListener(listener: () -> Unit) {
        screenshotSavedListener = listener
    }
}












