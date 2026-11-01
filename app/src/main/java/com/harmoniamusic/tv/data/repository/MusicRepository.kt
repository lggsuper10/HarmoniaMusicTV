package com.harmoniamusic.tv.data.repository

import android.util.Log
import com.harmoniamusic.tv.data.source.BoardInfo
import com.harmoniamusic.tv.data.source.MusicItem
import com.harmoniamusic.tv.data.source.MusicSources
import com.harmoniamusic.tv.data.source.PlaylistInfo
import com.harmoniamusic.tv.data.source.SearchResult
import com.harmoniamusic.tv.data.source.userapi.UserApiManager
import com.harmoniamusic.tv.data.source.userapi.normalizeStreamUrl
import com.harmoniamusic.tv.player.TvMediaPlayer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 音乐仓库：聚合各音源的搜索与取流，并驱动播放。
 * 对应 RN 的 searchMusic / getMusicUrl / 播放内核，是 UI 层唯一的数据入口。
 */
class MusicRepository(
    private val player: TvMediaPlayer,
    private val userApi: UserApiManager,
) {

    data class PlaybackStream(
        val url: String,
        val sourceLabel: String,
    )

    /** 并发搜索内置直连音源，返回 sourceId -> 搜索结果；sourceId 指定时只搜该源。 */
    suspend fun search(
        keyword: String,
        page: Int = 1,
        limit: Int = 25,
        sourceId: String? = null,
    ): Map<String, SearchResult> = MusicSources.searchAll(keyword, page, limit, sourceId)

    /**
     * 取某首歌的播放地址并交给播放引擎。quality: flac24bit/flac/320k/128k。
     * 歌曲信息（封面/歌词）来自搜索或榜单的目录源；这里只负责选择独立的取流源。
     * 优先使用设置的「优先取流源」（含其他已启用脚本），失败再回退歌曲对应的内置直连源。
     */
    suspend fun play(item: MusicItem, quality: String = "128k"): PlaybackStream {
        val userStream = runCatching { userApi.resolveMusicUrl(item, quality) }
            .onFailure { Log.w(TAG, "用户 API 取流失败: ${item.name} source=${item.source}", it) }
            .getOrNull()
        val builtinUrl = normalizeStreamUrl(
            runCatching {
                MusicSources.builtin[item.source]?.getMusicUrl(item.songmid, quality)
            }.onFailure {
                Log.w(TAG, "内置源取流失败: ${item.name} source=${item.source}", it)
            }.getOrNull(),
        )
        val stream = userStream?.let {
            PlaybackStream(it.url, it.providerName)
        } ?: builtinUrl?.let {
            PlaybackStream(it, "${MusicSources.displayName(item.source)}直连")
        } ?: throw IllegalArgumentException("取流失败: ${item.name}（用户 API 与内置取流源均未返回可用地址）")

        currentCoroutineContext().ensureActive()
        player.play(stream.url)
        Log.i(TAG, "开始播放: song=${item.name} catalog=${item.source} provider=${stream.sourceLabel}")
        return stream
    }

    /** 取某音源的榜单列表。 */
    suspend fun getBoards(sourceId: String): List<BoardInfo> = MusicSources.getBoards(sourceId)

    /** 取某音源某榜单的曲目。 */
    suspend fun getBoard(
        sourceId: String,
        bangid: String,
        page: Int = 1,
        limit: Int = 100,
    ): SearchResult = MusicSources.getBoard(sourceId, bangid, page, limit)

    /** 取歌词。 */
    suspend fun getLyric(sourceId: String, songmid: String): String? =
        MusicSources.getLyric(sourceId, songmid)

    /** 首页推荐歌单。 */
    suspend fun recommendPlaylists(limit: Int = 12): List<PlaylistInfo> =
        MusicSources.getRecommendPlaylists(limit)

    /** 歌单曲目（复用榜单详情接口）。 */
    suspend fun playlistSongs(sourceId: String, id: String): List<MusicItem> =
        MusicSources.getBoard(sourceId, id, 1, 200).list

    fun pause() = player.pause()
    fun resume() = player.resume()
    fun stop() = player.stop()
    fun seekBy(deltaMs: Long) = player.seekBy(deltaMs)

    private companion object {
        const val TAG = "MusicRepository"
    }
}
