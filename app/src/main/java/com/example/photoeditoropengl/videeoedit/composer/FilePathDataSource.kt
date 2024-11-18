package com.example.photoeditoropengl.videeoedit.composer

import androidx.annotation.NonNull
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException

class FilePathDataSource(
    @NonNull filePath: String,
    @NonNull private val logger: Logger,
    @NonNull private val listener: DataSource.Listener
) : DataSource {

    companion object {
        private val TAG = FilePathDataSource::class.java.simpleName
    }

    override val fileDescriptor: FileDescriptor

    init {
        val srcFile = File(filePath)
        val fileInputStream = try {
            FileInputStream(srcFile)
        } catch (e: FileNotFoundException) {
            logger.error(TAG, "Unable to find file", e)
            listener.onError(e)
            throw e
        }

        fileDescriptor = try {
            fileInputStream.fd
        } catch (e: IOException) {
            logger.error(TAG, "Unable to read input file", e)
            listener.onError(e)
            throw e
        }
    }

}
