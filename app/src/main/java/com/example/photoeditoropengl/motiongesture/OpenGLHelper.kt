/*
* Copyright (C) 2020 Stanislav Georgiev
* https://github.com/slaviboy
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.slaviboy.openglexamples.single

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.example.photoeditoropengl.R
import com.example.photoeditoropengl.motiongesture.Image
import com.example.photoeditoropengl.motiongesture.OpenGLMatrixGestureDetector
import com.example.photoeditoropengl.motiongesture.OpenGLStatic
import com.example.photoeditoropengl.motiongesture.Shapes.Companion.STYLE_STROKE

/**
 * Helper class that holds and has method for drawing all available shapes on the
 * OpenGL surface view
 */
class OpenGLHelper : View.OnTouchListener {

    // gesture detector used for applying transformations to all OpenGL objects: line, images, triangles..
    var mainGestureDetector: OpenGLMatrixGestureDetector =
        OpenGLMatrixGestureDetector()
    lateinit var image: Image
    var requestRenderListener: () -> Unit = {}
    var style: Int = STYLE_STROKE
    var singleColorsProgram: Int = -1

    /**
     * Creates the initial shapes that are drawn, that includes image texture of a circle
     * representing the points set by the user. The programs for the different OpenGL
     * objects, and the object them selves. This method is called only from the OpenGL renderer
     * since, programs and textures can be create only when the OpenGL context is available.
     */
    fun createShapes(context: Context? = null) {

        // preload program in case a shape need to be created when the OpenGL context is not available 
        singleColorsProgram = OpenGLStatic.setSingleColorsProgram()

        if (context != null) {
            val imageProgram = OpenGLStatic.setTextureProgram()
            val textureHandler = OpenGLStatic.loadTexture(context, R.drawable.ic_test_image)
            image = Image(
                bitmapWidth = 302f,
                bitmapHeight = 303f,
                x = 500f,
                y = 500f,
                width = 100f,
                height = 100f,
                textureHandle = textureHandler,
                preloadProgram = imageProgram,
                keepSize = false,
                usePositionAsCenter = false,
                gestureDetector = mainGestureDetector
            )
        }

    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        mainGestureDetector.onTouchEvent(event)
        requestRenderListener.invoke()
        return true
    }

    fun onTouchEvent(event: MotionEvent) : Boolean{
        mainGestureDetector.onTouchEvent(event)
        requestRenderListener.invoke()
        Log.d("data>>>>", "ontouchevent: ")
        return true
    }

    /**
     * Method called when the shapes need to be redrawn, with the responsible OpenGL matrix values, that are applied by the
     * user from his finger gestures.
     * @param transformedMatrixOpenGL OpenGL matrix values, for transformation applied to all shapes
     */
    fun draw(transformedMatrixOpenGL: FloatArray) {
        image.draw(transformedMatrixOpenGL)
    }

}
