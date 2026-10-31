package com.harmoniamusic.tv.data.store

import android.content.Context
import android.content.SharedPreferences
import com.harmoniamusic.tv.data.source.MusicItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地收藏（我喜欢的音乐）。用 SharedPreferences 持久化，零额外依赖。
 * 收藏项不含 qualities，播放时由对应音源重新取流。
 */
class FavoritesStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

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
        if (list.none { it.source == item.source && it.songmid == item.songmid }) {
            list.add(item)
            save(list)
        }
    }

    /** 用重新扫描或在线匹配得到的封面刷新收藏项。 */
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

    fun remove(item: MusicItem) {
        save(get().filterNot { it.source == item.source && it.songmid == item.songmid })
    }

    fun clear() = prefs.edit().putString(KEY, "[]").apply()

    private fun save(list: List<MusicItem>) {
        val arr = JSONArray()
        list.forEach { it ->
            arr.put(
                JSONObject().apply {
                    put("source", it.source)
                    put("songmid", it.songmid)
                    put("name", it.name)
                    put("singer", it.singer)
                    put("albumName", it.albumName)
                    put("interval", it.interval)
                    put("img", it.img ?: "")
                },
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "list"
    }
}
