
package com.example.photoeditoropengl.motiongesture

import android.graphics.Matrix
import android.graphics.PointF
import android.util.Log
import android.view.MotionEvent
import com.example.photoeditoropengl.motiongesture.OpenGLStatic.DEVICE_HEIGHT
import com.example.photoeditoropengl.motiongesture.OpenGLStatic.DEVICE_WIDTH
import com.example.photoeditoropengl.motiongesture.OpenGLStatic.NEAR
import com.example.photoeditoropengl.motiongesture.OpenGLStatic.RATIO

class OpenGLMatrixGestureDetector(var matrix: Matrix = Matrix(), listener: OnMatrixChangeListener? = null) {

    internal var pointerIndex: Int
    internal val tempMatrix: Matrix
    internal val source: FloatArray
    internal val distance: FloatArray
    internal var count: Int

    internal val convertMatrix: Matrix
    internal val convertMatrixInvert: Matrix

    lateinit var listener: OnMatrixChangeListener
    var scale: Float
    var angle: Float
    var translate: PointF

    init {

        if (listener != null) {
            this.listener = listener
        }

        pointerIndex = 0
        tempMatrix = Matrix()
        source = FloatArray(4)
        distance = FloatArray(4)
        count = 0
        scale = 0f
        angle = 0f
        translate = PointF()
        convertMatrix = Matrix()
        convertMatrixInvert = Matrix()

        setTransformations()
    }

    fun onTouchEvent(event: MotionEvent) {
        if (event.pointerCount > 2) {
            return
        }

        val action = event.actionMasked
        val index = event.actionIndex
        when (action) {

            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {

                val idx = index * 2
                source[idx] = event.getX(index)
                source[idx + 1] = event.getY(index)

                count++
                pointerIndex = 0
            }

            MotionEvent.ACTION_MOVE -> {
                var i = 0
                while (i < count) {
                    val idx = pointerIndex + i * 2
                    distance[idx] = event.getX(i)
                    distance[idx + 1] = event.getY(i)
                    i++
                }

                tempMatrix.setPolyToPoly(source, pointerIndex, distance, pointerIndex, count)
                matrix.postConcat(tempMatrix)
                System.arraycopy(distance, 0, source, 0, distance.size)

                if (::listener.isInitialized) {
                    listener.onMatrixChange(this)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(index) == 0) pointerIndex = 2
                count--
            }
        }

        setTransformations()
    }

    fun setTransformations() {
        val points = FloatArray(9)
        matrix.getValues(points)

        val scaleX: Float = points[Matrix.MSCALE_X]
        val skewX: Float = points[Matrix.MSKEW_X]
        val skewY: Float = points[Matrix.MSKEW_Y]
        val translateX: Float = points[Matrix.MTRANS_X]
        val translateY: Float = points[Matrix.MTRANS_Y]

        // set translate
        translate.x = translateX
        translate.y = translateY

        // set scale
        scale = Math.sqrt(scaleX * scaleX + skewY * skewY.toDouble()).toFloat()

        // set rotation as angle in degrees
        angle = -(Math.atan2(skewX.toDouble(), scaleX.toDouble()) * (180 / Math.PI)).toFloat()
        if (angle == -0f) {
            angle = -angle
        }

    }

    fun transform(sourceMatrix: FloatArray, destinationMatrix: FloatArray) {
        Companion.transform(sourceMatrix, destinationMatrix, matrix)
    }

    fun normalizeCoordinate(x: Float, y: Float, pointOpenGL: PointF = PointF()): PointF {

        val v = FloatArray(9)
        matrix.getValues(v)

        v[2] = normalizeTranslateX(v[2])
        v[5] = normalizeTranslateY(v[5])

        convertMatrix.setValues(v)
        convertMatrix.invert(convertMatrixInvert)

        val xOpenGL = normalizeTranslateX(x)
        val yOpenGL = normalizeTranslateY(y)
        val coords = floatArrayOf(xOpenGL, yOpenGL)
        convertMatrixInvert.mapPoints(coords)

        pointOpenGL.x = coords[0]
        pointOpenGL.y = coords[1]

        return pointOpenGL
    }

