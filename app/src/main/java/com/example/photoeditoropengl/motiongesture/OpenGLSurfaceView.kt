package  com.example.photoeditoropengl.motiongesture

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.EGLConfig
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.photoeditoropengl.R
import com.example.photoeditoropengl.motiongesture.OpenGLStatic.ENABLE_ALPHA
import com.example.photoeditoropengl.motiongesture.OpenGLStatic.ENABLE_ANTIALIASING
import com.example.photoeditoropengl.motiongesture.Shapes.Companion.STYLE_STROKE
import com.slaviboy.opengl.main.OpenGLConfigChooser
import com.slaviboy.openglexamples.single.OpenGLHelper
import com.slaviboy.openglexamples.single.OpenGLRenderer
import javax.microedition.khronos.opengles.GL10

class OpenGLSurfaceView : GLSurfaceView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    val openGLRenderer: OpenGLRenderer
    var openGLHelper: OpenGLHelper

    init {

        if (ENABLE_ALPHA) {
            holder.setFormat(PixelFormat.TRANSLUCENT)
        }

        setEGLContextClientVersion(2)

        if (ENABLE_ANTIALIASING) {
            setEGLConfigChooser(OpenGLConfigChooser())
        } else {
            this.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        }

        openGLHelper = OpenGLHelper()
      //  this.setOnTouchListener(openGLHelper)

        openGLRenderer = OpenGLRenderer(context, openGLHelper) {
            requestRender()
        }
        setRenderer(openGLRenderer)

        renderMode = RENDERMODE_WHEN_DIRTY
    }
}


