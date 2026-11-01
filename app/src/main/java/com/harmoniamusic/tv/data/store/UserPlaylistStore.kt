package com.harmoniamusic.tv.data.store

import android.content.Context
import android.content.SharedPreferences
import com.harmoniamusic.tv.data.source.MusicItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 用户自建歌单。歌单保存歌曲快照，不保存临时播放地址；
 * 播放时仍由对应音源重新取流。
 */
data class UserPlaylist(
    val id: String,
    val name: String,
    val songs: List<MusicItem> = emptyList(),
    val createdAt: Long = 0L,
)

class UserPlaylistStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_playlists", Context.MODE_PRIVATE)

    fun get(): List<UserPlaylist> {
        val json = prefs.getString(KEY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                UserPlaylist(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    createdAt = o.optLong("createdAt"),
                    songs = parseSongs(o.optJSONArray("songs") ?: JSONArray()),
                )
            }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun create(name: String): UserPlaylist {
        val playlist = UserPlaylist(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            createdAt = System.currentTimeMillis(),
        )
        save(get() + playlist)
        return playlist
    }

    fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        save(get().map { if (it.id == id) it.copy(name = trimmed) else it })
    }

    fun delete(id: String) {
        save(get().filterNot { it.id == id })
    }

    /** 返回 false 表示歌曲已存在。 */
    fun addSong(id: String, item: MusicItem): Boolean {
        var added = false
        val updated = get().map { playlist ->
            if (playlist.id != id) return@map playlist
            if (playlist.songs.any { it.sameSong(item) }) return@map playlist
            added = true
            playlist.copy(songs = playlist.songs + item)
        }
        if (added) save(updated)
        return added
    }

    fun removeSong(id: String, item: MusicItem) {
        save(
            get().map { playlist ->
                if (playlist.id == id) {
                    playlist.copy(songs = playlist.songs.filterNot { it.sameSong(item) })
                } else {
                    playlist
                }
            },
        )
    }

    fun updateArtwork(items: List<MusicItem>) {
        val artwork = items.filter { !it.img.isNullOrBlank() }
            .associateBy { "${it.source}|${it.songmid}" }
        if (artwork.isEmpty()) return

        val current = get()
        var changed = false
        val updated = current.map { playlist ->
            val songs = playlist.songs.map { song ->
                val cover = artwork["${song.source}|${song.songmid}"]?.img
                if (song.img.isNullOrBlank() && !cover.isNullOrBlank()) {
                    changed = true
                    song.copy(img = cover)
                } else {
                    song
                }
            }
            if (songs != playlist.songs) playlist.copy(songs = songs) else playlist
        }
        if (changed) save(updated)
    }

    private fun parseSongs(arr: JSONArray): List<MusicItem> =
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

    private fun save(playlists: List<UserPlaylist>) {
        val arr = JSONArray()
        playlists.forEach { playlist ->
            val songs = JSONArray()
            playlist.songs.forEach { item ->
                songs.put(
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
            arr.put(
                JSONObject().apply {
                    put("id", playlist.id)
                    put("name", playlist.name)
                    put("createdAt", playlist.createdAt)
                    put("songs", songs)
                },
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun MusicItem.sameSong(other: MusicItem): Boolean =
        source == other.source && songmid == other.songmid

    companion object {
        private const val KEY = "playlists"
    }
}