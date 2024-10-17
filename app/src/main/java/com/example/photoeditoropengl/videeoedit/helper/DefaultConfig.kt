package com.example.photoeditoropengl.videeoedit.helper

import android.opengl.GLSurfaceView

import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

open class DefaultConfig : GLSurfaceView.EGLConfigChooser {

    private val configSpec: IntArray
    private val redSize: Int
    private val greenSize: Int
    private val blueSize: Int
    private val alphaSize: Int
    private val depthSize: Int
    private val stencilSize: Int

    constructor(version: Int) : this(true, version)

    constructor(withDepthBuffer: Boolean, version: Int) : this(
        redSize = 8,
        greenSize = 8,
        blueSize = 8,
        alphaSize = 0,
        depthSize = if (withDepthBuffer) 16 else 0,
        stencilSize = 0,
        version = version
    )

    constructor(
        redSize: Int,
        greenSize: Int,
        blueSize: Int,
        alphaSize: Int,
        depthSize: Int,
        stencilSize: Int,
        version: Int
    ) {
        configSpec = filterConfigSpec(
            intArrayOf(
                EGL10.EGL_RED_SIZE, redSize,
                EGL10.EGL_GREEN_SIZE, greenSize,
                EGL10.EGL_BLUE_SIZE, blueSize,
                EGL10.EGL_ALPHA_SIZE, alphaSize,
                EGL10.EGL_DEPTH_SIZE, depthSize,
                EGL10.EGL_STENCIL_SIZE, stencilSize,
                EGL10.EGL_NONE
            ), version
        )
        this.redSize = redSize
        this.greenSize = greenSize
        this.blueSize = blueSize
        this.alphaSize = alphaSize
        this.depthSize = depthSize
        this.stencilSize = stencilSize
    }

    companion object {
        private const val EGL_OPENGL_ES2_BIT = 4
    }

    private fun filterConfigSpec(configSpec: IntArray, version: Int): IntArray {
        return if (version != 2) {
            configSpec
        } else {
            val len = configSpec.size
            val newConfigSpec = IntArray(len + 2)
            System.arraycopy(configSpec, 0, newConfigSpec, 0, len - 1)
            newConfigSpec[len - 1] = EGL10.EGL_RENDERABLE_TYPE
            newConfigSpec[len] = EGL_OPENGL_ES2_BIT
            newConfigSpec[len + 1] = EGL10.EGL_NONE
            newConfigSpec
        }
    }

    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        val numConfig = IntArray(1)
        if (!egl.eglChooseConfig(display, configSpec, null, 0, numConfig)) {
            throw IllegalArgumentException("eglChooseConfig failed")
        }

        val configSize = numConfig[0]
        if (configSize <= 0) {
            throw IllegalArgumentException("No configs match configSpec")
        }

        val configs = arrayOfNulls<EGLConfig>(configSize)
        if (!egl.eglChooseConfig(display, configSpec, configs, configSize, numConfig)) {
            throw IllegalArgumentException("eglChooseConfig#2 failed")
        }

        val config = chooseConfig(egl, display, configs.requireNoNulls())
            ?: throw IllegalArgumentException("No config chosen")
        return config
    }

    private fun chooseConfig(egl: EGL10, display: EGLDisplay, configs: Array<EGLConfig>): EGLConfig? {
        for (config in configs) {
            val d = findConfigAttrib(egl, display, config, EGL10.EGL_DEPTH_SIZE, 0)
            val s = findConfigAttrib(egl, display, config, EGL10.EGL_STENCIL_SIZE, 0)
            if (d >= depthSize && s >= stencilSize) {
                val r = findConfigAttrib(egl, display, config, EGL10.EGL_RED_SIZE, 0)
                val g = findConfigAttrib(egl, display, config, EGL10.EGL_GREEN_SIZE, 0)
                val b = findConfigAttrib(egl, display, config, EGL10.EGL_BLUE_SIZE, 0)
                val a = findConfigAttrib(egl, display, config, EGL10.EGL_ALPHA_SIZE, 0)
                if (r == redSize && g == greenSize && b == blueSize && a == alphaSize) {
                    return config
                }
            }
        }
        return null
    }

    private fun findConfigAttrib(egl: EGL10, display: EGLDisplay, config: EGLConfig, attribute: Int, defaultValue: Int): Int {
        val value = IntArray(1)
        return if (egl.eglGetConfigAttrib(display, config, attribute, value)) {
            value[0]
        } else {
            defaultValue
        }
    }
}