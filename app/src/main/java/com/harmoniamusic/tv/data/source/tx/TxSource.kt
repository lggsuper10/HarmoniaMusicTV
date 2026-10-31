package com.harmoniamusic.tv.data.source.tx

import android.util.Log
import com.harmoniamusic.tv.data.net.HttpClient
import com.harmoniamusic.tv.data.source.MusicItem
import com.harmoniamusic.tv.data.source.MusicQuality
import com.harmoniamusic.tv.data.source.MusicSource
import com.harmoniamusic.tv.data.source.SearchResult
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * QQ 音乐直连音源（Kotlin 重写自洛雪 musicSdk tx 目录的公开接口）。
 *
 * 搜索走明文接口 `c.y.qq.com/soso/fcgi-bin/client_search_cp`（无需签名）；
 * 取流走 vkey 流程：先取 guid，再向 `u.y.qq.com/.../get_play_url` 申请 purl。
 * 免费曲可拿到 128k/320k 地址；版权受限曲 purl 为空，返回 null。
 */
object TxSource : MusicSource {
    override val sourceId = "tx"

    private const val SEARCH_HOST = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
    private const val VKEY_HOST =
        "https://u.y.qq.com/cgi-bin/musicuinbox/fcgi-bin/get_play_url"
    private const val GUID_HOST =
        "https://u.y.qq.com/cgi-bin/musicuinbox/fcgi-bin/getguid"

    override suspend fun search(keyword: String, page: Int, limit: Int): SearchResult {
        val url = "$SEARCH_HOST?w=${encode(keyword)}&p=$page&n=$limit&format=json"
        val headers = mapOf("Referer" to "https://y.qq.com/")
        val body = HttpClient.get(url, headers)
        val song = body.optJSONObject("data")?.optJSONObject("song")
            ?: return SearchResult(sourceId, emptyList(), 0, 1, limit)
        val listArr = song.optJSONArray("list") ?: JSONArray()
        val total = song.optInt("totalnum", 0)
        val list = (0 until listArr.length()).mapNotNull { i ->
            runCatching { parseItem(listArr.getJSONObject(i)) }.getOrNull()
        }
        val allPage = if (limit > 0) ((total + limit - 1) / limit) else 1
        return SearchResult(sourceId, list, total, allPage, limit)
    }

    private fun parseItem(item: JSONObject): MusicItem {
        val singer = buildString {
            val ar = item.optJSONArray("singer")
            ar?.let {
                for (i in 0 until it.length()) {
                    if (i > 0) append("、")
                    append(it.getJSONObject(i).optString("name"))
                }
            }
        }
        val albummid = item.optString("albummid")
        val img = if (albummid.isNotBlank())
            "https://y.gtimg.cn/music/photo_new/T002R300x300M000$albummid.jpg" else null
        val interval = item.optInt("interval", 0)
        return MusicItem(
            source = sourceId,
            songmid = item.optString("songmid"),
            name = item.optString("songname"),
            singer = singer,
            albumName = item.optString("albumname"),
            interval = formatPlayTime(interval.toLong()),
            img = img,
            qualities = listOf(
                MusicQuality("128k", null),
                MusicQuality("320k", null),
            ),
        )
    }

    override suspend fun getMusicUrl(songmid: String, quality: String): String? {
        val guid = getGuid() ?: return null
        val prefix = when (quality) {
            "flac24bit", "flac" -> "M800"
            "320k" -> "M500"
            else -> "C400"
        }
        val filename = "$prefix$songmid.mp3"
        val data = JSONObject().apply {
            put(
                "req_0",
                JSONObject().apply {
                    put("module", "vkey.GetVkeyServer")
                    put("method", "CgiGetVkey")
                    put(
                        "param",
                        JSONObject().apply {
                            put("filename", JSONArray().put(filename))
                            put("guid", guid)
                            put("songmid", JSONArray().put(songmid))
                            put("songtype", JSONArray().put(0))
                            put("uin", "0")
                            put("loginflag", 1)
                            put("platform", "20")
                        },
                    )
                },
            )
            put(
                "comm",
                JSONObject().apply {
                    put("uin", 0)
                    put("format", "json")
                    put("outCharset", "utf-8")
                    put("notice", 0)
                    put("platform", "yqq.json")
                    put("needNewCode", 0)
                },
            )
        }
        val form = mapOf(
            "g_tk" to "5381",
            "loginuin" to "0",
            "hostuin" to "0",
            "format" to "json",
            "inCharset" to "utf8",
            "outCharset" to "utf-8",
            "notice" to "0",
            "platform" to "yqq.json",
            "needNewCode" to "0",
            "data" to data.toString(),
        )
        val headers = mapOf("Referer" to "https://y.qq.com/")
        val body = HttpClient.postForm(VKEY_HOST, form, headers)
        val req = body.optJSONObject("req_0")
        if (req?.optInt("code", -1) != 0) return null
        val d = req.optJSONObject("data") ?: return null
        val sip = d.optJSONArray("sip")?.optString(0).orEmpty()
        val purl = d.optJSONArray("midurlinfo")?.optJSONObject(0)?.optString("purl").orEmpty()
        if (purl.isBlank()) return null
        return "$sip$purl"
    }

    /** 取 QQ 的匿名 guid（用于申请播放地址）。接口返回 JSONP，取原始文本再解析。 */
    private suspend fun getGuid(): String? {
        val ts = System.currentTimeMillis()
        val raw = runCatching {
            HttpClient.getRaw(
                "$GUID_HOST?r=${abs(ts)}&callback=MusicJsonCallback&format=json",
                mapOf("Referer" to "https://y.qq.com/"),
            )
        }.getOrNull() ?: return null
        val jsonText = if (raw.contains("MusicJsonCallback")) {
            raw.substringAfter("(").substringBeforeLast(")")
        } else {
            raw
        }
        return Regex(""""guid"\s*:\s*"([^"]+)"""").find(jsonText)?.groupValues?.get(1)
    }

    override suspend fun getLyric(songmid: String): String? {
        val url = "$LYRIC_HOST?songmid=$songmid&format=json&nobase64=1&platform=yqq.json"
        val body = HttpClient.get(url, mapOf("Referer" to "https://y.qq.com/"))
        val raw = body.optString("lyric").ifBlank { body.toString() }
        return Regex(""""lyric":"([^"]*)"""").find(raw)?.groupValues?.get(1)
            ?.replace("\\n", "\n")
            ?.takeIf { it.isNotBlank() }
    }

    private const val LYRIC_HOST =
        "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"

    private fun encode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")

    private fun formatPlayTime(sec: Long): String {
        if (sec <= 0) return "00:00"
        val m = (sec % 3600) / 60
        val s = sec % 60
        return "%02d:%02d".format(m, s)
    }
}
