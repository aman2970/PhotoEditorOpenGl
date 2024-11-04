package com.example.photoeditoropengl.videeoedit.filter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.Size

class GlCustomOverlayFilter(
    private val overlayBitmap: Bitmap,
    private val overlayScale: Float = 0.3f,
    private val xPosition: Float = 0.1f,
    private val yPosition: Float = 0.1f
) : GlOverlayFilter() {

    private val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }

    override fun drawCanvas(canvas: Canvas?) {
        if (canvas == null) return

        val originalAspectRatio = overlayBitmap.width.toFloat() / overlayBitmap.height

        val targetWidth = inputResolution.width * overlayScale
        val targetHeight = targetWidth / originalAspectRatio

        val x = inputResolution.width * xPosition
        val y = inputResolution.height * yPosition

        val srcRect = RectF(0f, 0f, overlayBitmap.width.toFloat(), overlayBitmap.height.toFloat())
        val dstRect = RectF(x, y, x + targetWidth, y + targetHeight)

        // Draw the scaled and positioned bitmap
        canvas.drawBitmap(overlayBitmap, null, dstRect, paint)
    }

    override fun release() {
        super.release()
    }
}