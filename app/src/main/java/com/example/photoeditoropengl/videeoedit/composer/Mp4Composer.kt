package com.example.photoeditoropengl.videeoedit.composer

import android.annotation.TargetApi
import android.content.Context
import android.media.MediaCodec
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLContext
import android.os.Build
import android.util.Size
import java.io.FileDescriptor
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Mp4Composer {
    private val srcDataSource: DataSource
    private val destPath: String?
    private var destFileDescriptor: FileDescriptor? = null
    private var filter: GlFilter? = null
    private var outputResolution: Size? = null
    private var bitrate = -1
    private var mute = false
    private var rotation: Rotation = Rotation.NORMAL
    private var listener: Listener? = null
    private var fillMode: FillMode? = FillMode.PRESERVE_ASPECT_FIT
    private var fillModeCustomItem: FillModeCustomItem? = null
    private var timeScale = 1f // should be in range 0.125 (-8X) to 8.0 (8X)
    private var isPitchChanged = false
    private var flipVertical = false
    private var flipHorizontal = false
    private var trimStartMs: Long = 0
    private var trimEndMs: Long = -1
    private var videoFormatMimeType: VideoFormatMimeType = VideoFormatMimeType.AUTO
    private var shareContext: EGLContext? = null

    private var executorService: ExecutorService? = null
    private var engine: Mp4ComposerEngine? = null

    private var logger: Logger? = null

    private val errorDataSource: DataSource.Listener = object : Listener, DataSource.Listener{
        override fun onError(e: Exception?) {
            TODO("Not yet implemented")
        }

        override fun onProgress(progress: Double) {
            TODO("Not yet implemented")
        }

        override fun onCurrentWrittenVideoTime(timeUs: Long) {
            TODO("Not yet implemented")
        }

        override fun onCompleted() {
            TODO("Not yet implemented")
        }

        override fun onCanceled() {
            TODO("Not yet implemented")
        }

        override fun onFailed(exception: Exception?) {
            TODO("Not yet implemented")
        }

    }
    constructor(srcPath: String, destPath: String) : this(srcPath, destPath, AndroidLogger())

    constructor(srcPath: String, destPath: String, logger: Logger) {
        this.logger = logger
        this.srcDataSource = FilePathDataSource(srcPath, logger, errorDataSource)
        this.destPath = destPath
    }

    constructor(srcFileDescriptor: FileDescriptor, destPath: String) {
        this.srcDataSource = FileDescriptorDataSource(srcFileDescriptor)
        this.destPath = destPath
    }

    constructor(srcUri: Uri, destPath: String, context: Context) : this(
        srcUri,
        destPath,
        context,
        AndroidLogger()
    )

    constructor(srcUri: Uri, destPath: String, context: Context, logger: Logger) {
        this.logger = logger
        this.srcDataSource = UriDataSource(srcUri, context, logger, errorDataSource)
        this.destPath = destPath
    }

    @TargetApi(Build.VERSION_CODES.O)
    constructor(srcFileDescriptor: FileDescriptor, destFileDescriptor: FileDescriptor) {
        require(Build.VERSION.SDK_INT >= 26) { "destFileDescriptor can not use" }
        this.srcDataSource = FileDescriptorDataSource(srcFileDescriptor)
        this.destPath = null
        this.destFileDescriptor = destFileDescriptor
    }

    @TargetApi(Build.VERSION_CODES.O)
    constructor(srcUri: Uri, destFileDescriptor: FileDescriptor, context: Context) : this(
        srcUri,
        destFileDescriptor,
        context,
        AndroidLogger()
    )

    @TargetApi(Build.VERSION_CODES.O)
    constructor(srcUri: Uri, destFileDescriptor: FileDescriptor, context: Context, logger: Logger) {
        require(Build.VERSION.SDK_INT >= 26) { "destFileDescriptor can not use" }
        this.logger = logger
        this.srcDataSource = UriDataSource(srcUri, context, logger, errorDataSource)
        this.destPath = null
        this.destFileDescriptor = destFileDescriptor
    }

    fun filter(filter: GlFilter): Mp4Composer {
        this.filter = filter
        return this
    }

    fun size(width: Int, height: Int): Mp4Composer {
        this.outputResolution = Size(width, height)
        return this
    }

    fun videoBitrate(bitrate: Int): Mp4Composer {
        this.bitrate = bitrate
        return this
    }

    fun mute(mute: Boolean): Mp4Composer {
        this.mute = mute
        return this
    }

    fun flipVertical(flipVertical: Boolean): Mp4Composer {
        this.flipVertical = flipVertical
        return this
    }

    fun flipHorizontal(flipHorizontal: Boolean): Mp4Composer {
        this.flipHorizontal = flipHorizontal
        return this
    }

    fun rotation(rotation: Rotation): Mp4Composer {
        this.rotation = rotation
        return this
    }

    fun fillMode(fillMode: FillMode): Mp4Composer {
        this.fillMode = fillMode
        return this
    }

    fun customFillMode(fillModeCustomItem: FillModeCustomItem): Mp4Composer {
        this.fillModeCustomItem = fillModeCustomItem
        this.fillMode = FillMode.CUSTOM
        return this
    }


    fun listener(listener: Listener): Mp4Composer {
        this.listener = listener
        return this
    }

    fun timeScale(timeScale: Float): Mp4Composer {
        this.timeScale = timeScale
        return this
    }

    fun changePitch(isPitchChanged: Boolean): Mp4Composer {
        this.isPitchChanged = isPitchChanged
        return this
    }

    fun videoFormatMimeType(videoFormatMimeType: VideoFormatMimeType): Mp4Composer {
        this.videoFormatMimeType = videoFormatMimeType
        return this
    }

    fun logger(logger: Logger): Mp4Composer {
        this.logger = logger
        return this
    }


    fun trim(trimStartMs: Long, trimEndMs: Long): Mp4Composer {
        this.trimStartMs = trimStartMs
        this.trimEndMs = trimEndMs
        return this
    }

    fun shareContext(shareContext: EGLContext): Mp4Composer {
        this.shareContext = shareContext
        return this
    }

    private fun getExecutorService(): ExecutorService? {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor()
        }
        return executorService
    }


    fun start(): Mp4Composer {
        if (engine != null) {
            return this
        }

        getExecutorService()!!.execute(Runnable {
            if (logger == null) {
                logger = AndroidLogger()
            }
            engine = Mp4ComposerEngine(logger!!)

            engine!!.setProgressCallback(object : Mp4ComposerEngine.ProgressCallback {
                override fun onProgress(progress: Double) {
                    if (listener != null) {
                        listener!!.onProgress(progress)
                    }
                }

                override fun onCurrentWrittenVideoTime(timeUs: Long) {
                    if (listener != null) {
                        listener!!.onCurrentWrittenVideoTime(timeUs)
                    }
                }
            })

            val videoRotate = getVideoRotation(srcDataSource)
            val srcVideoResolution = getVideoResolution(srcDataSource)

            if (srcVideoResolution == null || videoRotate == null) {
                notifyListenerOfFailureAndShutdown(UnsupportedOperationException("File type unsupported, path: $srcDataSource"))
                return@Runnable
            }

            if (filter == null) {
                filter = GlFilter()
            }

            if (fillMode == null) {
                fillMode = FillMode.PRESERVE_ASPECT_FIT
            }
            if (fillMode === FillMode.CUSTOM && fillModeCustomItem == null) {
                notifyListenerOfFailureAndShutdown(IllegalAccessException("FillMode.CUSTOM must need fillModeCustomItem."))
                return@Runnable
            }

            if (fillModeCustomItem != null) {
                fillMode = FillMode.CUSTOM
            }

            if (outputResolution == null) {
                if (fillMode === FillMode.CUSTOM) {
                    outputResolution = srcVideoResolution
                } else {
                    val rotate: Rotation = Rotation.fromInt(rotation.rotation + videoRotate)
                    outputResolution =
                        if (rotate === Rotation.ROTATION_90 || rotate === Rotation.ROTATION_270) {
                            Size(srcVideoResolution.height, srcVideoResolution.width)
                        } else {
                            srcVideoResolution
                        }
                }
            }

            if (timeScale < 0.125f) {
                timeScale = 0.125f
            } else if (timeScale > 8f) {
                timeScale = 8f
            }

            if (shareContext == null) {
                shareContext = EGL14.EGL_NO_CONTEXT
            }

            logger!!.debug(TAG, "rotation = " + (rotation.rotation + videoRotate))
            logger!!.debug(
                TAG,
                "rotation = " + Rotation.fromInt(rotation.rotation + videoRotate)
            )
            logger!!.debug(
                TAG,
                "inputResolution width = " + srcVideoResolution.width + " height = " + srcVideoResolution.height
            )
            logger!!.debug(
                TAG,
                "outputResolution width = " + outputResolution!!.width + " height = " + outputResolution!!.height
            )
            logger!!.debug(TAG, "fillMode = $fillMode")

            try {
                if (bitrate < 0) {
                    bitrate = calcBitRate(outputResolution!!.width, outputResolution!!.height)
                }
                engine!!.compose(
                    srcDataSource,
                    destPath,
                    destFileDescriptor,
                    outputResolution!!,
                    filter,
                    bitrate,
                    mute,
                    Rotation.fromInt(rotation.rotation + videoRotate),
                    srcVideoResolution,
                    fillMode,
                    fillModeCustomItem,
                    timeScale,
                    isPitchChanged,
                    flipVertical,
                    flipHorizontal,
                    trimStartMs,
                    trimEndMs,
                    videoFormatMimeType,
                    shareContext
                )
            } catch (e: Exception) {
                if (e is MediaCodec.CodecException) {
                    logger!!.error(
                        TAG,
                        "This devicel cannot codec with that setting. Check width, height, bitrate and video format.",
                        e
                    )
                    notifyListenerOfFailureAndShutdown(e)
                    return@Runnable
                }

                logger!!.error(TAG, "Unable to compose the engine", e)
                notifyListenerOfFailureAndShutdown(e)
                return@Runnable
            }

            if (listener != null) {

            }
            executorService!!.shutdown()
            engine = null
        })

        return this
    }

    private fun notifyListenerOfFailureAndShutdown(failure: Exception) {
        if (listener != null) {
            listener!!.onFailed(failure)
        }
        if (executorService != null) {
            executorService!!.shutdown()
        }
    }

    fun cancel() {
        engine?.cancel()
    }


    interface Listener {
        fun onProgress(progress: Double)

        fun onCurrentWrittenVideoTime(timeUs: Long)

        fun onCompleted()

        fun onCanceled()

        fun onFailed(exception: Exception?)
    }

    private fun getVideoRotation(dataSource: DataSource): Int? {
        var mediaMetadataRetriever: MediaMetadataRetriever? = null
        try {
            mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(dataSource.fileDescriptor)
            val orientation =
                mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?: return null
            return orientation.toInt()
        } catch (e: IllegalArgumentException) {
            logger?.error("MediaMetadataRetriever", "getVideoRotation IllegalArgumentException", e)
            return 0
        } catch (e: RuntimeException) {
            logger?.error("MediaMetadataRetriever", "getVideoRotation RuntimeException", e)
            return 0
        } catch (e: Exception) {
            logger?.error("MediaMetadataRetriever", "getVideoRotation Exception", e)
            return 0
        } finally {
            try {
                mediaMetadataRetriever?.release()
            } catch (e: RuntimeException) {
                logger?.error(TAG, "Failed to release mediaMetadataRetriever.", e)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    private fun calcBitRate(width: Int, height: Int): Int {
        val bitrate = (0.25 * 30 * width * height).toInt()
        logger?.debug(TAG, "bitrate=$bitrate")
        return bitrate
    }

    private fun getVideoResolution(dataSource: DataSource): Size? {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(dataSource.fileDescriptor)
            val rawWidth =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val rawHeight =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (rawWidth == null || rawHeight == null) {
                return null
            }
            val width = rawWidth.toInt()
            val height = rawHeight.toInt()

            return Size(width, height)
        } catch (e: IllegalArgumentException) {
            logger?.error("MediaMetadataRetriever", "getVideoResolution IllegalArgumentException", e)
            return null
        } catch (e: RuntimeException) {
            logger?.error("MediaMetadataRetriever", "getVideoResolution RuntimeException", e)
            return null
        } catch (e: Exception) {
            logger?.error("MediaMetadataRetriever", "getVideoResolution Exception", e)
            return null
        } finally {
            try {
                retriever?.release()
            } catch (e: RuntimeException) {
                logger?.error(TAG, "Failed to release mediaMetadataRetriever.", e)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    companion object {
        private val TAG: String = Mp4Composer::class.java.simpleName
    }
}
