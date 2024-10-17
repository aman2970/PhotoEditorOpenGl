package com.example.photoeditoropengl.videeoedit.videosave

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat

import java.nio.ByteBuffer
import java.nio.ByteOrder


class AudioComposer(
    private val mediaExtractor: MediaExtractor,
    private val trackIndex: Int,
    private val muxRender: MuxRender
) : IAudioComposer {

    private val sampleType = MuxRender.SampleType.AUDIO
    private val bufferInfo = MediaCodec.BufferInfo()
    private var bufferSize: Int
    private var buffer: ByteBuffer
    private var isEOS = false
    private var actualOutputFormat: MediaFormat = mediaExtractor.getTrackFormat(trackIndex)
    private var writtenPresentationTimeUs: Long = 0

    init {
        muxRender.setOutputFormat(sampleType, actualOutputFormat)
        bufferSize = actualOutputFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        buffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
    }

    @SuppressLint("Assert")
    override fun stepPipeline(): Boolean {
        if (isEOS) return false

        val currentTrackIndex = mediaExtractor.sampleTrackIndex
        if (currentTrackIndex < 0) {
            buffer.clear()
            bufferInfo.set(0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            muxRender.writeSampleData(sampleType, buffer, bufferInfo)
            isEOS = true
            return true
        }
        if (currentTrackIndex != trackIndex) return false

        buffer.clear()
        val sampleSize = mediaExtractor.readSampleData(buffer, 0)
        assert(sampleSize <= bufferSize)

        val isKeyFrame = (mediaExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
        val flags = if (isKeyFrame) MediaCodec.BUFFER_FLAG_SYNC_FRAME else 0
        bufferInfo.set(0, sampleSize, mediaExtractor.sampleTime, flags)
        muxRender.writeSampleData(sampleType, buffer, bufferInfo)
        writtenPresentationTimeUs = bufferInfo.presentationTimeUs

        mediaExtractor.advance()
        return true
    }

    override fun getWrittenPresentationTimeUs(): Long {
        return writtenPresentationTimeUs
    }

    override fun isFinished(): Boolean {
        return isEOS
    }

    override fun setup() {
        // Do nothing
    }

    override fun release() {
        // Do nothing
    }
}