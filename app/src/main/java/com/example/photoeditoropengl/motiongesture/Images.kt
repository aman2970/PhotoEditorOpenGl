
package com.example.photoeditoropengl.motiongesture

import android.graphics.PointF
import android.opengl.GLES20
import com.example.photoeditoropengl.motiongesture.OpenGLStatic.DEVICE_HEIGHT
import com.example.photoeditoropengl.motiongesture.OpenGLStatic.DEVICE_WIDTH
import com.example.photoeditoropengl.motiongesture.OpenGLStatic.delete
import com.example.photoeditoropengl.motiongesture.Shapes.Companion.COORDINATES_PER_VERTEX
import com.slaviboy.opengl.main.OpenGLColor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

open class Images(
    var bitmapWidth: Float,
    var bitmapHeight: Float,
    positions: FloatArray,
    width: Float = 100f,
    height: Float = 100f,
    var isVisible: Boolean = true,
    var gestureDetector: OpenGLMatrixGestureDetector,
    var keepSize: Boolean = false,
    var usePositionAsCenter: Boolean = true,
    var textureHandle: Int = -1,
    val preloadProgram: Int = -1
) {

    var positions: FloatArray = positions
        set(value) {
            field = value
            needUpdate = true
        }

    var width: Float = width
        set(value) {
            field = value
            needUpdate = true
            setRawSize()
        }

    var height: Float = height
        set(value) {
            field = value
            needUpdate = true
            setRawSize()
        }

    var actualWidth: Float                              // the actual calculated width in case it is set as WRAP_CONTENT, AUTO_SIZE... (graphic coordinates system)
    var actualHeight: Float                             // the actual calculated height in case it is set as WRAP_CONTENT, AUTO_SIZE... (graphic coordinates system)

    internal var needUpdate: Boolean                     // if the coordinates in OpenGL should be generated again, once the draw method is called

    internal lateinit var textureBuffer: FloatBuffer     // array with coordinate for the texture
    internal var textureUniformHandle: Int               // handle for the texture uniform
    internal var textureCoordinateHandle: Int            // handle for the texture coordinates
    internal var textureCoordinateDataSize: Int          // size for the texture coordinates
    lateinit var textureCoordinate: FloatArray          // texture coordinates for each rectangle

    internal var program: Int                            // program for attaching the shader
    lateinit var vertexBuffer: FloatBuffer              // buffer fo the vertex
    internal lateinit var drawOrderBuffer: ShortBuffer   // buffer for the draw list
    internal var positionHandle: Int                     // handle for the position
    internal var colorHandle: Int                        // handle for the color
    internal var MVPMatrixHandle: Int                    // handle for the MVP matrix
    lateinit var drawOrder: ShortArray                  // order of draw vertices
    internal var vertexStride: Int                       // bytes per vertex

    var vertexShaderCode: String                        // shader with the vertex
    var fragmentShaderCode: String                      // shader with the fragment
    var color: OpenGLColor                              // color for the shape

    lateinit var positionsOpenGL: FloatArray            // array with x,y coordinates of all images position in OpenGL coordinate system
    var widthOpenGL: Float                              // the width for all images in OpenGL coordinate system
    var heightOpenGL: Float                             // the height for all images in OpenGL coordinate system
    var tempPointOpenGL: PointF                         // temp point that is used in the conversion from the graphic to OpenGL coordinate system

    // array with coordinate for the rectangle bound, for each image between in OpenGL coordinate system [-1,1]
    var coordinatesOpenGL = FloatArray((positions.size / 2) * 4 * COORDINATES_PER_VERTEX)

    init {

        tempPointOpenGL = PointF()
        needUpdate = true

        actualWidth = 0f
        actualHeight = 0f

        textureUniformHandle = 0
        textureCoordinateHandle = 0
        textureCoordinateDataSize = 2

        positionHandle = 0
        colorHandle = 0
        MVPMatrixHandle = 0
        vertexStride = COORDINATES_PER_VERTEX * 4

        widthOpenGL = 0f
        heightOpenGL = 0f

        color = OpenGLColor(255, 255, 255, 255)

        // set shaders, that were loaded
        vertexShaderCode = OpenGLStatic.vertexTextureShaderCode
        fragmentShaderCode = OpenGLStatic.fragmentTextureShaderCode

        // attach or create program
        if (preloadProgram == -1) {
            val vertexShader: Int = OpenGLStatic.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            val fragmentShader: Int = OpenGLStatic.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)

            GLES20.glBindAttribLocation(program, 0, "a_TexCoordinate")
            GLES20.glLinkProgram(program)
        } else {
            program = preloadProgram
        }

        setRawSize()
        generateDrawOrderAndTexture(positions.size / 2)
        generateCoordinatesOpenGL(true)
        updateBuffers()
    }

    fun draw(mvpMatrix: FloatArray) {

        // draw images only if its property visible is set to true
        if (!isVisible) {
            return
        }

        if (needUpdate || keepSize) {

            // generate OpenGL coordinate from the graphic coordinates
            val updateCoordinate = if (needUpdate) true else !keepSize
            generateCoordinatesOpenGL(updateCoordinate)

            vertexBuffer.put(coordinatesOpenGL)
            vertexBuffer.position(0)
        }

        // add program to OpenGL ES Environment
        GLES20.glUseProgram(program)

        // get handle to vertex shader's vPosition member
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, COORDINATES_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer)

        // set color for the vertices
        colorHandle = GLES20.glGetUniformLocation(program, "vColor")
        GLES20.glUniform4fv(colorHandle, 1, color.value, 0)

        // set texture
        textureUniformHandle = GLES20.glGetAttribLocation(program, "u_Texture")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle)
        GLES20.glUniform1i(textureUniformHandle, 0)

        // set coordinates for the texture
        textureCoordinateHandle = GLES20.glGetAttribLocation(program, "a_TexCoordinate")
        textureBuffer.position(0)
        GLES20.glVertexAttribPointer(textureCoordinateHandle, textureCoordinateDataSize, GLES20.GL_FLOAT, false, 0, textureBuffer)
        GLES20.glEnableVertexAttribArray(textureCoordinateHandle)

        // get handle to shape's transformation matrix
        MVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(MVPMatrixHandle, 1, false, mvpMatrix, 0)

        // draw bitmap using two triangle per image
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawOrderBuffer)

        // disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(textureCoordinateHandle)
    }

    fun updateBuffers() {

        // init buffer for vertices
        var byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(coordinatesOpenGL.size * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        vertexBuffer = byteBuffer.asFloatBuffer()
        vertexBuffer.put(coordinatesOpenGL)
        vertexBuffer.position(0)

        // init buffer for texture
        byteBuffer = ByteBuffer.allocateDirect(textureCoordinate.size * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        textureBuffer = byteBuffer.asFloatBuffer()
        textureBuffer.put(textureCoordinate)
        textureBuffer.position(0)

        // init buffer for drawing order
        byteBuffer = ByteBuffer.allocateDirect(coordinatesOpenGL.size * 2)
        byteBuffer.order(ByteOrder.nativeOrder())
        drawOrderBuffer = byteBuffer.asShortBuffer()
        drawOrderBuffer.put(drawOrder)
        drawOrderBuffer.position(0)
    }


    fun generateDrawOrderAndTexture(numberOfImages: Int) {

        drawOrder = ShortArray(numberOfImages * 6)
        textureCoordinate = FloatArray(numberOfImages * 8)

        for (i in 0 until numberOfImages) {
            setDrawOrderAndTexture(i)
        }
    }


    fun setDrawOrderAndTexture(i: Int) {
        val v1 = i * 4
        val v2 = i * 4 + 1
        val v3 = i * 4 + 2
        val v4 = i * 4 + 3

        // set drawing order for each of the 6 vertices in a rectangle, since it is made by two triangles
        drawOrder[i * 6] = v1.toShort()
        drawOrder[i * 6 + 1] = v2.toShort()
        drawOrder[i * 6 + 2] = v3.toShort()
        drawOrder[i * 6 + 3] = v1.toShort()
        drawOrder[i * 6 + 4] = v3.toShort()
        drawOrder[i * 6 + 5] = v4.toShort()

        // set the texture for each of the 4 vertices in a rectangle 2 values x and y => 2*4 = 8 total values per rectangle
        textureCoordinate[i * 8] = 0.0f
        textureCoordinate[i * 8 + 1] = 0.0f
        textureCoordinate[i * 8 + 2] = 0.0f
        textureCoordinate[i * 8 + 3] = 1.0f
        textureCoordinate[i * 8 + 4] = 1.0f
        textureCoordinate[i * 8 + 5] = 1.0f
        textureCoordinate[i * 8 + 6] = 1.0f
        textureCoordinate[i * 8 + 7] = 0.0f
    }


    fun add(x: Float, y: Float, i: Int = positionsOpenGL.size / 2) {

        // generate position in OpenGL coordinate system
        gestureDetector.normalizeCoordinate(x, y, tempPointOpenGL)

        // add coordinates in OpenGL coordinates
        addOpenGL(tempPointOpenGL.x, tempPointOpenGL.y, i)
    }


    fun addOpenGL(x: Float, y: Float, i: Int) {

        val result = positionsOpenGL.toMutableList()
        result.addAll(i * 2, mutableListOf(x, y))
        setImagesPositionsOpenGL(result.toFloatArray())
    }


    fun move(x: Float, y: Float, i: Int) {

        // generate position in OpenGL coordinate system
        gestureDetector.normalizeCoordinate(x, y, tempPointOpenGL)

        // move image by setting coordinates in OpenGL coordinate system
        moveOpenGL(tempPointOpenGL.x, tempPointOpenGL.y, i)
    }


    fun moveOpenGL(x: Float, y: Float, i: Int) {
        positionsOpenGL[i * 2] = x
        positionsOpenGL[i * 2 + 1] = y
        updateImageCoordinate(i)
    }

    fun delete(i: Int) {

        // remove elements from the array with positions
        positionsOpenGL = positionsOpenGL.delete(i * 2, 2)

        // remove element used by the buffers and update them
        coordinatesOpenGL = coordinatesOpenGL.delete(i * 8, 8)
        drawOrder = drawOrder.sliceArray(0 until drawOrder.size - 6)
        textureCoordinate = textureCoordinate.sliceArray(0 until textureCoordinate.size - 8)

        // update all buffers
        updateBuffers()
    }

    fun setImagesPositionsOpenGL(newPositionsOpenGL: FloatArray) {

        positionsOpenGL = newPositionsOpenGL
        coordinatesOpenGL = FloatArray((newPositionsOpenGL.size / 2) * 4 * COORDINATES_PER_VERTEX)

        // generate coordinates for each of the 3 sides, for each new image
        for (i in 0 until newPositionsOpenGL.size / 2) {
            updateImageCoordinate(i)
        }

        generateDrawOrderAndTexture(newPositionsOpenGL.size / 2)
        updateBuffers()
    }

    internal fun setRawSize() {

        actualWidth = when (width) {
            WRAP_CONTENT -> bitmapWidth
            MATCH_DEVICE -> DEVICE_WIDTH
            AUTO_SIZE -> height * (bitmapWidth / bitmapHeight)
            else -> width
        }

        actualHeight = when (height) {
            WRAP_CONTENT -> bitmapHeight
            MATCH_DEVICE -> DEVICE_HEIGHT
            AUTO_SIZE -> width * (bitmapHeight / bitmapWidth)
            else -> height
        }
    }

    fun generateCoordinatesOpenGL(updateImagePositions: Boolean = true) {

        // get width and height in OpenGL coordinate system
        widthOpenGL = -gestureDetector.normalizeWidth(actualWidth, !keepSize) * 2f
        heightOpenGL = -gestureDetector.normalizeHeight(actualHeight, !keepSize) * 2f

        // get the position of all images in OpenGL coordinate system
        if (updateImagePositions) {
            positionsOpenGL = FloatArray(positions.size)
            for (i in 0 until positions.size / 2) {
                gestureDetector.normalizeCoordinate(positions[i * 2], positions[i * 2 + 1], tempPointOpenGL)
                positionsOpenGL[i * 2] = tempPointOpenGL.x //roundToDecimalPlaces(tempPointOpenGL.x)
                positionsOpenGL[i * 2 + 1] = tempPointOpenGL.y //roundToDecimalPlaces(tempPointOpenGL.y)
            }
        }

        // generate coordinates for the bound box of each image
        for (i in 0 until positionsOpenGL.size / 2) {
            updateImageCoordinate(i)
        }

        needUpdate = false
    }

    fun updateImageCoordinate(i: Int) {

        // image width, height and position x and y coordinates all in OpenGL coordinate system
        val w = widthOpenGL
        val h = heightOpenGL
        var x = positionsOpenGL[i * 2]
        var y = positionsOpenGL[i * 2 + 1]

        // if the x ,y coordinates should indicate the middle of the image
        if (usePositionAsCenter) {
            x -= (w / 2f)
            y -= (h / 2f)
        }

        // top left
        coordinatesOpenGL[i * 8] = x
        coordinatesOpenGL[i * 8 + 1] = y

        // bottom left
        coordinatesOpenGL[i * 8 + 2] = x
        coordinatesOpenGL[i * 8 + 3] = y + h

        // bottom right
        coordinatesOpenGL[i * 8 + 4] = x + w
        coordinatesOpenGL[i * 8 + 5] = y + h

        // top right
        coordinatesOpenGL[i * 8 + 6] = x + w
        coordinatesOpenGL[i * 8 + 7] = y
    }

    companion object {

        // different types of width and height sizes, that can be set instead of actual size in px
        const val WRAP_CONTENT = -1f    // set size to match the size of the bitmap
        const val MATCH_DEVICE = -2f    // match the device size, DEVICE_WIDTH for width and DEVICE_HEIGHT for height
        const val AUTO_SIZE = -3f       // side is auto size to keep the bitmap ratio
    }
}