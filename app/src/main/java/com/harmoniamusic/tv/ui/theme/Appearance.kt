package com.harmoniamusic.tv.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 应用外观模式。默认沿用深色，用户可在设置中切换为纯白浅色。 */
enum class AppThemeMode {
    DARK,
    LIGHT,
}

/** 可选的交互主题色。颜色经过深浅模式区分，保证按钮上的白色文字可读。 */
enum class ThemeAccent(
    val label: String,
    val darkColor: Color,
    val lightColor: Color,
) {
    RED("红", Color(0xFFE53935), Color(0xFFD32F2F)),
    GREEN("绿", Color(0xFF0B8A4B), Color(0xFF087F4A)),
    BLUE("蓝", Color(0xFF2F6BDB), Color(0xFF1565C0)),
    PURPLE("紫", Color(0xFF7E57C2), Color(0xFF6A1B9A)),
    ORANGE("橙", Color(0xFFC25E00), Color(0xFFB45309));

    fun colorFor(mode: AppThemeMode): Color =
        if (mode == AppThemeMode.DARK) darkColor else lightColor
}

/**
 * 外观设置的运行时控制器。
 * 设置页只调用这里的切换方法，实际持久化由 TvTheme 统一处理。
 */
class AppearanceController(
    val mode: AppThemeMode,
    val accent: ThemeAccent,
    private val onModeChanged: (AppThemeMode) -> Unit,
    private val onAccentChanged: (ThemeAccent) -> Unit,
) {
    fun selectMode(value: AppThemeMode) = onModeChanged(value)

    fun selectAccent(value: ThemeAccent) = onAccentChanged(value)
}

val LocalAppearanceController = staticCompositionLocalOf {
    AppearanceController(AppThemeMode.DARK, ThemeAccent.RED, {}, {})
}
