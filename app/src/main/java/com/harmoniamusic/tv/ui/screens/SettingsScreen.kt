package com.harmoniamusic.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmoniamusic.tv.data.lan.LanImportServer
import com.harmoniamusic.tv.data.source.MusicSources
import com.harmoniamusic.tv.data.source.userapi.PresetUserApis
import com.harmoniamusic.tv.data.source.userapi.UserApiManager
import com.harmoniamusic.tv.data.store.SourceStore
import com.harmoniamusic.tv.data.store.UserApiPrefs
import com.harmoniamusic.tv.data.store.UserApiStore
import com.harmoniamusic.tv.data.update.ApkInstaller
import com.harmoniamusic.tv.data.update.UpdateChecker
import com.harmoniamusic.tv.data.update.UpdateDownloader
import com.harmoniamusic.tv.data.update.UpdateInfo
import com.harmoniamusic.tv.ui.state.PlayerViewModel
import com.harmoniamusic.tv.ui.strings.TvText
import com.harmoniamusic.tv.ui.theme.LocalTvColors
import com.harmoniamusic.tv.ui.theme.LocalTvMetrics
import com.harmoniamusic.tv.ui.theme.tvFont
import com.harmoniamusic.tv.ui.theme.tvSize
import kotlinx.coroutines.launch

private enum class SettingsCategory(val title: String, val subtitle: String) {
    SOURCES("音源与取流", "歌曲目录源与音频取流源分开管理，状态清楚可见"),
    PLAYBACK("播放与下载", "下载任务、保存位置与取流回退策略"),
    UPDATE("应用更新", "检查并安装新的 HarmoniaMusic TV"),
    ABOUT("关于", "版本、包名与音源说明"),
}

/**
 * 设置页采用 TV 主流双栏：左侧分类导航，右侧只展示当前分类。
 * 聚焦态才使用绿色实底，选中态只保留描边与色条，避免整页同时出现多个绿色块。
 */
