package com.example.photoeditoropengl.videeoedit.videosave

import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import android.util.Size
import com.example.photoeditoropengl.videeoedit.helper.GlFilterOld
import java.io.FileDescriptor
import java.io.IOException
import kotlin.math.min


internal class GPUMp4ComposerEngine {
    private var inputFileDescriptor: FileDescriptor? = null
    private var videoComposer: VideoComposer? = null
    private var audioComposer: IAudioComposer? = null
    private var mediaExtractor: MediaExtractor? = null
    private var mediaMuxer: MediaMuxer? = null
    private var progressCallback: ProgressCallback? = null
    private var durationUs: Long = 0
    private var mediaMetadataRetriever: MediaMetadataRetriever? = null


    fun setDataSource(fileDescriptor: FileDescriptor?) {
        inputFileDescriptor = fileDescriptor
    }

    fun setProgressCallback(progressCallback: ProgressCallback?) {
        this.progressCallback = progressCallback
    }


    @Throws(IOException::class)
    fun compose(
        destPath: String?,
        outputResolution: Size,
        filter: GlFilterOld?,
        bitrate: Int,
        mute: Boolean,
        rotation: Rotation?,
        inputResolution: Size?,
        fillMode: FillMode?,
        fillModeItem: FillModeItem?,
        timeScale: Int,
        flipVertical: Boolean,
        flipHorizontal: Boolean
    ) {
        try {
            mediaExtractor = MediaExtractor()
            mediaExtractor!!.setDataSource(inputFileDescriptor!!)
            mediaMuxer = MediaMuxer(destPath!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever!!.setDataSource(inputFileDescriptor)
            durationUs = try {
                mediaMetadataRetriever!!.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!
                    .toLong() * 1000
            } catch (e: NumberFormatException) {
                -1
            }
            Log.d(TAG, "Duration (us): $durationUs")

            val videoOutputFormat = MediaFormat.createVideoFormat(
                "video/avc",
                outputResolution.width,
                outputResolution.height
            )

            videoOutputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            videoOutputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            videoOutputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            videoOutputFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )


            val muxRender = MuxRender(mediaMuxer!!)

            val format = mediaExtractor!!.getTrackFormat(0)
            val mime = format.getString(MediaFormat.KEY_MIME)

            val videoTrackIndex: Int
            val audioTrackIndex: Int

            if (mime!!.startsWith("video/")) {
                videoTrackIndex = 0
                audioTrackIndex = 1
            } else {
                videoTrackIndex = 1
                audioTrackIndex = 0
            }

            videoComposer = VideoComposer(
                mediaExtractor!!,
                videoTrackIndex,
                videoOutputFormat,
                muxRender,
                timeScale
            )
            videoComposer!!.setUp(
                filter!!,
                rotation!!, outputResolution,
                inputResolution!!, fillMode!!, fillModeItem, flipVertical, flipHorizontal
            )
            mediaExtractor!!.selectTrack(videoTrackIndex)

            if (mediaMetadataRetriever!!.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null && !mute) {

                audioComposer = if (timeScale < 2) {
                    AudioComposer(mediaExtractor!!, audioTrackIndex, muxRender)
                } else {
                    RemixAudioComposer(
                        mediaExtractor!!,
                        audioTrackIndex,
                        mediaExtractor!!.getTrackFormat(audioTrackIndex),
                        muxRender,
                        timeScale
                    )
                }

                audioComposer!!.setup()

                mediaExtractor!!.selectTrack(audioTrackIndex)

                runPipelines()
            } else {
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
                throw Error("Could not shutdown mediaExtractor, codecs and mediaMuxer pipeline.", e)
            }
            try {
                if (mediaMuxer != null) {
                    mediaMuxer!!.release()
                    mediaMuxer = null
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "Failed to release mediaMuxer.", e)
            }
            try {
                if (mediaMetadataRetriever != null) {
                    mediaMetadataRetriever!!.release()
                    mediaMetadataRetriever = null
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "Failed to release mediaMetadataRetriever.", e)
            }
        }
    }


    private fun runPipelines() {
        var loopCount: Long = 0
        if (durationUs <= 0) {
            if (progressCallback != null) {
                progressCallback!!.onProgress(PROGRESS_UNKNOWN)
            }
        }
        while (!(videoComposer!!.isFinished && audioComposer!!.isFinished())) {
            val stepped = (videoComposer!!.stepPipeline()
                    || audioComposer!!.stepPipeline())
            loopCount++
            if (durationUs > 0 && loopCount % PROGRESS_INTERVAL_STEPS == 0L) {
                val videoProgress = if (videoComposer!!.isFinished) 1.0 else min(
                    1.0, videoComposer!!.writtenPresentationTimeUs
                        .toDouble() / durationUs
                )
                val audioProgress = if (audioComposer!!.isFinished()) 1.0 else min(
                    1.0, audioComposer!!.getWrittenPresentationTimeUs()
                        .toDouble() / durationUs
                )
                val progress = (videoProgress + audioProgress) / 2.0
                if (progressCallback != null) {
                    progressCallback!!.onProgress(progress)
                }
            }
            if (!stepped) {
                try {
                    Thread.sleep(SLEEP_TO_WAIT_TRACK_TRANSCODERS)
                } catch (e: InterruptedException) { }
            }
        }
    }

    private fun runPipelinesNoAudio() {
        var loopCount: Long = 0
        if (durationUs <= 0) {
            if (progressCallback != null) {
                progressCallback!!.onProgress(PROGRESS_UNKNOWN)
            }
        }
        while (!videoComposer!!.isFinished) {
            val stepped = videoComposer!!.stepPipeline()
            loopCount++
            if (durationUs > 0 && loopCount % PROGRESS_INTERVAL_STEPS == 0L) {
                val videoProgress = if (videoComposer!!.isFinished) 1.0 else min(
                    1.0, videoComposer!!.writtenPresentationTimeUs
                        .toDouble() / durationUs
                )
                if (progressCallback != null) {
                    progressCallback!!.onProgress(videoProgress)
                }
            }
            if (!stepped) {
                try {
                    Thread.sleep(SLEEP_TO_WAIT_TRACK_TRANSCODERS)
                } catch (e: InterruptedException) {
                }
            }
        }
    }


    internal interface ProgressCallback {
        fun onProgress(progress: Double)
    }

    companion object {
        private const val TAG = "GPUMp4ComposerEngine"
        private const val PROGRESS_UNKNOWN = -1.0
        private const val SLEEP_TO_WAIT_TRACK_TRANSCODERS: Long = 10
        private const val PROGRESS_INTERVAL_STEPS: Long = 10
    }
}