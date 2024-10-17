package com.example.photoeditoropengl.videeoedit.helper

import android.content.res.Resources
import android.opengl.GLES20

open class GlFilter {
    companion object {
        const val DEFAULT_UNIFORM_SAMPLER = "sTexture"

        const val DEFAULT_VERTEX_SHADER = """
            attribute highp vec4 aPosition;
            attribute highp vec4 aTextureCoord;
            varying highp vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = aTextureCoord.xy;
            }
        """

        const val DEFAULT_FRAGMENT_SHADER = """
            precision mediump float;
            varying highp vec2 vTextureCoord;
            uniform lowp sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """

        private val VERTICES_DATA = floatArrayOf(
            // X, Y, Z, U, V
            -1.0f, 1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f, 1.0f,
            -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
            1.0f, -1.0f, 0.0f, 1.0f, 0.0f
        )

        const val FLOAT_SIZE_BYTES = 4
        const val VERTICES_DATA_POS_SIZE = 3
        const val VERTICES_DATA_UV_SIZE = 2
        const val VERTICES_DATA_STRIDE_BYTES = (VERTICES_DATA_POS_SIZE + VERTICES_DATA_UV_SIZE) * FLOAT_SIZE_BYTES
        const val VERTICES_DATA_POS_OFFSET = 0 * FLOAT_SIZE_BYTES
        const val VERTICES_DATA_UV_OFFSET = VERTICES_DATA_POS_OFFSET + VERTICES_DATA_POS_SIZE * FLOAT_SIZE_BYTES
    }

    private val vertexShaderSource: String
    private var fragmentShaderSource: String

    private var program = 0
    private var vertexShader = 0
    private var fragmentShader = 0
    private var vertexBufferName = 0

    private val handleMap = mutableMapOf<String, Int>()

    constructor() : this(DEFAULT_VERTEX_SHADER, DEFAULT_FRAGMENT_SHADER)

    constructor(res: Resources, vertexShaderSourceResId: Int, fragmentShaderSourceResId: Int) :
            this(res.getString(vertexShaderSourceResId), res.getString(fragmentShaderSourceResId))

    constructor(vertexShaderSource: String, fragmentShaderSource: String) {
        this.vertexShaderSource = vertexShaderSource
        this.fragmentShaderSource = fragmentShaderSource
    }

    open fun setup() {
        release()
        vertexShader = EglUtil.loadShader(vertexShaderSource, GLES20.GL_VERTEX_SHADER)
        fragmentShader = EglUtil.loadShader(fragmentShaderSource, GLES20.GL_FRAGMENT_SHADER)
        program = EglUtil.createProgram(vertexShader, fragmentShader)
        vertexBufferName = EglUtil.createBuffer(VERTICES_DATA)

        getHandle("aPosition")
        getHandle("aTextureCoord")
        getHandle("sTexture")
    }

    fun setFragmentShaderSource(fragmentShaderSource: String) {
        this.fragmentShaderSource = fragmentShaderSource
    }

    fun setFrameSize(width: Int, height: Int) {
        // Do nothing
    }

    fun release() {
        GLES20.glDeleteProgram(program)
        program = 0
        GLES20.glDeleteShader(vertexShader)
        vertexShader = 0
        GLES20.glDeleteShader(fragmentShader)
        fragmentShader = 0
        GLES20.glDeleteBuffers(1, intArrayOf(vertexBufferName), 0)
        vertexBufferName = 0

        handleMap.clear()
    }

    fun draw(texName: Int, fbo: GlFramebufferObject?) {
        useProgram()

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferName)
        GLES20.glEnableVertexAttribArray(getHandle("aPosition"))
        GLES20.glVertexAttribPointer(
            getHandle("aPosition"),
            VERTICES_DATA_POS_SIZE,
            GLES20.GL_FLOAT,
            false,
            VERTICES_DATA_STRIDE_BYTES,
            VERTICES_DATA_POS_OFFSET
        )
        GLES20.glEnableVertexAttribArray(getHandle("aTextureCoord"))
        GLES20.glVertexAttribPointer(
            getHandle("aTextureCoord"),
            VERTICES_DATA_UV_SIZE,
            GLES20.GL_FLOAT,
            false,
            VERTICES_DATA_STRIDE_BYTES,
            VERTICES_DATA_UV_OFFSET
        )

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texName)
        GLES20.glUniform1i(getHandle("sTexture"), 0)

        onDraw()

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(getHandle("aPosition"))
        GLES20.glDisableVertexAttribArray(getHandle("aTextureCoord"))
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    protected open fun onDraw() {}

    protected fun useProgram() {
        GLES20.glUseProgram(program)
    }

    protected fun getVertexBufferName(): Int {
        return vertexBufferName
    }

    protected fun getHandle(name: String): Int {
        handleMap[name]?.let {
            return it
        }

        var location = GLES20.glGetAttribLocation(program, name)
        if (location == -1) {
            location = GLES20.glGetUniformLocation(program, name)
        }
        if (location == -1) {
            throw IllegalStateException("Could not get attrib or uniform location for $name")
        }
        handleMap[name] = location
        return location
    }
}