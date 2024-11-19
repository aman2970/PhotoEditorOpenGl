package com.example.photoeditoropengl.videeoedit.helper

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.media3.exoplayer.ExoPlayer

import com.example.photoeditoropengl.videeoedit.view.OpenGlPlayerView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig


class GlPlayerRenderer(private val glPreview: OpenGlPlayerView) : GlFrameBufferObjectRenderer(), SurfaceTexture.OnFrameAvailableListener {
    private var videoEncoder: VideoEncoder? = null
    private var isRecording = false

    private var fadeAlpha = 1.0f
    private var isFadingOut = false
    private var isFadingIn = false
    private var fadeStartTime: Long = 0
    private val FADE_DURATION = 2000L

    private val TAG = GlPlayerRenderer::class.java.simpleName

    private lateinit var previewTexture: GlSurfaceTexture
    private var updateSurface = false

    private var texName: Int = 0

    private val MVPMatrix = FloatArray(16)
    private val ProjMatrix = FloatArray(16)
    private val MMatrix = FloatArray(16)
    private val VMatrix = FloatArray(16)
    private val STMatrix = FloatArray(16)

    private lateinit var filterFramebufferObject: GlFramebufferObject
    private lateinit var previewFilter: GlPreviewFilter

    private var glFilter: GlFilterOld? = null
    private var isNewFilter = false

    private var aspectRatio = 1f
    private var exoPlayer: ExoPlayer? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    private var viewportX = 0
    private var viewportY = 0
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var screenRatio = 1f
    private var currentAspectRatio = AspectRatio.RATIO_16_9

    init {
        Matrix.setIdentityM(STMatrix, 0)
    }

    fun setGlFilter(filter: GlFilterOld) {
        glPreview.queueEvent {
            glFilter?.let {
                it.release()
                if (it is GlLookUpTableFilter) {
                    it.releaseLutBitmap()
                }
            }
            glFilter = filter
            isNewFilter = true
            glPreview.requestRender()
        }
    }

    fun setAspectRatio(ratio: AspectRatio) {
        if (currentAspectRatio != ratio) {
            currentAspectRatio = ratio
            glPreview.queueEvent {
                onSurfaceChanged(viewportWidth, viewportHeight)
                glPreview.requestRender()
            }
        }
    }

    override fun onSurfaceCreated(config: EGLConfig?) {
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f)

        val args = IntArray(1)
        GLES20.glGenTextures(args.size, args, 0)
        texName = args[0]

        previewTexture = GlSurfaceTexture(texName).apply {
            setOnFrameAvailableListener(this@GlPlayerRenderer)
        }

        GLES20.glBindTexture(previewTexture.getTextureTarget(), texName)
        EglUtil.setupSampler(previewTexture.getTextureTarget(), GLES20.GL_LINEAR, GLES20.GL_NEAREST)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        filterFramebufferObject = GlFramebufferObject()
        previewFilter = GlPreviewFilter(previewTexture.getTextureTarget()).apply {
            setup()
        }

        val surface = Surface(previewTexture.getSurfaceTextures())
        uiHandler.post { exoPlayer?.setVideoSurface(surface) }

        Matrix.setLookAtM(VMatrix, 0, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f)

        synchronized(this) {
            updateSurface = false
        }

