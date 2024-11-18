package com.example.photoeditoropengl.videeoedit.composer


import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.IOException
import java.util.concurrent.TimeUnit


internal class RemixAudioComposer(
    private val extractor: MediaExtractor,
    private val trackIndex: Int,
    private val outputFormat: MediaFormat,
    private val muxer: MuxRender,
    private val timeScale: Float,
    private val isPitchChanged: Boolean,
    trimStartMs: Long,
    trimEndMs: Long
) :
    IAudioComposer {
    override var writtenPresentationTimeUs: Long = 0

    private val bufferInfo = MediaCodec.BufferInfo()
    private var decoder: MediaCodec? = null
    private var encoder: MediaCodec? = null
    private var actualOutputFormat: MediaFormat? = null

    private var isExtractorEOS = false
    private var isDecoderEOS = false
    override var isFinished: Boolean = false
        private set
    private var decoderStarted = false
    private var encoderStarted = false

    private var audioChannel: AudioChannelWithSP? = null

    private val trimStartUs = TimeUnit.MILLISECONDS.toMicros(trimStartMs)
    private val trimEndUs =
        if (trimEndMs == -1L) trimEndMs else TimeUnit.MILLISECONDS.toMicros(trimEndMs)
    var numTracks: Int = 0

    // Used for AAC priming offset.
    private var addPrimingDelay = false
    private val frameCounter = 0
    private val primingDelay: Long = 0

    override fun setup() {
        extractor.selectTrack(trackIndex)
        try {
            encoder = MediaCodec.createEncoderByType(outputFormat.getString(MediaFormat.KEY_MIME)!!)
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
        encoder!!.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder!!.start()
        encoderStarted = true

        val inputFormat = extractor.getTrackFormat(trackIndex)
        try {
            decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
        decoder!!.configure(inputFormat, null, null, 0)
        decoder!!.start()
        decoderStarted = true

        audioChannel = AudioChannelWithSP(decoder, encoder, outputFormat, timeScale, isPitchChanged)
    }

    override fun stepPipeline(): Boolean {
        var busy = false

        var status: Int

        while (drainEncoder(0) != DRAIN_STATE_NONE) busy = true
        do {
            if (audioChannel!!.isAnyPendingBuffIndex) {
                break
            }
            status = drainDecoder(0)
            if (status != DRAIN_STATE_NONE) busy = true
            // NOTE: not repeating to keep from deadlock when encoder is full.
        } while (status == DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY)

        while (audioChannel!!.feedEncoder(0)) busy = true
        while (drainExtractor(0) != DRAIN_STATE_NONE) busy = true

        return busy
    }

    private fun drainExtractor(timeoutUs: Long): Int {
        if (isExtractorEOS) return DRAIN_STATE_NONE
        val trackIndex = extractor.sampleTrackIndex
        if (trackIndex >= 0 && trackIndex != this.trackIndex) {
            return DRAIN_STATE_NONE
        }

        val result = decoder!!.dequeueInputBuffer(timeoutUs)
        if (result < 0) return DRAIN_STATE_NONE
        if (trackIndex < 0 || (writtenPresentationTimeUs >= trimEndUs && trimEndUs != -1L)) {
            isExtractorEOS = true
            decoder!!.queueInputBuffer(result, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            extractor.unselectTrack(this.trackIndex)
            return DRAIN_STATE_NONE
        }

        val sampleSize = extractor.readSampleData(decoder!!.getInputBuffer(result)!!, 0)
        val isKeyFrame = (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
        decoder!!.queueInputBuffer(
            result,
            0,
            sampleSize,
            extractor.sampleTime,
            if (isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        )
        extractor.advance()
        numTracks++
        return DRAIN_STATE_CONSUMED
    }

    private fun drainDecoder(timeoutUs: Long): Int {
        if (isDecoderEOS) return DRAIN_STATE_NONE

        val result = decoder!!.dequeueOutputBuffer(bufferInfo, timeoutUs)
        when (result) {
            MediaCodec.INFO_TRY_AGAIN_LATER -> return DRAIN_STATE_NONE
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                audioChannel!!.setActualDecodedFunFormat(decoder!!.outputFormat)
                return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
            }

            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
        }
        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            isDecoderEOS = true
            audioChannel!!.drainDecoderBufferAndQueue(BaseAudioChannel.BUFFER_INDEX_END_OF_STREAM, 0)
        } else if (bufferInfo.size > 0) {
            audioChannel!!.drainDecoderBufferAndQueue(result, bufferInfo.presentationTimeUs)
        }

        return DRAIN_STATE_CONSUMED
    }

    private fun drainEncoder(timeoutUs: Long): Int {
        if (isFinished) return DRAIN_STATE_NONE
        val result = encoder!!.dequeueOutputBuffer(bufferInfo, timeoutUs)
        when (result) {
            MediaCodec.INFO_TRY_AGAIN_LATER -> return DRAIN_STATE_NONE
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                if (actualOutputFormat != null) {
                    throw RuntimeException("Audio output format changed twice.")
                }
                actualOutputFormat = encoder!!.outputFormat
                addPrimingDelay =
                    MediaFormat.MIMETYPE_AUDIO_AAC == actualOutputFormat!!.getString(MediaFormat.KEY_MIME)
                muxer.setOutputFormat(SAMPLE_TYPE, actualOutputFormat)
                return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
            }

            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
        }
        if (actualOutputFormat == null) {
            throw RuntimeException("Could not determine actual output format.")
        }

        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            isFinished = true

            bufferInfo[0, 0, 0] = bufferInfo.flags
        }
        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            // SPS or PPS, which should be passed by MediaFormat.
            encoder!!.releaseOutputBuffer(result, false)
            return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
        }
        muxer.writeSampleData(
            SAMPLE_TYPE,
            encoder!!.getOutputBuffer(result)!!, bufferInfo
        )

        writtenPresentationTimeUs = bufferInfo.presentationTimeUs
        encoder!!.releaseOutputBuffer(result, false)
        return DRAIN_STATE_CONSUMED
    }

    fun getWrittenPresentationTimeUss(): Long {
        return (writtenPresentationTimeUs * timeScale).toLong()
    }

    override fun release() {
        if (decoder != null) {
            if (decoderStarted) decoder!!.stop()
            decoder!!.release()
            decoder = null
        }
        if (encoder != null) {
            if (encoderStarted) encoder!!.stop()
            encoder!!.release()
            encoder = null
        }
    }

    companion object {
        private val SAMPLE_TYPE: SampleType = SampleType.AUDIO

        private const val DRAIN_STATE_NONE = 0
        private const val DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY = 1
        private const val DRAIN_STATE_CONSUMED = 2
    }
}
