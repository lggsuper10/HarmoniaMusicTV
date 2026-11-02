package com.harmoniamusic.tv.data.store

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用户导入的 user-api 脚本存储（局域网导入 / 扫码导入落盘）。
 * 新格式同时保存脚本与用户自定义名称；读取时兼容旧版纯字符串数组。
 */
class UserApiStore(context: Context) {
    data class Entry(val script: String, val name: String? = null)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_apis", Context.MODE_PRIVATE)

    fun listEntries(): List<Entry> {
        val json = prefs.getString(KEY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { index ->
                when (val value = arr.opt(index)) {
                    is JSONObject -> value.optString("script").takeIf { it.isNotBlank() }?.let {
                        Entry(it, value.optString("name").trim().ifBlank { null })
                    }
                    is String -> value.takeIf { it.isNotBlank() }?.let { Entry(it) }
                    else -> null
                }
            }
        }.getOrDefault(emptyList())
    }

    fun list(): List<String> = listEntries().map { it.script }

    fun add(script: String, name: String? = null) {
        if (script.isBlank()) return
        val all = listEntries().toMutableList()
        val existing = all.indexOfFirst { it.script == script }
        if (existing >= 0) {
            val cleanName = name?.trim()?.ifBlank { null }
            if (cleanName != null && all[existing].name != cleanName) {
                all[existing] = all[existing].copy(name = cleanName)
                saveEntries(all)
            }
        } else {
            all.add(Entry(script, name?.trim()?.ifBlank { null }))
            saveEntries(all)
        }
    }

    fun updateScriptAt(index: Int, script: String) {
        val all = listEntries().toMutableList()
        if (index in all.indices && script.isNotBlank()) {
            all[index] = all[index].copy(script = script)
            saveEntries(all)
        }
    }

    fun renameAt(index: Int, name: String) {
        val all = listEntries().toMutableList()
        if (index in all.indices) {
            all[index] = all[index].copy(name = name.trim().ifBlank { null })
            saveEntries(all)
        }
    }

    fun clear() = prefs.edit().putString(KEY, "[]").apply()

    /** 删除第 index 个导入脚本。 */
    fun removeAt(index: Int) {
        val all = listEntries().toMutableList()
        if (index in all.indices) {
            all.removeAt(index)
            saveEntries(all)
        }
    }

    /** 兼容旧调用方：覆盖脚本内容，但按位置保留已有自定义名称。 */
    fun saveAll(scripts: List<String>) {
        val old = listEntries()
        saveEntries(scripts.mapIndexed { index, script ->
            Entry(script, old.getOrNull(index)?.name)
        })
    }

    fun saveEntries(entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { entry ->
            arr.put(
                JSONObject()
                    .put("script", entry.script)
                    .apply { entry.name?.let { put("name", it) } },
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "scripts"
    }
}
