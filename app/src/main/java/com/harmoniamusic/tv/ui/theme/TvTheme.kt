package com.harmoniamusic.tv.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalContext
import com.harmoniamusic.tv.data.store.AppearanceStore

val LocalTvColors = compositionLocalOf { darkTvColors }
val LocalTvMetrics = compositionLocalOf { TvMetrics(1f, 1f, false) }

/**
 * Android TV 原生框架逻辑：电视端不需要 Material 的点击 ripple / 聚焦白圈。
 * 这些默认指示会叠加到每个可聚焦项上，形成「全加上白边框」并在移动焦点时淹没自定义高亮。
 * 这里提供一个不附加任何绘制节点的空 Indication（Compose 1.7 的 IndicationNodeFactory API），
 * 全局替换掉默认指示，焦点表现完全交给各页面 onFocusChanged 驱动的边框/背景高亮。
 */
private val NoIndication = object : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = System.identityHashCode(this)
}

private fun tvMaterialColorScheme(colors: TvColorScheme, mode: AppThemeMode) =
    if (mode == AppThemeMode.DARK) {
        darkColorScheme(
            primary = colors.primary,
            background = colors.bgDeep,
            surface = colors.bg,
            onPrimary = Color.White,
            onBackground = colors.text,
            onSurface = colors.text,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            background = colors.bgDeep,
            surface = colors.bg,
            onPrimary = Color.White,
            onBackground = colors.text,
            onSurface = colors.text,
        )
    }

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    val metrics = rememberTvMetrics()
    val context = LocalContext.current
    val store = remember(context) { AppearanceStore(context.applicationContext) }
    var mode by remember { mutableStateOf(store.getMode()) }
    var accent by remember { mutableStateOf(store.getAccent()) }
    val colors = remember(mode, accent) { buildTvColorScheme(mode, accent) }
    val controller = remember(mode, accent) {
        AppearanceController(
            mode = mode,
            accent = accent,
            onModeChanged = { value -> store.setMode(value); mode = value },
            onAccentChanged = { value -> store.setAccent(value); accent = value },
        )
    }
    CompositionLocalProvider(
        LocalTvColors provides colors,
        LocalTvMetrics provides metrics,
        LocalAppearanceController provides controller,
        LocalIndication provides NoIndication,
    ) {
        MaterialTheme(colorScheme = tvMaterialColorScheme(colors, mode), content = content)
    }
}
