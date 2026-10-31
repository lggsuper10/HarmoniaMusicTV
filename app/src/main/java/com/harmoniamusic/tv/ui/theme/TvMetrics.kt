package com.harmoniamusic.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/**
 * 屏幕适配指标，照搬自 RN 版 `theme/tv.ts` 的 `getTVLayoutMetrics`。
 * 基准逻辑分辨率 1280x720；shortEdge<=1080 时 scale 封顶 1.2，4K 按 1080p 基准等比放大。
 */
data class TvMetrics(
    val scale: Float,
    val fontScale: Float,
    val isUhd: Boolean,
)

@Composable
fun rememberTvMetrics(): TvMetrics {
    val config = LocalConfiguration.current
    val width = config.screenWidthDp.toFloat()
    val height = config.screenHeightDp.toFloat()
    val longEdge = max(width, height)
    val shortEdge = min(width, height)
    val logicalScale = min(width / 1280f, height / 720f)
    val isUhd = longEdge >= 3000f
    val minScale = if (shortEdge < 650f) 0.9f else 1f
    val scale = if (shortEdge <= 1080f) {
        max(minScale, min(logicalScale, 1.2f))
    } else {
        max(min(shortEdge / 900f, 3f), 1.2f)
    }
    val fontScale = max(minScale, min(scale * (1.16f / 1.2f), 3f))
    return TvMetrics(scale, fontScale, isUhd)
}

/** 尺寸：返回 dp（对应 RN tvSize 的逻辑点）。 */
fun TvMetrics.tvSize(value: Int): Dp = (value * scale).dp

/** 字号：返回 sp（对应 RN tvFont 的逻辑点）。 */
fun TvMetrics.tvFont(value: Int): TextUnit = (value * fontScale).sp
