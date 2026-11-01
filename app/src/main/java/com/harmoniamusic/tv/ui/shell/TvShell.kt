package com.harmoniamusic.tv.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.harmoniamusic.tv.ui.screens.ChartsScreen
import com.harmoniamusic.tv.ui.screens.HomeScreen
import com.harmoniamusic.tv.ui.screens.MyListScreen
import com.harmoniamusic.tv.ui.screens.PlayerScreen
import com.harmoniamusic.tv.ui.screens.SearchScreen
import com.harmoniamusic.tv.ui.screens.SettingsScreen
import com.harmoniamusic.tv.ui.screens.DownloadPanel
import com.harmoniamusic.tv.ui.state.PlayerViewModel
import com.harmoniamusic.tv.ui.strings.TvText
import com.harmoniamusic.tv.ui.theme.LocalTvColors
import com.harmoniamusic.tv.ui.theme.LocalTvMetrics
import com.harmoniamusic.tv.ui.theme.tvFont
import com.harmoniamusic.tv.ui.theme.tvSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext

/**
 * TV 外壳：6 个 Tab 共用一个屏幕，切 tab 仅用淡入淡出切换可见页（对应 RN Shell 常驻换页）。
 * 播放页作为叠加层（showPlayer 控制），底部常驻 now-playing 栏。
 */
@Composable
fun TvShell() {
    var selectedTab by remember { mutableStateOf(TvTab.HOME) }
    var showPlayer by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }
    val vm: PlayerViewModel = viewModel()
    val colors = LocalTvColors.current
    val playlistTitle by vm.playlistTitle.collectAsState()
    val openPlayerSignal by vm.openPlayerSignal.collectAsState()
    var showExitDialog by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity

    // 焦点层级：true = 焦点在顶部导航 Tab 上；false = 焦点在内容区
    var navActive by remember { mutableStateOf(false) }
    val navFocusRequester = remember { FocusRequester() }

    // 点歌（play/playList/next/prev）都会发出信号，自动打开播放页，
    // 保证即使取流失败也有可见反馈，而不是“点击没反应”。
    LaunchedEffect(openPlayerSignal) {
        if (openPlayerSignal == 0) return@LaunchedEffect
        showPlayer = true
    }

    // 在顶栏按 OK 时把焦点送进内容区，避免用户以为已选中内容区按钮
    var contentFocusTick by remember { mutableStateOf(0) }
    val contentRequester = remember { FocusRequester() }
    LaunchedEffect(contentFocusTick) {
        if (contentFocusTick == 0) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        runCatching { contentRequester.requestFocus() }
    }

    // 播放页 / 下载面板是全屏叠加层：叠加期间不再挂载底层 Tab 与内容，
    // 否则方向键会把焦点移到被盖住、看不见的顶栏或列表项上（表现为焦点消失 / 按 OK 没反应）。
    val overlayVisible = showPlayer || showDownload
    var hadOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(overlayVisible) {
        if (hadOverlay && !overlayVisible) {
            withFrameNanos { }
            withFrameNanos { }
            runCatching { contentRequester.requestFocus() }
        }
        hadOverlay = overlayVisible
    }

    BackHandler {
        when {
            showPlayer -> showPlayer = false
            showDownload -> showDownload = false
            playlistTitle.isNotBlank() -> vm.closePlaylist()
            showExitDialog -> showExitDialog = false
            // 焦点在内容区：返回先把焦点移回顶部导航（停留在当前 Tab，例如“搜索”按钮），
            // 不切换页面；焦点已在导航上：再次返回才弹退出确认。避免误触直接退出，也避免跳回“推荐”。
            !navActive -> runCatching { navFocusRequester.requestFocus() }
            else -> showExitDialog = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.bgDeep)) {
        if (!overlayVisible) Column(modifier = Modifier.fillMaxSize()) {
            TvTopTabs(
                selected = selectedTab,
                onSelect = { selectedTab = it },
                onConfirm = { contentFocusTick++ },
                navFocusRequester = navFocusRequester,
                onNavFocusChanged = { navActive = it },
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusRequester(contentRequester)
                    .focusGroup()
                    .onFocusChanged { navActive = !it.hasFocus },
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier.fillMaxSize(),
                    label = "TvTabSwitch",
                ) { tab ->
                    when (tab) {
                        TvTab.HOME -> HomeScreen(
                            onOpenSearch = {
                                selectedTab = TvTab.SEARCH
                                contentFocusTick++
                            },
                        )
                        TvTab.NEW -> ChartsScreen(focusTick = contentFocusTick)
                        TvTab.MYLIST -> MyListScreen()
                        TvTab.SEARCH -> SearchScreen()
                        TvTab.SETTINGS -> SettingsScreen(onOpenDownload = { showDownload = true })
                    }
                }
            }
        }
        if (showPlayer) {
            PlayerScreen(vm, onBack = { showPlayer = false })
        }
        if (showDownload) {
            DownloadPanel(onClose = { showDownload = false })
        }
        if (showExitDialog) {
            ExitConfirmDialog(
                onExit = { activity?.finish() },
                onCancel = { showExitDialog = false },
            )
        }
    }
}

/**
 * 退出确认弹窗：使用独立窗口 Dialog 隔离底层焦点，遥控才可正常上下左右与 OK 激活。
 * 默认聚焦「取消」，避免误触「退出」；按返回键等同取消。
 */
@Composable
private fun ExitConfirmDialog(onExit: () -> Unit, onCancel: () -> Unit) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val cancelRequester = remember { FocusRequester() }
    var focusExit by remember { mutableStateOf(false) }
    var focusCancel by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { cancelRequester.requestFocus() }
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .background(colors.bgWarm, RoundedCornerShape(tv.tvSize(16)))
                    .border(2.dp, colors.focus, RoundedCornerShape(tv.tvSize(16)))
                    .padding(tv.tvSize(48)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(TvText.exitConfirmTitle, color = colors.text, fontSize = tv.tvFont(30))
                Spacer(Modifier.height(tv.tvSize(18)))
                Text(TvText.exitConfirmMessage, color = colors.subtext, fontSize = tv.tvFont(22))
                Spacer(Modifier.height(tv.tvSize(40)))
                Row(
                    modifier = Modifier.focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(tv.tvSize(24)),
                ) {
                    Box(
                        modifier = Modifier
                            .focusRequester(cancelRequester)
                            .onFocusChanged { focusCancel = it.isFocused }
                            .clickable { onCancel() }
                            .background(
                                if (focusCancel) colors.selectGreen else colors.surfaceHigh,
                                RoundedCornerShape(tv.tvSize(10)),
                            )
                            .border(
                                width = if (focusCancel) 3.dp else 0.dp,
                                color = colors.focus,
                                shape = RoundedCornerShape(tv.tvSize(10)),
                            )
                            .padding(horizontal = tv.tvSize(36), vertical = tv.tvSize(12)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(TvText.cancelAction, color = Color.White, fontSize = tv.tvFont(20))
                    }
                    Box(
                        modifier = Modifier
                            .onFocusChanged { focusExit = it.isFocused }
                            .clickable { onExit() }
                            .background(
                                if (focusExit) colors.danger else colors.surfaceHigh,
                                RoundedCornerShape(tv.tvSize(10)),
                            )
                            .border(
                                width = if (focusExit) 3.dp else 0.dp,
                                color = colors.focus,
                                shape = RoundedCornerShape(tv.tvSize(10)),
                            )
                            .padding(horizontal = tv.tvSize(36), vertical = tv.tvSize(12)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(TvText.exitApp, color = Color.White, fontSize = tv.tvFont(20))
                    }
                }
            }
        }
    }
}
