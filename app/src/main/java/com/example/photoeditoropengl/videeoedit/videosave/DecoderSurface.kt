package com.example.photoeditoropengl.videeoedit.videosave

import android.graphics.SurfaceTexture
import android.opengl.*
import android.util.Log
import android.util.Size
import android.view.Surface

import android.opengl.GLES20.*
import com.example.photoeditoropengl.videeoedit.helper.EglUtil
import com.example.photoeditoropengl.videeoedit.helper.GlFilter
import com.example.photoeditoropengl.videeoedit.helper.GlFramebufferObject
import com.example.photoeditoropengl.videeoedit.helper.GlPreviewFilter
import com.example.photoeditoropengl.videeoedit.helper.GlSurfaceTexture


class DecoderSurface(private var filter: GlFilter?) : SurfaceTexture.OnFrameAvailableListener {
    private val TAG = "DecoderSurface"
    private val VERBOSE = false
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    var surface: Surface? = null
    private val frameSyncObject = Object()
    private var frameAvailable = false

    private var texName = 0
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
    private var outputResolution: Size? = null
    private var inputResolution: Size? = null
    private var fillMode = FillMode.PRESERVE_ASPECT_FIT
    private var fillModeItem: FillModeItem? = null
    private var flipVertical = false
    private var flipHorizontal = false

    init {
        setup()
    }

    private fun setup() {
        filter?.setup()
        framebufferObject = GlFramebufferObject()
        normalShader = GlFilter()
        normalShader.setup()

        val args = IntArray(1)
        GLES20.glGenTextures(args.size, args, 0)
        texName = args[0]

        previewTexture = GlSurfaceTexture(texName)
        previewTexture!!.setOnFrameAvailableListener(this)
        surface = Surface(previewTexture!!.surfaceTexture)

        glBindTexture(previewTexture!!.getTextureTarget(), texName)
        EglUtil.setupSampler(previewTexture!!.getTextureTarget(), GLES20.GL_LINEAR, GLES20.GL_NEAREST)
        glBindTexture(GL_TEXTURE_2D, 0)

        previewShader = GlPreviewFilter(previewTexture!!.getTextureTarget())
        previewShader.setup()
        filterFramebufferObject = GlFramebufferObject()

        Matrix.setLookAtM(
            VMatrix, 0,
            0.0f, 0.0f, 5.0f,
            0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f
        )

        glGetIntegerv(GL_MAX_TEXTURE_SIZE, args, 0)
    }

    fun release() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        surface?.release()
        previewTexture.release()

        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
        filter?.release()
        filter = null
        surface = null
    }

    fun awaitNewImage() {
        val TIMEOUT_MS = 10000
        synchronized(frameSyncObject) {
            while (!frameAvailable) {
                try {
                    frameSyncObject.wait(TIMEOUT_MS.toLong())
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
        glViewport(0, 0, framebufferObject.width, framebufferObject.height)

        if (filter != null) {
            filterFramebufferObject.enable()
            glViewport(0, 0, filterFramebufferObject.width, filterFramebufferObject.height)
        }

        glClear(GL_COLOR_BUFFER_BIT)
        Matrix.multiplyMM(MVPMatrix, 0, VMatrix, 0, MMatrix, 0)
        Matrix.multiplyMM(MVPMatrix, 0, ProjMatrix, 0, MVPMatrix, 0)

        val scaleDirectionX = if (flipHorizontal) -1 else 1
        val scaleDirectionY = if (flipVertical) -1 else 1

        when (fillMode) {
            FillMode.PRESERVE_ASPECT_FIT -> {
                val scale = FillMode.getScaleAspectFit(
                    rotation.rotation, inputResolution!!.width,
                    inputResolution!!.height, outputResolution!!.width, outputResolution!!.height
                )
                Matrix.scaleM(MVPMatrix, 0, scale[0] * scaleDirectionX, scale[1] * scaleDirectionY, 1f)
                if (rotation != Rotation.NORMAL) {
                    Matrix.rotateM(MVPMatrix, 0, (-rotation.rotation).toFloat(), 0f, 0f, 1f)
                }
            }
            FillMode.PRESERVE_ASPECT_CROP -> {
                val scale = FillMode.getScaleAspectCrop(
                    rotation.rotation, inputResolution!!.width,
                    inputResolution!!.height, outputResolution!!.width, outputResolution!!.height
                )
                Matrix.scaleM(MVPMatrix, 0, scale[0] * scaleDirectionX, scale[1] * scaleDirectionY, 1f)
                if (rotation != Rotation.NORMAL) {
                    Matrix.rotateM(MVPMatrix, 0, (-rotation.rotation).toFloat(), 0f, 0f, 1f)
                }
            }
            FillMode.CUSTOM -> {
                fillModeItem?.let {
                    Matrix.translateM(MVPMatrix, 0, it.translateX, -it.translateY, 0f)
                    val scale = FillMode.getScaleAspectCrop(
                        rotation.rotation, inputResolution!!.width,
                        inputResolution!!.height, outputResolution!!.width, outputResolution!!.height
                    )
                    Matrix.scaleM(MVPMatrix, 0, it.scale * scale[0] * scaleDirectionX, it.scale * scale[1] * scaleDirectionY, 1f)
                    Matrix.rotateM(MVPMatrix, 0, -(rotation.rotation + it.rotate), 0f, 0f, 1f)
                }
            }
            else -> {}
        }

        previewShader.draw(texName, MVPMatrix, STMatrix, 1f)

        if (filter != null) {
            framebufferObject.enable()
            glClear(GL_COLOR_BUFFER_BIT)
            filter!!.draw(filterFramebufferObject.texName, framebufferObject)
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glViewport(0, 0, framebufferObject.width, framebufferObject.height)
        glClear(GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        normalShader.draw(framebufferObject.texName, null)
    }

    override fun onFrameAvailable(st: SurfaceTexture) {
        if (VERBOSE) Log.d(TAG, "new frame available")
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

    fun setFillModeCustomItem(fillModeItem: FillModeItem?) {
        this.fillModeItem = fillModeItem
    }

    fun setFlipVertical(flipVertical: Boolean) {
        this.flipVertical = flipVertical
    }

    fun setFlipHorizontal(flipHorizontal: Boolean) {
        this.flipHorizontal = flipHorizontal
    }

    fun completeParams() {
        val width = outputResolution!!.width
        val height = outputResolution!!.height
        framebufferObject.setup(width, height)
        normalShader.setFrameSize(width, height)

        filterFramebufferObject.setup(width, height)
        previewShader.setFrameSize(width, height)
        Matrix.frustumM(ProjMatrix, 0, -1f, 1f, -1f, 1f, 5f, 7f)
        Matrix.setIdentityM(MMatrix, 0)

        filter?.setFrameSize(width, height)
    }
}