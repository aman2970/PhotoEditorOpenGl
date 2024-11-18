package com.example.photoeditoropengl.videeoedit.composer

import android.opengl.GLES20

class GlPreview(private val texTarget: Int) : GlFilter(
    VERTEX_SHADER, createFragmentShaderSourceOESIfNeed(
        texTarget
    )
) {
    override fun setup() {
        super.setup()
        getHandle("uMVPMatrix")
        getHandle("uSTMatrix")
        getHandle("uCRatio")
        getHandle("aPosition")
        getHandle("aTextureCoord")
    }

    fun draw(texName: Int, mvpMatrix: FloatArray?, stMatrix: FloatArray?, aspectRatio: Float) {
        useProgram()

        GLES20.glUniformMatrix4fv(getHandle("uMVPMatrix"), 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(getHandle("uSTMatrix"), 1, false, stMatrix, 0)
        GLES20.glUniform1f(getHandle("uCRatio"), aspectRatio)

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
        GLES20.glBindTexture(texTarget, texName)
        GLES20.glUniform1i(getHandle(DEFAULT_UNIFORM_SAMPLER), 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(getHandle("aPosition"))
        GLES20.glDisableVertexAttribArray(getHandle("aTextureCoord"))
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }


    companion object {
        const val GL_TEXTURE_EXTERNAL_OES: Int = 0x8D65

        private const val VERTEX_SHADER = "uniform mat4 uMVPMatrix;\n" +
                "uniform mat4 uSTMatrix;\n" +
                "uniform float uCRatio;\n" +
                "attribute vec4 aPosition;\n" +
                "attribute vec4 aTextureCoord;\n" +
                "varying highp vec2 vTextureCoord;\n" +
                "void main() {\n" +
                "vec4 scaledPos = aPosition;\n" +
                "scaledPos.x = scaledPos.x * uCRatio;\n" +
                "gl_Position = uMVPMatrix * scaledPos;\n" +
                "vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
                "}\n"

        private fun createFragmentShaderSourceOESIfNeed(texTarget: Int): String {
            if (texTarget == GL_TEXTURE_EXTERNAL_OES) {
                return StringBuilder()
                    .append("#extension GL_OES_EGL_image_external : require\n")
                    .append(DEFAULT_FRAGMENT_SHADER.replace("sampler2D", "samplerExternalOES"))
                    .toString()
            }
            return DEFAULT_FRAGMENT_SHADER
        }
    }
}