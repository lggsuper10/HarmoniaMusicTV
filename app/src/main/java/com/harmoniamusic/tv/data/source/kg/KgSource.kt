package com.harmoniamusic.tv.data.source.kg

import com.harmoniamusic.tv.data.net.HttpClient
import com.harmoniamusic.tv.data.source.MusicItem
import com.harmoniamusic.tv.data.source.MusicQuality
import com.harmoniamusic.tv.data.source.MusicSource
import com.harmoniamusic.tv.data.source.SearchResult
import org.json.JSONArray
import org.json.JSONObject

/**
 * 酷狗直连音源（Kotlin 重写自 RN utils/musicSdk/kg/musicSearch.js）。
 *
 * 搜索走 `https://songsearch.kugou.com/song_search_v2`（明文 GET）。
 * 取流需 FileHash + 签名（RN 直连同样未实现，走用户 API），此处返回 null。
 */
object KgSource : MusicSource {
    override val sourceId = "kg"

    override suspend fun search(keyword: String, page: Int, limit: Int): SearchResult {
        val url = "https://songsearch.kugou.com/song_search_v2?keyword=${enc(keyword)}&page=$page" +
            "&pagesize=$limit&userid=0&clientver=&platform=WebFilter&filter=2&iscorrection=1" +
            "&privilege_filter=0&area_code=1"
        val body = HttpClient.get(url)
        require(body.optInt("error_code", -1) == 0) { "kg search failed" }
        val data = body.optJSONObject("data")
            ?: return SearchResult(sourceId, emptyList(), 0, 1, limit)
        val total = data.optInt("total", 0)
        val lists = data.optJSONArray("lists")
            ?: return SearchResult(sourceId, emptyList(), total, 1, limit)

        val seen = HashSet<String>()
        val list = mutableListOf<MusicItem>()
        fun collect(o: JSONObject) {
            // 去重键：Audioid + FileHash（对应 RN handleResult 的 ids Set）
            val key = o.optString("Audioid") + o.optString("FileHash")
            if (seen.add(key)) {
                runCatching { parseItem(o) }.getOrNull()?.let { list.add(it) }
            }
        }
        for (i in 0 until lists.length()) {
            val item = lists.getJSONObject(i)
            collect(item)
            val grp: JSONArray? = item.optJSONArray("Grp")
            grp?.let {
                for (j in 0 until it.length()) {
                    it.optJSONObject(j)?.let(::collect)
                }
            }
        }
        val allPage = if (limit > 0) (total + limit - 1) / limit else 1
        return SearchResult(sourceId, list, total, allPage, limit)
    }

    private fun parseItem(o: JSONObject): MusicItem = MusicItem(
        source = sourceId,
        songmid = o.optString("Audioid"),
        name = o.optString("SongName"),
        singer = singers(o.optJSONArray("Singers")),
        albumName = o.optString("AlbumName"),
        interval = formatPlayTime(o.optLong("Duration")),
        img = null,
        qualities = buildList {
            if (o.optLong("FileSize", 0) != 0L) add(MusicQuality("128k", sizeFormate(o.optLong("FileSize"))))
            if (o.optLong("HQFileSize", 0) != 0L) add(MusicQuality("320k", sizeFormate(o.optLong("HQFileSize"))))
            if (o.optLong("SQFileSize", 0) != 0L) add(MusicQuality("flac", sizeFormate(o.optLong("SQFileSize"))))
            if (o.optLong("ResFileSize", 0) != 0L) add(MusicQuality("flac24bit", sizeFormate(o.optLong("ResFileSize"))))
        },
    )

    private fun singers(arr: JSONArray?): String {
        arr ?: return ""
        return (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it)?.optString("name") }
            .filter { it.isNotBlank() }
            .joinToString("、")
    }

    override suspend fun getMusicUrl(songmid: String, quality: String): String? = null

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    private fun sizeFormate(size: Long): String {
        if (size <= 0) return ""
        val kb = size / 1024.0
        return if (kb < 1024) "%.2f KB".format(kb) else "%.2f MB".format(kb / 1024.0)
    }

    private fun formatPlayTime(sec: Long): String {
        if (sec <= 0) return "00:00"
        val m = (sec % 3600) / 60
        val s = sec % 60
        return "%02d:%02d".format(m, s)
    }
}
