package com.harmoniamusic.tv.data.source.mg

import com.harmoniamusic.tv.data.net.HttpClient
import com.harmoniamusic.tv.data.source.MusicItem
import com.harmoniamusic.tv.data.source.MusicQuality
import com.harmoniamusic.tv.data.source.MusicSource
import com.harmoniamusic.tv.data.source.SearchResult
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 咪咕直连音源（Kotlin 重写自 RN utils/musicSdk/mg/musicSearch.js）。
 *
 * 搜索走 `https://jadeite.migu.cn/music_search/v3/search/searchAll`，
 * 需要 deviceId + timestamp + MD5 sign 请求头（签名参数照搬 RN 的 createSignature）。
 * 取流走用户 API（RN 直连同样未实现），此处返回 null。
 */
object MgSource : MusicSource {
    override val sourceId = "mg"

    private const val DEVICE_ID = "963B7AA0D21511ED807EE5846EC87D20"
    private const val SIGN_KEY = "6cdc72a439cef99a3418d2a78aa28c73"
    private const val SIGN_TAIL = "yyapp2d16148780a1dcc7408e06336b98cfd50"
    private const val MOBILE_UA =
        "Mozilla/5.0 (Linux; U; Android 11.0.0; zh-cn; MI 11 Build/OPR1.170623.032) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30"
    private const val SEARCH_SWITCH =
        """{"song":1,"album":0,"singer":0,"tagSong":1,"mvSong":0,"bestShow":1,"songlist":0,"lyricSong":0}"""

    override suspend fun search(keyword: String, page: Int, limit: Int): SearchResult {
        val time = System.currentTimeMillis().toString()
        val sign = md5("$keyword$SIGN_KEY$SIGN_TAIL$DEVICE_ID$time")
        val url = "https://jadeite.migu.cn/music_search/v3/search/searchAll?isCorrect=0&isCopyright=1" +
            "&searchSwitch=${enc(SEARCH_SWITCH)}&pageSize=$limit&text=${enc(keyword)}" +
            "&pageNo=$page&sort=0&sid=USS"
        val headers = mapOf(
            "uiVersion" to "A_music_3.6.1",
            "deviceId" to DEVICE_ID,
            "timestamp" to time,
            "sign" to sign,
            "channel" to "0146921",
            "User-Agent" to MOBILE_UA,
        )
        val body = HttpClient.get(url, headers)
        require(body.optString("code") == "000000") {
            body.optString("info").ifBlank { "mg search failed" }
        }
        val songData = body.optJSONObject("songResultData")
            ?: return SearchResult(sourceId, emptyList(), 0, 1, limit)
        val total = songData.optInt("totalCount", 0)
        val groups = songData.optJSONArray("resultList")
            ?: return SearchResult(sourceId, emptyList(), total, 1, limit)

        val seen = HashSet<String>()
        val list = mutableListOf<MusicItem>()
        for (i in 0 until groups.length()) {
            val group: JSONArray = groups.optJSONArray(i) ?: continue
            for (j in 0 until group.length()) {
                val data = group.optJSONObject(j) ?: continue
                val copyrightId = data.optString("copyrightId")
                if (data.optString("songId").isBlank() || copyrightId.isBlank()) continue
                if (!seen.add(copyrightId)) continue
                runCatching { parseItem(data) }.getOrNull()?.let { list.add(it) }
            }
        }
        val allPage = if (limit > 0) (total + limit - 1) / limit else 1
        return SearchResult(sourceId, list, total, allPage, limit)
    }

    private fun parseItem(data: JSONObject): MusicItem {
        val types = mutableListOf<MusicQuality>()
        val formats: JSONArray? = data.optJSONArray("audioFormats")
        formats?.let {
            for (i in 0 until it.length()) {
                val f = it.optJSONObject(i) ?: continue
                val size = sizeFormate((f.optLong("asize", 0)).takeIf { s -> s > 0 } ?: f.optLong("isize", 0))
                when (f.optString("formatType")) {
                    "PQ" -> types.add(MusicQuality("128k", size))
                    "HQ" -> types.add(MusicQuality("320k", size))
                    "SQ" -> types.add(MusicQuality("flac", size))
                    "ZQ24" -> types.add(MusicQuality("flac24bit", size))
                }
            }
        }
        var img = data.optString("img3")
            .ifBlank { data.optString("img2") }
            .ifBlank { data.optString("img1") }
            .ifBlank { null }
        if (img != null && !img.startsWith("http")) img = "http://d.musicapp.migu.cn$img"

        return MusicItem(
            source = sourceId,
            songmid = data.optString("songId"),
            name = data.optString("name"),
            singer = singers(data.optJSONArray("singerList")),
            albumName = data.optString("album"),
            interval = formatPlayTime(data.optLong("duration")),
            img = img,
            qualities = types,
        )
    }

    private fun singers(arr: JSONArray?): String {
        arr ?: return ""
        return (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it)?.optString("name") }
            .filter { it.isNotBlank() }
            .joinToString("、")
    }

    override suspend fun getMusicUrl(songmid: String, quality: String): String? = null

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

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
