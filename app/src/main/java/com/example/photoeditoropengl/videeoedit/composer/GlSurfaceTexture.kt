package com.example.photoeditoropengl.videeoedit.composer

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener


class GlSurfaceTexture(texName: Int) : OnFrameAvailableListener {
    val surfaceTexture: SurfaceTexture = SurfaceTexture(texName)
    private var onFrameAvailableListener: OnFrameAvailableListener? = null

    init {
        surfaceTexture.setOnFrameAvailableListener(this)
    }


    fun setOnFrameAvailableListener(l: OnFrameAvailableListener?) {
        onFrameAvailableListener = l
    }


    val textureTarget: Int
        get() = GlPreview.GL_TEXTURE_EXTERNAL_OES

    fun updateTexImage() {
        surfaceTexture.updateTexImage()
    }

    fun getTransformMatrix(mtx: FloatArray?) {
        surfaceTexture.getTransformMatrix(mtx)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        if (onFrameAvailableListener != null) {
            onFrameAvailableListener!!.onFrameAvailable(this.surfaceTexture)
        }
    }

    fun release() {
        surfaceTexture.release()
    }
}

