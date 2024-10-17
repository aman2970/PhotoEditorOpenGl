package com.example.photoeditoropengl.videeoedit.videosave

import android.media.MediaCodec
import android.os.Build

import java.nio.ByteBuffer

class MediaCodecBufferCompatWrapper(private val mediaCodec: MediaCodec) {
    private val inputBuffers: Array<ByteBuffer>?
    private val outputBuffers: Array<ByteBuffer>?

    init {
        if (Build.VERSION.SDK_INT < 21) {
            inputBuffers = mediaCodec.inputBuffers
            outputBuffers = mediaCodec.outputBuffers
        } else {
            inputBuffers = null
            outputBuffers = null
        }
    }

    fun getInputBuffer(index: Int): ByteBuffer? {
        return if (Build.VERSION.SDK_INT >= 21) {
            mediaCodec.getInputBuffer(index)
        } else {
            inputBuffers?.get(index)
        }
    }

    fun getOutputBuffer(index: Int): ByteBuffer? {
        return if (Build.VERSION.SDK_INT >= 21) {
            mediaCodec.getOutputBuffer(index)
        } else {
            outputBuffers?.get(index)
        }
    }
}