package com.example.photoeditoropengl.videeoedit.videosave


import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.Size
import com.example.photoeditoropengl.videeoedit.helper.GlFilter
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class GlMp4Composer {
    private var context: Context? = null
    private val srcPath: String
    private val destPath: String
    private var filter: GlFilter? = null
    private var outputResolution: Size? = null
    private var bitrate = -1
    private var mute = false
    private var rotation = Rotation.NORMAL
    private var listener: Listener? = null
    private var fillMode: FillMode? = FillMode.PRESERVE_ASPECT_FIT
    private var fillModeItem: FillModeItem? = null
    private var timeScale = 1
    private var flipVertical = false
    private var flipHorizontal = false

    private var executorService: ExecutorService? = null


    constructor(srcPath: String, destPath: String) {
        this.srcPath = srcPath
        this.destPath = destPath
    }

    constructor(context: Context?, srcPath: String, destPath: String) {
        this.context = context
        this.srcPath = srcPath
        this.destPath = destPath
    }

    fun filter(filter: GlFilter?): GlMp4Composer {
        this.filter = filter
        return this
    }

    fun size(width: Int, height: Int): GlMp4Composer {
        this.outputResolution = Size(width, height)
        return this
    }

    fun videoBitrate(bitrate: Int): GlMp4Composer {
        this.bitrate = bitrate
        return this
    }

    fun mute(mute: Boolean): GlMp4Composer {
        this.mute = mute
        return this
    }

    fun flipVertical(flipVertical: Boolean): GlMp4Composer {
        this.flipVertical = flipVertical
        return this
    }

    fun flipHorizontal(flipHorizontal: Boolean): GlMp4Composer {
        this.flipHorizontal = flipHorizontal
        return this
    }

    fun rotation(rotation: Rotation): GlMp4Composer {
        this.rotation = rotation
        return this
    }

    fun fillMode(fillMode: FillMode?): GlMp4Composer {
        this.fillMode = fillMode
        return this
    }

    fun customFillMode(fillModeItem: FillModeItem?): GlMp4Composer {
        this.fillModeItem = fillModeItem
        this.fillMode = FillMode.CUSTOM
        return this
    }


    fun listener(listener: Listener?): GlMp4Composer {
        this.listener = listener
        return this
    }

    fun timeScale(timeScale: Int): GlMp4Composer {
        this.timeScale = timeScale
        return this
    }

    private fun getExecutorService(): ExecutorService? {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor()
        }
        return executorService
    }


    fun start(): GlMp4Composer {
        getExecutorService()!!.execute {
            val engine = GPUMp4ComposerEngine()
            engine.setProgressCallback(object : GPUMp4ComposerEngine.ProgressCallback {
                override fun onProgress(progress: Double) {
                    if (listener != null) {
                        listener!!.onProgress(progress)
                    }
                }
            })

            val srcFile = File(srcPath)
            val fileInputStream: FileInputStream?
            try {
                fileInputStream = if (srcPath.contains("content:/")) {
                    context!!.contentResolver
                        .openInputStream(Uri.parse(srcPath)) as FileInputStream?
                } else {
                    FileInputStream(srcFile)
                }
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                if (listener != null) {
                    listener!!.onFailed(e)
                }
                return@execute
            } catch (e: NullPointerException) {
                Log.e(
                    TAG,
                    "Must have a context when use ScopedStorage"
                )
                e.printStackTrace()
                if (listener != null) {
                    listener!!.onFailed(e)
                }
                return@execute
            }

            try {
                engine.setDataSource(fileInputStream!!.fd)
            } catch (e: IOException) {
                e.printStackTrace()
                if (listener != null) {
                    listener!!.onFailed(e)
                }
                return@execute
            }

            val videoRotate = getVideoRotation(srcPath)
            val srcVideoResolution = getVideoResolution(srcPath, videoRotate)

            if (filter == null) {
                filter = GlFilter()
            }

            if (fillMode == null) {
                fillMode = FillMode.PRESERVE_ASPECT_FIT
            }

            if (fillModeItem != null) {
                fillMode = FillMode.CUSTOM
            }

            if (outputResolution == null) {
                if (fillMode === FillMode.CUSTOM) {
                    outputResolution = srcVideoResolution
                } else {
                    val rotate = Rotation.fromInt(rotation.rotation + videoRotate)
                    outputResolution =
                        if (rotate === Rotation.ROTATION_90 || rotate === Rotation.ROTATION_270) {
                            Size(
                                srcVideoResolution.height,
                                srcVideoResolution.width
                            )
                        } else {
                            srcVideoResolution
                        }
                }
            }

            if (timeScale < 2) {
                timeScale = 1
            }

            try {
                if (bitrate < 0) {
                    bitrate =
                        calcBitRate(outputResolution!!.width, outputResolution!!.height)
                }
                engine.compose(
                    destPath,
                    outputResolution!!,
                    filter,
                    bitrate,
                    mute,
                    Rotation.fromInt(rotation.rotation + videoRotate),
                    srcVideoResolution,
                    fillMode,
                    fillModeItem,
                    timeScale,
                    flipVertical,
                    flipHorizontal
                )
            } catch (e: Exception) {
                e.printStackTrace()
                if (listener != null) {
                    listener!!.onFailed(e)
                }
                executorService!!.shutdown()
                return@execute
            }

            if (listener != null) {
                listener!!.onCompleted()
            }
            executorService!!.shutdown()
        }

        return this
    }

    fun cancel() {
        getExecutorService()!!.shutdownNow()
    }


    interface Listener {
        fun onProgress(progress: Double)

        fun onCompleted()

        fun onCanceled()

        fun onFailed(exception: Exception?)
    }

    private fun getVideoRotation(videoFilePath: String): Int {
        var mediaMetadataRetriever: MediaMetadataRetriever? = null
        try {
            mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(videoFilePath)
            val orientation =
                mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            return orientation!!.toInt()
        } catch (e: IllegalArgumentException) {
            return 0
        } catch (e: RuntimeException) {
            return 0
        } catch (e: Exception) {
            return 0
        } finally {
            try {
                mediaMetadataRetriever?.release()
            } catch (e: RuntimeException) {
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    private fun calcBitRate(width: Int, height: Int): Int {
        val bitrate = (0.25 * 30 * width * height).toInt()
        return bitrate
    }

    private fun getVideoResolution(path: String, rotation: Int): Size {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!
                .toInt()
            val height =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!
                    .toInt()

            return Size(width, height)
        } finally {
            try {
                retriever?.release()
            } catch (e: RuntimeException) {
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    companion object {
        private val TAG: String = GlMp4Composer::class.java.simpleName
    }
}
