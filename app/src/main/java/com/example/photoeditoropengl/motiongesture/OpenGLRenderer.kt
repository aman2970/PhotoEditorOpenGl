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
package  com.slaviboy.openglexamples.single

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.photoeditoropengl.motiongesture.OpenGLStatic
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Renderer class that extends the renderer interface that has the methods for drawing frame,
 * changing and creating the surface.
 */
class OpenGLRenderer(
    val context: Context,
    var openGLHelper: OpenGLHelper,
    private val requestRenderListener: (() -> Unit)
) : GLSurfaceView.Renderer {

    private var MVPMatrix: FloatArray
    private var projectionMatrix: FloatArray
    private var viewMatrix: FloatArray
    private val transformedMatrixOpenGL: FloatArray
    private val totalScaleMatrix: android.graphics.Matrix

    init {
        MVPMatrix = FloatArray(16)
        viewMatrix = FloatArray(16)
        projectionMatrix = FloatArray(16)
        transformedMatrixOpenGL = FloatArray(16)
        totalScaleMatrix = android.graphics.Matrix()
        OpenGLStatic.setShaderStrings(context)

        // Assign requestRenderListener to openGLHelper
        openGLHelper.requestRenderListener = requestRenderListener
    }

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        GLES20.glClearColor(1f, 1f, 1f, 1f)
    }

    override fun onDrawFrame(unused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, -3f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)
        Matrix.multiplyMM(MVPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        openGLHelper.mainGestureDetector.transform(MVPMatrix, transformedMatrixOpenGL)
        openGLHelper.draw(transformedMatrixOpenGL)
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        if (OpenGLStatic.ENABLE_ALPHA) {
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        }

        OpenGLStatic.setComponents(width.toFloat(), height.toFloat())
        GLES20.glViewport(0, 0, width, height)
        Matrix.frustumM(projectionMatrix, 0, -OpenGLStatic.RATIO, OpenGLStatic.RATIO, -1f, 1f, 3f / OpenGLStatic.NEAR, 7f)
        openGLHelper.mainGestureDetector.matrix = android.graphics.Matrix()
        openGLHelper.mainGestureDetector.matrix.postTranslate(OpenGLStatic.DEVICE_HALF_WIDTH, OpenGLStatic.DEVICE_HALF_HEIGHT)

        openGLHelper.createShapes(context)
    }
}