    fun normalizeCoordinates(coordinates: FloatArray, coordinatesOpenGL: FloatArray = coordinates): FloatArray {
        val v = FloatArray(9)
        matrix.getValues(v)

        v[2] = normalizeTranslateX(v[2])
        v[5] = normalizeTranslateY(v[5])

        convertMatrix.setValues(v)
        convertMatrix.invert(convertMatrixInvert)

        for (i in 0 until coordinates.size / 2) {

            val x = coordinates[i * 2]
            val y = coordinates[i * 2 + 1]

            val xOpenGL = normalizeTranslateX(x)
            val yOpenGL = normalizeTranslateY(y)

            coordinatesOpenGL[i * 2] = xOpenGL
            coordinatesOpenGL[i * 2 + 1] = yOpenGL
        }

        convertMatrixInvert.mapPoints(coordinatesOpenGL)
        return coordinatesOpenGL
    }

    fun normalizeWidth(width: Float, isScalable: Boolean = true): Float {
        var openGLWidth = (width / DEVICE_WIDTH) * NEAR * RATIO * 2
        if (!isScalable) {
            openGLWidth /= scale
        }
        return openGLWidth
    }


    fun normalizeHeight(height: Float, isScalable: Boolean = true): Float {
        var openGLHeight = (height / DEVICE_HEIGHT) * NEAR * 2
        if (!isScalable) {
            openGLHeight /= scale
        }
        return openGLHeight
    }


    interface OnMatrixChangeListener {
        fun onMatrixChange(matrixGestureDetector: OpenGLMatrixGestureDetector)
    }

    companion object {

        fun Matrix.scale(): Float {
            val points = FloatArray(9)
            getValues(points)

            val scaleX: Float = points[Matrix.MSCALE_X]
            val skewY: Float = points[Matrix.MSKEW_Y]
            return Math.sqrt(scaleX * scaleX + skewY * skewY.toDouble()).toFloat()
        }

        fun Matrix.angle(): Float {
            val points = FloatArray(9)
            getValues(points)

            val scaleX: Float = points[Matrix.MSCALE_X]
            val skewX: Float = points[Matrix.MSKEW_X]
            return -(Math.atan2(skewX.toDouble(), scaleX.toDouble()) * (180 / Math.PI)).toFloat()
        }


        fun Matrix.translate(): PointF {
            val points = FloatArray(9)
            getValues(points)

            return PointF(points[Matrix.MTRANS_X], points[Matrix.MTRANS_Y])
        }


        fun transform(sourceMatrix: FloatArray, destinationMatrix: FloatArray, matrix: Matrix) {

            val v = FloatArray(9)
            matrix.getValues(v)

            v[2] = normalizeTranslateX(v[2])
            v[5] = normalizeTranslateY(v[5])

            val openGLMatrix = floatArrayOf(
                v[0], v[3], 0f, 0f,
                v[1], v[4], 0f, 0f,
                0f, 0f, 1f, 0f,
                v[2], v[5], 0f, 1f
            )

            android.opengl.Matrix.multiplyMM(destinationMatrix, 0, sourceMatrix, 0, openGLMatrix, 0)
        }

        fun normalizeTranslateX(x: Float, allowZero: Boolean = true): Float {

            if (!allowZero && x == 0f) {
                return 0f
            }

            val translateX = if (x < DEVICE_WIDTH / 2f) {
                -1f + (x / (DEVICE_WIDTH / 2f))
            } else {
                (x - (DEVICE_WIDTH / 2f)) / (DEVICE_WIDTH / 2f)
            }

            return -translateX * NEAR * RATIO
        }

        fun normalizeTranslateY(y: Float, allowZero: Boolean = true): Float {

            if (!allowZero && y == 0f) {
                return 0f
            }

            val translateY = if (y < DEVICE_HEIGHT / 2f) {
                1f - (y / (DEVICE_HEIGHT / 2f))
            } else {
                -(y - (DEVICE_HEIGHT / 2f)) / (DEVICE_HEIGHT / 2f)
            }

            return translateY * NEAR
        }
    }
}