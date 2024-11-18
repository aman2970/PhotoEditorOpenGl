package com.example.photoeditoropengl.videeoedit.composer

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class MuxRender(private val muxer: MediaMuxer, logger: Logger) {
    private var videoFormat: MediaFormat? = null
    private var audioFormat: MediaFormat? = null
    private var videoTrackIndex = 0
    private var audioTrackIndex = 0
    private var byteBuffer: ByteBuffer? = null
    private val sampleInfoList: MutableList<SampleInfo>
    private var started = false
    private val logger: Logger = logger

    init {
        sampleInfoList = ArrayList()
    }

    fun setOutputFormat(sampleType: SampleType?, format: MediaFormat?) {
        when (sampleType) {
            SampleType.VIDEO -> videoFormat = format
            SampleType.AUDIO -> audioFormat = format
            else -> throw AssertionError()
        }
    }

    fun onSetOutputFormat() {
        if (videoFormat != null && audioFormat != null) {
            videoTrackIndex = muxer.addTrack(videoFormat!!)
            logger.debug(
                TAG,
                "Added track #" + videoTrackIndex + " with " + videoFormat!!.getString(MediaFormat.KEY_MIME) + " to muxer"
            )
            audioTrackIndex = muxer.addTrack(audioFormat!!)
            logger.debug(
                TAG,
                "Added track #" + audioTrackIndex + " with " + audioFormat!!.getString(MediaFormat.KEY_MIME) + " to muxer"
            )
        } else if (videoFormat != null) {
            videoTrackIndex = muxer.addTrack(videoFormat!!)
            logger.debug(
                TAG,
                "Added track #" + videoTrackIndex + " with " + videoFormat!!.getString(MediaFormat.KEY_MIME) + " to muxer"
            )
        }

        muxer.start()
        started = true

        if (byteBuffer == null) {
            byteBuffer = ByteBuffer.allocate(0)
        }
        byteBuffer!!.flip()
        logger.debug(
            TAG, "Output format determined, writing " + sampleInfoList.size +
                    " samples / " + byteBuffer!!.limit() + " bytes to muxer."
        )
        val bufferInfo = MediaCodec.BufferInfo()
        var offset = 0
        for (sampleInfo in sampleInfoList) {
            sampleInfo.writeToBufferInfo(bufferInfo, offset)
            muxer.writeSampleData(
                getTrackIndexForSampleType(sampleInfo.sampleType),
                byteBuffer!!, bufferInfo
            )
            offset += sampleInfo.size
        }
        sampleInfoList.clear()
        byteBuffer = null
    }

    fun writeSampleData(
        sampleType: SampleType,
        byteBuf: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    ) {
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
            else -> throw AssertionError()
        }
    }

    private class SampleInfo(
        sampleType: SampleType,
        val size: Int,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        val sampleType: SampleType = sampleType
        private val presentationTimeUs = bufferInfo.presentationTimeUs
        private val flags = bufferInfo.flags

        fun writeToBufferInfo(bufferInfo: MediaCodec.BufferInfo, offset: Int) {
            bufferInfo[offset, size, presentationTimeUs] = flags
        }
    }

    companion object {
        private const val TAG = "MuxRender"
        private const val BUFFER_SIZE =
            64 * 1024
    }
}
