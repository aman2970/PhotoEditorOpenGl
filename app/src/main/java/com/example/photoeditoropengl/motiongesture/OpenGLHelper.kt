package com.slaviboy.openglexamples.single

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.util.trace
import com.example.photoeditoropengl.R
import com.example.photoeditoropengl.motiongesture.Image
import com.example.photoeditoropengl.motiongesture.OpenGLMatrixGestureDetector
import com.example.photoeditoropengl.motiongesture.OpenGLStatic
import com.example.photoeditoropengl.motiongesture.OpenGLStatic.DEVICE_HEIGHT
import com.example.photoeditoropengl.motiongesture.OpenGLStatic.DEVICE_WIDTH
import com.example.photoeditoropengl.motiongesture.Shapes.Companion.STYLE_STROKE

class OpenGLHelper : View.OnTouchListener {
    var mainGestureDetector: OpenGLMatrixGestureDetector = OpenGLMatrixGestureDetector()
    lateinit var image: Image
    var requestRenderListener: () -> Unit = {}
    var style: Int = STYLE_STROKE
    var singleColorsProgram: Int = -1

    fun createShapes(context: Context? = null) {
        singleColorsProgram = OpenGLStatic.setSingleColorsProgram()

        if (context != null) {
            val imageProgram = OpenGLStatic.setTextureProgram()
            val textureHandler = OpenGLStatic.loadTexture(context, R.drawable.ic_emoji)
            val centerX = DEVICE_WIDTH / 2f
            val centerY = DEVICE_HEIGHT / 2f
            image = Image(
                bitmapWidth = 302f,
                bitmapHeight = 303f,
                x = centerX,
                y = centerY,
                width = 100f,
                height = 100f,
                textureHandle = textureHandler,
                preloadProgram = imageProgram,
                keepSize = false,
                usePositionAsCenter = true,
                gestureDetector = mainGestureDetector
            )
        }

    }

    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        mainGestureDetector.onTouchEvent(event)
        requestRenderListener.invoke()
        return true
    }


    fun onTouchEvent(event: MotionEvent) : Boolean{
        mainGestureDetector.onTouchEvent(event)
        requestRenderListener.invoke()
        return true
    }

    fun draw(transformedMatrixOpenGL: FloatArray) {
        image.draw(transformedMatrixOpenGL)
    }

}
