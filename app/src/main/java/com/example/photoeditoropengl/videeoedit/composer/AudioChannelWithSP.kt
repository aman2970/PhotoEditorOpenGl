package com.example.photoeditoropengl.videeoedit.composer

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

class AudioChannelWithSP internal constructor(
    decoder: MediaCodec?,
    encoder: MediaCodec?,
    encodeFormat: MediaFormat?,
    timeScale: Float, // if true the scale will impact in speed with pitch
    private val isAffectInPitch: Boolean
) : BaseAudioChannel(decoder, encoder, encodeFormat) {
    private var stream: SonicAudioProcessor? =
        null // SonicAudioProcessor can deal with stereo Audio
    private var timeScale = 1f
    var isEOF: Boolean = false
    private val BUFFER_CAPACITY = 2048 // in ShortBuffer size
    private var totalDataAdded: Long = 0
    private var pendingDecoderOutputBuffIndx = -1
    private var tempInputBuffer: ByteBuffer? = null
    private var isPendingFeeding = true

    init {
        this.timeScale = timeScale
    }

    override fun setActualDecodedFunFormat(decodedFormat: MediaFormat?) {
        super.setActualDecodedFunFormat(decodedFormat)

        if (inputChannelCount > 2) {
            throw UnsupportedOperationException("Input channel count ($inputChannelCount) not supported.")
        }
        stream = SonicAudioProcessor(inputSampleRate, outputChannelCount)
        isEOF = false
        totalDataAdded = 0
        isPendingFeeding = true
        tempInputBuffer =
            ByteBuffer.allocateDirect(BUFFER_CAPACITY * 16).order(ByteOrder.nativeOrder())

        if (isAffectInPitch) {
            stream!!.setRate(timeScale)
        } else {
            stream!!.speed = timeScale
        }
    }

    override fun sampleCountToDurationUs(
        sampleCount: Long,
        sampleRate: Int,
        channelCount: Int
    ): Long {
        //considered short buffer as data
        return ((MICROSECS_PER_SEC * (sampleCount * 1f) / (sampleRate * 1f * channelCount)).toLong())
    }

    public override fun drainDecoderBufferAndQueue(bufferIndex: Int, presentationTimeUs: Long) {
        if (actualDecodedFormat == null) {
            throw RuntimeException("Buffer received before format!")
        }

        val data: ByteBuffer? =
            if (bufferIndex == BUFFER_INDEX_END_OF_STREAM) null else decoder?.getOutputBuffer(
                bufferIndex
            )

        if (data != null) {
            writeToSonicSteam(data.asShortBuffer())
            pendingDecoderOutputBuffIndx = bufferIndex
            isEOF = false
            decoder!!.releaseOutputBuffer(bufferIndex, false)
        } else {
            stream!!.flushStream()
            isEOF = true
        }
    }

    public override fun feedEncoder(timeoutUs: Long): Boolean {
        if (stream == null || !isPendingFeeding || (!isEOF && stream!!.samplesAvailable() === 0)) {
            //no data available

            updatePendingDecoderStatus()

            return false
        } else if ((!isEOF && timeScale < 1f && stream!!.samplesAvailable() > 0) && (stream!!.samplesAvailable() * outputChannelCount) < BUFFER_CAPACITY) {
            //few data remaining in stream wait for next stream data
            updatePendingDecoderStatus()

            return false
        }

        val encoderInBuffIndex: Int = encoder!!.dequeueInputBuffer(timeoutUs)

        if (encoderInBuffIndex < 0) {
            // Encoder is full - Bail out
            return false
        }

        var status = false
        status = if (timeScale < 1f) {
            slowTimeBufferProcess(encoderInBuffIndex)
        } else {
            FastOrNormalTimeBufferProcess(encoderInBuffIndex)
        }

        return status
    }

    private fun updatePendingDecoderStatus() {
        if (pendingDecoderOutputBuffIndx != -1) {
            pendingDecoderOutputBuffIndx = -1
        }
    }

    private fun FastOrNormalTimeBufferProcess(encoderInBuffIndex: Int): Boolean {
        val samplesNum: Int = stream!!.samplesAvailable()

        val status = false

        val rawDataLen: Int = samplesNum * outputChannelCount

        return if (rawDataLen >= BUFFER_CAPACITY) {
            readStreamDataAndQueueToEncoder(BUFFER_CAPACITY, encoderInBuffIndex)
        } else if (rawDataLen > 0 && rawDataLen < BUFFER_CAPACITY) {
            readStreamDataAndQueueToEncoder(rawDataLen, encoderInBuffIndex)
        } else if (isEOF && samplesNum == 0) {
            finalizeEncoderQueue(encoderInBuffIndex)
        } else {
            status
        }
    }

    private fun slowTimeBufferProcess(encoderInBuffIndex: Int): Boolean {
        val samplesNum: Int = stream!!.samplesAvailable()

        val status = false

        val rawDataLen: Int = samplesNum * outputChannelCount

        return if (rawDataLen >= BUFFER_CAPACITY) {
            readStreamDataAndQueueToEncoder(BUFFER_CAPACITY, encoderInBuffIndex)
        } else if (isEOF && (rawDataLen > 0 && rawDataLen < BUFFER_CAPACITY)) {
            readStreamDataAndQueueToEncoder(rawDataLen, encoderInBuffIndex)
        } else if (isEOF && rawDataLen == 0) {
            finalizeEncoderQueue(encoderInBuffIndex)
        } else {
            status
        }
    }

    private fun finalizeEncoderQueue(encoderInBuffIndex: Int): Boolean {
        isPendingFeeding = false
        return queueInputBufferInEncoder(null, encoderInBuffIndex)
    }

    private fun readStreamDataAndQueueToEncoder(capacity: Int, encoderInBuffIndex: Int): Boolean {
        val rawData = ShortArray(capacity)
        stream?.readShortFromStream(rawData, (capacity / outputChannelCount))
        return queueInputBufferInEncoder(rawData, encoderInBuffIndex)
    }

    private fun queueInputBufferInEncoder(rawData: ShortArray?, encoderInBuffIndex: Int): Boolean {
        val outBuffer: ShortBuffer = encoder!!.getInputBuffer(encoderInBuffIndex)!!.asShortBuffer()

        outBuffer.clear()
        if (rawData != null) {
            outBuffer.put(rawData)
            totalDataAdded += rawData.size.toLong()

            val presentationTimeUs =
                sampleCountToDurationUs(totalDataAdded, inputSampleRate, outputChannelCount)

            encoder.queueInputBuffer(
                encoderInBuffIndex, 0, rawData.size * BYTES_PER_SHORT,
                presentationTimeUs, 0
            )
            return false
        } else {
            encoder.queueInputBuffer(
                encoderInBuffIndex,
                0,
                0,
                0,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
            return false
        }
    }

    private fun writeToSonicSteam(data: ShortBuffer) {
        val temBuff = ShortArray(data.capacity())
        data[temBuff]
        data.rewind()
        stream!!.writeShortToStream(temBuff, temBuff.size / outputChannelCount)
    }

    val isAnyPendingBuffIndex: Boolean
        get() =// allow to decoder to send data into stream (e.i. sonicprocessor)
            if (pendingDecoderOutputBuffIndx != -1) {
                true
            } else {
                false
            }

    companion object {
        private const val TAG = "AUDIO_CHANNEL_WITH_SONIC"
    }
}
