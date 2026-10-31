package com.harmoniamusic.tv.ui.shell

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import com.harmoniamusic.tv.ui.theme.LocalTvColors
import com.harmoniamusic.tv.ui.theme.LocalTvMetrics
import com.harmoniamusic.tv.ui.theme.tvFont
import com.harmoniamusic.tv.ui.theme.tvSize

/**
 * 顶栏 Tab 条：直接使用 tv-material 自带的 TvTabRow + Tab。
 * 选中态自带下划线指示器，焦点态由 tv-material 自带（聚焦放大/背景），
 * 不再手画选中绿底与白色描边。
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

    // 切页后焦点保持在当前 Tab 上：否则旧内容被移除会让焦点丢失并落到第一个 Tab（表现为按 OK 跳回首页）
    LaunchedEffect(selectedIndex) {
        withFrameNanos { }
        runCatching { tabRequesters.getOrNull(selectedIndex)?.requestFocus() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tv.tvSize(68), vertical = tv.tvSize(18)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "ZL-Music",
            color = colors.text,
            fontSize = tv.tvFont(30),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Box(modifier = Modifier.onFocusChanged { onNavFocusChanged(it.hasFocus) }) {
            TabRow(
                selectedTabIndex = selectedIndex,
            ) {
                TvTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        modifier = Modifier
                            .focusRequester(tabRequesters[index])
                            .let { m -> if (index == selectedIndex && navFocusRequester != null) m.focusRequester(navFocusRequester) else m },
                        selected = index == selectedIndex,
                        onFocus = { onSelect(tab) },
                        onClick = {
                            onSelect(tab)
                            onConfirm(tab)
                        },
                    ) {
                        Text(
                            text = tab.label,
                            color = if (index == selectedIndex) Color.White else colors.subtext,
                            fontSize = tv.tvFont(17),
                            fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = tv.tvSize(18), vertical = tv.tvSize(8)),
                        )
                    }
                }
            }
        }
    }
}
