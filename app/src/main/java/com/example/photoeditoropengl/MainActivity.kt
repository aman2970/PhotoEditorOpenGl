package com.example.photoeditoropengl

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Bundle
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
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
    val glSurfaceView = remember { MyGlSurfaceViews(context) }

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

class MyGlSurfaceViews(context: Context) : GLSurfaceView(context) {
    var isRendererSet: Boolean = false

    init {
        setEGLContextClientVersion(2)
    }
}

@Composable
fun TriangleScreen() {
    val context = LocalContext.current
    val bitmapRenderer = remember { BitmapRenderer(context) }
    val glSurfaceView = OpenGLTriangleView(bitmapRenderer)

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
                        bitmapRenderer.requestScreenshot()
                        glSurfaceView.requestRender()
                    },
                    modifier = Modifier

                ) {
                    Text(text = "Export")
                }

                Button(
                    onClick = {
                        bitmapRenderer.increaseImageSize()
                        glSurfaceView.requestRender()
                    },
                    modifier = Modifier

                ) {
                    Text(text = "Scale")
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
                    modifier = Modifier
                        .padding(2.dp)
                ) {
                    Text(text = "Bg Color")
                }

            }
        }
    }
}


