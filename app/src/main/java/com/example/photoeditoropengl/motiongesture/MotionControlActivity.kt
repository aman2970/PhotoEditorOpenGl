package com.example.photoeditoropengl.motiongesture
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.photoeditoropengl.R
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
                .pointerInteropFilter { event ->
                    surfaceView.openGLHelper.onTouchEvent(event)
                    true
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .offset { IntOffset(boxPosition.x.roundToInt(), boxPosition.y.roundToInt()) }
                    .graphicsLayer(
                        scaleX = boxScale,
                        scaleY = boxScale,
                        rotationZ = boxRotation
                    )
                    .border(BorderStroke(0.3.dp, Color.Black))
            )

            Box(
                modifier = Modifier
                    .size(90.dp)
                    .offset { IntOffset(boxPosition.x.roundToInt(), boxPosition.y.roundToInt()) }
                    .graphicsLayer(
                        scaleX = boxScale,
                        scaleY = boxScale,
                        rotationZ = boxRotation
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = null,
                    modifier = Modifier
                        .size(15.dp)
                        .align(Alignment.TopStart)
                        .offset(x = (-5).dp, y = (-5).dp)
                        .clickable {
                            surfaceView.openGLHelper.onDelete()
                            Toast.makeText(context, "Delete clicked", Toast.LENGTH_SHORT).show()
                        }

                )

                Image(
                    painter = painterResource(id = R.drawable.ic_edit),
                    contentDescription = null,
                    modifier = Modifier
                        .size(15.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-5).dp)
                        .clickable {
                            surfaceView.openGLHelper.onDelete()
                            Toast.makeText(context, "Edit clicked", Toast.LENGTH_SHORT).show()
                        }
                )

                Image(
                    painter = painterResource(id = R.drawable.ic_flip),
                    contentDescription = null,
                    modifier = Modifier
                        .size(15.dp)
                        .align(Alignment.BottomStart)
                        .offset(x = (-5).dp, y = 5.dp)
                        .clickable {
                            surfaceView.openGLHelper.onDelete()
                            Toast.makeText(context, "Flip clicked", Toast.LENGTH_SHORT).show()
                        }
                )

                Image(
                    painter = painterResource(id = R.drawable.ic_rotate),
                    contentDescription = null,
                    modifier = Modifier
                        .size(15.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 5.dp, y = 5.dp)
                        .clickable {
                            surfaceView.openGLHelper.onDelete()
                            Toast.makeText(context, "Rotate clicked", Toast.LENGTH_SHORT).show()
                        }
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