@Composable
fun SettingsScreen(vm: PlayerViewModel = viewModel(), onOpenDownload: () -> Unit = {}) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userApiStore = remember { UserApiStore(context) }
    val userApiPrefs = remember { UserApiPrefs(context) }

    var activeUserApi by remember { mutableStateOf(userApiPrefs.getActive()) }
    var userApiCards by remember { mutableStateOf(vm.userApiList()) }
    var importCount by remember { mutableStateOf(userApiStore.listEntries().size) }
    var categoryKey by rememberSaveable { mutableStateOf(SettingsCategory.SOURCES.name) }
    val category = SettingsCategory.values().firstOrNull { it.name == categoryKey }
        ?: SettingsCategory.SOURCES

    var showImportDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<UserApiManager.ApiInfo?>(null) }
    var deleteTarget by remember { mutableStateOf<UserApiManager.ApiInfo?>(null) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var operationFailed by remember { mutableStateOf(false) }

    var server by remember { mutableStateOf<LanImportServer?>(null) }
    var lanUrl by remember { mutableStateOf<String?>(null) }

    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf(0) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateMsg by remember { mutableStateOf<String?>(null) }

    val activeSource by vm.activeSource.collectAsState()
    val sourceTests by vm.sourceTests.collectAsState()
    val isTestingSource by vm.isTesting.collectAsState()
    val version = remember { UpdateChecker.currentVersion(context) }
    val contentState = rememberLazyListState()
    val firstNavRequester = remember { FocusRequester() }

    fun showResult(message: String, failed: Boolean = false) {
        operationMessage = message
        operationFailed = failed
    }

    fun refreshUserApis() {
        userApiCards = vm.userApiList()
        activeUserApi = userApiPrefs.getActive()
        importCount = userApiStore.listEntries().size
    }

    fun startLan() {
        if (lanUrl != null) return
        val s = LanImportServer { content, name ->
            val result = vm.importUserApi(content, name)
            refreshUserApis()
            val msg = if (result.error == null) "已导入「${result.name}」" else "导入失败：${result.error}"
            showResult(msg, failed = result.error != null)
            result.error
        }
        server = s
        lanUrl = s.start()
        if (lanUrl == null) showResult("无法启动手机导入服务，请检查局域网", failed = true)
    }

    fun stopLan() {
        server?.stop()
        server = null
        lanUrl = null
    }

    LaunchedEffect(Unit) {
        userApiCards = vm.ensureUserApiLoaded()
        activeUserApi = userApiPrefs.getActive()
        importCount = userApiStore.listEntries().size
        withFrameNanos { }
        withFrameNanos { }
        runCatching { firstNavRequester.requestFocus() }
    }
    LaunchedEffect(category) {
        if (contentState.layoutInfo.totalItemsCount > 0) contentState.scrollToItem(0)
    }
    DisposableEffect(Unit) { onDispose { server?.stop() } }

    if (showImportDialog) {
        UserApiImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { name, content ->
                val result = vm.importUserApi(content, name)
                refreshUserApis()
                if (result.error == null) {
                    showResult("已导入「${result.name}」")
                } else {
                    showResult("导入失败：${result.error}", failed = true)
                }
                result.error
            },
        )
    }

    renameTarget?.let { target ->
        UserApiRenameDialog(
            initialName = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                val idx = target.id.removePrefix("imported_").toIntOrNull()
                if (idx != null) {
                    vm.renameImportedUserApi(idx, newName)
                    refreshUserApis()
                    showResult("已重命名为「$newName」")
                }
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { target ->
        SettingsConfirmDialog(
            title = "删除音源？",
            message = "将移除「${target.name}」及其已保存脚本；目录源、海报和歌词不受影响。",
            confirmLabel = "删除",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                val idx = target.id.removePrefix("imported_").toIntOrNull()
                deleteTarget = null
                if (idx != null) {
                    scope.launch {
                        runCatching { vm.removeImportedUserApiAndAwait(idx) }
                            .onSuccess {
                                refreshUserApis()
                                showResult("已删除「${target.name}」")
                            }
                            .onFailure {
                                showResult("删除失败：${it.message ?: "未知错误"}", failed = true)
                            }
                    }
                }
            },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tv.tvSize(68), vertical = tv.tvSize(28)),
        horizontalArrangement = Arrangement.spacedBy(tv.tvSize(44)),
    ) {
        Column(Modifier.width(tv.tvSize(300)).fillMaxHeight()) {
            Text("设置", color = colors.text, fontSize = tv.tvFont(40), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(tv.tvSize(6)))
            Text("HarmoniaMusic", color = colors.dimText, fontSize = tv.tvFont(17))
            Spacer(Modifier.height(tv.tvSize(28)))

            SettingsCategory.values().forEachIndexed { index, item ->
                SettingsNavItem(
                    title = item.title,
                    selected = category == item,
                    onClick = { categoryKey = item.name },
                    focusRequester = if (index == 0) firstNavRequester else null,
                )
                Spacer(Modifier.height(tv.tvSize(10)))
            }

            Spacer(Modifier.weight(1f))
            SettingsCard {
                Text("当前状态", color = colors.dimText, fontSize = tv.tvFont(15))
                Spacer(Modifier.height(tv.tvSize(8)))
                Text(
                    "目录源 · ${MusicSources.displayName(activeSource)}",
                    color = colors.subtext,
                    fontSize = tv.tvFont(17),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(tv.tvSize(5)))
                val apiName = userApiCards.firstOrNull { it.active }?.name
                Text(
                    "优先取流 · ${apiName ?: "自动"}",
                    color = colors.subtext,
                    fontSize = tv.tvFont(17),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        category.title,
                        color = colors.text,
                        fontSize = tv.tvFont(36),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(tv.tvSize(5)))
                    Text(category.subtitle, color = colors.dimText, fontSize = tv.tvFont(17))
                }
                Text("v$version", color = colors.dimText, fontSize = tv.tvFont(16))
            }
            Spacer(Modifier.height(tv.tvSize(20)))

            LazyColumn(
                state = contentState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(tv.tvSize(14)),
            ) {
                when (category) {
                    SettingsCategory.SOURCES -> {
                        item {
                            SettingsSectionTitle(
                                "歌曲目录源",
                                "搜索、榜单、海报和歌词使用这里的目录源；它不代表实际音频流来源。",
                            )
                        }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(12))) {
                                item {
                                    SettingsSourceChip(
                                        title = "聚合音源",
                                        selected = activeSource == SourceStore.AGGREGATE,
                                        onClick = { vm.setActiveSource(SourceStore.AGGREGATE) },
                                    )
                                }
                                items(MusicSources.builtin.keys.toList(), key = { it }) { id ->
                                    SettingsSourceChip(
                                        title = MusicSources.displayName(id),
                                        selected = activeSource == id,
                                        onClick = { vm.setActiveSource(id) },
                                    )
                                }
                            }
                        }
                        item {
                            SettingsSectionTitle(
                                "音频取流源",
                                "播放时先尝试选中的用户源；未命中再按顺序尝试其他启用源，最后回退同目录源的内置直连。",
                            )
                        }
                        if (userApiCards.isEmpty()) {
                            item {
                                SettingsCard {
                                    Text("暂无可用音源", color = colors.subtext, fontSize = tv.tvFont(20))
                                    Spacer(Modifier.height(tv.tvSize(5)))
                                    Text("可以直接导入脚本链接，也可以从手机填写名称和内容。", color = colors.dimText, fontSize = tv.tvFont(16))
                                }
                            }
                        }
                        items(userApiCards, key = { it.id }) { info ->
                            UserApiSettingCard(
                                info = info,
                                onToggle = {
                                    vm.setUserApiEnabled(info.id, !info.enabled)
                                    refreshUserApis()
                                },
                                onActivate = {
                                    vm.setActiveUserApi(info.id)
                                    refreshUserApis()
                                    showResult("优先取流已切换为「${info.name}」")
                                },
                                onRename = { renameTarget = info },
                                onDelete = { deleteTarget = info },
                            )
                        }
                        item {
                            SettingsImportCard(
                                importCount = importCount,
                                lanUrl = lanUrl,
                                onOpenImport = { showImportDialog = true },
                                onToggleLan = { if (lanUrl == null) startLan() else stopLan() },
                            )
                        }
                        operationMessage?.let { message ->
                            item {
                                Text(
                                    message,
                                    color = if (operationFailed) colors.danger else colors.selectGreen,
                                    fontSize = tv.tvFont(17),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        item {
                            SettingsSectionTitle(
                                "网络诊断",
                                "检测内置目录源与已加载用户脚本；失败项会保留具体原因。",
                            )
                        }
                        item {
                            SettingsPillButton(
                                label = if (isTestingSource) "测试中…" else "测试所有源",
                                enabled = !isTestingSource,
                                primary = true,
                                onClick = { vm.testSources() },
                            )
                        }
                        items(sourceTests, key = { it.id }) { result ->
                            SettingsSourceTestRow(
                                result = result,
                                displayName = userApiCards.firstOrNull { it.id == result.id }?.name
                                    ?: MusicSources.displayName(result.id),
                            )
                        }
                    }

                    SettingsCategory.PLAYBACK -> {
                        item {
                            SettingsSectionTitle("下载", "查看任务进度，或从歌单、榜单和播放页加入下载。")
                        }
                        item {
                            SettingsCard {
                                Text("下载管理", color = colors.text, fontSize = tv.tvFont(23), fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(tv.tvSize(5)))
                                Text("保存位置、任务进度与已完成记录都在这里。", color = colors.dimText, fontSize = tv.tvFont(16))
                                Spacer(Modifier.height(tv.tvSize(14)))
                                SettingsPillButton(TvText.downloadManage, primary = true) { onOpenDownload() }
                            }
                        }
                        item {
                            SettingsSectionTitle("播放策略", "当前行为与回退顺序。")
                        }
                        item {
                            SettingsCard {
                                Text("音频流优先级", color = colors.text, fontSize = tv.tvFont(22), fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(tv.tvSize(6)))
                                Text(
                                    "选中的用户源 → 其他已启用用户源 → 同目录源的内置直连。用户源返回地址后如果播放失败，不会自动切换到内置源。",
                                    color = colors.subtext,
                                    fontSize = tv.tvFont(17),
                                )
                                Spacer(Modifier.height(tv.tvSize(8)))
                                Text(
                                    "播放页右上角始终显示本次实际取流的来源名称。",
                                    color = colors.dimText,
                                    fontSize = tv.tvFont(16),
                                )
                            }
                        }
                    }

                    SettingsCategory.UPDATE -> {
                        item {
                            SettingsSectionTitle("版本更新", "从 GitHub Release 检查并安装新版本。")
                        }
                        item {
                            SettingsCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("HarmoniaMusic TV", color = colors.text, fontSize = tv.tvFont(23), fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(tv.tvSize(4)))
                                        Text("当前版本 v$version", color = colors.dimText, fontSize = tv.tvFont(16))
                                    }
                                    SettingsStatusBadge("已安装", colors.selectGreen)
                                }
                                Spacer(Modifier.height(tv.tvSize(16)))
                                SettingsPillButton(
                                    label = if (checking) "检查中…" else "检查更新",
                                    enabled = !checking && !downloading,
                                    primary = true,
                                ) {
                                    checking = true
                                    updateMsg = "正在检查更新…"
                                    scope.launch {
                                        val info = UpdateChecker.check(context)
                                        updateInfo = info
                                        updateMsg = if (info == null) {
                                            "已是最新版本（$version）"
                                        } else {
                                            "发现新版本 ${info.version}（当前 ${info.currentVersion}）"
                                        }
                                        checking = false
                                    }
                                }
                            }
                        }
                        updateInfo?.let { info ->
                            item {
                                SettingsPillButton(
                                    label = if (downloading) "下载中 $updateProgress%" else "下载并安装 v${info.version}",
                                    enabled = !downloading,
                                ) {
                                    downloading = true
                                    updateProgress = 0
                                    scope.launch {
                                        val file = UpdateDownloader.download(context, info) { updateProgress = it }
                                        downloading = false
                                        if (file != null) {
                                            updateMsg = "下载完成，正在打开安装器…"
                                            ApkInstaller.install(context, file)
                                        } else {
                                            updateMsg = "更新包下载失败"
                                        }
                                    }
                                }
                            }
                        }
                        updateMsg?.let { message ->
                            item { Text(message, color = colors.selectGreen, fontSize = tv.tvFont(17)) }
                        }
                    }

                    SettingsCategory.ABOUT -> {
                        item {
                            SettingsCard {
                                Text("HarmoniaMusic TV", color = colors.text, fontSize = tv.tvFont(28), fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(tv.tvSize(6)))
                                Text("客厅音乐播放器 · v$version", color = colors.dimText, fontSize = tv.tvFont(17))
                                Spacer(Modifier.height(tv.tvSize(14)))
                                Text("包名  com.harmoniamusic.tv", color = colors.subtext, fontSize = tv.tvFont(16))
                                Text("目录源负责歌曲信息、海报与歌词；用户音源只负责音频取流。", color = colors.subtext, fontSize = tv.tvFont(16))
                            }
                        }
                        item {
                            SettingsSectionTitle("内置取流源", "随应用提供，无需联网导入脚本。")
                        }
                        item {
                            SettingsCard {
                                PresetUserApis.list.forEach { preset ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = tv.tvSize(5)),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(preset.name, color = colors.text, fontSize = tv.tvFont(18))
                                        Text(preset.author, color = colors.dimText, fontSize = tv.tvFont(15))
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(tv.tvSize(24))) }
            }
        }
    }
}

