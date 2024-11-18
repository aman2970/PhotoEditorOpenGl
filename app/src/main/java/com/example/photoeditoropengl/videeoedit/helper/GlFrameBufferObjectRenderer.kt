package com.example.photoeditoropengl.videeoedit.helper

import android.opengl.GLES20
import android.opengl.GLSurfaceView

import java.util.LinkedList
import java.util.Queue

import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

abstract class GlFrameBufferObjectRenderer : GLSurfaceView.Renderer {

    private lateinit var framebufferObject: GlFramebufferObject
    private lateinit var normalShader: GlFilterOld

    private val runOnDraw: Queue<Runnable> = LinkedList()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        framebufferObject = GlFramebufferObject()
        normalShader = GlFilterOld()
        normalShader.setup()
        onSurfaceCreated(config)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        framebufferObject.setup(width, height)
        normalShader.setFrameSize(width, height)
        onSurfaceChanged(width, height)
        GLES20.glViewport(0, 0, framebufferObject.width, framebufferObject.height)
    }

    override fun onDrawFrame(gl: GL10?) {
        synchronized(runOnDraw) {
            while (runOnDraw.isNotEmpty()) {
                runOnDraw.poll()?.run()
            }
        }
        framebufferObject.enable()

        onDrawFrame(framebufferObject)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        normalShader.draw(framebufferObject.texName, null)
    }

    @Throws(Throwable::class)
    protected fun finalize() {}

    abstract fun onSurfaceCreated(config: EGLConfig?)

    abstract fun onSurfaceChanged(width: Int, height: Int)

    abstract fun onDrawFrame(fbo: GlFramebufferObject)
}