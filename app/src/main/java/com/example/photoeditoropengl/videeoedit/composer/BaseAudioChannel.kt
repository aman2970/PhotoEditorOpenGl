package com.example.photoeditoropengl.videeoedit.composer

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ShortBuffer
import java.util.ArrayDeque
import java.util.Queue

abstract class BaseAudioChannel(
    protected val decoder: MediaCodec?,
    protected val encoder: MediaCodec?, protected val encodeFormat: MediaFormat?
) {
    protected class AudioBuffer {
        var bufferIndex: Int = 0
        var presentationTimeUs: Long = 0
        var data: ShortBuffer? = null
    }

    protected class BufferInfo {
        var totaldata: Long = 0
        var presentationTimeUs: Long = 0
    }

    protected val emptyBuffers: Queue<AudioBuffer> = ArrayDeque()
    protected val filledBuffers: Queue<AudioBuffer> = ArrayDeque()

    protected var inputSampleRate: Int = 0
    protected var inputChannelCount: Int = 0
    protected var outputChannelCount: Int = 0

    protected val overflowBuffer: AudioBuffer = AudioBuffer()

    protected var actualDecodedFormat: MediaFormat? = null

    open fun setActualDecodedFunFormat(decodedFormat: MediaFormat?) {
        actualDecodedFormat = decodedFormat

        inputSampleRate = actualDecodedFormat!!.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        if (inputSampleRate != encodeFormat!!.getInteger(MediaFormat.KEY_SAMPLE_RATE)) {
            throw UnsupportedOperationException("Audio sample rate conversion not supported yet.")
        }

        inputChannelCount = actualDecodedFormat!!.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        outputChannelCount = encodeFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        if (outputChannelCount != 1 && outputChannelCount != 2) {
            throw UnsupportedOperationException("Output channel count ($outputChannelCount) not supported.")
        }

        overflowBuffer.presentationTimeUs = 0
    }

    protected abstract fun sampleCountToDurationUs(
        sampleCount: Long,
        sampleRate: Int,
        channelCount: Int
    ): Long

    protected abstract fun drainDecoderBufferAndQueue(bufferIndex: Int, presentationTimeUs: Long)

    protected abstract fun feedEncoder(timeoutUs: Long): Boolean

    companion object {
        const val BUFFER_INDEX_END_OF_STREAM: Int = -1
        public const val BYTE_PER_SAMPLE: Int = 16 / 8
        public const val BYTES_PER_SHORT: Int = 2
        public const val MICROSECS_PER_SEC: Long = 1000000
    }
}
