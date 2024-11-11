package com.example.photoeditoropengl.motiongesture
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.photoeditoropengl.imageedit.BitmapRenderer
import com.example.photoeditoropengl.motiongesture.OpenGLStatic.DEVICE_HEIGHT
import com.example.photoeditoropengl.motiongesture.OpenGLStatic.DEVICE_WIDTH
import com.example.photoeditoropengl.ui.theme.PhotoEditorOpenGlTheme
import com.slaviboy.openglexamples.single.OpenGLHelper
import kotlin.math.roundToInt

class MotionControlActivity : ComponentActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PhotoEditorOpenGlTheme {
                TriangleScreenWithOverlay()
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TransparentSquareBoxOne(openGLHelper: OpenGLHelper,surfaceView: OpenGLSurfaceView) {
    val context = LocalContext.current
    var boxPosition by remember { mutableStateOf(Offset(0f, 0f)) }
    var boxScale by remember { mutableStateOf(1f) }
    var boxRotation by remember { mutableStateOf(0f) }

    LaunchedEffect(openGLHelper) {
        surfaceView.openGLHelper.mainGestureDetector.listener = object : OpenGLMatrixGestureDetector.OnMatrixChangeListener {
            override fun onMatrixChange(matrixGestureDetector: OpenGLMatrixGestureDetector) {
                val translation = matrixGestureDetector.translate
                val centerX = DEVICE_WIDTH / 2f
                val centerY = DEVICE_HEIGHT / 2f
                boxPosition = Offset(translation.x-centerX, translation.y-centerY)
                boxScale = matrixGestureDetector.scale
                boxRotation = matrixGestureDetector.angle
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize(),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
            ,
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TriangleScreenWithOverlay() {
    val context = LocalContext.current
    val bitmapRenderer = remember { BitmapRenderer(context) }
    val isExporting = remember { mutableStateOf(false) }
    val openGLHelper = remember { OpenGLHelper() }
    val glSurfaceView = remember { OpenGLSurfaceView(context) }

    bitmapRenderer.setScreenshotSavedListener {
        isExporting.value = false
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInteropFilter { event ->
                    glSurfaceView.openGLHelper.onTouchEvent(event)
                    true
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    OpenGLTriangleViews(openGLHelper, glSurfaceView)

                    TransparentSquareBoxOne(openGLHelper,glSurfaceView)
                }
            }

        }
    }
}

@Composable
fun OpenGLTriangleViews(openGLHelper: OpenGLHelper, glSurfaceView: OpenGLSurfaceView) {
    AndroidView(
        factory = { glSurfaceView },
        modifier = Modifier
            .fillMaxSize()
    )
}