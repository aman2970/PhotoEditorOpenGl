package com.example.photoeditoropengl.videeoedit.composer

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileDescriptor
import java.io.FileNotFoundException

class UriDataSource(uri: Uri, context: Context, logger: Logger, listener: DataSource.Listener) :
    DataSource {
    override val fileDescriptor: FileDescriptor


    init {
        var parcelFileDescriptor: ParcelFileDescriptor? = null
        try {
            parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
        } catch (e: FileNotFoundException) {
            logger.error(TAG, "Unable to find file", e)
            listener.onError(e)
        }
        fileDescriptor = parcelFileDescriptor!!.fileDescriptor
    }

    companion object {
        private val TAG: String = UriDataSource::class.java.simpleName
    }
}
