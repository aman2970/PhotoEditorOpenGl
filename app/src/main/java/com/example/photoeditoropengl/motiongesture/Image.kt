package com.example.photoeditoropengl.motiongesture

class Image(
    bitmapWidth: Float,
    bitmapHeight: Float,
    x: Float = 0f,
    y: Float = 0f,
    width: Float = 100f,
    height: Float = 100f,
    textureHandle: Int = -1,
    preloadProgram: Int = -1,
    isVisible: Boolean = true,
    keepSize: Boolean = false,
    usePositionAsCenter: Boolean = true,
    gestureDetector: OpenGLMatrixGestureDetector
) : Images(bitmapWidth, bitmapHeight, floatArrayOf(x, y), width, height, isVisible, gestureDetector, keepSize, usePositionAsCenter, textureHandle, preloadProgram) {

    var x: Float = x
        set(value) {
            field = value
            positions[0] = value
            needUpdate = true
        }

    var y: Float = y
        set(value) {
            field = value
            positions[1] = value
            needUpdate = true
        }
}