
package com.example.photoeditoropengl.motiongesture

import android.graphics.Color
import android.graphics.PointF
import android.opengl.GLES20
import com.example.photoeditoropengl.motiongesture.OpenGLStatic.concat
import com.example.photoeditoropengl.motiongesture.OpenGLStatic.delete
import com.slaviboy.opengl.main.OpenGLColor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

open class Shapes(
    coordinates: FloatArray = floatArrayOf(),
    colors: IntArray = intArrayOf(),
    var strokeWidth: Float = -1f,
    var isVisible: Boolean = true,
    var gestureDetector: OpenGLMatrixGestureDetector,
    val shapeType: Int = GLES20.GL_LINES,
    numberOfVerticesPerShape: Int = 2,
    val preloadProgram: Int = -1,
    val useSingleColor: Boolean = false
) {

    internal var coordinatesPerShape: Int         // how many coordinate values are per shape: {Line:4}, {Triangle:6}, {Rectangle:8}..
    internal var colorsPerShape: Int              // how many color values are per shape

    internal var needUpdate: Boolean              // if the coordinates in OpenGL should be generated again, once the draw method is called

    internal var program: Int                     // program for attaching the shaders
    internal var positionHandle: Int              // handle for the position
    internal var colorHandle: Int                 // handle for the color
    internal var MVPMatrixHandle: Int             // handle for the MVP matrix
    internal var tempPoint: PointF                // temp point that is used in the conversion from the graphic to OpenGL coordinate system

    internal var vertexShaderCode: String         // shader string with the vertex
    internal var fragmentShaderCode: String       // shader string with the fragment
    lateinit var colorBuffer: FloatBuffer         // buffer for the vertex colors
    lateinit var vertexBuffer: FloatBuffer        // buffer for the vertex coordinates

    internal var colorsOpenGL: FloatArray         // colors for each shapes, with 4 color channel values r,g,b,a for each vertex
    internal var coordinatesOpenGL: FloatArray    // coordinates for each shape, with 2 values x,y for each vertex and it is in OpenGL coordinate system


    var colors: IntArray = colors
        set(value) {

            if (value.size == 1 && useSingleColor) {
                colorsOpenGL = OpenGLColor.getArray(value[0])
            } else {
                // generate the array with OpenGL colors
                colorsOpenGL = FloatArray(value.size * colorsPerShape)
                for (i in value.indices) {
                    setColor(i, value[i])
                }
            }
            field = value
        }

    var numberOfVerticesPerShape: Int = numberOfVerticesPerShape
        set(value) {
            field = value
            coordinatesPerShape = value * COORDINATES_PER_VERTEX
            colorsPerShape = value * COLOR_CHANNELS_PER_VERTEX
        }

    // the coordinates from the constructor with custom setter
    var coordinates: FloatArray = coordinates
        set(value) {
            field = value
            needUpdate = true
        }

    init {

        coordinatesPerShape = numberOfVerticesPerShape * COORDINATES_PER_VERTEX
        colorsPerShape = numberOfVerticesPerShape * COLOR_CHANNELS_PER_VERTEX

        needUpdate = false
        positionHandle = 0
        colorHandle = 0
        MVPMatrixHandle = 0
        program = -1

        colorsOpenGL = FloatArray((coordinates.size / coordinatesPerShape) * COLOR_CHANNELS_PER_VERTEX)
        coordinatesOpenGL = FloatArray(coordinates.size)

        tempPoint = PointF()
        vertexShaderCode = OpenGLStatic.vertexMultipleColorsShaderCode
        fragmentShaderCode = OpenGLStatic.fragmentMultipleColorsShaderCode

        if (preloadProgram == -1) {
            val vertexShader: Int = OpenGLStatic.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            val fragmentShader: Int = OpenGLStatic.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

            program = GLES20.glCreateProgram()              // create empty OpenGL ES Program
            GLES20.glAttachShader(program, vertexShader)    // add the vertex shader to program
            GLES20.glAttachShader(program, fragmentShader)  // add the fragment shader to program
            GLES20.glLinkProgram(program)                   // creates OpenGL ES program executables
        } else {
            program = preloadProgram
        }

        this.colors = colors
        generateCoordinatesOpenGL()
        updateBuffers()
    }

    open fun draw(mvpMatrix: FloatArray) {

        // draw shapes only if its property visible is set to true
        if (!isVisible) {
            return
        }

        if (needUpdate) {
            // generate OpenGL coordinate from the graphic coordinates
            generateCoordinatesOpenGL()

            vertexBuffer.put(coordinatesOpenGL)
            vertexBuffer.position(0)
        }

        val vertexCount = coordinatesOpenGL.size / COORDINATES_PER_VERTEX

        // set program and stroke, and use alpha
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(program)

        if (strokeWidth != -1.0f) {
            GLES20.glLineWidth(strokeWidth)
        }

        // set position for the vertices
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, COORDINATES_PER_VERTEX, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        if (useSingleColor) {

            // set single color for all the vertices
            colorHandle = GLES20.glGetUniformLocation(program, "vColor")
            GLES20.glUniform4fv(colorHandle, 1, colorsOpenGL, 0)
        } else {

            // set multiple colors for each vertex
            colorHandle = GLES20.glGetAttribLocation(program, "vColor")
            GLES20.glEnableVertexAttribArray(colorHandle)
            GLES20.glVertexAttribPointer(colorHandle, COLOR_CHANNELS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, colorBuffer)
        }

        // get handle to shape's transformation matrix
        MVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        OpenGLStatic.checkGlError("glGetUniformLocation")

        // apply the projection and view transformation
        GLES20.glUniformMatrix4fv(MVPMatrixHandle, 1, false, mvpMatrix, 0)
        OpenGLStatic.checkGlError("glUniformMatrix4fv")

        // draw the shapes
        GLES20.glDrawArrays(shapeType, 0, vertexCount)

        // disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }


    private fun updateBuffers() {

        // init buffer for vertices
        var byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(coordinatesOpenGL.size * BYTES_PER_FLOAT)
        byteBuffer.order(ByteOrder.nativeOrder())
        vertexBuffer = byteBuffer.asFloatBuffer()
        vertexBuffer.put(coordinatesOpenGL)
        vertexBuffer.position(0)

        // init buffer for colors
        byteBuffer = ByteBuffer.allocateDirect(colorsOpenGL.size * BYTES_PER_FLOAT)
        byteBuffer.order(ByteOrder.nativeOrder())
        colorBuffer = byteBuffer.asFloatBuffer()
        colorBuffer.put(colorsOpenGL)
        colorBuffer.position(0)
    }


    fun getActualCoordinate(isCoordinatesInfo: Boolean, coordinates: FloatArray): FloatArray {

        return if (isCoordinatesInfo) {
            when (this) {
                else -> coordinates
            }
        } else {
            coordinates
        }
    }


    fun setColor(i: Int, color: Int) {

        // if single color is used for all shapes
        if (useSingleColor) {
            colors = intArrayOf(color)
            return
        }

        // set the color
        colors[i] = color

        // get rgba channel values from the integer[0,255] color representation to float[0,1]
        val r: Float = (color shr 16 and 0xff) / 255.0f
        val g: Float = (color shr 8 and 0xff) / 255.0f
        val b: Float = (color and 0xff) / 255.0f
        val a: Float = (color shr 24 and 0xff) / 255.0f

        val j = i * colorsPerShape

        // set the color for each vertex to match the same color
        for (k in 0 until numberOfVerticesPerShape) {
            val m = k * COLOR_CHANNELS_PER_VERTEX
            colorsOpenGL[j + m] = r
            colorsOpenGL[j + m + 1] = g
            colorsOpenGL[j + m + 2] = b
            colorsOpenGL[j + m + 3] = a
        }
    }


    internal fun generateCoordinatesOpenGL(coordinates: FloatArray = this.coordinates) {

        // get the OpenGL coordinate for each of the vertices
        coordinatesOpenGL = FloatArray(coordinates.size)
        gestureDetector.normalizeCoordinates(coordinates, coordinatesOpenGL)

        needUpdate = false
    }


    internal fun add(i: Int = coordinatesOpenGL.size / coordinatesPerShape, color: Int = Color.TRANSPARENT, isCoordinatesInfo: Boolean, vararg newCoordinates: Float) {

        // get actual coordinates
        val actualCoordinates = getActualCoordinate(isCoordinatesInfo, newCoordinates)

        // convert the coordinates in OpenGL coordinate system
        gestureDetector.normalizeCoordinates(actualCoordinates)

        // add coordinates in OpenGL coordinates
        addOpenGL(i, color, false, *actualCoordinates)
    }

    fun add(i: Int, color: Int, vararg newCoordinates: Float) {
        add(i, color, true, *newCoordinates)
    }


    internal fun addOpenGL(i: Int = coordinatesOpenGL.size / coordinatesPerShape, color: Int = Color.TRANSPARENT, isCoordinatesInfo: Boolean = true, vararg newCoordinatesOpenGL: Float) {

        // get actual coordinates
        val actualCoordinates = getActualCoordinate(isCoordinatesInfo, newCoordinatesOpenGL)

        // add the coordinate for the new shape
        coordinatesOpenGL = coordinatesOpenGL.concat(i, actualCoordinates, coordinatesPerShape)

        // get rgba channel values from the integer color representation
        val r: Float = (color shr 16 and 0xff) / 255.0f
        val g: Float = (color shr 8 and 0xff) / 255.0f
        val b: Float = (color and 0xff) / 255.0f
        val a: Float = (color shr 24 and 0xff) / 255.0f

        // add the color for the new shape, 4 values(r,g,b,a) for each vertex of the shape
        val newColors = FloatArray(colorsPerShape)
        for (j in 0 until numberOfVerticesPerShape) {
            val k = j * COLOR_CHANNELS_PER_VERTEX
            newColors[k] = r
            newColors[k + 1] = g
            newColors[k + 2] = b
            newColors[k + 3] = a
        }
        colorsOpenGL = colorsOpenGL.concat(i, newColors, colorsPerShape)

        updateBuffers()
    }

    fun addOpenGL(i: Int, color: Int, vararg newCoordinatesOpenGL: Float) {
        addOpenGL(i, color, true, *newCoordinatesOpenGL)
    }


    internal fun change(i: Int = coordinatesOpenGL.size / coordinatesPerShape, isCoordinatesInfo: Boolean = true, vararg newCoordinates: Float) {

        // get actual coordinates for the triangles/lines
        val actualCoordinates = getActualCoordinate(isCoordinatesInfo, newCoordinates)

        // convert the coordinates in OpenGL coordinate system
        gestureDetector.normalizeCoordinates(actualCoordinates)

        // change coordinates in OpenGL coordinates
        changeOpenGL(i, false, *actualCoordinates)
    }

    fun change(i: Int, vararg newCoordinates: Float) {
        change(i, true, *newCoordinates)
    }


    internal fun changeOpenGL(i: Int, isCoordinatesInfo: Boolean = true, vararg newCoordinatesOpenGL: Float) {

        // get actual coordinates
        val actualCoordinates = getActualCoordinate(isCoordinatesInfo, newCoordinatesOpenGL)

        val j = i * coordinatesPerShape
        actualCoordinates.forEachIndexed { index, _ ->
            coordinatesOpenGL[j + index] = actualCoordinates[index]
        }
        updateBuffers()
    }

    fun changeOpenGL(i: Int, vararg newCoordinatesOpenGL: Float) {
        changeOpenGL(i, true, *newCoordinatesOpenGL)
    }


    open fun delete(i: Int) {

        // remove element used by the buffers and update them
        coordinatesOpenGL = coordinatesOpenGL.delete(i, coordinatesPerShape)
        colorsOpenGL = colorsOpenGL.delete(i, colorsPerShape)

        // update all buffers
        updateBuffers()
    }


    fun setShape(newCoordinates: FloatArray, newColors: IntArray, isCoordinatesInfo: Boolean = true) {

        // get actual coordinates
        val actualCoordinates = getActualCoordinate(isCoordinatesInfo, newCoordinates)

        generateCoordinatesOpenGL(actualCoordinates)
        setShapeOpenGL(coordinatesOpenGL, newColors, false)
    }


    fun setShapeOpenGL(newCoordinatesOpenGL: FloatArray, newColors: IntArray, isCoordinatesInfo: Boolean = true) {

        // get actual coordinates
        val actualCoordinates = getActualCoordinate(isCoordinatesInfo, newCoordinatesOpenGL)

        needUpdate = false
        coordinatesOpenGL = actualCoordinates
        colors = newColors
        updateBuffers()
    }


    fun setShape(newCoordinates: FloatArray, newColor: Int, isCoordinatesInfo: Boolean = true) {

        // get actual coordinates
        val actualCoordinates = getActualCoordinate(isCoordinatesInfo, newCoordinates)

        generateCoordinatesOpenGL(actualCoordinates)
        setShapeOpenGL(coordinatesOpenGL, newColor, false)
    }


    fun setShapeOpenGL(newCoordinatesOpenGL: FloatArray, newColor: Int, isCoordinatesInfo: Boolean = true) {

        // get actual coordinates
        val actualCoordinates = getActualCoordinate(isCoordinatesInfo, newCoordinatesOpenGL)

        needUpdate = false
        coordinatesOpenGL = actualCoordinates
        colors = intArrayOf(newColor)
        updateBuffers()
    }

    companion object {

        const val BYTES_PER_FLOAT = 4            // 4 bytes per float values
        const val COLOR_CHANNELS_PER_VERTEX = 4  // 4 color channel values per vertex: r,g,b,a
        const val COORDINATES_PER_VERTEX = 2     // 2 coordinate values per vertex: x,y

        const val STYLE_FILL = 0                 // fill the shape (triangles will be drawn)
        const val STYLE_STROKE = 1               // stroke shape (lines will be drawn)

        /**
         * Get the OpenGL type that will be draw using the coordinate from the style.
         * @param style style for the shape fill or stroke
         */
        fun getTypeByStyle(style: Int): Int {

            return when (style) {
                STYLE_FILL -> {
                    // for shapes made out of triangles
                    GLES20.GL_TRIANGLES
                }
                else -> {
                    // for shapes made out of lines, equivalent to 'moveTo' and then 'lineTo'
                    GLES20.GL_LINES
                }
            }
        }


        fun getRectanglesCoordinatesByStyle(style: Int, coordinates: FloatArray): FloatArray {
            return when (style) {
                STYLE_FILL -> {

                    val newCoordinates = FloatArray(coordinates.size * 3)
                    val numberOfPolygons = coordinates.size / 4

                    // get the coordinates of the two triangles that make up the fill rectangle
                    for (i in 0 until numberOfPolygons) {

                        val m = i * 4
                        val x = coordinates[m]
                        val y = coordinates[m + 1]
                        val width = coordinates[m + 2]
                        val height = coordinates[m + 3]

                        // 12 coordinates for the two triangles, that form the rectangle shape
                        val n = i * 12

                        // first triangle with points as follows: top-left, bottom-left, bottom-right
                        newCoordinates[n] = x
                        newCoordinates[n + 1] = y
                        newCoordinates[n + 2] = x
                        newCoordinates[n + 3] = y + height
                        newCoordinates[n + 4] = x + width
                        newCoordinates[n + 5] = y + height

                        // second triangle with points as follows: bottom-right, top-right, top-left
                        newCoordinates[n + 6] = x + width
                        newCoordinates[n + 7] = y + height
                        newCoordinates[n + 8] = x + width
                        newCoordinates[n + 9] = y
                        newCoordinates[n + 10] = x
                        newCoordinates[n + 11] = y
                    }
                    newCoordinates
                }
                STYLE_STROKE -> {

                    val newCoordinates = FloatArray(coordinates.size * 4)
                    val numberOfPolygons = coordinates.size / 4

                    // get the coordinates of the lines that make up the stroke rectangle
                    for (i in 0 until numberOfPolygons) {

                        val m = i * 4
                        val x = coordinates[m]
                        val y = coordinates[m + 1]
                        val width = coordinates[m + 2]
                        val height = coordinates[m + 3]

                        // each rectangle has 4 lines, each line has 2 point, each point has 2 coordinates x,y
                        val n = i * 16

                        // {top-left, bottom-left} line
                        newCoordinates[n] = x
                        newCoordinates[n + 1] = y
                        newCoordinates[n + 2] = x
                        newCoordinates[n + 3] = y + height

                        // {bottom-left, bottom-right} line
                        newCoordinates[n + 4] = x
                        newCoordinates[n + 5] = y + height
                        newCoordinates[n + 6] = x + width
                        newCoordinates[n + 7] = y + height

                        // {bottom-right, top-right} line
                        newCoordinates[n + 8] = x + width
                        newCoordinates[n + 9] = y + height
                        newCoordinates[n + 10] = x + width
                        newCoordinates[n + 11] = y

                        // {top-right, top-left} line
                        newCoordinates[n + 12] = x + width
                        newCoordinates[n + 13] = y
                        newCoordinates[n + 14] = x
                        newCoordinates[n + 15] = y
                    }
                    newCoordinates
                }
                else -> {
                    floatArrayOf()
                }
            }
        }


        fun getRectanglesNumberOfVerticesFromStyle(style: Int): Int {
            return when (style) {
                STYLE_FILL -> {
                    // fill rectangle has 2 triangles each with 3 vertices, that gives total of 6 vertices
                    6
                }
                else -> {
                    // stroke rectangle has 4 lines, each line has 2 vertices that give total of 8 vertices
                    8
                }
            }
        }


        fun getTrianglesCoordinatesByStyle(style: Int, coordinates: FloatArray): FloatArray {

            return when (style) {
                STYLE_FILL -> {

                    // for shapes made out of triangles, that made up a particular shape: Triangle, Rectangle, Circle return the same coordinates
                    coordinates
                }
                STYLE_STROKE -> {

                    // for shapes made out of lines, return array with the line coordinates
                    val newCoordinates = FloatArray(coordinates.size * 2)
                    for (i in 0 until coordinates.size / 6) {

                        // coordinates of the 3 triangle points (vertices)
                        val m = i * 6
                        val x1 = coordinates[m]
                        val y1 = coordinates[m + 1]
                        val x2 = coordinates[m + 2]
                        val y2 = coordinates[m + 3]
                        val x3 = coordinates[m + 4]
                        val y3 = coordinates[m + 5]

                        // add the 12 coordinates for the 3 lines of the triangle
                        val n = i * 12
                        newCoordinates[n] = x1
                        newCoordinates[n + 1] = y1
                        newCoordinates[n + 2] = x2
                        newCoordinates[n + 3] = y2
                        newCoordinates[n + 4] = x2
                        newCoordinates[n + 5] = y2
                        newCoordinates[n + 6] = x3
                        newCoordinates[n + 7] = y3
                        newCoordinates[n + 8] = x3
                        newCoordinates[n + 9] = y3
                        newCoordinates[n + 10] = x1
                        newCoordinates[n + 11] = y1
                    }

                    newCoordinates
                }
                else -> {
                    floatArrayOf()
                }
            }
        }


        fun getTrianglesNumberOfVerticesFromStyle(style: Int): Int {
            return when (style) {
                STYLE_FILL -> {
                    // fill triangle has 3 vertices
                    3
                }
                else -> {
                    // stroke triangle has 3 lines, each line has 2 vertices that give total of 6 vertices
                    6
                }
            }
        }


        fun getRegularPolygonsCoordinatesByStyle(style: Int, coordinates: FloatArray, numberOfSegments: Int, innerDepth: Float): FloatArray {

            // if inner depth is set create a star object
            if (innerDepth > 0) {
                return getStarCoordinatesByStyle(style, coordinates, numberOfSegments, innerDepth)
            }

            // how many coordinates are needed when drawing the
            val coordinatesPerSegment = if (style == STYLE_FILL) {
                // triangles are made of 3 points, each with 2 coordinates x,y that gives 6 coordinates in total
                6
            } else {
                // lines are made of 2 point, each with 2 coordinates x,y, that gives 4 coordinates in total
                4
            }

            val newCoordinates = FloatArray((coordinates.size / 4) * numberOfSegments * coordinatesPerSegment)
            val result = PointF()
            val rotationalAngle = 360f / numberOfSegments
            val numberOfPolygons = coordinates.size / 4

            // get the coordinate of the triangles that make up each polygon
            for (i in 0 until numberOfPolygons) {

                // center point for the polygon
                val cx = coordinates[i * 4]
                val cy = coordinates[i * 4 + 1]

                // radius for the polygon
                val r = coordinates[i * 4 + 2]

                // start angle of the polygon
                val startAngle = coordinates[i * 4 + 3]

                // the coordinate at the edge-point laying on the the circle with the radius of the polygon
                val x = cx + r
                val y = cy

                // coordinates of the previous vertex
                var previousX = 0f
                var previousY = 0f

                // coordinates of the first vertex
                var firstX = 0f
                var firstY = 0f

                for (j in 0 until numberOfSegments) {

                    // get the new angle of rotation that is increased with the rotationalAngle each time
                    val angle = rotationalAngle * j + startAngle
                    OpenGLStatic.rotate(cx, cy, x, y, angle, false, result)

                    // start filling the array with coordinates once we have the first coordinate
                    // since for creating the triangle we need previous and current vertex point
                    if (j > 0) {
                        val m = i * numberOfSegments * coordinatesPerSegment + j * coordinatesPerSegment
                        newCoordinates[m] = previousX
                        newCoordinates[m + 1] = previousY
                        newCoordinates[m + 2] = result.x
                        newCoordinates[m + 3] = result.y

                        // for triangles the center is also used as vertex
                        if (coordinatesPerSegment == 6) {
                            newCoordinates[m + 4] = cx
                            newCoordinates[m + 5] = cy
                        }
                    } else {
                        firstX = result.x
                        firstY = result.y
                    }

                    // set previous vertex coordinate that will be used for the next triangle
                    previousX = result.x
                    previousY = result.y
                }

                // connect first and last triangle vertices
                val m = i * numberOfSegments * coordinatesPerSegment
                newCoordinates[m] = previousX
                newCoordinates[m + 1] = previousY
                newCoordinates[m + 2] = firstX
                newCoordinates[m + 3] = firstY

                // for triangles the center is also used as vertex
                if (coordinatesPerSegment == 6) {
                    newCoordinates[m + 4] = cx
                    newCoordinates[m + 5] = cy
                }
            }
            return newCoordinates
        }


        fun getStarCoordinatesByStyle(style: Int, coordinates: FloatArray, numberOfSegments: Int, innerDepth: Float): FloatArray {

            // how many coordinates are needed when drawing the
            val coordinatesPerSegment = if (style == STYLE_FILL) {
                // triangles are made of 3 points, each with 2 coordinates x,y that gives 6 coordinates in total
                6
            } else {
                // lines are made of 2 point, each with 2 coordinates x,y, that gives 4 coordinates in total
                4
            }

            val newCoordinates = FloatArray((coordinates.size / 4) * numberOfSegments * coordinatesPerSegment * 2)
            val result = PointF()
            val rotationalAngle = 360f / numberOfSegments
            val numberOfPolygons = coordinates.size / 4

            // get the coordinate of the triangles that make up each polygon
            for (i in 0 until numberOfPolygons) {

                // center point for the polygon
                val cx = coordinates[i * 4]
                val cy = coordinates[i * 4 + 1]

                // radius for the polygon
                val r = coordinates[i * 4 + 2]

                // start angle of the polygon
                val startAngle = coordinates[i * 4 + 3]

                // the coordinate at the edge-point laying on the the circle with the radius of the polygon
                val x = cx + r
                val y = cy

                // coordinates of the previous vertex
                var previousX = 0f
                var previousY = 0f

                // coordinates of the first vertex
                var firstX = 0f
                var firstY = 0f

                for (j in 0 until numberOfSegments) {

                    // get the new angle of rotation that is increased with the rotationalAngle each time
                    val angle = rotationalAngle * j + startAngle
                    OpenGLStatic.rotate(cx, cy, x, y, angle, false, result)

                    // start filling the array with coordinates once we have the first coordinate
                    // since for creating the triangle we need previous and current vertex point
                    if (j > 0) {
                        val m = i * numberOfSegments * coordinatesPerSegment + j * coordinatesPerSegment * 2
                        addStarCoordinates(m, newCoordinates, innerDepth, previousX, previousY, result.x, result.y, cx, cy, coordinatesPerSegment)
                    } else {
                        firstX = result.x
                        firstY = result.y
                    }

                    // set previous vertex coordinate that will be used for the next triangle
                    previousX = result.x
                    previousY = result.y
                }

                // connect first and last triangle vertices
                val m = i * numberOfSegments * coordinatesPerSegment * 2
                addStarCoordinates(m, newCoordinates, innerDepth, previousX, previousY, firstX, firstY, cx, cy, coordinatesPerSegment)
            }
            return newCoordinates
        }


        fun addStarCoordinates(
            i: Int, newCoordinates: FloatArray, innerDepth: Float, previousX: Float, previousY: Float,
            resultX: Float, resultY: Float, cX: Float, cY: Float, coordinatesPerSegment: Int
        ) {

            // middle point between current and previous points
            val middleX = previousX + (resultX - previousX) * 0.50f
            val middleY = previousY + (resultY - previousY) * 0.50f

            // get the inner star point using the inner-depth ratio
            val innerX = ((1 - innerDepth) * middleX + innerDepth * cX)
            val innerY = ((1 - innerDepth) * middleY + innerDepth * cY)

            // for triangles the center is also used as vertex
            if (coordinatesPerSegment == 6) {

                // triangle previous->inner->center
                newCoordinates[i] = previousX
                newCoordinates[i + 1] = previousY
                newCoordinates[i + 2] = innerX
                newCoordinates[i + 3] = innerY
                newCoordinates[i + 4] = cX
                newCoordinates[i + 5] = cY

                // triangle inner->current->center
                newCoordinates[i + 6] = innerX
                newCoordinates[i + 7] = innerY
                newCoordinates[i + 8] = resultX
                newCoordinates[i + 9] = resultY
                newCoordinates[i + 10] = cX
                newCoordinates[i + 11] = cY
            } else {

                // line from previous point to inner point
                newCoordinates[i] = previousX
                newCoordinates[i + 1] = previousY
                newCoordinates[i + 2] = innerX
                newCoordinates[i + 3] = innerY

                // line from inner point to current point
                newCoordinates[i + 4] = innerX
                newCoordinates[i + 5] = innerY
                newCoordinates[i + 6] = resultX
                newCoordinates[i + 7] = resultY
            }
        }


        fun getRegularPolygonsNumberOfVerticesFromStyle(style: Int, numberOfVertices: Int, innerDepth: Float): Int {

            // if the number of elements should double, since star like shapes will be created
            val doubleElements = if (innerDepth > 0f) 2 else 1

            return when (style) {
                STYLE_FILL -> {
                    // the number of triangles that make up the fill polygon matches the 'numberOfVertices', each one is triangle with 3 vertices
                    numberOfVertices * 3 * doubleElements
                }
                else -> {
                    // the number of lines that make up the fill polygon matches the numberOfVertices, each line has 2 vertices
                    numberOfVertices * 2 * doubleElements
                }
            }
        }


        fun getEllipsesCoordinatesByStyle(style: Int, coordinates: FloatArray, numberOfSegments: Int): FloatArray {

            // how many coordinates are needed when drawing the
            val coordinatesPerSegment = if (style == STYLE_FILL) {
                // triangles are made of 3 points, each with 2 coordinates x,y that gives 6 coordinates in total
                6
            } else {
                // lines are made of 2 point, each with 2 coordinates x,y, that gives 4 coordinates in total
                4
            }

            val newCoordinates = FloatArray((coordinates.size / 4) * numberOfSegments * coordinatesPerSegment)
            val numberOfPolygons = coordinates.size / 4

            val theta = 2 * Math.PI / numberOfSegments
            val c = Math.cos(theta)
            val s = Math.sin(theta)

            // get the coordinate of the triangles that make up each polygon
            for (i in 0 until numberOfPolygons) {

                // center point for the polygon
                val cx = coordinates[i * 4]
                val cy = coordinates[i * 4 + 1]

                // radii for the polygon
                val rx = coordinates[i * 4 + 2]
                val ry = coordinates[i * 4 + 3]

                var t = 0.0

                // we start at angle = 0
                var x = 1.0
                var y = 0.0

                // coordinates of the previous vertex
                var previousX = 0f
                var previousY = 0f

                // coordinates of the first vertex
                var firstX = 0f
                var firstY = 0f

                // for each segment
                for (j in 0 until numberOfSegments) {

                    //apply radius and offset
                    val actualX = (x * rx + cx).toFloat()
                    val actualY = (y * ry + cy).toFloat()

                    // start filling the array with coordinates once we have the first coordinate
                    // since for creating the triangle/lines we need previous and current vertex point
                    if (j > 0) {
                        val m = i * numberOfSegments * coordinatesPerSegment + j * coordinatesPerSegment
                        newCoordinates[m] = previousX
                        newCoordinates[m + 1] = previousY
                        newCoordinates[m + 2] = actualX
                        newCoordinates[m + 3] = actualY

                        // for triangles the center is also used as vertex
                        if (coordinatesPerSegment == 6) {
                            newCoordinates[m + 4] = cx
                            newCoordinates[m + 5] = cy
                        }
                    } else {
                        firstX = actualX
                        firstY = actualY
                    }

                    // set previous vertex coordinate that will be used for the next triangle
                    previousX = actualX
                    previousY = actualY

                    //apply the rotation matrix
                    t = x
                    x = c * x - s * y
                    y = s * t + c * y
                }

                // connect first and last triangle/line vertices
                val m = i * numberOfSegments * coordinatesPerSegment
                newCoordinates[m] = previousX
                newCoordinates[m + 1] = previousY
                newCoordinates[m + 2] = firstX
                newCoordinates[m + 3] = firstY

                // for triangles the center is also used as vertex
                if (coordinatesPerSegment == 6) {
                    newCoordinates[m + 4] = cx
                    newCoordinates[m + 5] = cy
                }
            }
            return newCoordinates
        }

        fun <T> MutableList<T>.addStart(element: T) {
            add(0, element)
        }

        fun <T> MutableList<T>.addStartAll(elements: List<T>) {
            addAll(0, elements)
        }


        fun getPassByCurveCoordinates(coordinates: FloatArray, isClosed: Boolean = false, tension: Float = 0.5f, numOfSegments: Int = 16): FloatArray {

            val coordinatesCopy = coordinates.toMutableList()  // clone array

            // The algorithm require a previous and next point to the actual point array. Check if we will draw
            // closed or open curve. If curve is closed, copy end points to beginning and first points to end.
            // And if curve is opened duplicate first points at the beginning, adn the end points to end.
            if (isClosed) {
                coordinatesCopy.addStartAll(listOf(coordinates[coordinates.size - 2], coordinates[coordinates.size - 1], coordinates[coordinates.size - 2], coordinates[coordinates.size - 1]))  // add at the begging
                coordinatesCopy.addAll(listOf(coordinates[0], coordinates[1]))                                                                                                                  // add at the end
            } else {
                coordinatesCopy.addStartAll(listOf(coordinates[0], coordinates[1]))                                   // add at the begging
                coordinatesCopy.addAll(listOf(coordinates[coordinates.size - 2], coordinates[coordinates.size - 1])) // add at the end
            }

            val newCoordinates = ArrayList<Float>()
            for (i in 2 until coordinatesCopy.size - 4 step 2) {
                for (t in 0..numOfSegments) {

                    // calculate tension vectors
                    val t1x = (coordinatesCopy[i + 2] - coordinatesCopy[i - 2]) * tension
                    val t2x = (coordinatesCopy[i + 4] - coordinatesCopy[i]) * tension

                    val t1y = (coordinatesCopy[i + 3] - coordinatesCopy[i - 1]) * tension
                    val t2y = (coordinatesCopy[i + 5] - coordinatesCopy[i + 1]) * tension

                    // calculate step
                    val st = t / numOfSegments.toDouble()
                    val stPow2 = Math.pow(st, 2.0)
                    val stPow3 = Math.pow(st, 3.0)

                    // calculate cardinals
                    val c1 = 2 * stPow3 - 3 * stPow2 + 1
                    val c2 = -(2 * stPow3) + 3 * stPow2
                    val c3 = stPow3 - 2 * stPow2 + st
                    val c4 = stPow3 - stPow2

                    // calculate x and y cords with common control vectors
                    val x = (c1 * coordinatesCopy[i] + c2 * coordinatesCopy[i + 2] + c3 * t1x + c4 * t2x).toFloat()
                    val y = (c1 * coordinatesCopy[i + 1] + c2 * coordinatesCopy[i + 3] + c3 * t1y + c4 * t2y).toFloat()

                    // add line coordinates
                    newCoordinates.add(x)
                    newCoordinates.add(y)
                }
            }

            return newCoordinates.toFloatArray()
        }

        fun getIrregularPolygonTypeByStyle(style: Int): Int {

            return when (style) {
                STYLE_FILL -> {
                    // for shapes made out of triangles
                    GLES20.GL_TRIANGLES
                }
                else -> {
                    // for shapes made out of lines, equivalent to 'lineTo'
                    GLES20.GL_LINE_STRIP
                }
            }
        }


        fun getIrregularPolygonNumberOfVerticesFromStyle(style: Int): Int {
            return when (style) {
                STYLE_FILL -> 3 // each triangle has 3 vertices
                else -> 2       // each line has 3 vertices
            }
        }

    }
}

