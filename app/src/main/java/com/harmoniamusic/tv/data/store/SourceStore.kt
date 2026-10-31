package com.harmoniamusic.tv.data.store

import android.content.Context
import android.content.SharedPreferences

/**
 * 当前生效的播放源选择。
 * `aggregate` 表示聚合全部内置源；其余值为具体 sourceId。
 */
class SourceStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("source", Context.MODE_PRIVATE)

    fun getActive(): String =
        prefs.getString(KEY_ACTIVE, AGGREGATE).orEmpty().ifBlank { AGGREGATE }

    fun setActive(id: String) {
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    companion object {
        const val AGGREGATE = "aggregate"
        private const val KEY_ACTIVE = "active_source"
    }
}
