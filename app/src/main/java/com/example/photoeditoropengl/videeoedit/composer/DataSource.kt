package com.example.photoeditoropengl.videeoedit.composer

import androidx.annotation.NonNull
import java.io.FileDescriptor


interface DataSource {
    val fileDescriptor: FileDescriptor?

    interface Listener {
        fun onError(e: Exception?)
    }

}
