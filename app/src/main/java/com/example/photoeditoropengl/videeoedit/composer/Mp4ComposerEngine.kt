package com.example.photoeditoropengl.videeoedit.composer

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.opengl.EGLContext
import android.os.Build
import android.util.Size
import java.io.FileDescriptor
import java.io.IOException
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min

internal class Mp4ComposerEngine(logger: Logger) {
    private var videoComposer: VideoComposer? = null
    private var audioComposer: IAudioComposer? = null
    private var mediaExtractor: MediaExtractor? = null
    private var mediaMuxer: MediaMuxer? = null
    private var progressCallback: ProgressCallback? = null
    private var durationUs: Long = 0
    private var mediaMetadataRetriever: MediaMetadataRetriever? = null

    @Volatile
    var isCanceled: Boolean = false
        private set
    private val logger: Logger = logger
    private var trimStartMs: Long = 0
    private var trimEndMs: Long = 0

    fun setProgressCallback(progressCallback: ProgressCallback?) {
        this.progressCallback = progressCallback
    }

    @Throws(IOException::class)
    fun compose(
        srcDataSource: DataSource,
        destSrc: String?,
        destFileDescriptor: FileDescriptor?,
        outputResolution: Size,
        filter: GlFilter?,
        bitrate: Int,
        mute: Boolean,
        rotation: Rotation?,
        inputResolution: Size?,
        fillMode: FillMode?,
        fillModeCustomItem: FillModeCustomItem?,
        timeScale: Float,
        isPitchChanged: Boolean,
        flipVertical: Boolean,
        flipHorizontal: Boolean,
        trimStartMs: Long,
        trimEndMs: Long,
        videoFormatMimeType: VideoFormatMimeType,
        shareContext: EGLContext?
    ) {
        try {
            mediaExtractor = MediaExtractor()
            mediaExtractor!!.setDataSource(srcDataSource.fileDescriptor!!)
            mediaMuxer = if (Build.VERSION.SDK_INT >= 26 && destSrc == null) {
                MediaMuxer(destFileDescriptor!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } else {
                MediaMuxer(destSrc!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }
            this.mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever!!.setDataSource(srcDataSource.fileDescriptor)

            this.trimStartMs = trimStartMs
            this.trimEndMs = trimEndMs
            durationUs = if (trimEndMs != -1L) {
                (trimEndMs - trimStartMs) * 1000
            } else {
                try {
                    mediaMetadataRetriever!!.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!
                        .toLong() * 1000
                } catch (e: NumberFormatException) {
                    -1
                }
            }

            logger.debug(TAG, "Duration (us): $durationUs")

            val muxRender: MuxRender = MuxRender(mediaMuxer!!, logger)

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            for (i in 0 until mediaExtractor!!.trackCount) {
                val mediaFormat = mediaExtractor!!.getTrackFormat(i)
                val mimeType = mediaFormat.getString(MediaFormat.KEY_MIME) ?: continue
                if (mimeType.startsWith(VIDEO_PREFIX)) {
                    videoTrackIndex = i
                } else if (mimeType.startsWith(AUDIO_PREFIX)) {
                    audioTrackIndex = i
                }
            }

            val actualVideoOutputFormat = createVideoOutputFormatWithAvailableEncoders(
                videoFormatMimeType,
                bitrate,
                outputResolution
            )
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                actualVideoOutputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            }

            // setup video composer
            videoComposer = VideoComposer(
                mediaExtractor!!,
                videoTrackIndex,
                actualVideoOutputFormat,
                muxRender,
                timeScale,
                trimStartMs,
                trimEndMs,
                logger
            )
            videoComposer!!.setUp(
                filter,
                rotation,
                outputResolution,
                inputResolution,
                fillMode,
                fillModeCustomItem,
                flipVertical,
                flipHorizontal,
                shareContext
            )
            mediaExtractor!!.selectTrack(videoTrackIndex)

            if (audioTrackIndex >= 0 && mediaMetadataRetriever!!.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
                ) != null && !mute
            ) {
                val inputMediaFormat = mediaExtractor!!.getTrackFormat(audioTrackIndex)
                val outputMediaFormat = createAudioOutputFormat(inputMediaFormat)

                if (timeScale >= 0.99 && timeScale <= 1.01 && outputMediaFormat == inputMediaFormat) {
                    audioComposer = AudioComposer(
                        mediaExtractor!!,
                        audioTrackIndex,
                        muxRender,
                        trimStartMs,
                        trimEndMs,
                        logger
                    )
                } else {
                    audioComposer = RemixAudioComposer(
                        mediaExtractor!!,
                        audioTrackIndex,
                        outputMediaFormat,
                        muxRender,
                        timeScale,
                        isPitchChanged,
                        trimStartMs,
                        trimEndMs
                    )
                }

                audioComposer!!.setup()
                mediaExtractor!!.selectTrack(audioTrackIndex)
                mediaExtractor!!.seekTo(trimStartMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                runPipelines()
            } else {
                mediaExtractor!!.seekTo(trimStartMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                runPipelinesNoAudio()
            }

            mediaMuxer!!.stop()
        } finally {
            try {
                if (videoComposer != null) {
                    videoComposer!!.release()
                    videoComposer = null
                }
                if (audioComposer != null) {
                    audioComposer!!.release()
                    audioComposer = null
                }
                if (mediaExtractor != null) {
                    mediaExtractor!!.release()
                    mediaExtractor = null
                }
            } catch (e: RuntimeException) {
                logger.error(
                    TAG,
                    "Could not shutdown mediaExtractor, codecs and mediaMuxer pipeline.",
                    e
                )
            }
            try {
                if (mediaMuxer != null) {
                    mediaMuxer!!.release()
                    mediaMuxer = null
                }
            } catch (e: RuntimeException) {
                logger.error(TAG, "Failed to release mediaMuxer.", e)
            }
            try {
                if (mediaMetadataRetriever != null) {
                    mediaMetadataRetriever!!.release()
                    mediaMetadataRetriever = null
                }
            } catch (e: RuntimeException) {
                logger.error(TAG, "Failed to release mediaMetadataRetriever.", e)
            }
        }
    }

    fun cancel() {
        isCanceled = true
    }

    private fun runPipelines() {
        var loopCount: Long = 0
        if (durationUs <= 0) {
            if (progressCallback != null) {
                progressCallback!!.onProgress(PROGRESS_UNKNOWN)
            } // unknown
        }
        while (!isCanceled && !(videoComposer!!.isFinished && audioComposer!!.isFinished)) {
            val stepped = (videoComposer!!.stepPipeline()
                    || audioComposer!!.stepPipeline())
            loopCount++
            if (durationUs > 0 && loopCount % PROGRESS_INTERVAL_STEPS == 0L) {
                val writtenPresentationVideoTimeUs: Long =
                    videoComposer!!.getWrittenPresentationTimeUs()
                if (progressCallback != null) {
                    progressCallback!!.onCurrentWrittenVideoTime(writtenPresentationVideoTimeUs)
                }
                val videoProgress = if (videoComposer!!.isFinished) 1.0 else min(
                    1.0,
                    getWrittenPresentationTimeUs(writtenPresentationVideoTimeUs).toDouble() / durationUs
                )
                val audioProgress = if (audioComposer!!.isFinished) 1.0 else min(
                    1.0,
                    getWrittenPresentationTimeUs(audioComposer!!.writtenPresentationTimeUs).toDouble() / durationUs
                )
                val progress = (videoProgress + audioProgress) / 2.0
                if (progressCallback != null) {
                    progressCallback!!.onProgress(progress)
                }
            }
            if (!stepped) {
                try {
                    Thread.sleep(SLEEP_TO_WAIT_TRACK_TRANSCODERS)
                } catch (e: InterruptedException) {
                    // nothing to do
                }
            }
        }
    }

    private fun getWrittenPresentationTimeUs(time: Long): Long {
        return max(0.0, (time - trimStartMs * 1000).toDouble()).toLong()
    }

    private fun runPipelinesNoAudio() {
        var loopCount: Long = 0
        if (durationUs <= 0) {
            if (progressCallback != null) {
                progressCallback!!.onProgress(PROGRESS_UNKNOWN)
            } // unknown
        }
        while (!isCanceled && !videoComposer?.isFinished!!) {
            val stepped: Boolean = videoComposer!!.stepPipeline()
            loopCount++
            if (durationUs > 0 && loopCount % PROGRESS_INTERVAL_STEPS == 0L) {
                val writtenPresentationVideoTimeUs: Long =
                    videoComposer!!.getWrittenPresentationTimeUs()
                if (progressCallback != null) {
                    progressCallback!!.onCurrentWrittenVideoTime(writtenPresentationVideoTimeUs)
                }
                val videoProgress = if (videoComposer!!.isFinished) 1.0 else min(
                    1.0,
                    getWrittenPresentationTimeUs(writtenPresentationVideoTimeUs).toDouble() / durationUs
                )
                if (progressCallback != null) {
                    progressCallback!!.onProgress(videoProgress)
                }
            }
            if (!stepped) {
                try {
                    Thread.sleep(SLEEP_TO_WAIT_TRACK_TRANSCODERS)
                } catch (e: InterruptedException) {
                    // nothing to do
                }
            }
        }
    }


    internal interface ProgressCallback {
        fun onProgress(progress: Double)

        fun onCurrentWrittenVideoTime(timeUs: Long)
    }

    companion object {
        private const val TAG = "Mp4ComposerEngine"
        private const val AUDIO_PREFIX = "audio/"
        private const val VIDEO_PREFIX = "video/"
        private const val PROGRESS_UNKNOWN = -1.0
        private const val SLEEP_TO_WAIT_TRACK_TRANSCODERS: Long = 10
        private const val PROGRESS_INTERVAL_STEPS: Long = 10
        private fun createVideoOutputFormatWithAvailableEncoders(
            mimeType: VideoFormatMimeType,
            bitrate: Int,
            outputResolution: Size
        ): MediaFormat {
            val mediaCodecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)

            if (mimeType !== VideoFormatMimeType.AUTO) {
                val mediaFormat = createVideoFormat(mimeType.format, bitrate, outputResolution)
                if (mediaCodecList.findEncoderForFormat(mediaFormat) != null) {
                    return mediaFormat
                }
            }

            val hevcMediaFormat =
                createVideoFormat(VideoFormatMimeType.HEVC.format, bitrate, outputResolution)
            if (mediaCodecList.findEncoderForFormat(hevcMediaFormat) != null) {
                return hevcMediaFormat
            }

            val avcMediaFormat =
                createVideoFormat(VideoFormatMimeType.AVC.format, bitrate, outputResolution)
            if (mediaCodecList.findEncoderForFormat(avcMediaFormat) != null) {
                return avcMediaFormat
            }

            val mp4vesMediaFormat =
                createVideoFormat(VideoFormatMimeType.MPEG4.format, bitrate, outputResolution)
            if (mediaCodecList.findEncoderForFormat(mp4vesMediaFormat) != null) {
                return mp4vesMediaFormat
            }

            return createVideoFormat(
                VideoFormatMimeType.H263.format,
                bitrate,
                outputResolution
            )
        }

        private fun createAudioOutputFormat(inputFormat: MediaFormat): MediaFormat {
            if (MediaFormat.MIMETYPE_AUDIO_AAC == inputFormat.getString(MediaFormat.KEY_MIME)) {
                return inputFormat
            } else {
                val outputFormat = MediaFormat()
                outputFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC)
                outputFormat.setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectELD
                )
                outputFormat.setInteger(
                    MediaFormat.KEY_SAMPLE_RATE,
                    inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                )
                outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                outputFormat.setInteger(
                    MediaFormat.KEY_CHANNEL_COUNT,
                    inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                )

                return outputFormat
            }
        }

        private fun createVideoFormat(
            mimeType: String,
            bitrate: Int,
            outputResolution: Size
        ): MediaFormat {
            val outputFormat =
                MediaFormat.createVideoFormat(
                    mimeType,
                    outputResolution.width,
                    outputResolution.height
                )

            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            // On Build.VERSION_CODES.LOLLIPOP, format must not contain a MediaFormat#KEY_FRAME_RATE.
            // https://developer.android.com/reference/android/media/MediaCodecInfo.CodecCapabilities.html#isFormatSupported(android.media.MediaFormat)
            if (Build.VERSION.SDK_INT != Build.VERSION_CODES.LOLLIPOP) {
                // Required but ignored by the encoder
                outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            }
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            outputFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )

            return outputFormat
        }
    }
}
