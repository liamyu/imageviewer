package com.github.iielse.imageviewer.widgets.video

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView
import com.github.iielse.imageviewer.utils.Config
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.video.VideoSize
import kotlin.math.min

open class ExoVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr) {
    interface VideoRenderedListener {
        fun onRendered(view: ExoVideoView)
    }

    interface MediaItemProvider {
        fun provide(playUrl: String): List<MediaItem>?
    }

    private val logger by lazy { EventLogger(null) }
    private var exoPlayer: SimpleExoPlayer? = null
    private var videoRenderedCallback: VideoRenderedListener? = null
    private val listeners = mutableListOf<AnalyticsListener>()
    private var playUrl: String? = null
    protected var prepared = false

    fun prepare(url: String) {
        playUrl = url
    }

    fun resume(
        provider: MediaItemProvider? = null
    ) {
        val url = playUrl ?: return
        if (exoPlayer == null) {
            prepared = false
            alpha = 0f
            newExoPlayer()
            exoPlayer?.setMediaItems(provider?.provide(url) ?: listOf(MediaItem.fromUri(url)))
            exoPlayer?.prepare()
        }
        exoPlayer?.playWhenReady = true
    }

    fun pause() {
        exoPlayer?.playWhenReady = false
    }

    fun reset() {
        exoPlayer?.seekTo(0)
        exoPlayer?.playWhenReady = false
    }

    fun release() {
        val player = exoPlayer ?: return
        player.playWhenReady = false
        player.setVideoTextureView(null)
        player.removeListener(videoListener)
        player.removeAnalyticsListener(logger)
        listeners.toList().forEach { player.removeAnalyticsListener(it) }
        player.release()
        exoPlayer = null
    }

    fun setVideoRenderedCallback(listener: VideoRenderedListener?) {
        videoRenderedCallback = listener
    }

    fun addAnalyticsListener(analyticsListener: AnalyticsListener) {
        if (!listeners.contains(analyticsListener)) {
            listeners.add(analyticsListener)
        }
    }

    fun player(
        provider: MediaItemProvider? = null
    ): ExoPlayer? {
        val url = playUrl ?: return null
        if (exoPlayer == null) {
            prepared = false
            alpha = 0f
            newExoPlayer()
            exoPlayer?.setMediaItems(provider?.provide(url) ?: listOf(MediaItem.fromUri(url)))
            exoPlayer?.prepare()
        }
        return exoPlayer
    }

    private fun newExoPlayer(): ExoPlayer {
        release()
        return SimpleExoPlayer.Builder(context).build().also {
            it.setVideoTextureView(this)
            it.addListener(videoListener)
            if (Config.DEBUG) it.addAnalyticsListener(logger)
            listeners.toList().forEach { userListener -> it.addAnalyticsListener(userListener) }
            exoPlayer = it
        }
    }

    private val videoListener = object : Player.Listener {
        override fun onVideoSizeChanged(
            videoSize: VideoSize
        ) {
            updateTextureViewSize(videoSize.width, videoSize.height)
        }
    }

    private fun updateTextureViewSize(videoWidth: Int, videoHeight: Int) {
        val sx = width * 1f / videoWidth
        val sy = height * 1f / videoHeight
        val matrix = android.graphics.Matrix()
        matrix.postScale(videoWidth * 1f / width, videoHeight * 1f / height)
        matrix.postScale(min(sx, sy), min(sx, sy))
        matrix.postTranslate(
            if (sx > sy) (width - videoWidth * sy) / 2 else 0f,
            if (sx > sy) 0f else (height - videoHeight * sx) / 2
        )
        setTransform(matrix)
        invalidate()
        alpha = 1f
        videoRenderedCallback?.onRendered(this)
        prepared = true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }
}