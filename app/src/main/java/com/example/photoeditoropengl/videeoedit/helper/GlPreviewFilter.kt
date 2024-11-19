package com.example.photoeditoropengl.videeoedit.helper

import android.opengl.GLES20
import android.opengl.GLES20.GL_ARRAY_BUFFER
import android.opengl.GLES20.GL_FLOAT
import android.opengl.GLES20.GL_TEXTURE0
import android.opengl.GLES20.GL_TEXTURE_2D
import android.opengl.GLES20.GL_TRIANGLE_STRIP

import android.opengl.GLES20.*
import com.example.photoeditoropengl.motiongesture.OpenGLStatic.loadShader


class GlPreviewFilter(private val texTarget: Int) : GlFilterOld(VERTEX_SHADER, createFragmentShaderSourceOESIfNeed(texTarget)) {

    companion object {
        const val GL_TEXTURE_EXTERNAL_OES = 0x8D65

 /*       private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uSTMatrix;
            uniform float uCRatio;

            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying highp vec2 vTextureCoord;

            void main() {
                vec4 scaledPos = aPosition;
                scaledPos.x = scaledPos.x * uCRatio;
                gl_Position = uMVPMatrix * scaledPos;
                vTextureCoord = (uSTMatrix * aTextureCoord).xy;
            }
        """*/

        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uSTMatrix;
            uniform float uCRatio;

            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying highp vec2 vTextureCoord;

            void main() {
                // Scale the position to maintain aspect ratio while fitting in square
                vec4 scaledPos = aPosition;
                scaledPos.xy *= uCRatio;
                gl_Position = uMVPMatrix * scaledPos;
                vTextureCoord = (uSTMatrix * aTextureCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying highp vec2 vTextureCoord;
            uniform sampler2D sTexture;
            uniform float uAlpha;
            void main() {
                vec4 color = texture2D(sTexture, vTextureCoord);
                gl_FragColor = vec4(color.rgb, color.a * uAlpha);
            }
        """

        private fun createFragmentShaderSourceOESIfNeed(texTarget: Int): String {
            return if (texTarget == GL_TEXTURE_EXTERNAL_OES) {
                "#extension GL_OES_EGL_image_external : require\n" + FRAGMENT_SHADER.replace("sampler2D", "samplerExternalOES")
            } else {
                FRAGMENT_SHADER
            }
        }
    }

    private var alpha = 1.0f

    fun setAlpha(alpha: Float) {
        this.alpha = alpha
    }

    override fun onDraw() {
        glUniform1f(getHandle("uAlpha"), alpha)
    }

    fun draw(texName: Int, mvpMatrix: FloatArray, stMatrix: FloatArray, aspectRatio: Float) {
        useProgram()

        glUniformMatrix4fv(getHandle("uMVPMatrix"), 1, false, mvpMatrix, 0)
        glUniformMatrix4fv(getHandle("uSTMatrix"), 1, false, stMatrix, 0)
        glUniform1f(getHandle("uCRatio"), aspectRatio)
        glUniform1f(getHandle("uAlpha"), alpha)

        glBindBuffer(GL_ARRAY_BUFFER, getVertexBufferName())
        glEnableVertexAttribArray(getHandle("aPosition"))
        glVertexAttribPointer(getHandle("aPosition"), VERTICES_DATA_POS_SIZE, GL_FLOAT, false, VERTICES_DATA_STRIDE_BYTES, VERTICES_DATA_POS_OFFSET)
        glEnableVertexAttribArray(getHandle("aTextureCoord"))
        glVertexAttribPointer(getHandle("aTextureCoord"), VERTICES_DATA_UV_SIZE, GL_FLOAT, false, VERTICES_DATA_STRIDE_BYTES, VERTICES_DATA_UV_OFFSET)

        glActiveTexture(GL_TEXTURE0)
        glBindTexture(texTarget, texName)
        glUniform1i(getHandle("sTexture"), 0)

        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)

        glDisableVertexAttribArray(getHandle("aPosition"))
        glDisableVertexAttribArray(getHandle("aTextureCoord"))
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindTexture(GL_TEXTURE_2D, 0)
    }
}