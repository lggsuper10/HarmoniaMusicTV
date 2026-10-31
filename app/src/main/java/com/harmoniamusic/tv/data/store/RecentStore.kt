package com.harmoniamusic.tv.data.store

import android.content.Context
import android.content.SharedPreferences
import com.harmoniamusic.tv.data.source.MusicItem
import org.json.JSONArray
import org.json.JSONObject

/** 最近播放：只保存歌曲快照，最多保留 [MAX_ITEMS] 首。 */
class RecentStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("recent_tracks", Context.MODE_PRIVATE)

    fun get(): List<MusicItem> {
        val json = prefs.getString(KEY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                MusicItem(
                    source = o.optString("source"),
                    songmid = o.optString("songmid"),
                    name = o.optString("name"),
                    singer = o.optString("singer"),
                    albumName = o.optString("albumName"),
                    interval = o.optString("interval"),
                    img = o.optString("img").takeIf { it.isNotEmpty() },
                    qualities = emptyList(),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun add(item: MusicItem) {
        val list = get().toMutableList()
        list.removeAll { it.source == item.source && it.songmid == item.songmid }
        list.add(0, item)
        save(list.take(MAX_ITEMS))
    }

    /** 用重新扫描或在线匹配得到的封面刷新历史记录，保留原来的播放顺序。 */
    fun updateArtwork(items: List<MusicItem>) {
        val artwork = items.filter { !it.img.isNullOrBlank() }
            .associateBy { "${it.source}|${it.songmid}" }
        if (artwork.isEmpty()) return
        val current = get()
        val updated = current.map { existing ->
            val cover = artwork["${existing.source}|${existing.songmid}"]?.img
            if (existing.img.isNullOrBlank() && !cover.isNullOrBlank()) existing.copy(img = cover)
            else existing
        }
        if (updated != current) save(updated)
    }

    fun updateArtwork(item: MusicItem) = updateArtwork(listOf(item))

    fun clear() = prefs.edit().putString(KEY, "[]").apply()

    private fun save(list: List<MusicItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("source", item.source)
                    put("songmid", item.songmid)
                    put("name", item.name)
                    put("singer", item.singer)
                    put("albumName", item.albumName)
                    put("interval", item.interval)
                    put("img", item.img ?: "")
                },
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "list"
        private const val MAX_ITEMS = 30
    }
}
