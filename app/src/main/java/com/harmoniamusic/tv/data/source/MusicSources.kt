package com.harmoniamusic.tv.data.source

import com.harmoniamusic.tv.data.source.kg.KgSource
import com.harmoniamusic.tv.data.source.kw.KwSource
import com.harmoniamusic.tv.data.source.mg.MgSource
import com.harmoniamusic.tv.data.source.tx.TxSource
import com.harmoniamusic.tv.data.source.wy.WySource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 内置直连音源注册表。当前接入 wy（网易云）、tx（QQ 音乐）、kw（酷我）、kg（酷狗）、mg（咪咕）；
 * 其他源可由社区预设脚本经用户 API 扩展。
 */
object MusicSources {
    val builtin: Map<String, MusicSource> = mapOf(
        WySource.sourceId to WySource,
        TxSource.sourceId to TxSource,
        KwSource.sourceId to KwSource,
        KgSource.sourceId to KgSource,
        MgSource.sourceId to MgSource,
    )

    /** 音源中文显示名。 */
    fun displayName(sourceId: String): String = when (sourceId) {
        "wy" -> "网易云"
        "kg" -> "酷狗"
        "tx" -> "QQ音乐"
        "kw" -> "酷我"
        "mg" -> "咪咕"
        "tv_preset_grass" -> "野草"
        "tv_preset_juhe" -> "聚合音源"
        "local" -> "本地"
        else -> sourceId
    }

    /**
     * 并发搜索内置音源（对应 RN `searchMusic`）。
     * sourceId 为空时搜全部；指定时只搜该源。
     */
    suspend fun searchAll(
        keyword: String,
        page: Int = 1,
        limit: Int = 25,
        sourceId: String? = null,
    ): Map<String, SearchResult> {
        val pool = if (sourceId.isNullOrBlank()) builtin else builtin.filterKeys { it == sourceId }
        return pool.mapValues { (_, src) ->
            runCatching { src.search(keyword, page, limit) }.getOrNull()
        }.filterValues { it != null } as Map<String, SearchResult>
    }

    /** 单个源的连通性测试结果。 */
    data class SourceTestResult(
        val id: String,
        val ok: Boolean,
        val ms: Long,
        val detail: String,
    )

    /**
     * 并发测试所有内置音源的连通性（设置页"测试所有源"用）。
     * 单个源超时即判定不通，避免界面卡住。
     */
    suspend fun testAll(timeoutMs: Int = 6000): List<SourceTestResult> {
        return coroutineScope {
            builtin.map { (id, src) ->
                async {
                    val start = System.currentTimeMillis()
                    try {
                        val ok = src.ping()
                        SourceTestResult(
                            id = id,
                            ok = ok,
                            ms = System.currentTimeMillis() - start,
                            detail = if (ok) "通畅" else "无结果",
                        )
                    } catch (e: Exception) {
                        SourceTestResult(
                            id = id,
                            ok = false,
                            ms = System.currentTimeMillis() - start,
                            detail = e.message?.take(60) ?: "失败",
                        )
                    }
                }
            }.awaitAll()
        }
    }

    /** 取某音源的榜单列表（对应 RN `leaderboard.getBoards`）。 */
    suspend fun getBoards(sourceId: String): List<BoardInfo> =
        builtin[sourceId]?.getBoards() ?: emptyList()

    /** 取某音源某榜单的曲目（对应 RN `leaderboard.getList`）。 */
    suspend fun getBoard(
        sourceId: String,
        bangid: String,
        page: Int = 1,
        limit: Int = 100,
    ): SearchResult =
        builtin[sourceId]?.getBoard(bangid, page, limit)
            ?: SearchResult(sourceId, emptyList(), 0, 1, limit)

    /** 取某音源歌词。 */
    suspend fun getLyric(sourceId: String, songmid: String): String? =
        builtin[sourceId]?.getLyric(songmid)

    /** 聚合各内置音源的推荐歌单。 */
    suspend fun getRecommendPlaylists(limit: Int = 12): List<PlaylistInfo> =
        builtin.values.flatMap { src ->
            runCatching { src.getRecommendPlaylists(limit) }.getOrDefault(emptyList())
        }.take(limit)
}
