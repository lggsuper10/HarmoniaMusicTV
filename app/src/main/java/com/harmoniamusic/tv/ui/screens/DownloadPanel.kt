package com.harmoniamusic.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmoniamusic.tv.data.download.DownloadTask
import com.harmoniamusic.tv.ui.state.DownloadViewModel
import com.harmoniamusic.tv.ui.strings.TvText
import com.harmoniamusic.tv.ui.theme.LocalTvColors
import com.harmoniamusic.tv.ui.theme.LocalTvMetrics
import com.harmoniamusic.tv.ui.theme.tvFont
import com.harmoniamusic.tv.ui.theme.tvSize
import androidx.compose.runtime.collectAsState

/**
 * 下载管理面板（叠加层）。展示下载任务与进度。
 */
@Composable
fun DownloadPanel(onClose: () -> Unit) {
    val vm: DownloadViewModel = viewModel()
    val tasks by vm.tasks.collectAsState()
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.overlay),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(tv.tvSize(900))
                .clip(RoundedCornerShape(tv.tvSize(20)))
                .background(colors.bgWarm)
                .padding(tv.tvSize(48)),
            verticalArrangement = Arrangement.spacedBy(tv.tvSize(16)),
        ) {
            Text("下载管理", color = colors.text, fontSize = tv.tvFont(32))
            if (tasks.isEmpty()) {
                Text(TvText.noMusic, color = colors.dimText, fontSize = tv.tvFont(22))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(tv.tvSize(8))) {
                    items(tasks) { t -> DownloadRow(t) }
                }
            }
            Spacer(Modifier.height(tv.tvSize(12)))
            var f by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .onFocusChanged { f = it.isFocused }
                    .clickable { onClose() }
                    .background(
                        if (f) colors.surfaceHigh else colors.surface,
                        RoundedCornerShape(tv.tvSize(10)),
                    )
                    .border(
                        width = if (f) 3.dp else 0.dp,
                        color = colors.focus,
                        shape = RoundedCornerShape(tv.tvSize(10)),
                    )
                    .padding(horizontal = tv.tvSize(24), vertical = tv.tvSize(10)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = TvText.back,
                    color = if (f) Color.White else colors.subtext,
                    fontSize = tv.tvFont(18),
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(t: DownloadTask) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(tv.tvSize(8)))
            .padding(tv.tvSize(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${t.item.name} - ${t.item.singer.ifBlank { TvText.unknownSinger }}",
            color = colors.text,
            fontSize = tv.tvFont(20),
            modifier = Modifier.weight(1f),
        )
        Text(
            "${t.progress}% ${t.state}",
            color = colors.dimText,
            fontSize = tv.tvFont(18),
        )
    }
}
