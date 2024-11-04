package com.example.photoeditoropengl.imageedit

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.photoeditoropengl.ui.theme.PhotoEditorOpenGlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoEditorOpenGlTheme {
                TriangleScreen()
            }
        }
    }
}

@Composable
fun OpenGLTriangleView(bitmapRenderer: BitmapRenderer): GLSurfaceView {
    val context = LocalContext.current
    val glSurfaceView = remember { MyGlSurfaceViews(context,bitmapRenderer) }

    if (!glSurfaceView.isRendererSet) {
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setRenderer(bitmapRenderer)
        glSurfaceView.isRendererSet = true
    }

    AndroidView(
        factory = { glSurfaceView },
        modifier = Modifier.fillMaxSize()
    )

    return glSurfaceView
}

class MyGlSurfaceViews(context: Context,private val renderer: BitmapRenderer) : GLSurfaceView(context) {
    var isRendererSet: Boolean = false
    private var currentPath: MutableList<FloatArray> = mutableListOf()


    init {
        setEGLContextClientVersion(2)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val normalizedX = (event.x / width) * 2 - 1
        val normalizedY = -((event.y / height) * 2 - 1)

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                currentPath.add(floatArrayOf(normalizedX, normalizedY))
                renderer.updateDoodlePath(currentPath)
                requestRender()
            }
            MotionEvent.ACTION_UP -> {
                // Finalize the path if needed
            }
        }
        return true
    }
}

@Composable
fun TriangleScreen() {
    val context = LocalContext.current
    val bitmapRenderer = remember { BitmapRenderer(context) }
    val glSurfaceView = OpenGLTriangleView(bitmapRenderer)
    val isExporting = remember { mutableStateOf(false) }

    bitmapRenderer.setScreenshotSavedListener {
        isExporting.value = false
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                OpenGLTriangleView(bitmapRenderer)
                if (isExporting.value) {
                    CircularProgressIndicator()
                }
            }

            Row(modifier = Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
                Button(
                    onClick = {
                        bitmapRenderer.rotateImage()
                        glSurfaceView.requestRender()
                    },
                    modifier = Modifier
                ) {
                    Text(text = "Rotate")
                }

                Button(
                    onClick = {
                        if (!isExporting.value) {
                            isExporting.value = true
                            bitmapRenderer.requestScreenshot()
                            glSurfaceView.requestRender()
                        }
                    },
                    modifier = Modifier

                ) {
                    Text(text = "Export")
                }


                Button(
                    onClick = {
                        bitmapRenderer.toggleGrayscale()

                        // Change the tint color (e.g., to blue)
                        //bitmapRenderer.setTintColor(0.0f, 0.0f, 1.0f, 0.5f)

                        // Request a redraw
                        glSurfaceView.requestRender()
                    },
                    modifier = Modifier

                ) {
                    Text(text = "Filter")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        bitmapRenderer.moveLeft()
                        glSurfaceView.requestRender()
                    },
                    modifier = Modifier
                        .padding(2.dp)
                ) {
                    Text(text = "Left")
                }

                Button(
                    onClick = {
                        bitmapRenderer.moveRight()
                        glSurfaceView.requestRender()
                    },
                    modifier = Modifier
                        .padding(2.dp)
                ) {
                    Text(text = "Right")
                }

                Button(
                    onClick = {
                        bitmapRenderer.moveUp()
                        glSurfaceView.requestRender()
                    },
                    modifier = Modifier
                        .padding(2.dp)
                ) {
                    Text(text = "Up")
                }

                Button(
                    onClick = {
                        bitmapRenderer.moveDown()
                        glSurfaceView.requestRender()
                    },
                    modifier = Modifier
                        .padding(2.dp)
                ) {
                    Text(text = "Down")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        bitmapRenderer.setBackgroundColor(1.0f, 0.0f, 0.0f, 1.0f)
                        glSurfaceView.requestRender()
                    },
                    modifier = Modifier.padding(2.dp)
                ) {
                    Text(text = "Bg Color")
                }

                Button(
                    onClick = {
                        bitmapRenderer.increaseImageSize()
                        glSurfaceView.requestRender()
                    },
                    modifier = Modifier

                ) {
                    Text(text = "ScaleUp")
                }

                Button(
                    onClick = {
                        bitmapRenderer.decreaseImageSize()
                        glSurfaceView.requestRender()
                    },
                    modifier = Modifier

                ) {
                    Text(text = "ScaleDown")
                }

            }
        }
    }
}


