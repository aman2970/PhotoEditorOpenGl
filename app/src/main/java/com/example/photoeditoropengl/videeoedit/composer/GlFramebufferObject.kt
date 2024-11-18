package com.example.photoeditoropengl.videeoedit.composer

import android.opengl.GLES20

class GlFramebufferObject {
    var width: Int = 0
        private set
    var height: Int = 0
        private set
    private var framebufferName = 0
    private var renderBufferName = 0
    var texName: Int = 0
        private set

    fun setup(width: Int, height: Int) {
        val args = IntArray(1)

        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, args, 0)
        require(!(width > args[0] || height > args[0])) { "GL_MAX_TEXTURE_SIZE " + args[0] }

        GLES20.glGetIntegerv(GLES20.GL_MAX_RENDERBUFFER_SIZE, args, 0)
        require(!(width > args[0] || height > args[0])) { "GL_MAX_RENDERBUFFER_SIZE " + args[0] }

        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, args, 0)
        val saveFramebuffer = args[0]
        GLES20.glGetIntegerv(GLES20.GL_RENDERBUFFER_BINDING, args, 0)
        val saveRenderbuffer = args[0]
        GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, args, 0)
        val saveTexName = args[0]

        release()

        try {
            this.width = width
            this.height = height

            GLES20.glGenFramebuffers(args.size, args, 0)
            framebufferName = args[0]
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferName)

            GLES20.glGenRenderbuffers(args.size, args, 0)
            renderBufferName = args[0]
            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, renderBufferName)
            GLES20.glRenderbufferStorage(
                GLES20.GL_RENDERBUFFER,
                GLES20.GL_DEPTH_COMPONENT16,
                width,
                height
            )
            GLES20.glFramebufferRenderbuffer(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_DEPTH_ATTACHMENT,
                GLES20.GL_RENDERBUFFER,
                renderBufferName
            )

            GLES20.glGenTextures(args.size, args, 0)
            texName = args[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texName)

            EglUtil.setupSampler(GLES20.GL_TEXTURE_2D, GLES20.GL_LINEAR, GLES20.GL_NEAREST)

            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                width,
                height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                null
            )
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                texName,
                0
            )

            val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                throw RuntimeException("Failed to initialize framebuffer object $status")
            }
        } catch (e: RuntimeException) {
            release()
            throw e
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, saveFramebuffer)
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, saveRenderbuffer)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, saveTexName)
    }

    fun release() {
        val args = IntArray(1)
        args[0] = texName
        GLES20.glDeleteTextures(args.size, args, 0)
        texName = 0
        args[0] = renderBufferName
        GLES20.glDeleteRenderbuffers(args.size, args, 0)
        renderBufferName = 0
        args[0] = framebufferName
        GLES20.glDeleteFramebuffers(args.size, args, 0)
        framebufferName = 0
    }

    fun enable() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferName)
    }
}