@Composable
private fun SettingsNavItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(12))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(tv.tvSize(62))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(
                when {
                    focused -> colors.selectGreen
                    selected -> colors.surfaceHigh
                    else -> Color.Transparent
                },
                shape,
            )
            .border(if (focused) 3.dp else 0.dp, colors.focus, shape)
            .padding(end = tv.tvSize(18)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(tv.tvSize(4))
                .height(tv.tvSize(28))
                .background(
                    if (selected && !focused) colors.selectGreen else Color.Transparent,
                    RoundedCornerShape(tv.tvSize(2)),
                ),
        )
        Spacer(Modifier.width(tv.tvSize(18)))
        Text(
            text = title,
            color = when {
                focused -> Color(0xFF06130B)
                selected -> colors.text
                else -> colors.subtext
            },
            fontSize = tv.tvFont(21),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String, subtitle: String) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    Column(Modifier.padding(top = tv.tvSize(10), bottom = tv.tvSize(2))) {
        Text(title, color = colors.text, fontSize = tv.tvFont(24), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(tv.tvSize(4)))
        Text(subtitle, color = colors.dimText, fontSize = tv.tvFont(16))
    }
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bgWarm, RoundedCornerShape(tv.tvSize(15)))
            .border(1.dp, colors.border, RoundedCornerShape(tv.tvSize(15)))
            .padding(horizontal = tv.tvSize(22), vertical = tv.tvSize(18)),
        content = content,
    )
}

