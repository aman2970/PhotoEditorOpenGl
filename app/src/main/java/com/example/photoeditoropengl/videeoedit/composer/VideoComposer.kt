package com.example.photoeditoropengl.videeoedit.composer

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.opengl.EGLContext
import android.util.Size
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class VideoComposer(
    private val mediaExtractor: MediaExtractor,
    private val trackIndex: Int,
    private val outputFormat: MediaFormat,
    private val muxRender: MuxRender,
    private val timeScale: Float,
    trimStartMs: Long,
    trimEndMs: Long,
    logger: Logger
) {
    private val bufferInfo = MediaCodec.BufferInfo()
    private var decoder: MediaCodec? = null
    private var encoder: MediaCodec? = null
    private var actualOutputFormat: MediaFormat? = null
    private var decoderSurface: DecoderSurface? = null
    private var encoderSurface: EncoderSurface? = null
    private var isExtractorEOS = false
    private var isDecoderEOS = false
    var isFinished: Boolean = false
        private set
    private var decoderStarted = false
    private var encoderStarted = false
    private var writtenPresentationTimeUs: Long = 0
    private val trimStartUs = TimeUnit.MILLISECONDS.toMicros(trimStartMs)
    private val trimEndUs =
        if (trimEndMs == -1L) trimEndMs else TimeUnit.MILLISECONDS.toMicros(trimEndMs)
    private val logger: Logger = logger

    fun setUp(
        filter: GlFilter?,
        rotation: Rotation?,
        outputResolution: Size?,
        inputResolution: Size?,
        fillMode: FillMode?,
        fillModeCustomItem: FillModeCustomItem?,
        flipVertical: Boolean,
        flipHorizontal: Boolean,
        shareContext: EGLContext?
    ) {
        try {
            encoder = MediaCodec.createEncoderByType(outputFormat.getString(MediaFormat.KEY_MIME)!!)
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
        encoder!!.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoderSurface = EncoderSurface(encoder!!.createInputSurface(), shareContext)
        encoderSurface!!.makeCurrent()
        encoder!!.start()
        encoderStarted = true

        val inputFormat = mediaExtractor.getTrackFormat(trackIndex)
        mediaExtractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        if (inputFormat.containsKey("rotation-degrees")) {
            inputFormat.setInteger("rotation-degrees", 0)
        }
        decoderSurface = DecoderSurface(filter!!, logger)
        decoderSurface!!.setRotation(rotation!!)
        decoderSurface!!.setOutputResolution(outputResolution!!)
        decoderSurface!!.setInputResolution(inputResolution!!)
        decoderSurface!!.setFillMode(fillMode!!)
        decoderSurface!!.setFillModeCustomItem(fillModeCustomItem!!)
        decoderSurface!!.setFlipHorizontal(flipHorizontal)
        decoderSurface!!.setFlipVertical(flipVertical)
        decoderSurface!!.completeParams()

        try {
            decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
        decoder!!.configure(inputFormat, decoderSurface!!.getSurface(), null, 0)
        decoder!!.start()
        decoderStarted = true
    }

    fun stepPipeline(): Boolean {
        var busy = false

        var status: Int
        while (drainEncoder() != DRAIN_STATE_NONE) {
            busy = true
        }
        do {
            status = drainDecoder()
            if (status != DRAIN_STATE_NONE) {
                busy = true
            }
        } while (status == DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY)
        while (drainExtractor() != DRAIN_STATE_NONE) {
            busy = true
        }

        return busy
    }

    fun getWrittenPresentationTimeUs(): Long {
        return (writtenPresentationTimeUs * timeScale).toLong()
    }

    fun release() {
        if (decoderSurface != null) {
            decoderSurface!!.release()
            decoderSurface = null
        }
        if (encoderSurface != null) {
            encoderSurface!!.release()
            encoderSurface = null
        }
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

    private fun drainExtractor(): Int {
        if (isExtractorEOS) return DRAIN_STATE_NONE
        val trackIndex = mediaExtractor.sampleTrackIndex
        logger.debug(TAG, "drainExtractor trackIndex:$trackIndex")
        if (trackIndex >= 0 && trackIndex != this.trackIndex) {
            return DRAIN_STATE_NONE
        }
        val result = decoder!!.dequeueInputBuffer(0)
        if (result < 0) return DRAIN_STATE_NONE
        if (trackIndex < 0 || (writtenPresentationTimeUs >= trimEndUs && trimEndUs != -1L)) {
            isExtractorEOS = true
            decoder!!.queueInputBuffer(result, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            mediaExtractor.unselectTrack(this.trackIndex)
            return DRAIN_STATE_NONE
        }
        val sampleSizeCompat = mediaExtractor.readSampleData(decoder!!.getInputBuffer(result)!!, 0)
        val isKeyFrame = (mediaExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
        decoder!!.queueInputBuffer(
            result,
            0,
            sampleSizeCompat,
            (mediaExtractor.sampleTime / timeScale).toLong(),
            if (isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        )
        mediaExtractor.advance()
        return DRAIN_STATE_CONSUMED
    }

    private fun drainDecoder(): Int {
        if (isDecoderEOS) return DRAIN_STATE_NONE
        val result = decoder!!.dequeueOutputBuffer(bufferInfo, 0)
        when (result) {
            MediaCodec.INFO_TRY_AGAIN_LATER -> return DRAIN_STATE_NONE
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
        }
        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            encoder!!.signalEndOfInputStream()
            isDecoderEOS = true
            bufferInfo.size = 0
        }
        val doRender =
            (bufferInfo.size > 0 && bufferInfo.presentationTimeUs >= trimStartUs && (bufferInfo.presentationTimeUs <= trimEndUs || trimEndUs == -1L))
        decoder!!.releaseOutputBuffer(result, doRender)
        if (doRender) {
            decoderSurface!!.awaitNewImage()
            decoderSurface!!.drawImage()
            encoderSurface!!.setPresentationTime(bufferInfo.presentationTimeUs * 1000)
            encoderSurface!!.swapBuffers()
        } else if (bufferInfo.presentationTimeUs != 0L) {
            writtenPresentationTimeUs = bufferInfo.presentationTimeUs
        }
        return DRAIN_STATE_CONSUMED
    }

    private fun drainEncoder(): Int {
        if (isFinished) return DRAIN_STATE_NONE
        val result = encoder!!.dequeueOutputBuffer(bufferInfo, 0)
        when (result) {
            MediaCodec.INFO_TRY_AGAIN_LATER -> return DRAIN_STATE_NONE
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                if (actualOutputFormat != null) {
                    throw RuntimeException("Video output format changed twice.")
                }
                actualOutputFormat = encoder!!.outputFormat
                muxRender.setOutputFormat(SampleType.VIDEO, actualOutputFormat)
                muxRender.onSetOutputFormat()
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
            encoder!!.releaseOutputBuffer(result, false)
            return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
        }
        muxRender.writeSampleData(
            SampleType.VIDEO,
            encoder!!.getOutputBuffer(result)!!, bufferInfo
        )
        writtenPresentationTimeUs = bufferInfo.presentationTimeUs
        encoder!!.releaseOutputBuffer(result, false)
        return DRAIN_STATE_CONSUMED
    }

    companion object {
        private const val TAG = "VideoComposer"
        private const val DRAIN_STATE_NONE = 0
        private const val DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY = 1
        private const val DRAIN_STATE_CONSUMED = 2
    }
}
