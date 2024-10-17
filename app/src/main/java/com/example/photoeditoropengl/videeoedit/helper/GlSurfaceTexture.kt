package com.example.photoeditoropengl.videeoedit.helper

import android.graphics.SurfaceTexture

class GlSurfaceTexture(texName: Int) : SurfaceTexture.OnFrameAvailableListener {

    var surfaceTexture: SurfaceTexture = SurfaceTexture(texName)
    private var onFrameAvailableListener: SurfaceTexture.OnFrameAvailableListener? = null

    init {
        surfaceTexture.setOnFrameAvailableListener(this)
    }

    fun setOnFrameAvailableListener(listener: SurfaceTexture.OnFrameAvailableListener?) {
        onFrameAvailableListener = listener
    }

    fun getTextureTarget(): Int {
        return GlPreview.GL_TEXTURE_EXTERNAL_OES
    }

    fun updateTexImage() {
        surfaceTexture.updateTexImage()
    }

    fun getTransformMatrix(mtx: FloatArray) {
        surfaceTexture.getTransformMatrix(mtx)
    }

    fun getSurfaceTextures(): SurfaceTexture {
        return surfaceTexture
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        onFrameAvailableListener?.onFrameAvailable(this.surfaceTexture)
    }

    fun release() {
        surfaceTexture.release()
    }
}