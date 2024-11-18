package com.example.photoeditoropengl.videeoedit.composer

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

internal class AudioComposer(
    private val mediaExtractor: MediaExtractor, private val trackIndex: Int,
    muxRender: MuxRender, trimStartMs: Long, trimEndMs: Long,
    logger: Logger
) : IAudioComposer {
    private val muxRender: MuxRender = muxRender
    private val sampleType: SampleType = SampleType.AUDIO
    private val bufferInfo = MediaCodec.BufferInfo()
    private var bufferSize: Int
    private var buffer: ByteBuffer
    override var isFinished: Boolean = false
        private set
    override var writtenPresentationTimeUs: Long = 0
        private set

    private val trimStartUs = TimeUnit.MILLISECONDS.toMicros(trimStartMs)
    private val trimEndUs =
        if (trimEndMs == -1L) trimEndMs else TimeUnit.MILLISECONDS.toMicros(trimEndMs)

    private val logger: Logger = logger

    init {
        val actualOutputFormat = mediaExtractor.getTrackFormat(this.trackIndex)
        this.muxRender.setOutputFormat(this.sampleType, actualOutputFormat)
        bufferSize =
            if (actualOutputFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) actualOutputFormat.getInteger(
                MediaFormat.KEY_MAX_INPUT_SIZE
            ) else (64 * 1024)
        buffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
        mediaExtractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
    }


    @SuppressLint("Assert")
    override fun stepPipeline(): Boolean {
        if (isFinished) return false
        val trackIndex = mediaExtractor.sampleTrackIndex
        logger.debug(TAG, "stepPipeline trackIndex:$trackIndex")
        if (trackIndex < 0 || (writtenPresentationTimeUs >= trimEndUs && trimEndUs != -1L)) {
            buffer.clear()
            bufferInfo[0, 0, 0] = MediaCodec.BUFFER_FLAG_END_OF_STREAM
            muxRender.writeSampleData(sampleType, buffer, bufferInfo)
            isFinished = true
            mediaExtractor.unselectTrack(this.trackIndex)
            return true
        }
        if (trackIndex != this.trackIndex) return false

        buffer.clear()
        val sampleSize = mediaExtractor.readSampleData(buffer, 0)
        if (sampleSize > bufferSize) {
            logger.warning(
                TAG,
                "Sample size smaller than buffer size, resizing buffer: $sampleSize"
            )
            bufferSize = 2 * sampleSize
            buffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
        }
        val isKeyFrame = (mediaExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
        val flags = if (isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0

        if (mediaExtractor.sampleTime >= trimStartUs && (mediaExtractor.sampleTime <= trimEndUs || trimEndUs == -1L)) {
            bufferInfo[0, sampleSize, mediaExtractor.sampleTime] = flags
            muxRender.writeSampleData(sampleType, buffer, bufferInfo)
        }

        writtenPresentationTimeUs = mediaExtractor.sampleTime
        mediaExtractor.advance()
        return true
    }

    override fun setup() {
        // do nothing
    }

    override fun release() {
        // do nothing
    }

    companion object {
        private const val TAG = "AudioComposer"
    }
}
