package com.example.photoeditoropengl.videeoedit.helper

import android.opengl.GLSurfaceView
import android.util.Log

import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay

open class DefaultContextFactory(private val EGLContextClientVersion: Int) : GLSurfaceView.EGLContextFactory {

    companion object {
        private const val TAG = "DefaultContextFactory"
        private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098
    }

    override fun createContext(egl: EGL10, display: EGLDisplay, config: EGLConfig): EGLContext {
        val attribList: IntArray? = if (EGLContextClientVersion != 0) {
            intArrayOf(EGL_CONTEXT_CLIENT_VERSION, EGLContextClientVersion, EGL10.EGL_NONE)
        } else {
            null
        }
        return egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attribList)
    }

    override fun destroyContext(egl: EGL10, display: EGLDisplay, context: EGLContext) {
        if (!egl.eglDestroyContext(display, context)) {
            throw RuntimeException("eglDestroyContext: ${egl.eglGetError()}")
        }
    }
}