        if (glFilter != null) {
            isNewFilter = true
        }

        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, args, 0)
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        Log.d(TAG, "onSurfaceChanged width = $width  height = $height")
      /*  filterFramebufferObject.setup(width, height)
        previewFilter.setFrameSize(width, height)
        glFilter?.setFrameSize(width, height)

        aspectRatio = width.toFloat() / height
        Matrix.frustumM(ProjMatrix, 0, -aspectRatio, aspectRatio, -1f, 1f, 5f, 7f)
        Matrix.setIdentityM(MMatrix, 0)
*/
      /*  if (width > height) {
            // Width is larger, center horizontally
            viewportHeight = height
            viewportWidth = height // Make it square
            viewportX = (width - height) / 2
            viewportY = 0
        } else {
            // Height is larger, center vertically
            viewportWidth = width
            viewportHeight = width // Make it square
            viewportX = 0
            viewportY = (height - width) / 2
        }

        // Setup framebuffer with square dimensions
        filterFramebufferObject.setup(viewportWidth, viewportHeight)
        previewFilter.setFrameSize(viewportWidth, viewportHeight)
        glFilter?.setFrameSize(viewportWidth, viewportHeight)

        // Use 1:1 aspect ratio for projection matrix
        Matrix.frustumM(ProjMatrix, 0, -1f, 1f, -1f, 1f, 5f, 7f)
        Matrix.setIdentityM(MMatrix, 0)*/

        // Store original dimensions
        val screenWidth = width
        val screenHeight = height

        // Calculate target aspect ratio
        val targetRatio = currentAspectRatio.getValue()
        screenRatio = width.toFloat() / height.toFloat()

        // Calculate viewport dimensions
        if (screenRatio > targetRatio) {
            // Screen is wider than target ratio
            viewportHeight = height
            viewportWidth = (height * targetRatio).toInt()
            viewportX = (width - viewportWidth) / 2
            viewportY = 0
        } else {
            // Screen is taller than target ratio
            viewportWidth = width
            viewportHeight = (width / targetRatio).toInt()
            viewportX = 0
            viewportY = (height - viewportHeight) / 2
        }

        // Setup framebuffer with calculated dimensions
        filterFramebufferObject.setup(viewportWidth, viewportHeight)
        previewFilter.setFrameSize(viewportWidth, viewportHeight)
        glFilter?.setFrameSize(viewportWidth, viewportHeight)

        // Calculate projection matrix based on target ratio
        val projRatio = targetRatio
        Matrix.frustumM(ProjMatrix, 0, -projRatio, projRatio, -1f, 1f, 5f, 7f)
        Matrix.setIdentityM(MMatrix, 0)

    }

    override fun onDrawFrame(fbo: GlFramebufferObject) {
        synchronized(this) {
            if (updateSurface) {
                previewTexture.updateTexImage()
                previewTexture.getTransformMatrix(STMatrix)
                updateSurface = false
            }
        }

        if (isFadingOut || isFadingIn) {
            val currentTime = System.currentTimeMillis()
            val elapsedTime = currentTime - fadeStartTime

            if (isFadingOut) {
                fadeAlpha = maxOf(0f, 1f - elapsedTime.toFloat() / FADE_DURATION)
                if (fadeAlpha == 0f) {
                    isFadingOut = false
                    uiHandler.post { fadeListener?.onFadeOutComplete() }
                }
            } else {
                fadeAlpha = minOf(1f, elapsedTime.toFloat() / FADE_DURATION)
                if (fadeAlpha == 1f) {
                    isFadingIn = false
                }
            }
        }

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        previewFilter.setAlpha(fadeAlpha)

        if (isNewFilter) {
            glFilter?.let {
                it.setup()
                it.setFrameSize(fbo.width, fbo.height)
            }
            isNewFilter = false
        }

     /*   glFilter?.let {
            filterFramebufferObject.enable()
            GLES20.glViewport(0, 0, filterFramebufferObject.width, filterFramebufferObject.height)
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        Matrix.multiplyMM(MVPMatrix, 0, VMatrix, 0, MMatrix, 0)
        Matrix.multiplyMM(MVPMatrix, 0, ProjMatrix, 0, MVPMatrix, 0)

        previewFilter.draw(texName, MVPMatrix, STMatrix, aspectRatio)

        glFilter?.let {
            fbo.enable()
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            it.draw(filterFramebufferObject.texName, fbo)
        }*/

        // Set viewport for filter rendering
        glFilter?.let {
            filterFramebufferObject.enable()
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Calculate MVP matrix for square rendering
        Matrix.multiplyMM(MVPMatrix, 0, VMatrix, 0, MMatrix, 0)
        Matrix.multiplyMM(MVPMatrix, 0, ProjMatrix, 0, MVPMatrix, 0)

        // Set viewport to maintain aspect ratio
        GLES20.glViewport(viewportX, viewportY, viewportWidth, viewportHeight)

        // Draw with current aspect ratio
        previewFilter.draw(texName, MVPMatrix, STMatrix, currentAspectRatio.getValue())

        glFilter?.let {
            fbo.enable()
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            it.draw(filterFramebufferObject.texName, fbo)
        }
    }

    override fun onFrameAvailable(previewTexture: SurfaceTexture) {
        synchronized(this) {
            updateSurface = true
        }
        glPreview.requestRender()
    }

    fun startFadeOut() {
        isFadingOut = true
        fadeStartTime = System.currentTimeMillis()
    }

    fun startFadeIn() {
        isFadingIn = true
        fadeAlpha = 0f
        fadeStartTime = System.currentTimeMillis()
    }

    private var fadeListener: FadeListener? = null

    interface FadeListener {
        fun onFadeOutComplete()
    }

    fun setFadeListener(listener: FadeListener) {
        fadeListener = listener
    }

    fun setExoPlayer(exoPlayer: ExoPlayer) {
        this.exoPlayer = exoPlayer
    }

    fun release() {
        glFilter?.release()
        previewTexture.release()
    }
}

enum class AspectRatio(val width: Float, val height: Float) {
    RATIO_1_1(1f, 1f),
    RATIO_4_5(4f, 5f),
    RATIO_16_9(16f, 9f),
    RATIO_9_16(9f, 16f);

    fun getValue(): Float = width / height
}