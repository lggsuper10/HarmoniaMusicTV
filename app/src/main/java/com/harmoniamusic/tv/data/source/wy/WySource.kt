package com.harmoniamusic.tv.data.source.wy

import android.util.Log
import com.harmoniamusic.tv.data.net.HttpClient
import com.harmoniamusic.tv.data.source.BoardInfo
import com.harmoniamusic.tv.data.source.MusicItem
import com.harmoniamusic.tv.data.source.MusicQuality
import com.harmoniamusic.tv.data.source.MusicSource
import com.harmoniamusic.tv.data.source.PlaylistInfo
import com.harmoniamusic.tv.data.source.SearchResult
import org.json.JSONArray
import org.json.JSONObject

/**
 * 网易云直连音源（Kotlin 重写自 RN utils/musicSdk/wy 目录）。
 *
 * 搜索走 eapi：`/api/search/song/list/page` → POST `http://interface.music.163.com/eapi/batch`，
 * 表单 params = eapi(url, data)。取流走 weapi：`/weapi/song/enhance/player/url/v1`。
 *
 * 说明：RN 内置直连源的 `getMusicUrl` 经由 `apis('wy')` 但该表为空，本实现直接补上 weapi 取流，
 * 端点/音质字段以「在设备上验证为准」（见 M1 验证步骤）。
 */
object WySource : MusicSource {
    override val sourceId = "wy"

    override suspend fun search(keyword: String, page: Int, limit: Int): SearchResult {
        val url = "/api/search/song/list/page"
        val data = mapOf(
            "keyword" to keyword,
            "needCorrect" to "1",
            "channel" to "typing",
            "offset" to (limit * (page - 1)),
            "scene" to "normal",
            "total" to (page == 1),
            "limit" to limit,
        )
        val form = WyCrypto.eapi(url, data)
        val headers = mapOf("origin" to "https://music.163.com")
        val body = HttpClient.postForm(
            "http://interface.music.163.com/eapi/batch",
            form,
            headers,
        )
        val code = body.optInt("code", -1)
        require(code == 200) { "wy search failed: code=$code" }

        val dataObj = body.optJSONObject("data")
            ?: return SearchResult(sourceId, emptyList(), 0, 1, limit)
        val resources = dataObj.optJSONArray("resources") ?: JSONArray()
        val list = (0 until resources.length()).mapNotNull { i ->
            val songData = resources.getJSONObject(i)
                .optJSONObject("baseInfo")
                ?.optJSONObject("simpleSongData") ?: return@mapNotNull null
            parseItem(songData)
        }
        val total = dataObj.optInt("totalCount", 0)
        val allPage = if (limit > 0) ((total + limit - 1) / limit) else 1
        return SearchResult(sourceId, list, total, allPage, limit)
    }

    private fun parseItem(item: JSONObject): MusicItem {
        val ar = item.optJSONArray("ar")
        val singer = buildString {
            ar?.let {
                for (i in 0 until it.length()) {
                    if (i > 0) append("、")
                    append(it.getJSONObject(i).optString("name"))
                }
            }
        }
        val al = item.optJSONObject("al")
        val privilege = item.optJSONObject("privilege")
        val types = mutableListOf<MusicQuality>()

        if (privilege?.optString("maxBrLevel") == "hires") {
            val size = item.optJSONObject("hr")?.optInt("size")?.let { sizeFormate(it) }
            types.add(MusicQuality("flac24bit", size))
        }
        when (privilege?.optInt("maxbr")) {
            999000 -> {
                val size = item.optJSONObject("sq")?.optInt("size")?.let { sizeFormate(it) }
                types.add(MusicQuality("flac", size))
            }
            320000 -> {
                val size = item.optJSONObject("h")?.optInt("size")?.let { sizeFormate(it) }
                types.add(MusicQuality("320k", size))
            }
            192000, 128000 -> {
                val size = item.optJSONObject("l")?.optInt("size")?.let { sizeFormate(it) }
                types.add(MusicQuality("128k", size))
            }
        }
        types.reverse()

        return MusicItem(
            source = sourceId,
            songmid = item.optString("id"),
            name = item.optString("name"),
            singer = singer,
            albumName = al?.optString("name") ?: "",
            interval = formatPlayTime(item.optLong("dt") / 1000),
            img = al?.optString("picUrl"),
            qualities = types,
        )
    }

