package com.quniu.baselib.widget

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

object Media3AudioPlayer : Player.Listener {

    private var player: ExoPlayer? = null
    private val playlist = mutableListOf<MediaItem>()
    private var currentIndex: Int = -1

    enum class PlayMode {
                SEQUENCE, //顺序播放
                LOOP_LIST ,//列表循环
                SINGLE //单曲播放
    }
    private var playMode = PlayMode.SINGLE

    enum class PlaybackState { IDLE, PLAYING, PAUSED, ENDED, ERROR }
    private var stateListener: ((PlaybackState) -> Unit)? = null
    private var trackChangedListener: ((Int) -> Unit)? = null

    // 内部短期抑制：当调用 setPlaylist/其它可能触发回调的 API 时，可以短暂抑制 trackChanged 回调以避免竞态
    private var suppressionUntilMs: Long = 0L
    private val defaultSuppressWindowMs: Long = 400L

    fun init(context: Context) {
        if (player == null) {
            player = ExoPlayer.Builder(context.applicationContext).build().apply {
                addListener(this@Media3AudioPlayer)
            }
        }
    }

    fun getPlayMode(): PlayMode = playMode

    /* 播放列表 */
    /**
     * 设置播放列表
     * @param list url 列表
     * @param autoPlay 是否自动播放
     * @param suppressNotify 如果为 true，则在播放启动时不会触发 trackChangedListener 回调（用于避免回调竞态）
     * @param suppressWindowMs 如果大于 0，则在当前时间起短暂抑制 trackChanged 回调的持续时间（毫秒）。如果同时 suppressNotify=true，两者等效。
     */
    fun setPlaylist(list: MutableList<String>, autoPlay: Boolean = false, suppressNotify: Boolean = false, suppressWindowMs: Long = 0L) {
        playlist.clear()
        playlist.addAll(list.map { MediaItem.fromUri(it) })
        currentIndex = 0
        // 设置短期抑制窗口（用于避免在 setMediaItem/prepare/play 等链式调用中触发回调竞态）
        if (suppressNotify || suppressWindowMs > 0L) {
            val window = if (suppressWindowMs > 0L) suppressWindowMs else defaultSuppressWindowMs
            suppressionUntilMs = System.currentTimeMillis() + window
        }
        if (autoPlay) playCurrent(suppressNotify)
        else {
            player?.setMediaItem(playlist[0])
            player?.prepare()
        }
    }

    fun getPlaylistSize(): Int = playlist.size
    // 获取当前播放索引（外部只读访问）
    fun getCurrentIndex(): Int = currentIndex
    // 获取播放列表中某下标的 uri 字符串（若存在）
    fun getPlaylistUriAt(index: Int): String? = playlist.getOrNull(index)?.localConfiguration?.uri?.toString()

    private val lock = Any()
    /** 热插播到列表尾部，不中断当前播放 */
    fun append(vararg urls: String) {
        synchronized(lock) {
            if (urls.isEmpty()) return
            playlist.addAll(urls.map { MediaItem.fromUri(it) })
            if (currentIndex == -1) {
                currentIndex = 0
                playCurrent()
            }
        }
    }

    fun next() {
        if (playlist.isEmpty()) return
        currentIndex = (currentIndex + 1) % playlist.size
        playCurrent()
    }

    fun previous() {
        if (playlist.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
        playCurrent()
    }

    /**
     * 播放当前播放列表中指定位置的音频
     * @param index 音频在播放列表中的索引位置
     */
    fun playAt(index: Int) {
        if (playlist.isEmpty() || index < 0 || index >= playlist.size) return
        currentIndex = index
        playCurrent()
    }

    /* 播放控制 */
    fun play() {
        if (playlist.isNotEmpty() &&
            player?.playbackState == Player.STATE_ENDED &&
            playMode == PlayMode.SINGLE
        ) {
            // 重新播放当前首
            // Use playCurrent() to ensure the media item/window is prepared and played again.
            try {
                playCurrent()
            } catch (_: Exception) {
                // fallback to simple seek/play if playCurrent fails for some reason
                try { player?.seekTo(0); player?.playWhenReady = true } catch (_: Exception) {}
            }
            // notify immediate playing state (listener will also be invoked via onPlaybackStateChanged)
            stateListener?.invoke(PlaybackState.PLAYING)
            return
        }
        if (playlist.isNotEmpty() && currentIndex == playlist.size - 1 &&
            player?.playbackState == Player.STATE_ENDED &&
            playMode == PlayMode.SEQUENCE
        ) {
            // 回到第一首重新播放
            currentIndex = 0
            playCurrent()
            return
        }
        player?.playWhenReady = true
        stateListener?.invoke(PlaybackState.PLAYING) // 手动派发
    }

    fun pause() {
        player?.playWhenReady = false
        stateListener?.invoke(PlaybackState.PAUSED)    // 手动派发
    }

    fun isPlaying(): Boolean = player?.isPlaying == true
    fun seekTo(positionMs: Long) = player?.seekTo(positionMs)
    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L
    fun getDuration(): Long = player?.duration ?: 0L

    fun release() {
        player?.release()
        player = null
        playlist.clear()
        currentIndex = -1
    }

    /* 模式 & 回调 */
    fun setPlayMode(mode: PlayMode) { playMode = mode }
    fun setStateListener(listener: (PlaybackState) -> Unit) { stateListener = listener }
    fun setTrackChangedListener(listener: (Int) -> Unit) { trackChangedListener = listener }

    /* 内部播放逻辑 */
    /**
     * 播放当前索引的音频；可选择是否抑制回调通知
     */
    private fun playCurrent(suppressNotify: Boolean = false) {
        if (playlist.isEmpty()) return
        player?.setMediaItem(playlist[currentIndex])
        player?.prepare()
        player?.playWhenReady = true
        // 判断是否处于抑制窗口或显式请求抑制
        val now = System.currentTimeMillis()
        val suppressedNow = suppressNotify || (suppressionUntilMs > now)
        if (!suppressedNow) {
            trackChangedListener?.invoke(currentIndex)
        }
    }

    /* Player.Listener */
    override fun onPlaybackStateChanged(playbackState: Int) {
        val state = when (playbackState) {
            Player.STATE_READY -> if (player?.playWhenReady == true) PlaybackState.PLAYING else PlaybackState.PAUSED
            Player.STATE_ENDED -> {
                when (playMode) {
                    PlayMode.LOOP_LIST -> next() // 列表循环，播下一首
                    PlayMode.SEQUENCE -> if (currentIndex < playlist.size - 1) next() // 有下一首就播下一首
                    PlayMode.SINGLE ->  stateListener?.invoke(PlaybackState.ENDED) // 不切歌，只派发结束状态
                }
                PlaybackState.ENDED
            }
            Player.STATE_IDLE -> PlaybackState.IDLE
            else -> return
        }
        stateListener?.invoke(state)
    }

    override fun onPlayerError(error: PlaybackException) {
        stateListener?.invoke(PlaybackState.ERROR)
    }
}