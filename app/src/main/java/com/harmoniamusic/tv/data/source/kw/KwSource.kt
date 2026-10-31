package com.harmoniamusic.tv.data.source.kw

import com.harmoniamusic.tv.data.net.HttpClient
import com.harmoniamusic.tv.data.source.MusicItem
import com.harmoniamusic.tv.data.source.MusicQuality
import com.harmoniamusic.tv.data.source.MusicSource
import com.harmoniamusic.tv.data.source.SearchResult
import org.json.JSONObject

/**
 * 酷我直连音源（Kotlin 重写自 RN utils/musicSdk/kw/musicSearch.js）。
 *
 * 搜索走 `http://search.kuwo.cn/r.s`（明文 GET），音质从 N_MINFO 正则解析；
 * 取流走 Web 播放接口 `www.kuwo.cn/api/v1/www/music/playUrl`（匿名 token 即可）。
 */
object KwSource : MusicSource {
    override val sourceId = "kw"

    private val MINFO = Regex("""level:(\w+),bitrate:(\d+),format:(\w+),size:([\w.]+)""")

    override suspend fun search(keyword: String, page: Int, limit: Int): SearchResult {
        val url = "http://search.kuwo.cn/r.s?client=kt&all=${enc(keyword)}&pn=${page - 1}&rn=$limit" +
            "&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music" +
            "&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1"
        val body = HttpClient.get(url)
        val total = body.optString("TOTAL").toIntOrNull() ?: 0
        val arr = body.optJSONArray("abslist")
            ?: return SearchResult(sourceId, emptyList(), total, 1, limit)
        val list = (0 until arr.length()).mapNotNull { i ->
            runCatching { parseItem(arr.getJSONObject(i)) }.getOrNull()
        }
        val allPage = if (limit > 0) (total + limit - 1) / limit else 1
        return SearchResult(sourceId, list, total, allPage, limit)
    }

    private fun parseItem(info: JSONObject): MusicItem {
        val types = mutableListOf<MusicQuality>()
        MINFO.findAll(info.optString("N_MINFO")).forEach { m ->
            val size = m.groupValues[4].uppercase()
            when (m.groupValues[2]) {
                "4000" -> types.add(MusicQuality("flac24bit", size))
                "2000" -> types.add(MusicQuality("flac", size))
                "320" -> types.add(MusicQuality("320k", size))
                "128" -> types.add(MusicQuality("128k", size))
            }
        }
        types.reverse()
        return MusicItem(
            source = sourceId,
            songmid = info.optString("MUSICRID").removePrefix("MUSIC_"),
            name = info.optString("SONGNAME"),
            singer = info.optString("ARTIST"),
            albumName = info.optString("ALBUM"),
            interval = formatPlayTime(info.optString("DURATION").toLongOrNull() ?: 0L),
            img = null,
            qualities = types,
        )
    }

    override suspend fun getMusicUrl(songmid: String, quality: String): String? = runCatching {
        // kuwo 网页接口需合法 token，这里用免鉴权的 antiserver（mp3 直链）
        val br = when (quality) {
            "flac24bit", "flac" -> "2000kflac"
            "320k" -> "320kmp3"
            else -> "128kmp3"
        }
        val format = if (br.endsWith("flac")) "flac" else "mp3"
        suspend fun req(target: String) = HttpClient
            .get("http://antiserver.kuwo.cn/anti.s?format=$format&rid=MUSIC_$songmid&type=convert_url3&br=$target")
            .optString("url")
            .takeIf { it.isNotBlank() }
        val url = req(br)
        // flac 常被拒，回退到 320k mp3
        url ?: if (br != "320kmp3") req("320kmp3") else null
    }.getOrNull()

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    private fun formatPlayTime(sec: Long): String {
        if (sec <= 0) return "00:00"
        val m = (sec % 3600) / 60
        val s = sec % 60
        return "%02d:%02d".format(m, s)
    }
}
