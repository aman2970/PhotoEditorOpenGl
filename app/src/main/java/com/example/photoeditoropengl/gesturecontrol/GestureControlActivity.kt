package com.example.photoeditoropengl.gesturecontrol

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.photoeditoropengl.ui.theme.PhotoEditorOpenGlTheme
import kotlin.math.roundToInt

class GestureControlActivity : ComponentActivity() {
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
fun OpenGLTriangleView(gestureRenderer: GestureRenderer): GLSurfaceView {
    val context = LocalContext.current
    val glSurfaceView = remember { MyGlSurfaceViews(context,gestureRenderer) }

    if (!glSurfaceView.isRendererSet) {
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setRenderer(gestureRenderer)
        glSurfaceView.isRendererSet = true
    }

    AndroidView(
        factory = { glSurfaceView },
        modifier = Modifier.fillMaxSize()
    )

    return glSurfaceView
}

class MyGlSurfaceViews(context: Context, private val renderer: GestureRenderer) : GLSurfaceView(context) {
    var isRendererSet: Boolean = false
    private var currentPath: MutableList<FloatArray> = mutableListOf()


    init {
        setEGLContextClientVersion(2)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {

                requestRender()
            }
            MotionEvent.ACTION_UP -> {
            }
        }
        return true
    }
}

@Composable
fun TriangleScreen() {
    val context = LocalContext.current
    val gestureRenderer = remember { GestureRenderer(context) }
    val glSurfaceView = OpenGLTriangleView(gestureRenderer)
    val isExporting = remember { mutableStateOf(false) }
    var boxPosition by remember { mutableStateOf(Offset(50f, 50f)) }
    var boxScale by remember { mutableStateOf(1.3f) }
    var boxRotation by remember { mutableStateOf(0f) }


    gestureRenderer.setScreenshotSavedListener {
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
                OpenGLTriangleView(gestureRenderer)

                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .offset { IntOffset(boxPosition.x.roundToInt(), boxPosition.y.roundToInt()) }
                        .graphicsLayer(
                            scaleX = boxScale,
                            scaleY = boxScale,
                            rotationZ = boxRotation
                        )
                        .border(BorderStroke(.5.dp, Color.Black))

                ) {

                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .offset(x = (-10).dp, y = (-10).dp)
                            .background(Color.Red)
                            .align(Alignment.TopStart)
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .offset(x = (10).dp, y = (-10).dp)
                            .background(Color.Green)
                            .align(Alignment.TopEnd)
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .offset(x = (-10).dp, y = (10).dp)
                            .background(Color.Blue)
                            .align(Alignment.BottomStart)
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .offset(x = (10).dp, y = (10).dp)
                            .background(Color.Yellow)
                            .align(Alignment.BottomEnd)
                    )
                }
            }
        }
    }
}
