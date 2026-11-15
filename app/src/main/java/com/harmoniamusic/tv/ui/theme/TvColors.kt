package com.harmoniamusic.tv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * TV 深色主题色板，照搬自 RN 版 `theme/tv.ts` 的 `tvColors`。
 * rgba 已转换为 ARGB（0xAARRGGBB）。
 */
data class TvColorScheme(
    val isDark: Boolean,
    val bg: Color,
    val bgDeep: Color,
    val bgWarm: Color,
    val bgElevated: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val card: Color,
    val cardBorder: Color,
    val surfaceWarm: Color,
    val surfaceRed: Color,
    val glass: Color,
    val glassHigh: Color,
    val text: Color,
    val subtext: Color,
    val dimText: Color,
    val primary: Color,
    val primaryHigh: Color,
    val focus: Color,
    val primarySoft: Color,
    val blue: Color,
    val purple: Color,
    val orange: Color,
    val green: Color,
    val selectGreen: Color,
    val selectGreenFocus: Color,
    val danger: Color,
    val warn: Color,
    val muted: Color,
    val border: Color,
    val line: Color,
    val overlay: Color,
    val lyricDim: Color,
    val lyricActive: Color,
)

val darkTvColors = TvColorScheme(
    isDark = true,
    bg = Color(0xFF0A0D15),
    bgDeep = Color(0xFF060810),
    bgWarm = Color(0xFF111827),
    bgElevated = Color(0xD1161A24),
    surface = Color(0x11FFFFFF),
    surfaceHigh = Color(0x24FFFFFF),
    card = Color(0xFF151A23),
    cardBorder = Color(0xFF2A303B),
    surfaceWarm = Color(0x2191A4C4),
    surfaceRed = Color(0x248AADFF),
    glass = Color(0x15FFFFFF),
    glassHigh = Color(0x26FFFFFF),
    text = Color(0xFFF7F8FB),
    subtext = Color(0xFFC4C9D4),
    dimText = Color(0xFF7D8493),
    primary = Color(0xFF8AADFF),
    primaryHigh = Color(0xFFD8E6FF),
    focus = Color(0xFFFFFFFF),
    primarySoft = Color(0x338AADFF),
    blue = Color(0xFF6EA8FF),
    purple = Color(0xFFB8A6FF),
    orange = Color(0xFFF1C36D),
    green = Color(0xFF77D9A8),
    selectGreen = Color(0xFFE53935),
    selectGreenFocus = Color(0xFFD32F2F),
    danger = Color(0xFFFF6B6B),
    warn = Color(0xFFF1C36D),
    muted = Color(0xFF646B78),
    border = Color(0x29FFFFFF),
    line = Color(0x1FFFFFFF),
    overlay = Color(0x9E04060C),
    lyricDim = Color(0x61F7F8FB),
    lyricActive = Color(0xFFFFFFFF),
)

/** 纯白浅色底：背景保持纯白，仅用浅灰表面和边框区分层级。 */
val lightTvColors = TvColorScheme(
    isDark = false,
    bg = Color(0xFFFFFFFF),
    bgDeep = Color(0xFFFFFFFF),
    bgWarm = Color(0xFFF5F6F8),
    bgElevated = Color(0xF2FFFFFF),
    surface = Color(0x0D000000),
    surfaceHigh = Color(0x1A000000),
    card = Color(0xFFF7F8FA),
    cardBorder = Color(0xFFE7E9ED),
    surfaceWarm = Color(0x14000000),
    surfaceRed = Color(0x14D32F2F),
    glass = Color(0x0A000000),
    glassHigh = Color(0x14000000),
    text = Color(0xFF17191F),
    subtext = Color(0xFF505762),
    dimText = Color(0xFF737B87),
    primary = Color(0xFFD32F2F),
    primaryHigh = Color(0xFFB71C1C),
    focus = Color(0xFF17191F),
    primarySoft = Color(0x1FD32F2F),
    blue = Color(0xFF1565C0),
    purple = Color(0xFF6A1B9A),
    orange = Color(0xFFB45309),
    green = Color(0xFF087F4A),
    selectGreen = Color(0xFFD32F2F),
    selectGreenFocus = Color(0xFFC62828),
    danger = Color(0xFFC62828),
    warn = Color(0xFFA15C00),
    muted = Color(0xFF9AA1AC),
    border = Color(0x1F000000),
    line = Color(0x14000000),
    overlay = Color(0xD9FFFFFF),
    lyricDim = Color(0x6617191F),
    lyricActive = Color(0xFF111318),
)

/** 根据深浅模式与主题色生成完整的 TV 色板。 */
fun buildTvColorScheme(mode: AppThemeMode, accent: ThemeAccent): TvColorScheme {
    val base = if (mode == AppThemeMode.DARK) darkTvColors else lightTvColors
    val accentColor = accent.colorFor(mode)
    val softAlpha = if (mode == AppThemeMode.DARK) 0.20f else 0.12f
    return base.copy(
        primary = accentColor,
        primaryHigh = accentColor,
        primarySoft = accentColor.copy(alpha = softAlpha),
        selectGreen = accentColor,
        selectGreenFocus = accentColor,
        surfaceRed = accentColor.copy(alpha = if (mode == AppThemeMode.DARK) 0.22f else 0.12f),
    )
}
