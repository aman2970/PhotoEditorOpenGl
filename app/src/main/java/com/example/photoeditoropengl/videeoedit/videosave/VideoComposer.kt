package com.example.photoeditoropengl.videeoedit.videosave

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Size
import com.example.photoeditoropengl.videeoedit.helper.GlFilter
import java.io.IOException
import java.nio.ByteBuffer

internal class VideoComposer(
    private val mediaExtractor: MediaExtractor,
    private val trackIndex: Int,
    private val outputFormat: MediaFormat,
    private val muxRender: MuxRender,
    private val timeScale: Int
) {
    private val bufferInfo = MediaCodec.BufferInfo()
    private var decoder: MediaCodec? = null
    private var encoder: MediaCodec? = null
    private lateinit var decoderInputBuffers: Array<ByteBuffer>
    private lateinit var encoderOutputBuffers: Array<ByteBuffer>
    private var actualOutputFormat: MediaFormat? = null
    private var decoderSurface: DecoderSurface? = null
    private var encoderSurface: EncoderSurface? = null
    private var isExtractorEOS = false
    private var isDecoderEOS = false
    var isFinished: Boolean = false
        private set
    private var decoderStarted = false
    private var encoderStarted = false
    var writtenPresentationTimeUs: Long = 0
        private set


    fun setUp(
        filter: GlFilter?,
        rotation: Rotation?,
        outputResolution: Size?,
        inputResolution: Size?,
        fillMode: FillMode?,
        fillModeItem: FillModeItem?,
        flipVertical: Boolean,
        flipHorizontal: Boolean
    ) {
        mediaExtractor.selectTrack(trackIndex)
        try {
            encoder = MediaCodec.createEncoderByType(outputFormat.getString(MediaFormat.KEY_MIME)!!)
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
        encoder!!.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoderSurface = EncoderSurface(encoder!!.createInputSurface())
        encoderSurface!!.makeCurrent()
        encoder!!.start()
        encoderStarted = true
        encoderOutputBuffers = encoder!!.outputBuffers

        val inputFormat = mediaExtractor.getTrackFormat(trackIndex)
        if (inputFormat.containsKey("rotation-degrees")) {
            inputFormat.setInteger("rotation-degrees", 0)
        }
        decoderSurface = DecoderSurface(filter)
        decoderSurface!!.setRotation(rotation!!)
        decoderSurface!!.setOutputResolution(outputResolution!!)
        decoderSurface!!.setInputResolution(inputResolution!!)
        decoderSurface!!.setFillMode(fillMode!!)
        decoderSurface!!.setFillModeCustomItem(fillModeItem)
        decoderSurface!!.setFlipHorizontal(flipHorizontal)
        decoderSurface!!.setFlipVertical(flipVertical)
        decoderSurface!!.completeParams()

        try {
            decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
        decoder!!.configure(inputFormat, decoderSurface!!.surface, null, 0)
        decoder!!.start()
        decoderStarted = true
        decoderInputBuffers = decoder!!.inputBuffers
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
        if (trackIndex >= 0 && trackIndex != this.trackIndex) {
            return DRAIN_STATE_NONE
        }
        val result = decoder!!.dequeueInputBuffer(0)
        if (result < 0) return DRAIN_STATE_NONE
        if (trackIndex < 0) {
            isExtractorEOS = true
            decoder!!.queueInputBuffer(result, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return DRAIN_STATE_NONE
        }
        val sampleSize = mediaExtractor.readSampleData(decoderInputBuffers[result], 0)
        val isKeyFrame = (mediaExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
        decoder!!.queueInputBuffer(
            result,
            0,
            sampleSize,
            mediaExtractor.sampleTime / timeScale,
            if (isKeyFrame) MediaCodec.BUFFER_FLAG_SYNC_FRAME else 0
        )
        mediaExtractor.advance()
        return DRAIN_STATE_CONSUMED
    }

    private fun drainDecoder(): Int {
        if (isDecoderEOS) return DRAIN_STATE_NONE
        val result = decoder!!.dequeueOutputBuffer(bufferInfo, 0)
        when (result) {
            MediaCodec.INFO_TRY_AGAIN_LATER -> return DRAIN_STATE_NONE
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED, MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
        }
        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            encoder!!.signalEndOfInputStream()
            isDecoderEOS = true
            bufferInfo.size = 0
        }
        val doRender = (bufferInfo.size > 0)
        decoder!!.releaseOutputBuffer(result, doRender)
        if (doRender) {
            decoderSurface!!.awaitNewImage()
            decoderSurface!!.drawImage()
            encoderSurface!!.setPresentationTime(bufferInfo.presentationTimeUs * 1000)
            encoderSurface!!.swapBuffers()
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
                muxRender.setOutputFormat(MuxRender.SampleType.VIDEO, actualOutputFormat!!)
                muxRender.onSetOutputFormat()
                return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
            }

            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                encoderOutputBuffers = encoder!!.outputBuffers
                return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY
            }
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
            MuxRender.SampleType.VIDEO,
            encoderOutputBuffers[result], bufferInfo
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
