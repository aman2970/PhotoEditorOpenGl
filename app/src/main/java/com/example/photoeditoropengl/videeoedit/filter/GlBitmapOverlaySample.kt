package com.example.photoeditoropengl.videeoedit.filter

import android.graphics.Bitmap
import android.graphics.Canvas

class GlBitmapOverlaySample(private val bitmap: Bitmap?) : GlOverlayFilter() {

    override fun drawCanvas(canvas: Canvas?) {
        if (bitmap != null && !bitmap.isRecycled) {
            canvas?.drawBitmap(bitmap, 0f, 0f, null)
        }
    }
}
