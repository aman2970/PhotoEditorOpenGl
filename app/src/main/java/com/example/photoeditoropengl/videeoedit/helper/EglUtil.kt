package com.example.photoeditoropengl.videeoedit.helper

import android.graphics.Bitmap
import android.opengl.GLES20.*
import android.opengl.GLException
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object EglUtil {
    const val NO_TEXTURE = -1
    private const val FLOAT_SIZE_BYTES = 4

    fun loadShader(strSource: String, iType: Int): Int {
        val compiled = IntArray(1)
        val iShader = glCreateShader(iType)
        glShaderSource(iShader, strSource)
        glCompileShader(iShader)
        glGetShaderiv(iShader, GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            return 0
        }
        return iShader
    }

    @Throws(GLException::class)
    fun createProgram(vertexShader: Int, pixelShader: Int): Int {
        val program = glCreateProgram()
        if (program == 0) {
            throw RuntimeException("Could not create program")
        }
        glAttachShader(program, vertexShader)
        glAttachShader(program, pixelShader)
        glLinkProgram(program)
        val linkStatus = IntArray(1)
        glGetProgramiv(program, GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GL_TRUE) {
            glDeleteProgram(program)
            throw RuntimeException("Could not link program")
        }
        return program
    }

    fun setupSampler(target: Int, mag: Int, min: Int) {
        glTexParameterf(target, GL_TEXTURE_MAG_FILTER, mag.toFloat())
        glTexParameterf(target, GL_TEXTURE_MIN_FILTER, min.toFloat())
        glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
    }

    fun createBuffer(data: FloatArray): Int {
        return createBuffer(toFloatBuffer(data))
    }

    fun createBuffer(data: FloatBuffer): Int {
        val buffers = IntArray(1)
        glGenBuffers(buffers.size, buffers, 0)
        updateBufferData(buffers[0], data)
        return buffers[0]
    }

    fun toFloatBuffer(data: FloatArray): FloatBuffer {
        val buffer = ByteBuffer
            .allocateDirect(data.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(data).position(0)
        return buffer
    }

    fun updateBufferData(bufferName: Int, data: FloatBuffer) {
        glBindBuffer(GL_ARRAY_BUFFER, bufferName)
        glBufferData(GL_ARRAY_BUFFER, data.capacity() * FLOAT_SIZE_BYTES, data, GL_STATIC_DRAW)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
    }

    fun loadTexture(img: Bitmap?, usedTexId: Int, recycle: Boolean): Int {
        val textures = IntArray(1)
        if (usedTexId == NO_TEXTURE) {
            glGenTextures(1, textures, 0)
            glBindTexture(GL_TEXTURE_2D, textures[0])
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR.toFloat())
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR.toFloat())
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GL_TEXTURE_2D, 0, img, 0)
        } else {
            glBindTexture(GL_TEXTURE_2D, usedTexId)
            GLUtils.texSubImage2D(GL_TEXTURE_2D, 0, 0, 0, img)
            textures[0] = usedTexId
        }
        if (recycle) {
            img?.recycle()
        }
        return textures[0]
    }
}