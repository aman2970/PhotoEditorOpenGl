package com.example.photoeditoropengl.videeoedit.videosave

enum class FillMode {
    PRESERVE_ASPECT_FIT,
    PRESERVE_ASPECT_CROP,
    CUSTOM;

    companion object {
        fun getScaleAspectFit(angle: Int, widthIn: Int, heightIn: Int, widthOut: Int, heightOut: Int): FloatArray {
            val scale = floatArrayOf(1f, 1f)
            var widthInModified = widthIn
            var heightInModified = heightIn

            if (angle == 90 || angle == 270) {
                val cx = widthInModified
                widthInModified = heightInModified
                heightInModified = cx
            }

            val aspectRatioIn = widthInModified.toFloat() / heightInModified.toFloat()
            val heightOutCalculated = widthOut.toFloat() / aspectRatioIn

            if (heightOutCalculated < heightOut) {
                scale[1] = heightOutCalculated / heightOut
            } else {
                scale[0] = heightOut * aspectRatioIn / widthOut
            }

            return scale
        }

        fun getScaleAspectCrop(angle: Int, widthIn: Int, heightIn: Int, widthOut: Int, heightOut: Int): FloatArray {
            val scale = floatArrayOf(1f, 1f)
            var widthInModified = widthIn
            var heightInModified = heightIn

            if (angle == 90 || angle == 270) {
                val cx = widthInModified
                widthInModified = heightInModified
                heightInModified = cx
            }

            val aspectRatioIn = widthInModified.toFloat() / heightInModified.toFloat()
            val aspectRatioOut = widthOut.toFloat() / heightOut.toFloat()

            if (aspectRatioIn > aspectRatioOut) {
                val widthOutCalculated = heightOut.toFloat() * aspectRatioIn
                scale[0] = widthOutCalculated / widthOut
            } else {
                val heightOutCalculated = widthOut.toFloat() / aspectRatioIn
                scale[1] = heightOutCalculated / heightOut
            }

            return scale
        }
    }
}