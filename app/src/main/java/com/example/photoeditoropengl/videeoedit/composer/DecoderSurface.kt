package com.example.photoeditoropengl.videeoedit.composer

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Size
import android.view.Surface
import androidx.annotation.NonNull

class DecoderSurface(@NonNull private var filter: GlFilter, private val logger: Logger) : SurfaceTexture.OnFrameAvailableListener {
    companion object {
        private const val TAG = "DecoderSurface"
        private const val VERBOSE = false
    }

    private var eglDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface = EGL14.EGL_NO_SURFACE
    private lateinit var surface: Surface
    private val frameSyncObject = Object() // guards frameAvailable
    private var frameAvailable = false

    private var texName: Int = 0
    private lateinit var previewTexture: GlSurfaceTexture
    private lateinit var filterFramebufferObject: GlFramebufferObject
    private lateinit var previewShader: GlPreviewFilter
    private lateinit var normalShader: GlFilter
    private lateinit var framebufferObject: GlFramebufferObject

    private val MVPMatrix = FloatArray(16)
    private val ProjMatrix = FloatArray(16)
    private val MMatrix = FloatArray(16)
    private val VMatrix = FloatArray(16)
    private val STMatrix = FloatArray(16)

    private var rotation = Rotation.NORMAL
    private lateinit var outputResolution: Size
    private lateinit var inputResolution: Size
    private var fillMode = FillMode.PRESERVE_ASPECT_FIT
    private var fillModeCustomItem: FillModeCustomItem? = null
    private var flipVertical = false
    private var flipHorizontal = false

    init {
        setup()
    }

