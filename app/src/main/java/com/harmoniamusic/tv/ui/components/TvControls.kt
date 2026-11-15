package com.harmoniamusic.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import com.harmoniamusic.tv.ui.theme.LocalTvColors
import com.harmoniamusic.tv.ui.theme.LocalTvMetrics
import com.harmoniamusic.tv.ui.theme.tvFont
import com.harmoniamusic.tv.ui.theme.tvSize

/** 公共 TV 按钮语义：普通、主操作、危险操作。 */
enum class TvButtonTone {
    NEUTRAL,
    PRIMARY,
    DANGER,
}

/** 公共按钮尺寸；只影响高度、内边距和字号，不影响状态样式。 */
enum class TvButtonSize {
    COMPACT,
    REGULAR,
    LARGE,
}

/**
 * 设置页与其他电视页面共用的控件底板。
 *
 * 视觉规则只在这里维护：
 * - 默认：低对比表面，无边框；
 * - 选中：主题色浅底；
 * - 聚焦：主题色实底，不额外绘制白边；
 * - 危险操作：红色文字，聚焦时红色实底。
 */
@Composable
fun TvButtonSurface(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    selected: Boolean = false,
    tone: TvButtonTone = TvButtonTone.NEUTRAL,
    enabled: Boolean = true,
    containerColor: Color? = null,
    onClick: () -> Unit,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val resolvedShape = shape ?: RoundedCornerShape(tv.tvSize(10))
    var focused by remember { mutableStateOf(false) }

    // 禁用时仍保留焦点节点，避免按钮因短暂 loading 失焦后跳到顶部 Tab；
    // 只是拦截点击动作，不真正执行。
    val pressable = Modifier
        .onFocusChanged { focused = it.isFocused }
        .clickable { if (enabled) onClick() }
    val focusFill = if (tone == TvButtonTone.DANGER) colors.danger else colors.selectGreen

    Box(
        modifier = modifier
            .then(pressable)
            .alpha(if (enabled) 1f else 0.42f)
            .background(
                color = when {
                    focused -> focusFill
                    selected -> colors.primarySoft
                    tone == TvButtonTone.PRIMARY -> colors.primarySoft
                    tone == TvButtonTone.DANGER -> colors.danger.copy(alpha = 0.10f)
                    else -> containerColor ?: colors.surface
                },
                shape = resolvedShape,
            ),
        contentAlignment = contentAlignment,
    ) {
        content(focused)
    }
}

/** 标准操作按钮，替代各页面分别实现的 primary/danger 描边按钮。 */
@Composable
fun TvActionButton(
    label: String,
    modifier: Modifier = Modifier,
    tone: TvButtonTone = TvButtonTone.NEUTRAL,
    enabled: Boolean = true,
    size: TvButtonSize = TvButtonSize.REGULAR,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val height = when (size) {
        TvButtonSize.COMPACT -> 40
        TvButtonSize.REGULAR -> 52
        TvButtonSize.LARGE -> 56
    }
    val horizontalPadding = when (size) {
        TvButtonSize.COMPACT -> 14
        TvButtonSize.REGULAR -> 20
        TvButtonSize.LARGE -> 22
    }
    val fontSize = when (size) {
        TvButtonSize.COMPACT -> 15
        TvButtonSize.REGULAR -> 17
        TvButtonSize.LARGE -> 20
    }

    TvButtonSurface(
        modifier = modifier.height(tv.tvSize(height)),
        tone = tone,
        enabled = enabled,
        onClick = onClick,
    ) { focused ->
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = tv.tvSize(horizontalPadding)),
            color = when {
                !enabled -> colors.muted
                focused -> Color.White
                tone == TvButtonTone.DANGER -> colors.danger
                tone == TvButtonTone.PRIMARY -> colors.selectGreen
                else -> colors.subtext
            },
            fontSize = tv.tvFont(fontSize),
            fontWeight = if (focused || tone == TvButtonTone.PRIMARY) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** 标准单选胶囊，适用于音源、深浅模式等横向选项组。 */
@Composable
fun TvChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val height = if (compact) 40 else 56
    val horizontalPadding = if (compact) 17 else 22
    val fontSize = if (compact) 16 else 17
    val shape = RoundedCornerShape(tv.tvSize(if (compact) 18 else 11))

    TvButtonSurface(
        modifier = modifier.height(tv.tvSize(height)),
        shape = shape,
        selected = selected,
        onClick = onClick,
    ) { focused ->
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = tv.tvSize(horizontalPadding)),
            color = when {
                focused -> Color.White
                selected -> colors.selectGreen
                else -> colors.subtext
            },
            fontSize = tv.tvFont(fontSize),
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** 标准图标按钮；首页搜索入口等无文字控件统一使用。 */
@Composable
fun TvIconButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    logicalSize: Int = 58,
    iconLogicalSize: Int = 28,
    shape: Shape? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current

    TvButtonSurface(
        modifier = modifier.size(tv.tvSize(logicalSize)),
        shape = shape ?: RoundedCornerShape(tv.tvSize(10)),
        enabled = enabled,
        onClick = onClick,
    ) { focused ->
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> colors.muted
                focused -> Color.White
                else -> colors.subtext
            },
            modifier = Modifier.size(tv.tvSize(iconLogicalSize)),
        )
    }
}