    override suspend fun getMusicUrl(songmid: String, quality: String): String? {
        val level = when (quality) {
            "flac24bit" -> "hires"
            "flac" -> "lossless"
            "320k" -> "higher"
            "128k" -> "standard"
            else -> "standard"
        }
        val data = mapOf(
            "ids" to "[$songmid]",
            "level" to level,
            "encodeType" to "flac",
        )
        val headers = mapOf(
            "origin" to "https://music.163.com",
            "Referer" to "https://music.163.com/",
            "Cookie" to "os=pc",
        )

        // 1) eapi 优先（与搜索同一网关，匿名可取免费曲的播放地址）
        runCatching {
            val body = HttpClient.postForm(
                "http://interface.music.163.com/eapi/batch",
                WyCrypto.eapi("/api/song/enhance/player/url/v1", data),
                headers,
            )
            parseUrl(body)
        }.getOrNull()?.let { return it }

        // 2) weapi 兜底
        return runCatching {
            val (params, encSecKey) = WyCrypto.weapi(data)
            val body = HttpClient.postForm(
                "https://music.163.com/weapi/song/enhance/player/url/v1",
                mapOf("params" to params, "encSecKey" to encSecKey),
                headers,
            )
            parseUrl(body)
        }.getOrNull()
    }

    /**
     * 从 url/v1 响应里取第一个可用播放地址；不可播（版权/试听受限）返回 null。
     *
     * 兼容两种真实结构：
     *  - weapi 直连：`{"code":200,"data":[{"url":"..."}]}` —— data 是数组；
     *  - eapi batch：`{"code":200,"data":{"<url>":{"code":200,"data":[{"url":"..."}]}}}` —— data 是按
     *    请求 URL 聚合的对象，响应再嵌一层。
     * 旧实现只认 `data` 为对象、且只往下挖一层，两种结构都取不到 url（网易云取流永远失败）。
     */
    private fun parseUrl(body: JSONObject): String? {
        if (body.has("code") && body.optInt("code", -1) != 200) return null
        return when (val data = body.opt("data")) {
            is JSONArray -> firstUrlFromArray(data)
            is JSONObject -> {
                // eapi batch：遍历每个接口响应，再向下挖其 data 数组 / 自身 url
                val keys = data.keys()
                while (keys.hasNext()) {
                    val inner = data.optJSONObject(keys.next()) ?: continue
                    firstUrlFromNode(inner)?.let { return it }
                }
                null
            }
            else -> null
        }
    }

    /** 单个接口响应：优先取 data 数组里的 url，否则取自身 url。 */
    private fun firstUrlFromNode(node: JSONObject): String? =
        node.optJSONArray("data")?.let { firstUrlFromArray(it) }
            ?: node.optString("url").takeIf { it.isNotBlank() }

