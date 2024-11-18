package com.example.photoeditoropengl.videeoedit.view

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.example.photoeditoropengl.videeoedit.helper.GlPlayerRenderer
import com.example.photoeditoropengl.videeoedit.helper.GlConfig
import com.example.photoeditoropengl.videeoedit.helper.GlContextFactory
import com.example.photoeditoropengl.videeoedit.helper.GlFilterOld
import com.example.photoeditoropengl.videeoedit.helper.PlayerScaleType



class OpenGlPlayerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), Player.Listener {

    companion object {
        private val TAG = OpenGlPlayerView::class.java.simpleName
    }

    val renderer: GlPlayerRenderer = GlPlayerRenderer(this)
    private var player: ExoPlayer? = null

    private var videoAspect = 1f
    private var playerScaleType: PlayerScaleType = PlayerScaleType.RESIZE_FIT_WIDTH

    init {
        setEGLContextFactory(GlContextFactory())
        setEGLConfigChooser(GlConfig(false))
        setRenderer(renderer)
    }

    fun setExoPlayer(player: ExoPlayer?): OpenGlPlayerView {
        this.player?.release()
        this.player = player
        this.player?.addListener(this)
        if (player != null) {
            renderer.setExoPlayer(player)
        }
        return this
    }

    fun setGlFilter(glFilter: GlFilterOld) {
        renderer.setGlFilter(glFilter)
    }

    fun setPlayerScaleType(playerScaleType: PlayerScaleType) {
        this.playerScaleType = playerScaleType
        requestLayout()
    }

    fun setPlayerAspectRatio(aspectRatio: Float) {
        videoAspect = aspectRatio
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val measuredWidth = measuredWidth
        val measuredHeight = measuredHeight

        var viewWidth = measuredWidth
        var viewHeight = measuredHeight

        when (playerScaleType) {
            PlayerScaleType.RESIZE_FIT_WIDTH -> viewHeight = (measuredWidth / videoAspect).toInt()
            PlayerScaleType.RESIZE_FIT_HEIGHT -> viewWidth = (measuredHeight * videoAspect).toInt()
            PlayerScaleType.RESIZE_NONE -> {

            }
        }

        setMeasuredDimension(viewWidth, viewHeight)
    }

    override fun onPause() {
        super.onPause()
        renderer.release()
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        videoAspect = (videoSize.width.toFloat() / videoSize.height) * videoSize.pixelWidthHeightRatio
        requestLayout()
    }

    override fun onRenderedFirstFrame() {
        // do nothing
    }

}