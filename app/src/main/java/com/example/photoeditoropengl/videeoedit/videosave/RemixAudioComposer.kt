package com.example.photoeditoropengl.videeoedit.videosave

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.IOException
import java.nio.ByteBuffer

class RemixAudioComposer(
    private val extractor: MediaExtractor,
    private val trackIndex: Int,
    private val outputFormat: MediaFormat,
    private val muxer: MuxRender,
    private val timeScale: Int
) : IAudioComposer {

    companion object {
        private val SAMPLE_TYPE = MuxRender.SampleType.AUDIO
        private const val DRAIN_STATE_NONE = 0
        private const val DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY = 1
        private const val DRAIN_STATE_CONSUMED = 2
    }

    private var writtenPresentationTimeUs: Long = 0
    private var muxCount = 1

    private val bufferInfo = MediaCodec.BufferInfo()
    private lateinit var decoder: MediaCodec
    private lateinit var encoder: MediaCodec
    private lateinit var actualOutputFormat: MediaFormat

    private lateinit var decoderBuffers: MediaCodecBufferCompatWrapper
    private lateinit var encoderBuffers: MediaCodecBufferCompatWrapper

    private var isExtractorEOS = false
    private var isDecoderEOS = false
    private var isEncoderEOS = false
    private var decoderStarted = false
    private var encoderStarted = false

    private lateinit var audioChannel: AudioChannel

    override fun setup() {
        extractor.selectTrack(trackIndex)
        try {
            encoder = MediaCodec.createEncoderByType(outputFormat.getString(MediaFormat.KEY_MIME)!!)
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        encoderStarted = true
        encoderBuffers = MediaCodecBufferCompatWrapper(encoder)

        val inputFormat = extractor.getTrackFormat(trackIndex)
        try {
            decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
        decoder.configure(inputFormat, null, null, 0)
        decoder.start()
        decoderStarted = true
        decoderBuffers = MediaCodecBufferCompatWrapper(decoder)

        audioChannel = AudioChannel(decoder, encoder, outputFormat)
    }

    override fun stepPipeline(): Boolean {
        var busy = false

        while (drainEncoder(0) != DRAIN_STATE_NONE) busy = true
        do {
            val status = drainDecoder(0)
            if (status != DRAIN_STATE_NONE) busy = true
        } while (status == DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY)

        while (audioChannel.feedEncoder(0)) busy = true
        while (drainExtractor(0) != DRAIN_STATE_NONE) busy = true

        return busy
    }

    private fun drainExtractor(timeoutUs: Long): Int {
        if (isExtractorEOS) return DRAIN_STATE_NONE
        val trackIndex = extractor.sampleTrackIndex
        if (trackIndex >= 0 && trackIndex != this.trackIndex) {
            return DRAIN_STATE_NONE
        }

        val result = decoder.dequeueInputBuffer(timeoutUs)
        if (result < 0) return DRAIN_STATE_NONE
        if (trackIndex < 0) {
            isExtractorEOS = true
            decoder.queueInputBuffer(result, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return DRAIN_STATE_NONE
        }

        val sampleSize = extractor.readSampleData(decoderBuffers.getInputBuffer(result)!!, 0)
        val isKeyFrame = (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
        decoder.queueInputBuffer(result, 0, sampleSize, extractor.sampleTime, if (isKeyFrame) MediaCodec.BUFFER_FLAG_SYNC_FRAME else 0)
        extractor.advance()
        return DRAIN_STATE_CONSUMED
    }

    private fun drainDecoder(timeoutUs: Long): Int {
        if (isDecoderEOS) return DRAIN_STATE_NONE

        val result = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
        return when (result) {
            MediaCodec.INFO_TRY_AGAIN_LATER -> DRAIN_STATE_NONE
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                audioChannel.setActualDecodedFormat(decoder.outputFormat)
                DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
            }
            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
            else -> {
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isDecoderEOS = true
                    audioChannel.drainDecoderBufferAndQueue(AudioChannel.BUFFER_INDEX_END_OF_STREAM, 0)
                } else if (bufferInfo.size > 0) {
                    audioChannel.drainDecoderBufferAndQueue(result, bufferInfo.presentationTimeUs / timeScale)
                }
                DRAIN_STATE_CONSUMED
            }
        }
    }

    private fun drainEncoder(timeoutUs: Long): Int {
        if (isEncoderEOS) return DRAIN_STATE_NONE

        val result = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
        return when (result) {
            MediaCodec.INFO_TRY_AGAIN_LATER -> DRAIN_STATE_NONE
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                if (::actualOutputFormat.isInitialized) {
                    throw RuntimeException("Audio output format changed twice.")
                }
                actualOutputFormat = encoder.outputFormat
                muxer.setOutputFormat(SAMPLE_TYPE, actualOutputFormat)
                DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
            }
            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                encoderBuffers = MediaCodecBufferCompatWrapper(encoder)
                DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
            }
            else -> {
                if (!::actualOutputFormat.isInitialized) {
                    throw RuntimeException("Could not determine actual output format.")
                }

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isEncoderEOS = true
                    bufferInfo.set(0, 0, 0, bufferInfo.flags)
                }
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    encoder.releaseOutputBuffer(result, false)
                    return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
                }

                if (muxCount == 1) {
                    muxer.writeSampleData(SAMPLE_TYPE,
                        encoderBuffers.getOutputBuffer(result)!!, bufferInfo)
                }
                if (muxCount < timeScale) {
                    muxCount++
                } else {
                    muxCount = 1
                }

                writtenPresentationTimeUs = bufferInfo.presentationTimeUs
                encoder.releaseOutputBuffer(result, false)
                DRAIN_STATE_CONSUMED
            }
        }
    }

    override fun getWrittenPresentationTimeUs(): Long = writtenPresentationTimeUs

    override fun isFinished(): Boolean = isEncoderEOS

    override fun release() {
        decoder?.let {
            if (decoderStarted) it.stop()
            it.release()
        }
        encoder?.let {
            if (encoderStarted) it.stop()
            it.release()
        }
    }
}