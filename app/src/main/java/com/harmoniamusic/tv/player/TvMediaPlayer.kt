package com.harmoniamusic.tv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 播放引擎封装（Media3 ExoPlayer）。替代 RN 自研播放内核 core/player 下的实现。
 * 对外暴露播放/暂停/seek 与可观察的播放状态（isPlaying / position / duration）。
 */
class TvMediaPlayer(context: Context) {
    private val exoPlayer = ExoPlayer.Builder(context).build().apply {
        playWhenReady = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    /** 播放错误（取流失败 / 解码失败等）；null 表示无错误。 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 曲终信号（每播放完一首自增），供 ViewModel 触发自动连播。 */
    private val _ended = MutableStateFlow(0)
    val ended: StateFlow<Int> = _ended.asStateFlow()

    private var syncJob: Job? = null
    private var currentUrl: String? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) _ended.value += 1
        }

        override fun onPlayerError(error: PlaybackException) {
            _error.value = error.message
        }
    }

    init {
        exoPlayer.addListener(listener)
        syncJob = scope.launch {
            while (true) {
                _positionMs.value = exoPlayer.currentPosition.coerceAtLeast(0)
                _durationMs.value = exoPlayer.duration.coerceAtLeast(0)
                delay(500)
            }
        }
    }

    fun play(url: String) {
        currentUrl = url
        _error.value = null
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    /** 播放器是否已载入音源（用于区分“暂停中”与“从未成功取流”）。 */
    fun hasMedia(): Boolean = currentUrl != null && exoPlayer.mediaItemCount > 0

    fun pause() = exoPlayer.pause()
    fun resume() = exoPlayer.play()

    /** 换歌/取流前清空旧音源，避免新歌取流失败后旧歌继续播放。 */
    fun stop() {
        currentUrl = null
        _error.value = null
        _isPlaying.value = false
        _positionMs.value = 0L
        _durationMs.value = 0L
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    fun seekTo(ms: Long) = exoPlayer.seekTo(ms.coerceAtLeast(0))
    fun seekBy(deltaMs: Long) =
        exoPlayer.seekTo((exoPlayer.currentPosition + deltaMs).coerceAtLeast(0))

    fun release() {
        syncJob?.cancel()
        scope.cancel()
        exoPlayer.removeListener(listener)
        exoPlayer.release()
        currentUrl = null
    }
}
