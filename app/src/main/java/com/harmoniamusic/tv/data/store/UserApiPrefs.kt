package com.harmoniamusic.tv.data.store

import android.content.Context
import android.content.SharedPreferences

/**
 * 用户 API 取流源的选择与启用状态持久化。
 * - active：选中的「优先取流源」脚本 id（空串 = 自动，用所有启用的脚本兜底）；
 * - enabled：各脚本是否参与取流（禁用后不取流，但保留导入/预设）。
 */
class UserApiPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_api_prefs", Context.MODE_PRIVATE)

    fun getActive(): String = prefs.getString(KEY_ACTIVE, "") ?: ""

    fun setActive(id: String) = prefs.edit().putString(KEY_ACTIVE, id).apply()

    fun isEnabled(id: String): Boolean = prefs.getBoolean("enabled_$id", true)

    fun setEnabled(id: String, on: Boolean) =
        prefs.edit().putBoolean("enabled_$id", on).apply()

    companion object {
        private const val KEY_ACTIVE = "active_user_api"
    }
}
