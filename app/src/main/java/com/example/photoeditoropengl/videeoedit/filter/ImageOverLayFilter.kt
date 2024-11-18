package com.example.photoeditoropengl.videeoedit.filter

import android.opengl.GLES20
import android.opengl.GLUtils
import com.example.photoeditoropengl.videeoedit.helper.GlFilterOld
import android.content.Context
import android.graphics.BitmapFactory

class GlImageOverlayFilter(
    private val context: Context,
    private val overlayResourceId: Int,
    private val overlayX: Float = 0f,
    private val overlayY: Float = 0f,
    private val overlayWidth: Float = 0.3f,
    private val overlayHeight: Float = 0.3f
) : GlFilterOld(DEFAULT_VERTEX_SHADER, FRAGMENT_SHADER) {

    private var overlayTexture = 0
    private var frameWidth = 0
    private var frameHeight = 0

    companion object {
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;  // video texture
            uniform sampler2D sOverlay;  // overlay texture
            uniform vec4 uOverlayRect;   // x, y, width, height in normalized coordinates
            
            void main() {
                vec4 videoColor = texture2D(sTexture, vTextureCoord);
                
                // Calculate if current pixel is within overlay bounds
                vec2 overlayPos = (vTextureCoord - uOverlayRect.xy) / uOverlayRect.zw;
                
                if (overlayPos.x >= 0.0 && overlayPos.x <= 1.0 &&
                    overlayPos.y >= 0.0 && overlayPos.y <= 1.0) {
                    // Sample overlay texture
                    vec4 overlayColor = texture2D(sOverlay, overlayPos);
                    
                    // Blend based on overlay alpha
                    videoColor = mix(videoColor, overlayColor, overlayColor.a);
                }
                
                gl_FragColor = videoColor;
            }
        """
    }

    override fun setup() {
        super.setup()

        // Create and bind overlay texture
        val textureHandles = IntArray(1)
        GLES20.glGenTextures(1, textureHandles, 0)
        overlayTexture = textureHandles[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexture)

        // Set texture parameters
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // Load overlay image
        val options = BitmapFactory.Options()
        options.inScaled = false  // Prevent automatic scaling
        val bitmap = BitmapFactory.decodeResource(context.resources, overlayResourceId, options)

        // Upload bitmap to texture
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        bitmap.recycle()

        // Get handle for overlay texture uniform
        getHandle("sOverlay")
        getHandle("uOverlayRect")
    }

    override fun onDraw() {
        super.onDraw()

        // Bind overlay texture to texture unit 1
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexture)
        GLES20.glUniform1i(getHandle("sOverlay"), 1)

        // Set overlay rectangle position and size
        GLES20.glUniform4f(
            getHandle("uOverlayRect"),
            overlayX,
            overlayY,
            overlayWidth,
            overlayHeight
        )
    }


}