    /** 遍历 data 数组，取第一个非空 url。 */
    private fun firstUrlFromArray(arr: JSONArray): String? {
        for (i in 0 until arr.length()) {
            val node = arr.optJSONObject(i) ?: continue
            node.optString("url").takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    /** 榜单列表（对应 RN `leaderboard.js` 的 list）。 */
    val boards: List<BoardInfo> = listOf(
        BoardInfo("wybsb", "飙升榜", "19723756"),
        BoardInfo("wyrgb", "热歌榜", "3778678"),
        BoardInfo("wyxgb", "新歌榜", "3779629"),
        BoardInfo("wyycb", "原创榜", "2884035"),
        BoardInfo("wygdb", "古典榜", "71384707"),
        BoardInfo("wydouyb", "抖音榜", "2250011882"),
        BoardInfo("wyhyb", "韩语榜", "745956260"),
        BoardInfo("wydianyb", "电音榜", "1978921795"),
        BoardInfo("wydjb", "电竞榜", "2006508653"),
        BoardInfo("wyktvbb", "KTV唛榜", "21845217"),
    )

    override suspend fun getBoards(): List<BoardInfo> = boards

    override suspend fun getBoard(bangid: String, page: Int, limit: Int): SearchResult {
        val url = "/weapi/v3/playlist/detail"
        val data = mapOf("id" to bangid, "n" to 100000, "p" to 1)
        val (params, encSecKey) = WyCrypto.weapi(data)
        val form = mapOf("params" to params, "encSecKey" to encSecKey)
        val headers = mapOf("origin" to "https://music.163.com")
        val body = HttpClient.postForm(
            "https://music.163.com/weapi/v3/playlist/detail",
            form,
            headers,
        )
        if (body.optInt("code", -1) != 200) {
            return SearchResult(sourceId, emptyList(), 0, 1, limit)
        }
        val tracks = body.optJSONObject("playlist")?.optJSONArray("tracks") ?: JSONArray()
        val list = (0 until tracks.length()).mapNotNull { i ->
            runCatching { parseItem(tracks.getJSONObject(i)) }.getOrNull()
        }
        return SearchResult(sourceId, list, list.size, 1, limit)
    }

    override suspend fun getLyric(songmid: String): String? {
        val url = "/weapi/song/lyric"
        val data = mapOf("id" to songmid, "lv" to -1, "kv" to -1, "tv" to -1)
        val (params, encSecKey) = WyCrypto.weapi(data)
        val form = mapOf("params" to params, "encSecKey" to encSecKey)
        val headers = mapOf("origin" to "https://music.163.com")
        val body = HttpClient.postForm("https://music.163.com/weapi/song/lyric", form, headers)
        if (body.optInt("code", -1) != 200) return null
        val lrc = body.optJSONObject("lrc")?.optString("lyric").orEmpty()
        val tlrc = body.optJSONObject("tlyric")?.optString("lyric").orEmpty()
        return mergeLyric(lrc, tlrc).ifBlank { null }
    }

    override suspend fun getRecommendPlaylists(limit: Int): List<PlaylistInfo> {
        val data = mapOf("limit" to limit.toString(), "total" to "true", "n" to "1000")
        val body = runCatching {
            HttpClient.postForm(
                "http://interface.music.163.com/eapi/batch",
                WyCrypto.eapi("/api/personalized/playlist", data),
                mapOf(
                    "origin" to "https://music.163.com",
                    "Referer" to "https://music.163.com/",
                    "Cookie" to "os=pc",
                ),
            )
        }.getOrNull() ?: return emptyList()
        val code = body.optInt("code", -1)
        if (code != 200) {
            Log.w("ZlPlay", "推荐歌单失败 code=$code body=${body.toString().take(300)}")
            return emptyList()
        }
        val arr = body.optJSONArray("result") ?: run {
            Log.w("ZlPlay", "推荐歌单无 result: ${body.toString().take(300)}")
            return emptyList()
        }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val cover = o.optString("picUrl")
            PlaylistInfo(
                source = sourceId,
                id = o.optString("id"),
                name = o.optString("name"),
                cover = cover.ifBlank { null },
                creator = o.optJSONObject("creator")?.optString("nickname").orEmpty(),
                trackCount = o.optInt("trackCount", 0),
            )
        }.take(limit)
    }

    /** 把翻译行按时间戳拼到原文行后；无翻译时原样返回。 */
    private fun mergeLyric(lrc: String, tlrc: String): String {
        if (tlrc.isBlank()) return lrc
        val trans = HashMap<String, String>()
        tlrc.lineSequence().forEach { line ->
            val m = TIME_TAG.find(line) ?: return@forEach
            val text = line.substring((m.range.last + 1).coerceAtMost(line.length)).trim()
            if (text.isNotEmpty()) trans[m.groupValues[1]] = text
        }
        return lrc.lineSequence().joinToString("\n") { line ->
            val t = TIME_TAG.find(line)?.groupValues?.get(1)?.let { trans[it] }
            if (t.isNullOrBlank()) line else "$line\n$t"
        }
    }

    private val TIME_TAG = Regex("\\[(\\d{2}:\\d{2}(?:\\.\\d+)?)]")

    private fun sizeFormate(size: Int): String {
        if (size <= 0) return ""
        val kb = size / 1024.0
        return if (kb < 1024) "%.2f KB".format(kb) else "%.2f MB".format(kb / 1024.0)
    }

    private fun formatPlayTime(sec: Long): String {
        if (sec <= 0) return "00:00"
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
