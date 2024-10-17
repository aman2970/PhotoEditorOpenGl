package com.example.photoeditoropengl.videeoedit.helper

class GlConfig(withDepthBuffer: Boolean) : DefaultConfig(withDepthBuffer, EGL_CONTEXT_CLIENT_VERSION) {
    companion object {
        private const val EGL_CONTEXT_CLIENT_VERSION = 2
    }
}