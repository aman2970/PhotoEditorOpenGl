package com.example.photoeditoropengl

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.photoeditoropengl.imageedit.MainActivity
import com.example.photoeditoropengl.ui.theme.PhotoEditorOpenGlTheme
import com.example.photoeditoropengl.videeoedit.VideoEditActivity

class PickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 1001)
        }

        val videoPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val videoUri: Uri? = result.data?.data
            videoUri?.let {
                val videoPath = getRealPathFromUri(it)
                videoPath?.let { path->
                    val intent = Intent(this,VideoEditActivity::class.java)
                    intent.putExtra("videoPath",path)
                    startActivity(intent)
                }
            }
        }

        setContent {
            PhotoEditorOpenGlTheme {
                VideoPicker{
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                    videoPickerLauncher.launch(intent)
                }
            }
        }
    }

    fun getRealPathFromUri(contentUri: Uri): String? {
        var cursor = contentResolver.query(contentUri, arrayOf(MediaStore.Video.Media.DATA), null, null, null)
        cursor?.use {
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            if (it.moveToFirst()) {
                return it.getString(columnIndex)
            }
        }
        return null
    }

}

@Composable
fun VideoPicker(onPickVideo:() -> Unit){
    val context = LocalContext.current

    Box(
        modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ){

            Button(
                onClick = {onPickVideo()},
            ) {
                Text("Pick Video")
            }

            Button(
                onClick = {   val intent = Intent(context, MainActivity::class.java)
                    context.startActivity(intent)},
            ) {
                Text("PhotoEditor")
            }

        }
    }
}

