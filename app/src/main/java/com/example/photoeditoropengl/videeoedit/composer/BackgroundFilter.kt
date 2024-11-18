package com.example.photoeditoropengl.videeoedit.composer

import android.opengl.GLES20
import android.opengl.GLES20.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class BackgroundFilter {
    companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform float uCRatio;
            attribute vec4 aPosition;
            
            void main() {
                vec4 scaledPos = aPosition;
                scaledPos.x = scaledPos.x * uCRatio;
                gl_Position = uMVPMatrix * scaledPos;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            
            void main() {
                gl_FragColor = uColor;
            }
        """
    }

    private var program = 0
    private var positionHandle = 0
    private var mvpMatrixHandle = 0
    private var colorHandle = 0
    private var cRatioHandle = 0
    private var vertexBuffer: FloatBuffer

    private var color = floatArrayOf(1.0f, 0.0f, 0.0f, 1.0f) // Red color (RGBA)

    init {
        // Initialize vertex buffer for a full-screen quad
        val vertices = floatArrayOf(
            -1.0f, -1.0f, 0.0f,  // bottom left
            1.0f, -1.0f, 0.0f,   // bottom right
            -1.0f, 1.0f, 0.0f,   // top left
            1.0f, 1.0f, 0.0f     // top right
        )

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer.position(0)
    }

    fun setup() {
        // Create program
        val vertexShader = loadShader(GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        program = glCreateProgram().also { program ->
            glAttachShader(program, vertexShader)
            glAttachShader(program, fragmentShader)
            glLinkProgram(program)
        }

        // Get handles
        positionHandle = glGetAttribLocation(program, "aPosition")
        mvpMatrixHandle = glGetUniformLocation(program, "uMVPMatrix")
        colorHandle = glGetUniformLocation(program, "uColor")
        cRatioHandle = glGetUniformLocation(program, "uCRatio")

        // Clean up shaders
        glDeleteShader(vertexShader)
        glDeleteShader(fragmentShader)
    }

    fun setColor(r: Float, g: Float, b: Float, a: Float) {
        color = floatArrayOf(r, g, b, a)
    }

    fun draw(mvpMatrix: FloatArray, aspectRatio: Float) {
        glUseProgram(program)

        // Set uniforms
        glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        glUniform1f(cRatioHandle, aspectRatio)
        glUniform4fv(colorHandle, 1, color, 0)

        // Set vertex attributes
        glEnableVertexAttribArray(positionHandle)
        vertexBuffer.position(0)
        glVertexAttribPointer(positionHandle, 3, GL_FLOAT, false, 0, vertexBuffer)

        // Draw
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)

        // Cleanup
        glDisableVertexAttribArray(positionHandle)
    }

    fun release() {
        glDeleteProgram(program)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    fun setFrameSize(width: Int, height: Int) {
        // Not needed for background filter
    }
}