@Composable
private fun SettingsPillButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(10))
    val pressable = if (enabled) {
        Modifier.onFocusChanged { focused = it.isFocused }.clickable { onClick() }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(pressable)
            .alpha(if (enabled) 1f else 0.42f)
            .height(tv.tvSize(52))
            .background(
                when {
                    focused -> colors.selectGreen
                    primary -> colors.selectGreen.copy(alpha = 0.13f)
                    else -> colors.surfaceHigh
                },
                shape,
            )
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = when {
                    focused -> colors.focus
                    primary -> colors.selectGreen.copy(alpha = 0.55f)
                    danger -> colors.danger.copy(alpha = 0.45f)
                    else -> Color.Transparent
                },
                shape = shape,
            )
            .padding(horizontal = tv.tvSize(20)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> colors.muted
                focused -> Color(0xFF06130B)
                danger -> colors.danger
                primary -> colors.selectGreen
                else -> colors.subtext
            },
            fontSize = tv.tvFont(17),
            fontWeight = if (primary || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingsStatusBadge(label: String, color: Color) {
    val tv = LocalTvMetrics.current
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(tv.tvSize(20)))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(tv.tvSize(20)))
            .padding(horizontal = tv.tvSize(12), vertical = tv.tvSize(5)),
    ) {
        Text(
            text = label,
            color = color,
            fontSize = tv.tvFont(14),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingsSourceChip(title: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(11))
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .height(tv.tvSize(56))
            .background(
                when {
                    focused -> colors.selectGreen
                    selected -> colors.selectGreen.copy(alpha = 0.12f)
                    else -> colors.surface
                },
                shape,
            )
            .border(
                width = if (focused) 3.dp else if (selected) 2.dp else 1.dp,
                color = when {
                    focused -> colors.focus
                    selected -> colors.selectGreen.copy(alpha = 0.55f)
                    else -> colors.border
                },
                shape = shape,
            )
            .padding(horizontal = tv.tvSize(22)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = when {
                focused -> Color(0xFF06130B)
                selected -> colors.selectGreen
                else -> colors.subtext
            },
            fontSize = tv.tvFont(17),
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingsSourceTestRow(result: MusicSources.SourceTestResult, displayName: String) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val statusColor = if (result.ok) colors.selectGreen else colors.danger
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(tv.tvSize(10)))
            .padding(horizontal = tv.tvSize(16), vertical = tv.tvSize(11)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tv.tvSize(14)),
    ) {
        Text(displayName, color = colors.text, fontSize = tv.tvFont(17), modifier = Modifier.weight(1f), maxLines = 1)
        Text(
            text = if (result.ok) {
                if (result.ms > 0) "✓ ${result.ms}ms" else "✓ 已加载"
            } else {
                "✗ ${result.detail}"
            },
            color = statusColor,
            fontSize = tv.tvFont(15),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsImportCard(
    importCount: Int,
    lanUrl: String?,
    onOpenImport: () -> Unit,
    onToggleLan: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("导入音源", color = colors.text, fontSize = tv.tvFont(23), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(tv.tvSize(4)))
                Text("支持脚本 URL 或完整内容；已导入 $importCount 个", color = colors.dimText, fontSize = tv.tvFont(16))
            }
            SettingsStatusBadge("名称可选", colors.selectGreen)
        }
        Spacer(Modifier.height(tv.tvSize(7)))
        Text(
            "留空显示名称时，会读取脚本 @name / SCRIPT_NAME；仍没有名称则根据链接或内容自动生成。",
            color = colors.subtext,
            fontSize = tv.tvFont(16),
        )
        Spacer(Modifier.height(tv.tvSize(14)))
        Row(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(12))) {
            SettingsPillButton("链接 / 脚本导入", primary = true, onClick = onOpenImport)
            SettingsPillButton(if (lanUrl == null) "手机导入" else "停止手机导入", onClick = onToggleLan)
        }
        if (lanUrl != null) {
            Spacer(Modifier.height(tv.tvSize(12)))
            Text("在手机浏览器打开", color = colors.dimText, fontSize = tv.tvFont(15))
            Text(lanUrl, color = colors.selectGreen, fontSize = tv.tvFont(18), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun UserApiSettingCard(
    info: UserApiManager.ApiInfo,
    onToggle: () -> Unit,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val imported = info.id.startsWith("imported_")
    val usable = info.supportSources.isNotEmpty() && info.error == null
    val statusLabel = when {
        !info.enabled -> "已停用"
        info.error != null -> "加载失败"
        !usable -> "未就绪"
        info.active -> "当前取流"
        else -> "已启用"
    }
    val statusColor = when {
        !info.enabled -> colors.dimText
        info.error != null -> colors.danger
        !usable -> colors.warn
        info.active -> colors.selectGreen
        else -> colors.primary
    }
    SettingsCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    info.name,
                    color = colors.text,
                    fontSize = tv.tvFont(22),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(tv.tvSize(5)))
                Text(
                    if (info.supportSources.isEmpty()) {
                        "尚未上报可用音源"
                    } else {
                        "支持 ${info.supportSources.joinToString(" / ") { MusicSources.displayName(it) }}"
                    },
                    color = colors.dimText,
                    fontSize = tv.tvFont(16),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                info.error?.let { error ->
                    Spacer(Modifier.height(tv.tvSize(5)))
                    Text(
                        error,
                        color = colors.danger,
                        fontSize = tv.tvFont(15),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(tv.tvSize(16)))
            SettingsStatusBadge(statusLabel, statusColor)
        }
        Spacer(Modifier.height(tv.tvSize(14)))
        Row(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(10))) {
            SettingsPillButton(if (info.enabled) "停用" else "启用", onClick = onToggle)
            SettingsPillButton(
                label = if (info.active) "当前取流" else "设为当前",
                primary = !info.active,
                enabled = !info.active && info.enabled && usable,
                onClick = onActivate,
            )
            if (imported) {
                SettingsPillButton("重命名", onClick = onRename)
                SettingsPillButton("删除", danger = true, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun UserApiImportDialog(
    onDismiss: () -> Unit,
    onImport: suspend (name: String, content: String) -> String?,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val nameRequester = remember { FocusRequester() }
    val contentRequester = remember { FocusRequester() }
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy) return
        val value = content.trim()
        if (value.isBlank()) {
            errorMessage = "请填写脚本 URL 或脚本内容"
            return
        }
        busy = true
        errorMessage = null
        scope.launch {
            val error = runCatching { onImport(name.trim(), value) }
                .getOrElse { it.message ?: "导入失败" }
            busy = false
            if (error == null) {
                keyboard?.hide()
                onDismiss()
            } else {
                errorMessage = error
            }
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { nameRequester.requestFocus() }
        runCatching { keyboard?.show() }
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.74f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(tv.tvSize(880))
                    .background(colors.bgWarm, RoundedCornerShape(tv.tvSize(18)))
                    .border(2.dp, colors.focus, RoundedCornerShape(tv.tvSize(18)))
                    .padding(tv.tvSize(36)),
            ) {
                Text("导入音源", color = colors.text, fontSize = tv.tvFont(30), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(tv.tvSize(5)))
                Text(
                    "可粘贴脚本 URL 或完整内容。导入前会下载并校验，失败会显示具体原因。",
                    color = colors.dimText,
                    fontSize = tv.tvFont(16),
                )
                Spacer(Modifier.height(tv.tvSize(22)))

                Text("显示名称（可选）", color = colors.subtext, fontSize = tv.tvFont(17), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(tv.tvSize(7)))
                SettingsTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "留空则读取脚本名称，或自动生成",
                    focusRequester = nameRequester,
                    imeAction = ImeAction.Next,
                    maxLength = 40,
                    onDone = { runCatching { contentRequester.requestFocus() } },
                    onKeyDown = { key ->
                        if (key == Key.DirectionDown) {
                            runCatching { contentRequester.requestFocus() }
                            true
                        } else {
                            false
                        }
                    },
                )

                Spacer(Modifier.height(tv.tvSize(18)))
                Text("脚本 URL 或内容", color = colors.subtext, fontSize = tv.tvFont(17), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(tv.tvSize(7)))
                SettingsTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = "https://example.com/latest.js",
                    focusRequester = contentRequester,
                    imeAction = ImeAction.Done,
                    maxLength = 500_000,
                    onDone = { submit() },
                )

                errorMessage?.let { message ->
                    Spacer(Modifier.height(tv.tvSize(10)))
                    Text(
                        message,
                        color = colors.danger,
                        fontSize = tv.tvFont(16),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (busy) {
                    Spacer(Modifier.height(tv.tvSize(10)))
                    Text("正在下载并验证脚本…", color = colors.selectGreen, fontSize = tv.tvFont(16))
                }

                Spacer(Modifier.height(tv.tvSize(24)))
                Row(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(14))) {
                    SettingsPillButton("取消", enabled = !busy) { onDismiss() }
                    SettingsPillButton(
                        label = if (busy) "导入中…" else "验证并导入",
                        primary = true,
                        enabled = !busy,
                        onClick = { submit() },
                    )
                }
            }
        }
    }
}

@Composable
private fun UserApiRenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldRequester = remember { FocusRequester() }
    var name by remember(initialName) { mutableStateOf(initialName) }

    fun submit() {
        val value = name.trim()
        if (value.isNotBlank()) {
            keyboard?.hide()
            onConfirm(value)
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { fieldRequester.requestFocus() }
        runCatching { keyboard?.show() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.74f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(tv.tvSize(700))
                    .background(colors.bgWarm, RoundedCornerShape(tv.tvSize(18)))
                    .border(2.dp, colors.focus, RoundedCornerShape(tv.tvSize(18)))
                    .padding(tv.tvSize(36)),
            ) {
                Text("重命名音源", color = colors.text, fontSize = tv.tvFont(30), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(tv.tvSize(6)))
                Text("名称只影响设置与播放页展示。", color = colors.dimText, fontSize = tv.tvFont(16))
                Spacer(Modifier.height(tv.tvSize(20)))
                SettingsTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "输入显示名称",
                    focusRequester = fieldRequester,
                    imeAction = ImeAction.Done,
                    maxLength = 40,
                    onDone = { submit() },
                )
                Spacer(Modifier.height(tv.tvSize(22)))
                Row(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(14))) {
                    SettingsPillButton("取消") { onDismiss() }
                    SettingsPillButton("保存", primary = true, enabled = name.isNotBlank()) { submit() }
                }
            }
        }
    }
}