    private fun setup() {
        filter.setup()
        framebufferObject = GlFramebufferObject()
        normalShader = GlFilter().apply { setup() }

        val args = IntArray(1)
        GLES20.glGenTextures(args.size, args, 0)
        texName = args[0]

        // Create SurfaceTexture
        previewTexture = GlSurfaceTexture(texName).apply {
            setOnFrameAvailableListener(this@DecoderSurface)
        }
        surface = Surface(previewTexture.surfaceTexture)

        GLES20.glBindTexture(previewTexture.textureTarget, texName)
        EglUtil.setupSampler(previewTexture.textureTarget, GLES20.GL_LINEAR, GLES20.GL_NEAREST)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        previewShader = GlPreviewFilter(previewTexture.textureTarget).apply { setup() }
        filterFramebufferObject = GlFramebufferObject()

        Matrix.setLookAtM(
            VMatrix, 0,
            0.0f, 0.0f, 5.0f,
            0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f
        )

        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, args, 0)
    }

    fun release() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }

        surface.release()
        previewTexture.release()

        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE

        filter.release()
    }

    fun getSurface(): Surface = surface

    fun awaitNewImage() {
        val TIMEOUT_MS = 10000L
        synchronized(frameSyncObject) {
            while (!frameAvailable) {
                try {
                    frameSyncObject.wait(TIMEOUT_MS)
                    if (!frameAvailable) {
                        throw RuntimeException("Surface frame wait timed out")
                    }
                } catch (ie: InterruptedException) {
                    throw RuntimeException(ie)
                }
            }
            frameAvailable = false
        }

        previewTexture.updateTexImage()
        previewTexture.getTransformMatrix(STMatrix)
    }

    fun drawImage() {
        framebufferObject.enable()
        GLES20.glViewport(0, 0, framebufferObject.width, framebufferObject.height)

        filter?.let {
            filterFramebufferObject.enable()
            GLES20.glViewport(0, 0, filterFramebufferObject.width, filterFramebufferObject.height)
            GLES20.glClearColor(
                it.clearColor[0],
                it.clearColor[1],
                it.clearColor[2],
                it.clearColor[3]
            )
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        Matrix.multiplyMM(MVPMatrix, 0, VMatrix, 0, MMatrix, 0)
        Matrix.multiplyMM(MVPMatrix, 0, ProjMatrix, 0, MVPMatrix, 0)

        val scaleDirectionX = if (flipHorizontal) -1f else 1f
        val scaleDirectionY = if (flipVertical) -1f else 1f

        when (fillMode) {
            FillMode.PRESERVE_ASPECT_FIT -> {
                val scale = FillMode.getScaleAspectFit(
                    rotation.rotation,
                    inputResolution.width,
                    inputResolution.height,
                    outputResolution.width,
                    outputResolution.height
                )
                Matrix.scaleM(MVPMatrix, 0, scale[0] * scaleDirectionX, scale[1] * scaleDirectionY, 1f)
                if (rotation != Rotation.NORMAL) {
                    Matrix.rotateM(MVPMatrix, 0, (-rotation.rotation).toFloat(), 0f, 0f, 1f)
                }
            }
            FillMode.PRESERVE_ASPECT_CROP -> {
                val scale = FillMode.getScaleAspectCrop(
                    rotation.rotation,
                    inputResolution.width,
                    inputResolution.height,
                    outputResolution.width,
                    outputResolution.height
                )
                Matrix.scaleM(MVPMatrix, 0, scale[0] * scaleDirectionX, scale[1] * scaleDirectionY, 1f)
                if (rotation != Rotation.NORMAL) {
                    Matrix.rotateM(MVPMatrix, 0, (-rotation.rotation).toFloat(), 0f, 0f, 1f)
                }
            }
            FillMode.CUSTOM -> {
                fillModeCustomItem?.let { item ->
                    Matrix.translateM(MVPMatrix, 0, item.translateX, -item.translateY, 0f)
                    val scale = FillMode.getScaleAspectCrop(
                        rotation.rotation,
                        inputResolution.width,
                        inputResolution.height,
                        outputResolution.width,
                        outputResolution.height
                    )

                    if (item.rotate == 0f || item.rotate == 180f) {
                        Matrix.scaleM(
                            MVPMatrix, 0,
                            item.scale * scale[0] * scaleDirectionX,
                            item.scale * scale[1] * scaleDirectionY,
                            1f
                        )
                    } else {
                        Matrix.scaleM(
                            MVPMatrix, 0,
                            item.scale * scale[0] * (1 / item.videoWidth * item.videoHeight) * scaleDirectionX,
                            item.scale * scale[1] * (item.videoWidth / item.videoHeight) * scaleDirectionY,
                            1f
                        )
                    }

                    Matrix.rotateM(MVPMatrix, 0, -(rotation.rotation + item.rotate), 0f, 0f, 1f)
                }
            }
        }

        previewShader.draw(texName, MVPMatrix, STMatrix, 1f)

        filter?.let {
            framebufferObject.enable()
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            it.draw(filterFramebufferObject.texName, framebufferObject)
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, framebufferObject.width, framebufferObject.height)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        normalShader.draw(framebufferObject.texName, null)
    }

    override fun onFrameAvailable(st: SurfaceTexture) {
        if (VERBOSE) logger.debug(TAG, "new frame available")
        synchronized(frameSyncObject) {
            if (frameAvailable) {
                throw RuntimeException("frameAvailable already set, frame could be dropped")
            }
            frameAvailable = true
            frameSyncObject.notifyAll()
        }
    }

    fun setRotation(rotation: Rotation) {
        this.rotation = rotation
    }

    fun setOutputResolution(resolution: Size) {
        this.outputResolution = resolution
    }

    fun setFillMode(fillMode: FillMode) {
        this.fillMode = fillMode
    }

    fun setInputResolution(resolution: Size) {
        this.inputResolution = resolution
    }

    fun setFillModeCustomItem(fillModeCustomItem: FillModeCustomItem) {
        this.fillModeCustomItem = fillModeCustomItem
    }

    fun setFlipVertical(flipVertical: Boolean) {
        this.flipVertical = flipVertical
    }

    fun setFlipHorizontal(flipHorizontal: Boolean) {
        this.flipHorizontal = flipHorizontal
    }

    fun completeParams() {
        val width = outputResolution.width
        val height = outputResolution.height

        framebufferObject.setup(width, height)
        normalShader.setFrameSize(width, height)

        filterFramebufferObject.setup(width, height)
        previewShader.setFrameSize(width, height)

        Matrix.frustumM(ProjMatrix, 0, -1f, 1f, -1f, 1f, 5f, 7f)
        Matrix.setIdentityM(MMatrix, 0)

        filter?.setFrameSize(width, height)
    }
}