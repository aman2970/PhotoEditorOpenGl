package com.example.photoeditoropengl.videeoedit

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.photoeditoropengl.R
import com.example.photoeditoropengl.ui.theme.PhotoEditorOpenGlTheme
import com.example.photoeditoropengl.videeoedit.filter.GlBitmapOverlaySample
import com.example.photoeditoropengl.videeoedit.filter.GlCustomOverlayFilter
import com.example.photoeditoropengl.videeoedit.filter.GlGrayScaleFilter
import com.example.photoeditoropengl.videeoedit.helper.GlFilter
import com.example.photoeditoropengl.videeoedit.videosave.FillMode
import com.example.photoeditoropengl.videeoedit.videosave.GlMp4Composer
import com.example.photoeditoropengl.videeoedit.view.OpenGlPlayerView
import com.example.photoeditoropengl.videeoedit.view.VideoPlayerWrapperView
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoEditActivity : ComponentActivity() {
    private var videoUriString: String? = null
    private var isExporting by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        videoUriString = intent.getStringExtra("videoPath")


        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001)
        }

        setContent {
            PhotoEditorOpenGlTheme {
                videoUriString?.let {
                    VideoEditingScreen(it,isExporting) {isFilterApplied,isMute,currentSpeed,isOverlayApplied ->
                        startCodec(isFilterApplied,isMute,currentSpeed,isOverlayApplied)
                    }
                }
            }
        }
    }

    private fun startCodec(isFilterApplied:Boolean, isMute:Boolean, currentSpeed:Int,isOverlayApplied:Boolean) {
        isExporting = true
        var speed = currentSpeed
        val destinationPath = getVideoFilePath()
        Log.d("data>>>", "currentSpeed>>> $currentSpeed")

        val selectedFilter = if(isFilterApplied) {
            GlGrayScaleFilter()
        }else if(isOverlayApplied){
            val overlayBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_emoji)

            GlCustomOverlayFilter(
                overlayBitmap = overlayBitmap,
                overlayScale = 0.3f,
                xPosition = 0.1f,
                yPosition = 0.1f
            )

        }else{
            GlFilter()
        }

        if(speed == 0){
            speed = 1
        }

        GlMp4Composer(this, videoUriString!!, destinationPath)
            .fillMode(FillMode.PRESERVE_ASPECT_CROP)
            .filter(selectedFilter)
            .mute(isMute)
            .size(1920,1080)
            .timeScale(speed)
            .listener(object : GlMp4Composer.Listener {
                override fun onProgress(progress: Double) {
                    Log.d("data>>>", "progressing $progress")
                }

                override fun onCompleted() {
                    exportMp4ToGallery(applicationContext, destinationPath)
                    runOnUiThread { Toast.makeText(this@VideoEditActivity, "video_path = $videoUriString", Toast.LENGTH_SHORT).show() }
                    isExporting = false
                }

                override fun onCanceled() {
                    Log.d("data>>>", "onCanceled()")
                    isExporting = false
                }

                override fun onFailed(exception: Exception?) {
                    //
                    isExporting = false
                }


            }).start()

    }

    fun getAndroidMoviesFolder(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    }

    fun getVideoFilePath(): String {
        return "${getAndroidMoviesFolder().absolutePath}/${
            SimpleDateFormat("yyyyMM_dd-HHmmss", Locale.getDefault()).format(
                Date()
            )
        }filter_apply.mp4"
    }

    fun exportMp4ToGallery(context: Context, filePath: String) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
        }

        val videoCollection: Uri
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            videoCollection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
            values.put(MediaStore.Video.Media.IS_PENDING, 1)

            val videoUri = context.contentResolver.insert(videoCollection, values)

            try {
                videoUri?.let {
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        Files.copy(Paths.get(filePath), out)
                    }
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(it, values, null, null)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } else {
            values.put(MediaStore.Video.Media.DATA, filePath)
            context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        }

        context.sendBroadcast(
            Intent(
                Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                Uri.parse("file://$filePath")
            )
        )
    }

}

@Composable
fun VideoEditingScreen(videoUri: String, isExporting: Boolean,onExport: (Boolean,Boolean,Int,Boolean) -> Unit) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMute by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableStateOf(1.0f) }
    var isTrimmed by remember { mutableStateOf(false) }
    var currentVideoIndex by remember { mutableStateOf(0) }
    var sliderPosition by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(0L) }
    var openGlPlayerView by remember { mutableStateOf<OpenGlPlayerView?>(null) }

    var isFilterApplied by remember{mutableStateOf(false)}
    var isOverlayApplied by remember { mutableStateOf(false) }

    val videoUrls = listOf(videoUri)

    DisposableEffect(Unit) {
        player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrls[currentVideoIndex]))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        currentVideoIndex = (currentVideoIndex + 1) % videoUrls.size
                        setMediaItem(MediaItem.fromUri(videoUrls[currentVideoIndex]))
                        prepare()
                        play()
                    }
                }
            })
        }
        onDispose {
            player?.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            player?.let {
                sliderPosition = it.currentPosition.toFloat()
                duration = it.duration.coerceAtLeast(1L)
            }
            delay(1000)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        AndroidView(
            factory = { context ->
                VideoPlayerWrapperView(context).apply {
                    addView(OpenGlPlayerView(context).apply {
                        player?.let { setExoPlayer(it) }
                        openGlPlayerView = this
                    })
                }
            },
            modifier = Modifier
                .fillMaxWidth(),
            update = { videoPlayerWrapperView ->
                val view = videoPlayerWrapperView.getChildAt(0) as? OpenGlPlayerView
                view?.setExoPlayer(player)
                openGlPlayerView = view
            }
        )

        Slider(
            value = sliderPosition,
            onValueChange = {
                sliderPosition = it
                player?.seekTo(it.toLong())
            },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = {
                if (isPlaying) player?.pause() else player?.play()
                isPlaying = !isPlaying
            }) {
                Text(if (isPlaying) "Pause" else "Play")
            }

            Button(onClick = {
                if (isMute) player?.volume = 1f else player?.volume = 0f
                isMute = !isMute
            }) {
                Text(if (isMute) "Unmute" else "Mute")
            }

          /*  Button(onClick = {
                if (currentSpeed > 0.5f) {
                    currentSpeed -= 0.5f
                    player?.setPlaybackSpeed(currentSpeed)
                }
            }) {
                Text("Slow")
            }
            Button(onClick = {
                if (currentSpeed < 3.0f) {
                    currentSpeed += 1.0f
                    player?.setPlaybackSpeed(currentSpeed)
                }
            }) {
                Text("Fast")
            }*/
        }


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            Button(onClick = {
                isOverlayApplied = true
                val overlayBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_emoji)

                val customOverlayFilter = GlCustomOverlayFilter(
                    overlayBitmap = overlayBitmap,
                    overlayScale = 0.3f,
                    xPosition = 0.1f,
                    yPosition = 0.1f
                )
                openGlPlayerView?.setGlFilter(customOverlayFilter)
            }) {
                Text("Watermark")
            }


            Button(onClick = {
                isFilterApplied = true
                openGlPlayerView?.setGlFilter(GlGrayScaleFilter())
            }) {
                Text("Filter")
            }

            Button(onClick = {
                isFilterApplied = false
                openGlPlayerView?.setGlFilter(GlFilter())
            }) {
                Text("Default")
            }

        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            Button(onClick = {
                onExport(isFilterApplied,isMute,currentSpeed.toInt(),isOverlayApplied)
            }) {
                Text("Export")
            }

        }


        if (isExporting) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        Text(
            text = "Current Speed: $currentSpeed",
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}




