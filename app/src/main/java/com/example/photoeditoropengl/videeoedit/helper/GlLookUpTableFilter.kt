package com.example.photoeditoropengl.videeoedit.helper

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20

class GlLookUpTableFilter : GlFilter {

    companion object {
        private const val FRAGMENT_SHADER =
            """precision mediump float;
                uniform mediump sampler2D lutTexture;
                uniform lowp sampler2D sTexture;
                varying highp vec2 vTextureCoord;
                
                vec4 sampleAs3DTexture(vec3 uv) {
                    float width = 16.0;
                    float sliceSize = 1.0 / width;
                    float slicePixelSize = sliceSize / width;
                    float sliceInnerSize = slicePixelSize * (width - 1.0);
                    float zSlice0 = min(floor(uv.z * width), width - 1.0);
                    float zSlice1 = min(zSlice0 + 1.0, width - 1.0);
                    float xOffset = slicePixelSize * 0.5 + uv.x * sliceInnerSize;
                    float s0 = xOffset + (zSlice0 * sliceSize);
                    float s1 = xOffset + (zSlice1 * sliceSize);
                    vec4 slice0Color = texture2D(lutTexture, vec2(s0, uv.y));
                    vec4 slice1Color = texture2D(lutTexture, vec2(s1, uv.y));
                    float zOffset = mod(uv.z * width, 1.0);
                    vec4 result = mix(slice0Color, slice1Color, zOffset);
                    return result;
                }
                
                void main() {
                    vec4 pixel = texture2D(sTexture, vTextureCoord);
                    vec4 gradedPixel = sampleAs3DTexture(pixel.rgb);
                    gradedPixel.a = pixel.a;
                    pixel = gradedPixel;
                    gl_FragColor = pixel;
                }"""
    }

    private var hTex: Int = EglUtil.NO_TEXTURE
    private var lutTexture: Bitmap?

    constructor(bitmap: Bitmap) : super(DEFAULT_VERTEX_SHADER, FRAGMENT_SHADER) {
        this.lutTexture = bitmap
    }

    constructor(resources: Resources, fxID: Int) : super(DEFAULT_VERTEX_SHADER, FRAGMENT_SHADER) {
        this.lutTexture = BitmapFactory.decodeResource(resources, fxID)
    }

    override fun onDraw() {
        val offsetDepthMapTextureUniform = getHandle("lutTexture")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, hTex)
        GLES20.glUniform1i(offsetDepthMapTextureUniform, 3)
    }

    override fun setup() {
        super.setup()
        loadTexture()
    }

    private fun loadTexture() {
        if (hTex == EglUtil.NO_TEXTURE) {
            hTex = EglUtil.loadTexture(lutTexture, EglUtil.NO_TEXTURE, false)
        }
    }

    fun releaseLutBitmap() {
        lutTexture?.let {
            if (!it.isRecycled) {
                it.recycle()
                lutTexture = null
            }
        }
    }

    fun reset() {
        hTex = EglUtil.NO_TEXTURE
        hTex = EglUtil.loadTexture(lutTexture, EglUtil.NO_TEXTURE, false)
    }
}