package com.example.photoeditoropengl.videeoedit.videosave

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayList

class MuxRender(private val muxer: MediaMuxer) {
    companion object {
        private const val TAG = "MuxRender"
        private const val BUFFER_SIZE = 64 * 1024
    }

    private var videoFormat: MediaFormat? = null
    private var audioFormat: MediaFormat? = null
    private var videoTrackIndex: Int = -1
    private var audioTrackIndex: Int = -1
    private var byteBuffer: ByteBuffer? = null
    private val sampleInfoList: MutableList<SampleInfo> = mutableListOf()
    private var started: Boolean = false

    fun setOutputFormat(sampleType: SampleType, format: MediaFormat) {
        when (sampleType) {
            SampleType.VIDEO -> videoFormat = format
            SampleType.AUDIO -> audioFormat = format
            else -> throw AssertionError()
        }
    }

    fun onSetOutputFormat() {
        if (videoFormat != null && audioFormat != null) {
            videoTrackIndex = muxer.addTrack(videoFormat!!)
            Log.v(TAG, "Added track #$videoTrackIndex with ${videoFormat!!.getString(MediaFormat.KEY_MIME)} to muxer")
            audioTrackIndex = muxer.addTrack(audioFormat!!)
            Log.v(TAG, "Added track #$audioTrackIndex with ${audioFormat!!.getString(MediaFormat.KEY_MIME)} to muxer")
        } else if (videoFormat != null) {
            videoTrackIndex = muxer.addTrack(videoFormat!!)
            Log.v(TAG, "Added track #$videoTrackIndex with ${videoFormat!!.getString(MediaFormat.KEY_MIME)} to muxer")
        }

        muxer.start()
        started = true

        if (byteBuffer == null) {
            byteBuffer = ByteBuffer.allocate(0)
        }
        byteBuffer!!.flip()
        Log.v(TAG, "Output format determined, writing ${sampleInfoList.size} samples / ${byteBuffer!!.limit()} bytes to muxer.")
        val bufferInfo = MediaCodec.BufferInfo()
        var offset = 0

        for (sampleInfo in sampleInfoList) {
            sampleInfo.writeToBufferInfo(bufferInfo, offset)
            muxer.writeSampleData(getTrackIndexForSampleType(sampleInfo.sampleType), byteBuffer!!, bufferInfo)
            offset += sampleInfo.size
        }
        sampleInfoList.clear()
        byteBuffer = null
    }

    fun writeSampleData(sampleType: SampleType, byteBuf: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (started) {
            muxer.writeSampleData(getTrackIndexForSampleType(sampleType), byteBuf, bufferInfo)
            return
        }
        byteBuf.limit(bufferInfo.offset + bufferInfo.size)
        byteBuf.position(bufferInfo.offset)

        if (byteBuffer == null) {
            byteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.nativeOrder())
        }
        byteBuffer!!.put(byteBuf)
        sampleInfoList.add(SampleInfo(sampleType, bufferInfo.size, bufferInfo))
    }

    private fun getTrackIndexForSampleType(sampleType: SampleType): Int {
        return when (sampleType) {
            SampleType.VIDEO -> videoTrackIndex
            SampleType.AUDIO -> audioTrackIndex
        }
    }

    enum class SampleType { VIDEO, AUDIO }

    private class SampleInfo(
        val sampleType: SampleType,
        val size: Int,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        val presentationTimeUs: Long = bufferInfo.presentationTimeUs
        val flags: Int = bufferInfo.flags

        fun writeToBufferInfo(bufferInfo: MediaCodec.BufferInfo, offset: Int) {
            bufferInfo.set(offset, size, presentationTimeUs, flags)
        }
    }
}