@Composable
private fun SettingsConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val cancelRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { cancelRequester.requestFocus() }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.74f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(tv.tvSize(660))
                    .background(colors.bgWarm, RoundedCornerShape(tv.tvSize(18)))
                    .border(2.dp, colors.focus, RoundedCornerShape(tv.tvSize(18)))
                    .padding(tv.tvSize(38)),
            ) {
                Text(title, color = colors.text, fontSize = tv.tvFont(28), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(tv.tvSize(12)))
                Text(message, color = colors.subtext, fontSize = tv.tvFont(18), maxLines = 4, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(tv.tvSize(26)))
                Row(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(14))) {
                    SettingsPillButton("取消", modifier = Modifier.focusRequester(cancelRequester)) { onDismiss() }
                    SettingsPillButton(confirmLabel, danger = true) { onConfirm() }
                }
            }
        }
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Done,
    maxLength: Int = 500_000,
    onDone: () -> Unit = {},
    onKeyDown: (Key) -> Boolean = { false },
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(10))
    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= maxLength) onValueChange(it) },
        singleLine = true,
        textStyle = TextStyle(color = colors.text, fontSize = tv.tvFont(20)),
        cursorBrush = SolidColor(colors.selectGreen),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onDone = { onDone() },
            onNext = { onDone() },
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(tv.tvSize(64))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && onKeyDown(event.key)
            }
            .background(colors.surface, shape)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) colors.focus else colors.border,
                shape = shape,
            )
            .padding(horizontal = tv.tvSize(16), vertical = tv.tvSize(12)),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = colors.dimText,
                        fontSize = tv.tvFont(18),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                innerTextField()
            }
        },
    )
}
