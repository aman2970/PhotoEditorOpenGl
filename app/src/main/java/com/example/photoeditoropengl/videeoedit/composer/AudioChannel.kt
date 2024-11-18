package com.example.photoeditoropengl.videeoedit.composer

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

internal class AudioChannel(
    decoder: MediaCodec?,
    encoder: MediaCodec?, encodeFormat: MediaFormat?
) : BaseAudioChannel(decoder!!, encoder!!, encodeFormat!!) {

    override fun drainDecoderBufferAndQueue(bufferIndex: Int, presentationTimeUs: Long) {
        if (actualDecodedFormat == null) {
            throw RuntimeException("Buffer received before format!")
        }

        val data: ByteBuffer? = if (bufferIndex == BUFFER_INDEX_END_OF_STREAM) {
            null
        } else {
            decoder?.getOutputBuffer(bufferIndex)
        }

        val buffer = emptyBuffers.poll() ?: AudioBuffer()

        buffer.bufferIndex = bufferIndex
        buffer.presentationTimeUs = presentationTimeUs
        buffer.data = data?.asShortBuffer()

        if (overflowBuffer.data == null) {
            overflowBuffer.data = ByteBuffer
                .allocateDirect(data?.capacity() ?: 0)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
            overflowBuffer.data!!.clear().flip()
        }

        filledBuffers.add(buffer)
    }

    override fun feedEncoder(timeoutUs: Long): Boolean {
        val hasOverflow = overflowBuffer.data != null && overflowBuffer.data!!.hasRemaining()
        if (filledBuffers.isEmpty() && !hasOverflow) {
            // No audio data - Bail out
            return false
        }

        val encoderInBuffIndex: Int = encoder!!.dequeueInputBuffer(timeoutUs)
        if (encoderInBuffIndex < 0) {
            // Encoder is full - Bail out
            return false
        }

        // Drain overflow first
        val outBuffer: ShortBuffer = encoder!!.getInputBuffer(encoderInBuffIndex)!!.asShortBuffer()
        if (hasOverflow) {
            val presentationTimeUs = drainOverflow(outBuffer)
            encoder.queueInputBuffer(
                encoderInBuffIndex,
                0, outBuffer.position() * BYTES_PER_SHORT,
                presentationTimeUs, 0
            )
            return true
        }

        val inBuffer: AudioBuffer = filledBuffers.poll()
        if (inBuffer.bufferIndex === BUFFER_INDEX_END_OF_STREAM) {
            encoder.queueInputBuffer(
                encoderInBuffIndex,
                0,
                0,
                0,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
            return false
        }

        val presentationTimeUs = remixAndMaybeFillOverflow(inBuffer, outBuffer)
        encoder.queueInputBuffer(
            encoderInBuffIndex,
            0, outBuffer.position() * BYTES_PER_SHORT,
            presentationTimeUs, 0
        )
        if (inBuffer != null) {
            decoder?.releaseOutputBuffer(inBuffer.bufferIndex, false)
            emptyBuffers.add(inBuffer)
        }

        return true
    }

    override fun sampleCountToDurationUs(
        sampleCount: Long,
        sampleRate: Int,
        channelCount: Int
    ): Long {
        return (sampleCount / (sampleRate * MICROSECS_PER_SEC)) / channelCount
    }

    private fun drainOverflow(outBuff: ShortBuffer): Long {
        val overflowBuff: ShortBuffer = overflowBuffer.data!!
        val overflowLimit = overflowBuff.limit()
        val overflowSize = overflowBuff.remaining()

        val beginPresentationTimeUs: Long = overflowBuffer.presentationTimeUs +
                sampleCountToDurationUs(
                    overflowBuff.position().toLong(),
                    inputSampleRate,
                    outputChannelCount
                )

        outBuff.clear()
        // Limit overflowBuff to outBuff's capacity
        overflowBuff.limit(outBuff.capacity())
        // Load overflowBuff onto outBuff
        outBuff.put(overflowBuff)

        if (overflowSize >= outBuff.capacity()) {
            // Overflow fully consumed - Reset
            overflowBuff.clear().limit(0)
        } else {
            // Only partially consumed - Keep position & restore previous limit
            overflowBuff.limit(overflowLimit)
        }

        return beginPresentationTimeUs
    }


    private fun remixAndMaybeFillOverflow(
        input: AudioBuffer?,
        outBuff: ShortBuffer
    ): Long {
        val inBuff: ShortBuffer = input?.data!!
        val overflowBuff: ShortBuffer = overflowBuffer.data!!

        outBuff.clear()

        // Reset position to 0, and set limit to capacity (Since MediaCodec doesn't do that for us)
        inBuff.clear()

        if (inBuff.remaining() > outBuff.remaining()) {
            // Overflow
            // Limit inBuff to outBuff's capacity
            inBuff.limit(outBuff.capacity())
            outBuff.put(inBuff)

            // Reset limit to its own capacity & Keep position
            inBuff.limit(inBuff.capacity())

            // Remix the rest onto overflowBuffer
            // NOTE: We should only reach this point when overflow buffer is empty
            val consumedDurationUs =
                sampleCountToDurationUs(
                    inBuff.position().toLong(),
                    inputSampleRate,
                    inputChannelCount
                )
            overflowBuff.put(inBuff)

            // Seal off overflowBuff & mark limit
            overflowBuff.flip()
            overflowBuffer.presentationTimeUs = input.presentationTimeUs + consumedDurationUs
        } else {
            // No overflow
            outBuff.put(inBuff)
        }
        return input.presentationTimeUs
    }
}

