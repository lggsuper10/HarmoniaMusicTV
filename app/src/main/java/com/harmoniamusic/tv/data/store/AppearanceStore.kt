package com.harmoniamusic.tv.data.store

import android.content.Context
import android.content.SharedPreferences
import com.harmoniamusic.tv.ui.theme.AppThemeMode
import com.harmoniamusic.tv.ui.theme.ThemeAccent

/**
 * 外观偏好：深浅模式与交互主题色。
 * 使用 SharedPreferences 持久化，重启应用后保留；默认品牌红 + 深色。
 */
class AppearanceStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    fun getMode(): AppThemeMode = enumValue(prefs.getString(KEY_MODE, null), AppThemeMode.DARK)

    fun setMode(value: AppThemeMode) {
        prefs.edit().putString(KEY_MODE, value.name).apply()
    }

    fun getAccent(): ThemeAccent = enumValue(prefs.getString(KEY_ACCENT, null), ThemeAccent.RED)

    fun setAccent(value: ThemeAccent) {
        prefs.edit().putString(KEY_ACCENT, value.name).apply()
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private companion object {
        const val KEY_MODE = "theme_mode"
        const val KEY_ACCENT = "theme_accent"
    }
}
