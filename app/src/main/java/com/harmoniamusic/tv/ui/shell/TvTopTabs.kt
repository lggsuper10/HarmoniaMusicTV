package com.harmoniamusic.tv.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harmoniamusic.tv.ui.theme.LocalTvColors
import com.harmoniamusic.tv.ui.theme.LocalTvMetrics
import com.harmoniamusic.tv.ui.theme.tvFont
import com.harmoniamusic.tv.ui.theme.tvSize

/**
 * 顶栏 Tab 条。方向键在 Tab 上直接切页，OK 才把焦点送进内容区。
 *
 * 这里不用 tv-material 的 TabRow：它的 Tab 会先消费方向键，父级 preview 收不到事件，
 * 导致遥控器右键只会移动焦点、不会切页。自绘 Tab 可把按键处理绑在每个可聚焦节点上。
 */
@Composable
fun TvTopTabs(
    selected: TvTab,
    onSelect: (TvTab) -> Unit,
    onConfirm: (TvTab) -> Unit = {},
    navFocusRequester: FocusRequester? = null,
    onNavFocusChanged: (Boolean) -> Unit = {},
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val selectedIndex = TvTab.entries.indexOf(selected).coerceAtLeast(0)
    val tabRequesters = remember { List(TvTab.entries.size) { FocusRequester() } }

    LaunchedEffect(selectedIndex) {
        runCatching { tabRequesters.getOrNull(selectedIndex)?.requestFocus() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tv.tvSize(68), vertical = tv.tvSize(18)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = ZL-Music,
            color = colors.text,
            fontSize = tv.tvFont(30),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(tv.tvSize(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvTab.entries.forEachIndexed { index, tab ->
                TopTabItem(
                    tab = tab,
                    selected = index == selectedIndex,
                    focusModifier = Modifier
                        .focusRequester(tabRequesters[index])
                        .let { modifier ->
                            if (index == selectedIndex && navFocusRequester != null) {
                                modifier.focusRequester(navFocusRequester)
                            } else {
                                modifier
                            }
                        },
                    onNavFocusChanged = onNavFocusChanged,
                    onSelect = onSelect,
                    onConfirm = onConfirm,
                    onRequestFocus = { target ->
                        runCatching { tabRequesters[target].requestFocus() }
                    },
                )
            }
        }
    }
}

@Composable
private fun TopTabItem(
    tab: TvTab,
    selected: Boolean,
    focusModifier: Modifier,
    onNavFocusChanged: (Boolean) -> Unit,
    onSelect: (TvTab) -> Unit,
    onConfirm: (TvTab) -> Unit,
    onRequestFocus: (Int) -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(10))
    val index = TvTab.entries.indexOf(tab)

    Box(
        modifier = focusModifier
            .onFocusChanged {
                focused = it.isFocused
                onNavFocusChanged(it.isFocused)
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.nativeKeyEvent.repeatCount > 0) {
                    return@onPreviewKeyEvent event.key == Key.DirectionLeft ||
                        event.key == Key.DirectionRight
                }
                val target = when (event.key) {
                    Key.DirectionLeft -> index - 1
                    Key.DirectionRight -> index + 1
                    else -> return@onPreviewKeyEvent false
                }
                if (target !in TvTab.entries.indices) return@onPreviewKeyEvent false

                onSelect(TvTab.entries[target])
                onRequestFocus(target)
                true
            }
            .clickable {
                onSelect(tab)
                onConfirm(tab)
            }
            .background(
                when {
                    focused -> colors.surfaceHigh
                    selected -> colors.selectGreen.copy(alpha = 0.16f)
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = colors.focus,
                shape = shape,
            )
            .padding(horizontal = tv.tvSize(20), vertical = tv.tvSize(10)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = tab.label,
                color = when {
                    focused -> Color.White
                    selected -> colors.selectGreen
                    else -> colors.subtext
                },
                fontSize = tv.tvFont(18),
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Normal,
            )
            Spacer(Modifier.height(tv.tvSize(5)))
            Box(
                Modifier
                    .width(tv.tvSize(28))
                    .height(tv.tvSize(3))
                    .background(
                        if (selected) colors.selectGreen else Color.Transparent,
                        RoundedCornerShape(tv.tvSize(2)),
                    ),
            )
        }
    }
}
