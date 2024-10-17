package com.example.photoeditoropengl.videeoedit.helper

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer

import java.io.IOException
import java.nio.ByteBuffer

class VideoEncoder(
    outputFilePath: String,
    width: Int,
    height: Int,
    bitrate: Int
) {
    companion object {
        private const val MIME_TYPE = "video/avc"
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL = 5
    }

    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var inputBuffers: Array<ByteBuffer>? = null
    private var outputBuffers: Array<ByteBuffer>? = null
    private var bufferInfo: MediaCodec.BufferInfo = MediaCodec.BufferInfo()
    private var isRecording = false

    init {
        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            encoder = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }

            muxer = MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: IOException) {
            throw RuntimeException("Failed to initialize video encoder", e)
        }
    }

    fun start() {
        encoder?.start()
        isRecording = true
        inputBuffers = encoder?.inputBuffers
        outputBuffers = encoder?.outputBuffers
    }

    fun encodeFrame(pixelData: IntArray) {
        if (!isRecording) {
            throw IllegalStateException("Encoder not started. Call start() before encoding frames.")
        }

        val inputBufferIndex = encoder?.dequeueInputBuffer(-1) ?: -1
        if (inputBufferIndex >= 0) {
            val inputBuffer = inputBuffers?.get(inputBufferIndex)
            inputBuffer?.clear()

            // Convert pixelData (RGBA) to the correct format and fill the inputBuffer
            inputBuffer?.asIntBuffer()?.put(pixelData)

            val presentationTimeUs = System.nanoTime() / 1000
            encoder?.queueInputBuffer(inputBufferIndex, 0, inputBuffer?.remaining() ?: 0, presentationTimeUs, 0)
        }

        drainEncoder()
    }

    private fun drainEncoder() {
        while (true) {
            val outputBufferIndex = encoder?.dequeueOutputBuffer(bufferInfo, 0) ?: -1
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> outputBuffers = encoder?.outputBuffers
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = encoder?.outputFormat
                    trackIndex = muxer?.addTrack(newFormat!!) ?: -1
                    muxer?.start()
                    muxerStarted = true
                }
                outputBufferIndex >= 0 -> {
                    val outputBuffer = outputBuffers?.get(outputBufferIndex)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0 && muxerStarted) {
                        outputBuffer?.position(bufferInfo.offset)
                        outputBuffer?.limit(bufferInfo.offset + bufferInfo.size)

                        muxer?.writeSampleData(trackIndex, outputBuffer!!, bufferInfo)
                    }

                    encoder?.releaseOutputBuffer(outputBufferIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
            }
        }
    }

    fun stop() {
        if (encoder != null) {
            isRecording = false
            encoder?.signalEndOfInputStream()
            drainEncoder()
        }
    }

    fun release() {
        encoder?.stop()
        encoder?.release()
        encoder = null

        if (muxerStarted) {
            muxer?.stop()
        }
        muxer?.release()
        muxer = null
        isRecording = false
    }
}