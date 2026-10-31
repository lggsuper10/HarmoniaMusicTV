package com.harmoniamusic.tv.data.store

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * 持久化「从硬盘添加的本地目录」树形 URI 列表（SharedPreferences）。
 * 配合 SAF 的 persistable URI permission，应用重启后仍可访问这些目录。
 */
class LocalFoldersStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("local_folders", Context.MODE_PRIVATE)

    fun get(): List<String> {
        val arr = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotEmpty() } }
    }

    fun add(uri: String) {
        val set = get().toMutableSet()
        set.add(uri)
        save(set.toList())
    }

    fun remove(uri: String) {
        save(get().filter { it != uri })
    }

    private fun save(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "folders"
    }
}
