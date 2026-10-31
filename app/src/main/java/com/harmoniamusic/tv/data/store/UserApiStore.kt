package com.harmoniamusic.tv.data.store

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * 用户导入的 user-api 脚本存储（局域网导入 / 扫码导入落盘）。
 * 引擎绑定后，可按此列表逐个注入 JS 沙箱执行。
 */
class UserApiStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_apis", Context.MODE_PRIVATE)

    fun list(): List<String> {
        val json = prefs.getString(KEY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun add(script: String) {
        if (script.isBlank()) return
        val all = list().toMutableList()
        if (all.none { it == script }) {
            all.add(script)
            prefs.edit().putString(KEY, JSONArray(all).toString()).apply()
        }
    }

    fun clear() = prefs.edit().putString(KEY, "[]").apply()

    /** 删除第 index 个导入脚本。 */
    fun removeAt(index: Int) {
        val all = list().toMutableList()
        if (index in all.indices) {
            all.removeAt(index)
            saveAll(all)
        }
    }

    /** 整体覆盖保存脚本列表。 */
    fun saveAll(scripts: List<String>) {
        prefs.edit().putString(KEY, JSONArray(scripts).toString()).apply()
    }

    companion object {
        private const val KEY = "scripts"
    }
}
