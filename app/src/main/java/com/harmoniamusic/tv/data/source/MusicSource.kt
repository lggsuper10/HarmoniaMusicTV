package com.harmoniamusic.tv.data.source

/**
 * 音源统一接口与数据模型，对应 RN utils/musicSdk 下各源导出结构。
 */
data class MusicQuality(
    val type: String,
    val size: String?,
)

/** 榜单元信息（id/name/bangid）。 */
data class BoardInfo(
    val id: String,
    val name: String,
    val bangid: String,
)

data class MusicItem(
    val source: String,
    val songmid: String,
    val name: String,
    val singer: String,
    val albumName: String,
    val interval: String,
    val img: String?,
    val qualities: List<MusicQuality>,
)

data class SearchResult(
    val source: String,
    val list: List<MusicItem>,
    val total: Int,
    val allPage: Int,
    val limit: Int,
)

/** 推荐歌单元信息（首页歌单卡片用）。 */
data class PlaylistInfo(
    val source: String,
    val id: String,
    val name: String,
    val cover: String?,
    val creator: String,
    val trackCount: Int,
)

interface MusicSource {
    val sourceId: String

    /** 搜索，返回某音源的单页结果。page 从 1 开始。 */
    suspend fun search(keyword: String, page: Int, limit: Int = 30): SearchResult

    /** 取播放地址；quality 取值：flac24bit / flac / 320k / 128k。返回 null 表示取不到。 */
    suspend fun getMusicUrl(songmid: String, quality: String = "128k"): String?

    /** 榜单列表；默认空（非所有源都支持）。 */
    suspend fun getBoards(): List<BoardInfo> = emptyList()

    /** 取某榜单曲目；默认空。 */
    suspend fun getBoard(bangid: String, page: Int = 1, limit: Int = 100): SearchResult =
        SearchResult(sourceId, emptyList(), 0, 1, limit)

    /** 取歌词（LRC 原文，可能已合并翻译）；默认 null。 */
    suspend fun getLyric(songmid: String): String? = null

    /** 首页推荐歌单；默认空。 */
    suspend fun getRecommendPlaylists(limit: Int = 12): List<PlaylistInfo> = emptyList()

    /**
     * 连通性测试：默认尝试一次搜索，能返回即视为可达。
     * 子类可按源特性覆盖（例如只探测一个轻接口）。
     */
    suspend fun ping(): Boolean = runCatching {
        search("啦啦啦", 1, 1)
        true
    }.getOrDefault(false)
}
