package com.example.photoeditoropengl.motiongesture

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.photoeditoropengl.imageedit.BitmapRenderer
import com.example.photoeditoropengl.imageedit.OpenGLTriangleView
import com.example.photoeditoropengl.ui.theme.PhotoEditorOpenGlTheme
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

@Composable
fun TransparentSquareBox() {
    var boxPosition by remember { mutableStateOf(Offset(0f, 0f)) }
    var boxScale by remember { mutableStateOf(1f) }
    var boxRotation by remember { mutableStateOf(0f) }

    Surface(
        modifier = Modifier
            .fillMaxSize(),
        color = Color.White,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .size(130.dp)
                    .offset { IntOffset(boxPosition.x.roundToInt(), boxPosition.y.roundToInt()) }
                    .graphicsLayer(
                        scaleX = boxScale,
                        scaleY = boxScale,
                        rotationZ = boxRotation
                    )
                    .border(BorderStroke(2.dp, Color.Black))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, rotation ->
                            boxPosition += pan
                            boxScale = (boxScale * zoom).coerceIn(0.5f, 3f)
                            boxRotation += rotation
                        }
                    },
                color = Color.Transparent,
            ) {}
        }
    }
}

@Composable
fun TransparentSquareBoxOne() {
    val context = LocalContext.current
    var boxPosition by remember { mutableStateOf(Offset(0f, 0f)) }
    var boxScale by remember { mutableStateOf(1f) }
    var boxRotation by remember { mutableStateOf(0f) }
    val bitmapRenderer = remember { BitmapRenderer(context) }
    val glSurfaceView = OpenGLTriangleView(bitmapRenderer)



    val currentPosition by rememberUpdatedState(boxPosition)
    val currentScale by rememberUpdatedState(boxScale)
    val currentRotation by rememberUpdatedState(boxRotation)

    LaunchedEffect(currentPosition, currentScale, currentRotation) {
        Log.d("BoxPosition", "BoxPosition: $currentPosition, Scale: $currentScale, Rotation: $currentRotation")
        val normalizedX = (currentPosition.x / glSurfaceView.width) * 2f - 1f
        val normalizedY = -((currentPosition.y / glSurfaceView.height) * 2f - 1f)
        bitmapRenderer.updatePosition(normalizedX,normalizedY)
        bitmapRenderer.updateRotation(-currentRotation)
        bitmapRenderer.updateScale(currentScale)
        glSurfaceView.requestRender()
    }


    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    boxPosition += pan
                    boxScale = (boxScale * zoom).coerceIn(0.5f, 3f)
                    boxRotation += rotation
                }
            },
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
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
                    .fillMaxSize()
            ) {
                OpenGLTriangleView(bitmapRenderer)

                TransparentSquareBoxOne()

                if (isExporting.value) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = {
                    bitmapRenderer.rotateImage()
                    glSurfaceView.requestRender()
                }) { Text(text = "Rotate") }

                Button(onClick = {
                    if (!isExporting.value) {
                        isExporting.value = true
                        bitmapRenderer.requestScreenshot()
                        glSurfaceView.requestRender()
                    }
                }) { Text(text = "Export") }

                Button(onClick = {
                    bitmapRenderer.toggleGrayscale()
                    glSurfaceView.requestRender()
                }) { Text(text = "Filter") }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { bitmapRenderer.moveLeft(); glSurfaceView.requestRender() }) { Text(text = "Left") }
                Button(onClick = { bitmapRenderer.moveRight(); glSurfaceView.requestRender() }) { Text(text = "Right") }
                Button(onClick = { bitmapRenderer.moveUp(); glSurfaceView.requestRender() }) { Text(text = "Up") }
                Button(onClick = { bitmapRenderer.moveDown(); glSurfaceView.requestRender() }) { Text(text = "Down") }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { bitmapRenderer.setBackgroundColor(1.0f, 0.0f, 0.0f, 1.0f); glSurfaceView.requestRender() }) { Text(text = "Bg Color") }
                Button(onClick = { bitmapRenderer.increaseImageSize(); glSurfaceView.requestRender() }) { Text(text = "ScaleUp") }
                Button(onClick = { bitmapRenderer.decreaseImageSize(); glSurfaceView.requestRender() }) { Text(text = "ScaleDown") }
            }
        }
    }
}
