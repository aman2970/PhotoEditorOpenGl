package com.example.photoeditoropengl.videeoedit.videosave

import android.media.MediaCodec
import android.media.MediaFormat

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.ArrayDeque
import java.util.Queue

class AudioChannel(
    private val decoder: MediaCodec,
    private val encoder: MediaCodec,
    private val encodeFormat: MediaFormat
) {

    private class AudioBuffer {
        var bufferIndex: Int = 0
        var presentationTimeUs: Long = 0
        var data: ShortBuffer? = null
    }

    companion object {
        const val BUFFER_INDEX_END_OF_STREAM = -1
        private const val BYTES_PER_SHORT = 2
        private const val MICROSECS_PER_SEC = 1000000
    }

    private val emptyBuffers: Queue<AudioBuffer> = ArrayDeque()
    private val filledBuffers: Queue<AudioBuffer> = ArrayDeque()

    private var inputSampleRate: Int = 0
    private var inputChannelCount: Int = 0
    private var outputChannelCount: Int = 0

    private val decoderBuffers = MediaCodecBufferCompatWrapper(this.decoder)
    private val encoderBuffers = MediaCodecBufferCompatWrapper(this.encoder)

    private val overflowBuffer = AudioBuffer()

    private var actualDecodedFormat: MediaFormat? = null

    fun setActualDecodedFormat(decodedFormat: MediaFormat) {
        actualDecodedFormat = decodedFormat

        inputSampleRate = actualDecodedFormat!!.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        if (inputSampleRate != encodeFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)) {
            throw UnsupportedOperationException("Audio sample rate conversion not supported yet.")
        }

        inputChannelCount = actualDecodedFormat!!.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        outputChannelCount = encodeFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        if (inputChannelCount != 1 && inputChannelCount != 2) {
            throw UnsupportedOperationException("Input channel count ($inputChannelCount) not supported.")
        }

        if (outputChannelCount != 1 && outputChannelCount != 2) {
            throw UnsupportedOperationException("Output channel count ($outputChannelCount) not supported.")
        }

        overflowBuffer.presentationTimeUs = 0
    }

    fun drainDecoderBufferAndQueue(bufferIndex: Int, presentationTimeUs: Long) {
        if (actualDecodedFormat == null) {
            throw RuntimeException("Buffer received before format!")
        }

        val data = if (bufferIndex == BUFFER_INDEX_END_OF_STREAM) null else decoderBuffers.getOutputBuffer(bufferIndex)

        var buffer = emptyBuffers.poll()
        if (buffer == null) {
            buffer = AudioBuffer()
        }

        buffer.bufferIndex = bufferIndex
        buffer.presentationTimeUs = presentationTimeUs
        buffer.data = data?.asShortBuffer()

        if (overflowBuffer.data == null) {
            overflowBuffer.data = ByteBuffer.allocateDirect(data!!.capacity())
                .order(ByteOrder.nativeOrder())
                .asShortBuffer().apply {
                    clear().flip()
                }
        }

        filledBuffers.add(buffer)
    }

    fun feedEncoder(timeoutUs: Long): Boolean {
        val hasOverflow = overflowBuffer.data != null && overflowBuffer.data!!.hasRemaining()
        if (filledBuffers.isEmpty() && !hasOverflow) {
            return false
        }

        val encoderInBuffIndex = encoder.dequeueInputBuffer(timeoutUs)
        if (encoderInBuffIndex < 0) {
            return false
        }

        val outBuffer = encoderBuffers.getInputBuffer(encoderInBuffIndex)!!.asShortBuffer()
        if (hasOverflow) {
            val presentationTimeUs = drainOverflow(outBuffer)
            encoder.queueInputBuffer(
                encoderInBuffIndex,
                0, outBuffer.position() * BYTES_PER_SHORT,
                presentationTimeUs, 0
            )
            return true
        }

        val inBuffer = filledBuffers.poll()
        if (inBuffer!!.bufferIndex == BUFFER_INDEX_END_OF_STREAM) {
            encoder.queueInputBuffer(encoderInBuffIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return false
        }

        val presentationTimeUs = remixAndMaybeFillOverflow(inBuffer, outBuffer)
        encoder.queueInputBuffer(
            encoderInBuffIndex,
            0, outBuffer.position() * BYTES_PER_SHORT,
            presentationTimeUs, 0
        )

        decoder.releaseOutputBuffer(inBuffer.bufferIndex, false)
        emptyBuffers.add(inBuffer)

        return true
    }

    private fun sampleCountToDurationUs(sampleCount: Int, sampleRate: Int, channelCount: Int): Long {
        return (sampleCount / (sampleRate * MICROSECS_PER_SEC)) / channelCount.toLong()
    }

    private fun drainOverflow(outBuff: ShortBuffer): Long {
        val overflowBuff = overflowBuffer.data!!
        val overflowLimit = overflowBuff.limit()
        val overflowSize = overflowBuff.remaining()

        val beginPresentationTimeUs = overflowBuffer.presentationTimeUs +
                sampleCountToDurationUs(overflowBuff.position(), inputSampleRate, outputChannelCount)

        outBuff.clear()
        overflowBuff.limit(outBuff.capacity())
        outBuff.put(overflowBuff)

        if (overflowSize >= outBuff.capacity()) {
            overflowBuff.clear().limit(0)
        } else {
            overflowBuff.limit(overflowLimit)
        }

        return beginPresentationTimeUs
    }

    private fun remixAndMaybeFillOverflow(input: AudioBuffer, outBuff: ShortBuffer): Long {
        val inBuff = input.data!!
        val overflowBuff = overflowBuffer.data!!

        outBuff.clear()
        inBuff.clear()

        if (inBuff.remaining() > outBuff.remaining()) {
            inBuff.limit(outBuff.capacity())
            outBuff.put(inBuff)

            inBuff.limit(inBuff.capacity())
            val consumedDurationUs = sampleCountToDurationUs(inBuff.position(), inputSampleRate, inputChannelCount)
            overflowBuff.put(inBuff)
            overflowBuff.flip()
            overflowBuffer.presentationTimeUs = input.presentationTimeUs + consumedDurationUs
        } else {
            outBuff.put(inBuff)
        }

        return input.presentationTimeUs
    }
}