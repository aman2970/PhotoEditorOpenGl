package com.example.photoeditoropengl.motiongesture

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.photoeditoropengl.imageedit.BitmapRenderer
import com.example.photoeditoropengl.motiongesture.OpenGLStatic.ENABLE_ALPHA
import com.example.photoeditoropengl.motiongesture.OpenGLStatic.ENABLE_ANTIALIASING
import com.example.photoeditoropengl.ui.theme.PhotoEditorOpenGlTheme
import com.slaviboy.opengl.main.OpenGLConfigChooser
import com.slaviboy.openglexamples.single.OpenGLHelper
import com.slaviboy.openglexamples.single.OpenGLRenderer
import kotlin.math.log
import kotlin.math.roundToInt

class MotionControlActivity : ComponentActivity() {
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
    Log.d("data>>>>", "second_view_created")

    val context = LocalContext.current
    var boxPosition by remember { mutableStateOf(Offset(0f, 0f)) }
    var boxScale by remember { mutableStateOf(1f) }
    var boxRotation by remember { mutableStateOf(0f) }

    LaunchedEffect(openGLHelper) {
        openGLHelper.mainGestureDetector.listener = object : OpenGLMatrixGestureDetector.OnMatrixChangeListener {
            override fun onMatrixChange(matrixGestureDetector: OpenGLMatrixGestureDetector) {
                Log.d("data>>>>", "onMatrixChange: ")
                val translation = matrixGestureDetector.translate
                boxPosition = Offset(translation.x, translation.y)

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
                .pointerInteropFilter { event ->
                    openGLHelper.onTouchEvent(event)
                    true
                }
            ,
            contentAlignment = Alignment.Center
        ) {
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
        ) {
            // Main content
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

                 //    TransparentSquareBoxOne(openGLHelper, glSurfaceView)

                }
            }

            Button(
                onClick = {

                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text("Move")
            }
        }
    }
}

@Composable
fun OpenGLTriangleViews(openGLHelper: OpenGLHelper, glSurfaceView: OpenGLSurfaceView) {
    // Set up the render listener only once
    Log.d("data>>>>", "view_created")

    AndroidView(
        factory = { glSurfaceView },
        modifier = Modifier
            .fillMaxSize()
    )
}