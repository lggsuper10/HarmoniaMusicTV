package com.harmoniamusic.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.harmoniamusic.tv.ui.state.LocalFolder
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.harmoniamusic.tv.data.lan.LanImportServer
import com.harmoniamusic.tv.data.source.MusicItem
import com.harmoniamusic.tv.data.source.MusicSources
import com.harmoniamusic.tv.data.source.PlaylistInfo
import com.harmoniamusic.tv.data.store.SourceStore
import com.harmoniamusic.tv.data.store.UserApiPrefs
import com.harmoniamusic.tv.data.store.UserApiStore
import com.harmoniamusic.tv.data.queue.PlayQueue
import androidx.compose.foundation.layout.PaddingValues
import com.harmoniamusic.tv.data.source.userapi.UserApiManager
import com.harmoniamusic.tv.data.update.ApkInstaller
import com.harmoniamusic.tv.data.update.UpdateChecker
import com.harmoniamusic.tv.data.update.UpdateDownloader
import com.harmoniamusic.tv.data.update.UpdateInfo
import kotlinx.coroutines.launch
import com.harmoniamusic.tv.ui.shell.TvTab
import com.harmoniamusic.tv.ui.state.DownloadViewModel
import com.harmoniamusic.tv.ui.state.PlayerViewModel
import com.harmoniamusic.tv.ui.strings.TvText
import com.harmoniamusic.tv.ui.theme.LocalTvColors
import com.harmoniamusic.tv.ui.theme.LocalTvMetrics
import com.harmoniamusic.tv.ui.theme.tvFont
import com.harmoniamusic.tv.ui.theme.tvSize
import androidx.compose.runtime.collectAsState
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import com.harmoniamusic.tv.data.source.countAudioFiles
import com.harmoniamusic.tv.data.source.listSubDirs
import com.harmoniamusic.tv.data.source.storageRoots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 首页：今日推荐（当前曲目）+ 推荐歌单网格；点歌单进入曲目列表。
 * 底部 now-playing 栏由 Shell 提供。
 */
@Composable
fun HomeScreen(
    vm: PlayerViewModel = viewModel(),
    onOpenSearch: () -> Unit = {},
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val playlists by vm.recommendPlaylists.collectAsState()
    val current by vm.current.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val detailTitle by vm.playlistTitle.collectAsState()
    val detailSongs by vm.playlistSongs.collectAsState()
    val detailCover by vm.playlistCover.collectAsState()
    val detailMeta by vm.playlistMeta.collectAsState()
    val detailLoading by vm.playlistLoading.collectAsState()

    LaunchedEffect(Unit) { vm.loadRecommendPlaylists() }

    if (detailTitle.isNotBlank()) {
        PlaylistDetailPane(
            title = detailTitle,
            meta = detailMeta,
            cover = detailCover,
            songs = detailSongs,
            loading = detailLoading,
            onBack = { vm.closePlaylist() },
            onPlay = { index -> vm.playList(detailSongs, index) },
            onAddToQueue = { vm.addListToQueue(detailSongs) },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tv.tvSize(68), vertical = tv.tvSize(26)),
    ) {
        Text(TvText.todayRecommend, color = colors.dimText, fontSize = tv.tvFont(22))
        Spacer(Modifier.height(tv.tvSize(12)))

        val item = current
        if (item == null) {
            Text(TvText.livingRoom, color = colors.text, fontSize = tv.tvFont(34))
            Spacer(Modifier.height(tv.tvSize(8)))
            Text(TvText.chooseMusicHint, color = colors.dimText, fontSize = tv.tvFont(20))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tv.tvSize(32)),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, color = colors.text, fontSize = tv.tvFont(34))
                    Spacer(Modifier.height(tv.tvSize(6)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${item.singer.ifBlank { TvText.unknownSinger }} · " +
                                if (isPlaying) TvText.playing else TvText.paused,
                            color = colors.subtext,
                            fontSize = tv.tvFont(20),
                        )
                        if (isPlaying) {
                            Spacer(Modifier.width(tv.tvSize(12)))
                            PlayingBars(color = colors.selectGreen)
                        }
                    }
                    Spacer(Modifier.height(tv.tvSize(16)))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tv.tvSize(14)),
                    ) {
                        HomePill(TvText.play) { vm.openPlayer() }
                        HomePill(TvText.controlPrev) { vm.prev() }
                        HomePill(TvText.controlNext) { vm.next() }
                        HomeIconPill(Icons.Rounded.Search, "搜索") { onOpenSearch() }
                    }
                }
                HomeCover(url = item.img, size = tv.tvSize(220))
            }
        }

        Spacer(Modifier.height(tv.tvSize(26)))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(TvText.recommendSonglist, color = colors.text, fontSize = tv.tvFont(28))
            Spacer(Modifier.weight(1f))
            Text("网易云", color = colors.dimText, fontSize = tv.tvFont(18))
        }
        Spacer(Modifier.height(tv.tvSize(14)))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(18))) {
            items(playlists) { p ->
                HomePlaylistCard(p) { vm.openPlaylist(p) }
            }
        }
    }
}

/** 单行圆角按钮（首页快捷入口）。 */
@Composable
private fun HomePill(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(
                if (focused) colors.surfaceHigh else colors.surface,
                RoundedCornerShape(tv.tvSize(10)),
            )
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = colors.focus,
                shape = RoundedCornerShape(tv.tvSize(10)),
            )
            .padding(horizontal = tv.tvSize(22), vertical = tv.tvSize(10)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (focused) Color.White else colors.subtext,
            fontSize = tv.tvFont(20),
        )
    }
}

/** 首页方形图标按钮（搜索入口）。 */
@Composable
private fun HomeIconPill(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(tv.tvSize(58))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(
                if (focused) colors.surfaceHigh else colors.surface,
                RoundedCornerShape(tv.tvSize(10)),
            )
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = colors.focus,
                shape = RoundedCornerShape(tv.tvSize(10)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (focused) Color.White else colors.subtext,
            modifier = Modifier.size(tv.tvSize(28)),
        )
    }
}

/** 正在播放时的“跳动音柱”小动画。 */
@Composable
private fun PlayingBars(color: Color) {
    val tv = LocalTvMetrics.current
    val barHeight = tv.tvSize(26)
    val transition = rememberInfiniteTransition(label = "playingBars")
    val bars = listOf(
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(360, easing = LinearEasing), RepeatMode.Reverse),
            label = "bar1",
        ),
        transition.animateFloat(
            initialValue = 0.9f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
            label = "bar2",
        ),
        transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
            label = "bar3",
        ),
    )
    Row(
        modifier = Modifier.height(barHeight),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(tv.tvSize(3)),
    ) {
        bars.forEach { bar ->
            Box(
                Modifier
                    .width(tv.tvSize(5))
                    .height(barHeight * bar.value)
                    .background(color, RoundedCornerShape(tv.tvSize(2))),
            )
        }
    }
}

/** 封面图；无图时给占位块。 */
@Composable
private fun HomeCover(url: String?, size: Dp) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val shape = RoundedCornerShape(tv.tvSize(12))
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier = Modifier.size(size).background(colors.surfaceHigh, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text("♪", color = colors.dimText, fontSize = tv.tvFont(28))
        }
    }
}

/** 推荐歌单卡片：封面 + 标题 + 作者。 */
@Composable
private fun HomePlaylistCard(p: PlaylistInfo, onClick: () -> Unit) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(tv.tvSize(184))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(
                if (focused) colors.surfaceHigh else Color.Transparent,
                RoundedCornerShape(tv.tvSize(14)),
            )
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = colors.focus,
                shape = RoundedCornerShape(tv.tvSize(14)),
            )
            .padding(tv.tvSize(6)),
    ) {
        HomeCover(url = p.cover, size = tv.tvSize(172))
        Spacer(Modifier.height(tv.tvSize(8)))
        Text(
            text = p.name,
            color = if (focused) colors.text else colors.subtext,
            fontSize = tv.tvFont(18),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(tv.tvSize(4)))
        Text(
            text = p.creator.ifBlank { "${p.trackCount} 首" },
            color = colors.dimText,
            fontSize = tv.tvFont(15),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 歌单详情：左侧封面与操作，右侧带序号的歌曲列表（对齐 TV 参考稿）。 */
@Composable
private fun PlaylistDetailPane(
    title: String,
    meta: String,
    cover: String?,
    songs: List<MusicItem>,
    loading: Boolean,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onAddToQueue: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val context = LocalContext.current
    val playAllRequester = remember { FocusRequester() }

    LaunchedEffect(title) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { playAllRequester.requestFocus() }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tv.tvSize(68), vertical = tv.tvSize(24)),
    ) {
        Column(
            modifier = Modifier.width(tv.tvSize(430)).fillMaxHeight(),
        ) {
            HomePill("返回") { onBack() }
            Spacer(Modifier.height(tv.tvSize(20)))
            HomeCover(url = cover, size = tv.tvSize(300))
            Spacer(Modifier.height(tv.tvSize(18)))
            Text(
                text = meta,
                color = colors.dimText,
                fontSize = tv.tvFont(18),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(tv.tvSize(8)))
            Text(
                text = title,
                color = colors.text,
                fontSize = tv.tvFont(38),
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(tv.tvSize(22)))
            Row(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(14))) {
                HomePill(
                    label = "播放全部",
                    modifier = Modifier.focusRequester(playAllRequester),
                ) {
                    if (songs.isNotEmpty()) onPlay(0)
                }
                HomePill("加入播放列表") {
                    onAddToQueue()
                    Toast.makeText(context, "已加入播放列表", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Spacer(Modifier.width(tv.tvSize(46)))
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(colors.surfaceHigh),
        )
        Spacer(Modifier.width(tv.tvSize(40)))

        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "歌曲列表",
                    color = colors.text,
                    fontSize = tv.tvFont(30),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (loading) "加载中…" else "已加载 ${songs.size} 首",
                    color = colors.dimText,
                    fontSize = tv.tvFont(18),
                )
            }
            Spacer(Modifier.height(tv.tvSize(8)))
            Text(
                text = "OK 播放整张歌单 · 点击歌曲立即播放",
                color = colors.dimText,
                fontSize = tv.tvFont(17),
            )
            Spacer(Modifier.height(tv.tvSize(10)))
            if (loading && songs.isEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.selectGreen,
                    trackColor = colors.surfaceHigh,
                )
            } else if (songs.isEmpty()) {
                Text(TvText.noMusic, color = colors.dimText, fontSize = tv.tvFont(20))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(tv.tvSize(4)),
                ) {
                    itemsIndexed(songs) { index, song ->
                        DetailSongRow(index, song) { onPlay(index) }
                    }
                }
            }
        }
    }
}

/** 歌单/本地文件夹详情中的歌曲行：序号、歌名、歌手与时长。 */
@Composable
private fun DetailSongRow(
    index: Int,
    item: MusicItem,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .clickable { onClick() }
            .background(
                if (focused) colors.surfaceHigh else Color.Transparent,
                RoundedCornerShape(tv.tvSize(8)),
            )
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = colors.focus,
                shape = RoundedCornerShape(tv.tvSize(8)),
            )
            .padding(horizontal = tv.tvSize(18), vertical = tv.tvSize(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "%02d".format(index + 1),
            color = if (focused) colors.selectGreen else colors.dimText,
            fontSize = tv.tvFont(18),
            modifier = Modifier.width(tv.tvSize(54)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = if (focused) Color.White else colors.text,
                fontSize = tv.tvFont(21),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.singer.ifBlank { TvText.unknownSinger },
                color = colors.subtext,
                fontSize = tv.tvFont(16),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.interval.isNotBlank()) {
            Text(
                text = item.interval,
                color = colors.dimText,
                fontSize = tv.tvFont(17),
            )
        }
    }
}

@Composable
private fun SongRow(item: MusicItem, onClick: () -> Unit) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(
                if (focused) colors.surfaceHigh else Color.Transparent,
                RoundedCornerShape(tv.tvSize(8)),
            )
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = colors.focus,
                shape = RoundedCornerShape(tv.tvSize(8)),
            )
            .padding(horizontal = tv.tvSize(20), vertical = tv.tvSize(12)),
    ) {
        Text(
            text = "${item.name} - ${item.singer.ifBlank { TvText.unknownSinger }}",
            color = if (focused) Color.White else colors.subtext,
            fontSize = tv.tvFont(22),
        )
    }
}

/**
 * 播放页（主流布局）：封面大图 + 暗化背景，顶部歌名/歌手/专辑与状态，
 * 中部封面 + 带时间轴高亮的歌词，底部进度条（方向键快进）+ 控制栏（上/下/播放/模式/收藏/下载/返回）。
 */
@Composable
fun PlayerScreen(vm: PlayerViewModel = viewModel(), onBack: () -> Unit = {}) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val current by vm.current.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val pos by vm.positionMs.collectAsState()
    val dur by vm.durationMs.collectAsState()
    val lyric by vm.lyric.collectAsState()
    val error by vm.playbackError.collectAsState()
    val mode by vm.playMode.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val downloadVm: DownloadViewModel = viewModel()
    val downloadMessage by downloadVm.message.collectAsState()
    val toastContext = LocalContext.current
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    val controlsToggleRequester = remember { FocusRequester() }

    LaunchedEffect(downloadMessage) {
        val msg = downloadMessage ?: return@LaunchedEffect
        Toast.makeText(toastContext, msg, Toast.LENGTH_SHORT).show()
        downloadVm.clearMessage()
    }

    // 进入播放页自动把焦点交给“播放/暂停”按钮，遥控器方向键/OK 立即可用
    val playRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { playRequester.requestFocus() }
    }

    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
            withFrameNanos { }
            runCatching { controlsToggleRequester.requestFocus() }
        }
    }

    // 点歌后若长时间未播放：区分“取流失败”与“准备中”，避免界面像“没反应”一样卡死
    val statusText = when {
        error -> "无法播放：版权受限或取流失败"
        isPlaying -> "正在播放"
        else -> "准备中…"
    }
    val statusColor = if (error) colors.danger else colors.dimText
    val isFav = current?.let { vm.isFavorite(it) } ?: false

    Box(
        modifier = Modifier.fillMaxSize().background(colors.bgDeep),
    ) {
        // 沉浸式背景：封面大图 + 暗化遮罩
        if (!current?.img.isNullOrBlank()) {
            AsyncImage(
                model = current?.img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.22f),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(
                start = tv.tvSize(70),
                end = tv.tvSize(70),
                top = tv.tvSize(56),
                bottom = tv.tvSize(24),
            ),
            verticalArrangement = Arrangement.spacedBy(tv.tvSize(20)),
        ) {
            // 顶部：歌名 / 歌手·专辑 + 来源与状态
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        current?.name ?: TvText.unknownSong,
                        color = colors.text,
                        fontSize = tv.tvFont(52),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(tv.tvSize(8)))
                    Text(
                        buildString {
                            append(current?.singer ?: TvText.unknownSinger)
                            if (!current?.albumName.isNullOrBlank()) append(" · ${current?.albumName}")
                        },
                        color = colors.subtext,
                        fontSize = tv.tvFont(22),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(tv.tvSize(20)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        val streamSourceName = vm.userApiList()
                            .firstOrNull { it.active }?.name
                            ?: MusicSources.displayName(current?.source ?: "")
                        Text(
                            streamSourceName,
                            color = colors.dimText,
                            fontSize = tv.tvFont(18),
                        )
                        Spacer(Modifier.height(tv.tvSize(6)))
                        Text(statusText, color = statusColor, fontSize = tv.tvFont(18))
                    }
                    Spacer(Modifier.width(tv.tvSize(18)))
                    PlayerControlsToggle(
                        visible = controlsVisible,
                        modifier = Modifier.focusRequester(controlsToggleRequester),
                    ) { controlsVisible = !controlsVisible }
                }
            }

            // 中部：封面 + 歌词
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(tv.tvSize(48)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeCover(url = current?.img, size = tv.tvSize(520))
                LyricsView(
                    raw = lyric,
                    positionMs = pos,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }

            if (controlsVisible) {
                // 进度条（可聚焦，左右方向键快进）
                SeekBar(
                    positionMs = pos,
                    durationMs = dur,
                    onSeekBy = { vm.seekBy(it) },
                    modifier = Modifier.fillMaxWidth(),
                )

                // 控制栏
                Row(
                    horizontalArrangement = Arrangement.spacedBy(tv.tvSize(14)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlButton(TvText.controlPrev) { vm.prev() }
                    ControlButton(
                        if (isPlaying) TvText.pause else TvText.playNow,
                        Modifier.focusRequester(playRequester),
                    ) {
                        vm.togglePlay()
                    }
                    ControlButton(TvText.controlNext) { vm.next() }
                    ControlButton(playModeLabel(mode)) { vm.cyclePlayMode() }
                    ControlButton(if (isFav) "♥ 已喜欢" else "♡ 喜欢") {
                        current?.let { vm.toggleFavorite(it) }
                    }
                    ControlButton(TvText.download) { current?.let { downloadVm.download(it) } }
                    ControlButton(TvText.back) { onBack() }
                }
            }
        }
    }
}

/** 播放页右上角：切换底部进度条与控制栏的显示状态。 */
@Composable
private fun PlayerControlsToggle(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(12))
    Box(
        modifier = modifier
            .size(tv.tvSize(58))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onToggle() }
            .background(if (focused) colors.surfaceHigh else colors.surface, shape)
            .border(if (focused) 3.dp else 0.dp, colors.focus, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (visible) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
            contentDescription = if (visible) "隐藏控制栏" else "显示控制栏",
            tint = if (focused) Color.White else colors.subtext,
            modifier = Modifier.size(tv.tvSize(30)),
        )
    }
}

/** 带时间轴高亮的歌词：根据当前播放进度定位当前行并自动滚动居中。 */
@Composable
private fun LyricsView(raw: String, positionMs: Long, modifier: Modifier = Modifier) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val lines = remember(raw) { parseLrc(raw) }
    val listState = rememberLazyListState()
    val currentIndex = remember(positionMs, lines) {
        if (lines.isEmpty()) -1 else lines.indexOfLast { it.first <= positionMs }.coerceAtLeast(0)
    }
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) listState.animateScrollToItem(currentIndex)
    }
    if (lines.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(TvText.noLyric, color = colors.dimText, fontSize = tv.tvFont(22))
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(tv.tvSize(10)),
            contentPadding = PaddingValues(vertical = tv.tvSize(40)),
        ) {
            items(lines.size) { i ->
                val (_, text) = lines[i]
                val cur = i == currentIndex
                Text(
                    text,
                    color = if (cur) colors.selectGreen else colors.subtext,
                    fontSize = if (cur) tv.tvFont(26) else tv.tvFont(20),
                    fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 进度条：可聚焦，获得焦点后左右方向键 ±5 秒快进。 */
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> { onSeekBy(-5_000); true }
                    Key.DirectionRight -> { onSeekBy(5_000); true }
                    else -> false
                }
            }
            .focusable()
            .border(if (focused) 3.dp else 0.dp, colors.focus, RoundedCornerShape(tv.tvSize(10)))
            .padding(horizontal = tv.tvSize(16), vertical = tv.tvSize(10)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tv.tvSize(16)),
    ) {
        Text(formatTime(positionMs), color = colors.dimText, fontSize = tv.tvFont(18))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(tv.tvSize(10)),
            color = colors.selectGreen,
            trackColor = colors.surfaceHigh,
        )
        Text(formatTime(durationMs), color = colors.dimText, fontSize = tv.tvFont(18))
        if (focused) {
            Text("◀ 左右键快进 ▶", color = colors.focus, fontSize = tv.tvFont(16))
        }
    }
}

private fun playModeLabel(mode: PlayQueue.Mode): String = when (mode) {
    PlayQueue.Mode.ORDER -> TvText.orderPlay
    PlayQueue.Mode.SINGLE -> TvText.singleLoop
    PlayQueue.Mode.SHUFFLE -> TvText.random
}

/** 解析 LRC 歌词为 (时间ms, 文本) 列表；一行多时间戳会展开为多行。 */
private fun parseLrc(raw: String): List<Pair<Long, String>> {
    if (raw.isBlank()) return emptyList()
    val result = mutableListOf<Pair<Long, String>>()
    val timeRe = Regex("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]")
    for (line in raw.lines()) {
        val times = timeRe.findAll(line).map { m ->
            val min = m.groupValues[1].toLongOrNull() ?: 0L
            val sec = m.groupValues[2].toDoubleOrNull() ?: 0.0
            min * 60_000 + (sec * 1000).toLong()
        }.toList()
        val text = line.replace(timeRe, "").trim()
        if (text.isNotEmpty()) times.forEach { t -> result.add(t to text) }
    }
    return result.sortedBy { it.first }
}

@Composable
private fun ControlButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(colors.surface, RoundedCornerShape(tv.tvSize(10)))
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = colors.focus,
                shape = RoundedCornerShape(tv.tvSize(10)),
            )
            .padding(horizontal = tv.tvSize(20), vertical = tv.tvSize(12)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = colors.text, fontSize = tv.tvFont(21), maxLines = 1)
    }
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

// ---- 其余 Tab ----
/** 排行榜 Tab：左榜单列表 + 右曲目，点击播放。 */
@Composable
fun HistoryScreen(vm: PlayerViewModel = viewModel()) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val boards by vm.boards.collectAsState()
    val boardList by vm.boardList.collectAsState()
    var selectedBangid by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.loadBoards() }
    LaunchedEffect(boards) {
        if (selectedBangid == null && boards.isNotEmpty()) {
            selectedBangid = boards.first().bangid
            vm.loadBoard(selectedBangid!!)
        }
    }

    Row(Modifier.fillMaxSize().padding(tv.tvSize(68))) {
        LazyColumn(
            modifier = Modifier.width(tv.tvSize(300)).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(tv.tvSize(8)),
        ) {
            items(boards) { b ->
                var f by remember { mutableStateOf(false) }
                val isSel = selectedBangid == b.bangid
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { f = it.isFocused }
                        .clickable {
                            selectedBangid = b.bangid
                            vm.loadBoard(b.bangid)
                        }
                        // Android TV 原生框架逻辑：焦点只由边框+背景高亮表达，不再叠默认白圈。
                        // 选中态（isSel）与聚焦态（f）必须分清——聚焦用白边，选中用常驻绿边+绿字，
                        // 这样焦点移走后仍看得出当前选中的是哪个榜单。
                        .background(
                            if (f) colors.surfaceHigh else if (isSel) colors.surface else Color.Transparent,
                            RoundedCornerShape(tv.tvSize(8)),
                        )
                        .border(
                            width = if (f) 4.dp else if (isSel) 2.dp else 0.dp,
                            color = if (f) colors.focus else colors.selectGreen,
                            shape = RoundedCornerShape(tv.tvSize(8)),
                        )
                        .padding(horizontal = tv.tvSize(20), vertical = tv.tvSize(12)),
                ) {
                    Text(
                        b.name,
                        color = if (f) colors.text else if (isSel) colors.selectGreen else colors.subtext,
                        fontWeight = if (f || isSel) FontWeight.Bold else FontWeight.Normal,
                        fontSize = tv.tvFont(22),
                    )
                }
            }
        }
        Spacer(Modifier.width(tv.tvSize(24)))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(tv.tvSize(8)),
        ) {
            items(boardList) { item ->
                SongRow(item = item) { vm.play(item) }
            }
        }
    }
}

/** 我的歌单 Tab：本地目录导入 + 收藏列表。 */
@Composable
fun MyListScreen(vm: PlayerViewModel = viewModel()) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val favorites by vm.favorites.collectAsState()
    val current by vm.current.collectAsState()
    val localFolders by vm.localFolders.collectAsState()

    val addLocalRequester = remember { FocusRequester() }
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    var openFolder by remember { mutableStateOf<LocalFolder?>(null) }

    LaunchedEffect(Unit) {
        vm.loadFavorites()
        vm.loadLocal()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) showPicker = true
    }

    openFolder?.let { folder ->
        LocalFolderDetailPane(
            folder = folder,
            onBack = { openFolder = null },
            onPlay = { index -> vm.playList(folder.songs, index) },
            onAddToQueue = {
                vm.addListToQueue(folder.songs)
                Toast.makeText(
                    context,
                    "已将 ${folder.name} 加入播放列表（${folder.songs.size} 首）",
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(tv.tvSize(68)),
        verticalArrangement = Arrangement.spacedBy(tv.tvSize(14)),
    ) {
        item { Text(TvText.library, color = colors.text, fontSize = tv.tvFont(32)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(16))) {
                LocalEntryCard(TvText.addLocalSongDisk, Modifier.focusRequester(addLocalRequester)) {
                    if (hasStoragePermission(context)) {
                        showPicker = true
                    } else {
                        permissionLauncher.launch(storagePermission())
                    }
                }
                LocalEntryCard(TvText.importSonglist) { /* TODO: 手机扫码 / 粘贴链接导入歌单 */ }
            }
        }
        if (localFolders.isNotEmpty()) {
            item {
                Text("本地歌单", color = colors.text, fontSize = tv.tvFont(24), fontWeight = FontWeight.Bold)
            }
            items(localFolders, key = { it.uri }) { folder ->
                LocalFolderRow(
                    folder = folder,
                    onOpen = { openFolder = folder },
                    onAddToQueue = {
                        vm.addListToQueue(folder.songs)
                        Toast.makeText(
                            context,
                            "已将 ${folder.name} 加入播放列表（${folder.songs.size} 首）",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onRemove = { vm.removeLocalFolder(folder.uri) },
                )
            }
            item {
                Text(
                    "点击文件夹进入本地歌单，可直接播放或加入播放列表",
                    color = colors.dimText,
                    fontSize = tv.tvFont(17),
                )
            }
        }
        item {
            Text("收藏歌曲", color = colors.text, fontSize = tv.tvFont(24), fontWeight = FontWeight.Bold)
        }
        current?.let { cur ->
            item {
                var f by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .onFocusChanged { f = it.isFocused }
                        .clickable { vm.addFavorite(cur) }
                        .background(colors.selectGreen, RoundedCornerShape(tv.tvSize(10)))
                        .border(
                            width = if (f) 3.dp else 0.dp,
                            color = colors.focus,
                            shape = RoundedCornerShape(tv.tvSize(10)),
                        )
                        .padding(horizontal = tv.tvSize(24), vertical = tv.tvSize(10)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("收藏当前歌曲", color = Color.White, fontSize = tv.tvFont(18))
                }
            }
        }
        item {
            if (favorites.isEmpty()) {
                Text("暂无收藏歌曲", color = colors.dimText, fontSize = tv.tvFont(22))
            }
        }
        items(favorites) { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SongRow(item = item) { vm.play(item) }
                }
                SettingActionButton("删除") { vm.removeFavorite(item) }
            }
        }
    }

    if (showPicker) {
        LocalDirPicker(
            onDismiss = { showPicker = false },
            onConfirm = { dir ->
                vm.addLocalFolder(dir.absolutePath)
                showPicker = false
            },
        )
    }
}

/** 本地目录入口卡（虚线风格占位框）。 */
@Composable
private fun LocalEntryCard(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var f by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { f = it.isFocused }
            .clickable { onClick() }
            .background(colors.surface, RoundedCornerShape(tv.tvSize(10)))
            .border(
                width = if (f) 3.dp else 2.dp,
                color = if (f) colors.focus else colors.subtext,
                shape = RoundedCornerShape(tv.tvSize(10)),
            )
            .padding(tv.tvSize(28)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = colors.subtext, fontSize = tv.tvFont(20))
    }
}

/** 本地文件夹歌单行：打开、加入播放列表、移除。 */
@Composable
private fun LocalFolderRow(
    folder: LocalFolder,
    onOpen: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused }
                .clickable { onOpen() }
                .background(
                    if (focused) colors.surfaceHigh else colors.surface,
                    RoundedCornerShape(tv.tvSize(12)),
                )
                .border(
                    width = if (focused) 3.dp else 0.dp,
                    color = colors.focus,
                    shape = RoundedCornerShape(tv.tvSize(12)),
                )
                .padding(tv.tvSize(14)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeCover(url = folder.songs.firstOrNull()?.img, size = tv.tvSize(92))
            Spacer(Modifier.width(tv.tvSize(18)))
            Column {
                Text(
                    text = folder.name,
                    color = colors.text,
                    fontSize = tv.tvFont(24),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(tv.tvSize(5)))
                Text(
                    text = "${folder.songs.size} 首本地歌曲 · 点击进入歌单",
                    color = colors.dimText,
                    fontSize = tv.tvFont(17),
                )
            }
        }
        Spacer(Modifier.width(tv.tvSize(12)))
        SettingActionButton("加入播放列表") { onAddToQueue() }
        Spacer(Modifier.width(tv.tvSize(10)))
        SettingActionButton("移除") { onRemove() }
    }
}

/** 本地文件夹歌单详情：左侧歌曲列表，右侧当前聚焦歌曲的海报信息。 */
@Composable
private fun LocalFolderDetailPane(
    folder: LocalFolder,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onAddToQueue: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val context = LocalContext.current
    var selectedIndex by remember(folder.uri) { mutableStateOf(0) }
    val selected = folder.songs.getOrNull(selectedIndex)
    val firstSongRequester = remember { FocusRequester() }

    BackHandler { onBack() }
    LaunchedEffect(folder.uri) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { firstSongRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tv.tvSize(68), vertical = tv.tvSize(24)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tv.tvSize(14)),
        ) {
            HomePill("返回") { onBack() }
            Text(
                text = folder.name,
                color = colors.text,
                fontSize = tv.tvFont(32),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${folder.songs.size} 首", color = colors.dimText, fontSize = tv.tvFont(18))
            HomePill("播放全部") { if (folder.songs.isNotEmpty()) onPlay(0) }
            HomePill("加入播放列表") {
                onAddToQueue()
                Toast.makeText(
                    context,
                    "已将 ${folder.name} 加入播放列表（${folder.songs.size} 首）",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        Spacer(Modifier.height(tv.tvSize(18)))
        if (folder.songs.isEmpty()) {
            Text("此文件夹内没有音乐文件", color = colors.dimText, fontSize = tv.tvFont(20))
        } else {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(tv.tvSize(5)),
                ) {
                    itemsIndexed(folder.songs) { index, song ->
                        DetailSongRow(
                            index = index,
                            item = song,
                            modifier = if (index == 0) Modifier.focusRequester(firstSongRequester) else Modifier,
                            onFocused = { selectedIndex = index },
                        ) { onPlay(index) }
                    }
                }
                Spacer(Modifier.width(tv.tvSize(48)))
                Column(
                    modifier = Modifier.width(tv.tvSize(390)).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HomeCover(url = selected?.img, size = tv.tvSize(360))
                    Spacer(Modifier.height(tv.tvSize(22)))
                    Text(
                        text = selected?.name ?: TvText.unknownSong,
                        color = colors.text,
                        fontSize = tv.tvFont(28),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(tv.tvSize(8)))
                    Text(
                        text = selected?.singer ?: TvText.unknownSinger,
                        color = colors.subtext,
                        fontSize = tv.tvFont(20),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!selected?.albumName.isNullOrBlank()) {
                        Spacer(Modifier.height(tv.tvSize(4)))
                        Text(
                            text = selected?.albumName.orEmpty(),
                            color = colors.dimText,
                            fontSize = tv.tvFont(18),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 应用内目录浏览器：这台电视没有系统文件选择器（OPEN_DOCUMENT_TREE 无处理者），
 * 所以自己列出「内部存储 / U 盘」并用遥控器逐级进入，用户确认哪个文件夹就只扫描哪个。
 */
@Composable
private fun LocalDirPicker(
    onDismiss: () -> Unit,
    onConfirm: (File) -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var opened by remember { mutableStateOf<File?>(null) }
    val roots = remember { storageRoots() }
    val entries = remember(opened) { opened?.let { listSubDirs(it) } ?: roots }
    val firstRequester = remember { FocusRequester() }
    var audioCount by remember { mutableStateOf(-1) }

    LaunchedEffect(opened) {
        val dir = opened
        audioCount = if (dir == null) -1 else withContext(Dispatchers.IO) { countAudioFiles(dir) }
        kotlinx.coroutines.delay(120)
        runCatching { firstRequester.requestFocus() }
    }

    BackHandler {
        val dir = opened
        if (dir == null) onDismiss() else opened = parentOf(dir, roots)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
    Column(modifier = Modifier.fillMaxSize().background(colors.bgDeep).padding(tv.tvSize(60))) {
        Text("选择音乐文件夹", color = colors.text, fontSize = tv.tvFont(32), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(tv.tvSize(6)))
        Text(
            text = opened?.absolutePath ?: "存储设备",
            color = colors.subtext,
            fontSize = tv.tvFont(20),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (audioCount >= 0) {
            Text(
                text = if (audioCount > 0) {
                    "此文件夹共 $audioCount 首音乐（含子文件夹），按「导入此文件夹」添加"
                } else {
                    "此文件夹内没有音乐文件"
                },
                color = if (audioCount > 0) colors.primary else colors.dimText,
                fontSize = tv.tvFont(18),
            )
        }
        Spacer(Modifier.height(tv.tvSize(20)))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(tv.tvSize(10)),
        ) {
            if (opened != null) {
                item {
                    DirRow("⬆  上一级", null, Modifier.focusRequester(firstRequester)) {
                        opened = opened?.let { parentOf(it, roots) }
                    }
                }
            }
            itemsIndexed(entries) { index, dir ->
                DirRow(
                    title = dir.name.ifBlank { dir.absolutePath },
                    subtitle = null,
                    modifier = if (opened == null && index == 0) Modifier.focusRequester(firstRequester) else Modifier,
                ) { opened = dir }
            }
            if (entries.isEmpty()) {
                item {
                    Text("（没有可进入的子文件夹）", color = colors.dimText, fontSize = tv.tvFont(20))
                }
            }
        }

        Spacer(Modifier.height(tv.tvSize(16)))
        Row(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(16))) {
            SettingActionButton("取消") { onDismiss() }
            opened?.let { dir ->
                SettingActionButton("导入此文件夹") { onConfirm(dir) }
            }
        }
    }
    }
}

/** 目录选择器中的一行：聚焦高亮，按 OK 进入。 */
@Composable
private fun DirRow(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var f by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { f = it.isFocused }
            .clickable { onClick() }
            .background(colors.surface, RoundedCornerShape(tv.tvSize(10)))
            .border(
                width = if (f) 3.dp else 1.dp,
                color = if (f) colors.focus else colors.surfaceHigh,
                shape = RoundedCornerShape(tv.tvSize(10)),
            )
            .padding(horizontal = tv.tvSize(24), vertical = tv.tvSize(16)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = colors.text, fontSize = tv.tvFont(22))
        subtitle?.let {
            Spacer(Modifier.width(tv.tvSize(16)))
            Text(it, color = colors.dimText, fontSize = tv.tvFont(18))
        }
    }
}

/** 返回上一级；已在某个存储根目录时返回 null（回到根列表）。 */
private fun parentOf(dir: File, roots: List<File>): File? =
    if (roots.any { it.absolutePath == dir.absolutePath }) null else dir.parentFile

private fun storagePermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun hasStoragePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, storagePermission()) == PackageManager.PERMISSION_GRANTED

/** 设置页的音源卡片数据。 */
private data class SourceCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val kind: String = "builtin",
)

/**
 * 设置 Tab：左侧音源列表（可切换，切换后搜索/榜单走该源），
 * 右侧应用更新与手机扫码导入。布局对齐参考图的两栏结构。
 */
@Composable
fun SettingsScreen(vm: PlayerViewModel = viewModel(), onOpenDownload: () -> Unit = {}) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val context = LocalContext.current
    val userApiStore = remember { UserApiStore(context) }
    val userApiPrefs = remember { UserApiPrefs(context) }
    var activeUserApi by remember { mutableStateOf(userApiPrefs.getActive()) }
    var userApiCards by remember { mutableStateOf(vm.userApiList()) }
    fun refreshUserApis() {
        userApiCards = vm.userApiList()
        activeUserApi = userApiPrefs.getActive()
    }
    val activeSource by vm.activeSource.collectAsState()
    var server by remember { mutableStateOf<LanImportServer?>(null) }
    var lanUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf(0) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    val version = remember { UpdateChecker.currentVersion(context) }
    val sourceTests by vm.sourceTests.collectAsState()
    val isTestingSource by vm.isTesting.collectAsState()

    var importCount by remember { mutableStateOf(userApiStore.list().size) }

    val sourceCards = remember(userApiCards) {
        buildList {
            add(
                SourceCard(
                    id = "__hdr_builtin",
                    title = "搜索 / 榜单音源",
                    subtitle = "选中后搜索与榜单走该源（单选）",
                    badge = "",
                    kind = "header",
                ),
            )
            add(SourceCard(SourceStore.AGGREGATE, "聚合音源", "搜索所有内置源（默认）", "预置"))
            MusicSources.builtin.keys.forEach { id ->
                add(
                    SourceCard(
                        id = id,
                        title = MusicSources.displayName(id),
                        subtitle = "",
                        badge = "预置",
                    ),
                )
            }
            if (userApiCards.isNotEmpty()) {
                add(
                    SourceCard(
                        id = "__hdr_api",
                        title = "取流音源 · 用户 API",
                        subtitle = "选中作为优先取流（绕过会员限制，独立于上方搜索源）",
                        badge = "",
                        kind = "header",
                    ),
                )
            }
            userApiCards.forEach { info ->
                add(
                    SourceCard(
                        id = info.id,
                        title = info.name,
                        subtitle = "取流源 · 支持 ${info.supportSources.joinToString("/")}",
                        badge = if (info.active) "正在使用" else if (!info.enabled) "已停用" else "取流",
                        kind = "userapi",
                    ),
                )
            }
        }
    }

    fun startLan() {
        if (lanUrl == null) {
            val s = LanImportServer { json ->
                userApiStore.add(json)
                scope.launch {
                    vm.reloadUserApiAndAwait()
                    importCount = userApiStore.list().size
                    refreshUserApis()
                }
            }
            lanUrl = s.start()
            server = s
        }
    }
    fun stopLan() {
        server?.stop()
        server = null
        lanUrl = null
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { server?.stop() } }

    val firstSourceRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // 进入设置页后直接把焦点放到音源列表，避免落在右栏按钮上
        withFrameNanos { }
        runCatching { firstSourceRequester.requestFocus() }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tv.tvSize(68), vertical = tv.tvSize(28)),
        horizontalArrangement = Arrangement.spacedBy(tv.tvSize(56)),
    ) {
        Column(Modifier.width(tv.tvSize(500)).fillMaxHeight()) {
            Text(
                text = "设置",
                color = colors.text,
                fontSize = tv.tvFont(38),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(tv.tvSize(6)))
            Text(
                text = "音源、应用更新与手机扫码导入",
                color = colors.dimText,
                fontSize = tv.tvFont(18),
            )
            Spacer(Modifier.height(tv.tvSize(24)))
            Text(
                text = "音源 · 用户 API",
                color = colors.text,
                fontSize = tv.tvFont(28),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(tv.tvSize(10)))
            Text(
                text = "「搜索/榜单音源」与「取流音源」是两种独立选择，可同时各选一个，并非 Bug。",
                color = colors.dimText,
                fontSize = tv.tvFont(16),
            )
            Spacer(Modifier.height(tv.tvSize(6)))
            Text(
                text = "OK 键选择 · 长按 OK 对用户 API 启用/停用（导入项可在右侧删除）",
                color = colors.dimText,
                fontSize = tv.tvFont(16),
            )
            Spacer(Modifier.height(tv.tvSize(16)))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(tv.tvSize(14)),
            ) {
                itemsIndexed(sourceCards) { index, card ->
                    when (card.kind) {
                        "header" -> SourceGroupHeader(title = card.title, subtitle = card.subtitle)
                        else -> SourceCardRow(
                            card = card,
                            active = if (card.kind == "userapi") card.id == activeUserApi else card.id == activeSource,
                            onSelect = {
                                if (card.kind == "userapi") {
                                    vm.setActiveUserApi(card.id)
                                    activeUserApi = card.id
                                } else {
                                    vm.setActiveSource(card.id)
                                }
                            },
                            onLongPress = {
                                if (card.kind == "userapi") {
                                    val cur = userApiCards.firstOrNull { it.id == card.id }?.enabled ?: true
                                    vm.setUserApiEnabled(card.id, !cur)
                                    refreshUserApis()
                                }
                            },
                            modifier = if (card.id == SourceStore.AGGREGATE) {
                                Modifier.focusRequester(firstSourceRequester)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Text(
                text = "更新应用",
                color = colors.text,
                fontSize = tv.tvFont(28),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(tv.tvSize(10)))
            Text(
                text = "从 GitHub Release 检查最新 TV APK，下载后会打开系统安装器。",
                color = colors.dimText,
                fontSize = tv.tvFont(17),
            )
            Spacer(Modifier.height(tv.tvSize(6)))
            Text(
                text = "当前版本 · v$version",
                color = colors.dimText,
                fontSize = tv.tvFont(17),
            )
            Spacer(Modifier.height(tv.tvSize(14)))
            SettingWideButton(if (checking) "检查中..." else "检查更新") {
                if (!checking && !downloading) {
                    checking = true
                    updateMsg = "检查中..."
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
            updateInfo?.let { info ->
                Spacer(Modifier.height(tv.tvSize(12)))
                SettingWideButton(
                    if (downloading) "下载中 $updateProgress%" else "下载并安装 v${info.version}",
                ) {
                    if (!downloading) {
                        downloading = true
                        updateProgress = 0
                        scope.launch {
                            val f = UpdateDownloader.download(context, info) { updateProgress = it }
                            downloading = false
                            if (f != null) {
                                updateMsg = "下载完成，正在安装..."
                                ApkInstaller.install(context, f)
                            } else {
                                updateMsg = "下载失败"
                            }
                        }
                    }
                }
            }
            updateMsg?.let { msg ->
                Spacer(Modifier.height(tv.tvSize(10)))
                Text(msg, color = colors.selectGreen, fontSize = tv.tvFont(17))
            }

            Spacer(Modifier.height(tv.tvSize(34)))
            Text(
                text = "下载管理",
                color = colors.text,
                fontSize = tv.tvFont(28),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(tv.tvSize(10)))
            Text(
                text = "查看下载任务与进度，歌单/详情页可加入下载。",
                color = colors.dimText,
                fontSize = tv.tvFont(17),
            )
            Spacer(Modifier.height(tv.tvSize(14)))
            SettingWideButton(TvText.downloadManage) {
                onOpenDownload()
            }

            Spacer(Modifier.height(tv.tvSize(34)))
            Text(
                text = "导入用户 API",
                color = colors.text,
                fontSize = tv.tvFont(28),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(tv.tvSize(10)))
            Text(
                text = "输入远程 js 脚本地址，导入后在左侧列表启用。",
                color = colors.dimText,
                fontSize = tv.tvFont(17),
            )
            Spacer(Modifier.height(tv.tvSize(14)))
            SettingWideButton(if (lanUrl == null) "手机扫码导入" else "停止手机扫码导入") {
                if (lanUrl == null) startLan() else stopLan()
            }
            lanUrl?.let { url ->
                Spacer(Modifier.height(tv.tvSize(12)))
                Text(
                    text = "请在手机浏览器打开：$url",
                    color = colors.selectGreen,
                    fontSize = tv.tvFont(18),
                )
                Text(
                    text = "已导入音源：$importCount 个",
                    color = colors.dimText,
                    fontSize = tv.tvFont(16),
                )
            }

            Spacer(Modifier.height(tv.tvSize(34)))
            Text(
                text = "用户 API 管理",
                color = colors.text,
                fontSize = tv.tvFont(28),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(tv.tvSize(10)))
            Text(
                text = "点击左侧用户 API 卡片设为优先取流源；此处可启用/停用、删除导入项。",
                color = colors.dimText,
                fontSize = tv.tvFont(17),
            )
            Spacer(Modifier.height(tv.tvSize(12)))
            if (userApiCards.isEmpty()) {
                Text(
                    "暂无用户 API（可扫码导入，或等待初始化完成）",
                    color = colors.dimText,
                    fontSize = tv.tvFont(16),
                )
            }
            for (info in userApiCards) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tv.tvSize(12)),
                ) {
                    Text(
                        text = info.name,
                        color = colors.text,
                        fontSize = tv.tvFont(18),
                        modifier = Modifier.weight(1f),
                    )
                    SettingSmallButton(if (info.enabled) "停用" else "启用") {
                        vm.setUserApiEnabled(info.id, !info.enabled)
                        refreshUserApis()
                    }
                    if (info.id.startsWith("imported_")) {
                        SettingSmallButton("删除") {
                            val idx = info.id.removePrefix("imported_").toIntOrNull() ?: -1
                            vm.removeImportedUserApi(idx)
                            refreshUserApis()
                        }
                    }
                }
                Spacer(Modifier.height(tv.tvSize(8)))
            }

            Spacer(Modifier.height(tv.tvSize(34)))
            Text(
                text = "音源网络测试",
                color = colors.text,
                fontSize = tv.tvFont(28),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(tv.tvSize(10)))
            Text(
                text = "点击后并行检测所有内置音源的连通性。",
                color = colors.dimText,
                fontSize = tv.tvFont(17),
            )
            Spacer(Modifier.height(tv.tvSize(14)))
            SettingWideButton(if (isTestingSource) "测试中..." else "测试所有源") {
                vm.testSources()
            }
            for (r in sourceTests) {
                Spacer(Modifier.height(tv.tvSize(10)))
                SourceTestRow(r = r)
            }
        }
    }
}

/** 分组标题（不可聚焦），用于区分「搜索/榜单音源」与「取流音源」。 */
@Composable
private fun SourceGroupHeader(title: String, subtitle: String) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    Column(Modifier.padding(top = tv.tvSize(14), bottom = tv.tvSize(2))) {
        Text(title, color = colors.selectGreen, fontSize = tv.tvFont(22), fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(tv.tvSize(2)))
            Text(subtitle, color = colors.dimText, fontSize = tv.tvFont(15))
        }
    }
}

/** 音源卡片：选中态绿底，焦点态白色描边；OK 选择，长按（用户 API）启用/停用。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceCardRow(
    card: SourceCard,
    active: Boolean,
    onSelect: () -> Unit,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(14))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onSelect, onLongClick = onLongPress)
            .background(
                when {
                    active -> colors.selectGreen
                    focused -> colors.surfaceHigh
                    else -> Color(0xFF23262D)
                },
                shape,
            )
            .border(if (focused) 3.dp else 0.dp, colors.focus, shape)
            .padding(horizontal = tv.tvSize(26), vertical = tv.tvSize(18)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = card.title,
                color = if (active) Color(0xFF08110C) else colors.text,
                fontSize = tv.tvFont(23),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(tv.tvSize(6)))
            Text(
                text = card.subtitle,
                color = if (active) Color(0xB308110C) else colors.dimText,
                fontSize = tv.tvFont(16),
            )
        }
        Text(
            text = if (active) "正在使用" else card.badge,
            color = if (active) Color(0xFF08110C) else colors.dimText,
            fontSize = tv.tvFont(16),
        )
    }
}

/** 设置页右侧整行按钮。 */
@Composable
private fun SettingWideButton(label: String, onClick: () -> Unit) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(10))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(tv.tvSize(66))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(if (focused) colors.surfaceHigh else Color(0xFF32353C), shape)
            .border(if (focused) 3.dp else 0.dp, colors.focus, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = colors.text, fontSize = tv.tvFont(20))
    }
}

/** 设置页小按钮（用户 API 的启用/停用/删除）。 */
@Composable
private fun SettingSmallButton(label: String, onClick: () -> Unit) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(10))
    Box(
        modifier = Modifier
            .height(tv.tvSize(56))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(if (focused) colors.surfaceHigh else Color(0xFF32353C), shape)
            .border(if (focused) 3.dp else 0.dp, colors.focus, shape)
            .padding(horizontal = tv.tvSize(22)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = colors.text, fontSize = tv.tvFont(18))
    }
}

/** 单个音源连通性测试结果行（名称 + 状态徽标）。 */
@Composable
private fun SourceTestRow(r: MusicSources.SourceTestResult) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val okColor = colors.selectGreen
    val failColor = Color(0xFFFF5252)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tv.tvSize(8), vertical = tv.tvSize(4)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = MusicSources.displayName(r.id),
            color = colors.text,
            fontSize = tv.tvFont(18),
        )
        val (label, color) = if (r.ok) {
            "✓ 通畅 ${r.ms}ms" to okColor
        } else {
            "✗ ${r.detail}" to failColor
        }
        Text(text = label, color = color, fontSize = tv.tvFont(18))
    }
}

@Composable
private fun SettingActionButton(label: String, onClick: () -> Unit) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(10))
    Box(
        modifier = Modifier
            .onFocusChanged { f = it.isFocused }
            .clickable { onClick() }
            .background(if (f) colors.surfaceHigh else colors.surface, shape)
            .border(if (f) 3.dp else 0.dp, colors.focus, shape)
            .padding(horizontal = tv.tvSize(24), vertical = tv.tvSize(10)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (f) Color.White else colors.subtext,
            fontSize = tv.tvFont(18),
        )
    }
}

/**
 * 搜索 Tab：复刻 RN screens/TV/Search.tsx。
 * 左栏：标题 + 输入框（虚拟键盘输入）+ 热门搜索 + 历史 + QWERTY 虚拟键盘；
 * 右栏：来源筛选（ALL/酷我/酷狗/QQ音乐/网易云/咪咕）+ 分页搜索结果。
 */
@Composable
fun SearchScreen(vm: PlayerViewModel = viewModel()) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var text by remember { mutableStateOf("") }
    val results by vm.tvSearchResults.collectAsState()
    val pageInfo by vm.tvSearchPage.collectAsState()
    val loading by vm.tvSearchLoading.collectAsState()
    val source by vm.tvSearchSource.collectAsState()
    val history by vm.tvSearchHistory.collectAsState()
    val kbFocus = remember { FocusRequester() }

    val hotWords = listOf("周杰伦", "林俊杰", "陈奕迅")
    val sourceTabs = listOf(
        PlayerViewModel.SOURCE_ALL, "kw", "kg", "tx", "wy", "mg",
    )

    fun submit(kw: String = text) {
        val k = kw.trim()
        if (k.isNotBlank()) vm.searchTv(k, 1)
    }

    // 进入搜索页时把焦点落在虚拟键盘第一个键上
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { kbFocus.requestFocus() }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tv.tvSize(68), vertical = tv.tvSize(26)),
        horizontalArrangement = Arrangement.spacedBy(tv.tvSize(56)),
    ) {
        // ---- 左栏 ----
        Column(Modifier.weight(0.42f).fillMaxHeight()) {
            Text(
                text = TvText.searchTitle,
                color = colors.text,
                fontSize = tv.tvFont(36),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(tv.tvSize(8)))
            Text(TvText.searchInputHint, color = colors.dimText, fontSize = tv.tvFont(18))
            Spacer(Modifier.height(tv.tvSize(18)))
            Row(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(14))) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(tv.tvSize(12)))
                        .background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(tv.tvSize(12)))
                        .padding(horizontal = tv.tvSize(20), vertical = tv.tvSize(14)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = text.ifBlank { TvText.searchPlaceholder },
                        color = if (text.isBlank()) colors.dimText else colors.text,
                        fontSize = tv.tvFont(20),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SettingActionButton(TvText.search) { submit() }
            }
            Spacer(Modifier.height(tv.tvSize(22)))
            Text(TvText.hotSearch, color = colors.dimText, fontSize = tv.tvFont(18))
            Spacer(Modifier.height(tv.tvSize(10)))
            Row(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(12))) {
                hotWords.forEach { kw ->
                    SearchChip(kw) { text = kw; submit(kw) }
                }
            }
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(tv.tvSize(14)))
                Text(TvText.searchHistoryLabel, color = colors.dimText, fontSize = tv.tvFont(18))
                Spacer(Modifier.height(tv.tvSize(10)))
                Row(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(12))) {
                    history.take(4).forEach { kw ->
                        SearchChip(kw) { text = kw; submit(kw) }
                    }
                    SearchChip(TvText.clear) { vm.clearTvSearchHistory() }
                }
            }
            Spacer(Modifier.height(tv.tvSize(18)))
            SearchKeyboard(
                firstKeyFocus = kbFocus,
                onKey = { ch -> text += ch },
                onBackspace = { if (text.isNotEmpty()) text = text.dropLast(1) },
                onClear = {
                    text = ""
                    vm.clearTvSearch()
                },
                onSubmit = { submit() },
            )
        }
        // ---- 右栏 ----
        Column(Modifier.weight(0.58f).fillMaxHeight()) {
            Row(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(10))) {
                sourceTabs.forEach { id ->
                    SearchSourceTab(
                        label = if (id == PlayerViewModel.SOURCE_ALL) "ALL" else MusicSources.displayName(id),
                        selected = source == id,
                        onClick = { vm.setTvSearchSource(id) },
                    )
                }
            }
            Spacer(Modifier.height(tv.tvSize(20)))
            Text(
                text = TvText.searchResult,
                color = colors.text,
                fontSize = tv.tvFont(28),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(tv.tvSize(6)))
            Text(
                text = "${pageInfo.total} ${TvText.songs} / 第 ${pageInfo.page}/${pageInfo.maxPage} 页",
                color = colors.dimText,
                fontSize = tv.tvFont(18),
            )
            Spacer(Modifier.height(tv.tvSize(12)))
            when {
                loading && results.isEmpty() -> Text(
                    TvText.searching,
                    color = colors.dimText,
                    fontSize = tv.tvFont(20),
                )
                results.isEmpty() -> Text(
                    TvText.emptySearchHint,
                    color = colors.dimText,
                    fontSize = tv.tvFont(20),
                )
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(tv.tvSize(8)),
                ) {
                    itemsIndexed(results) { index, item ->
                        SongRow(item = item) { vm.playList(results, index) }
                    }
                    if (pageInfo.page < pageInfo.maxPage) {
                        item {
                            SettingActionButton(TvText.loadMore) {
                                vm.searchTv(text, pageInfo.page + 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchChip(label: String, onClick: () -> Unit) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(16))
    Box(
        modifier = Modifier
            .onFocusChanged { f = it.isFocused }
            .clickable { onClick() }
            .background(if (f) colors.surfaceHigh else colors.surface, shape)
            .border(if (f) 3.dp else 0.dp, colors.focus, shape)
            .padding(horizontal = tv.tvSize(20), vertical = tv.tvSize(8)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (f) Color.White else colors.subtext,
            fontSize = tv.tvFont(18),
        )
    }
}

@Composable
private fun SearchSourceTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(18))
    Box(
        modifier = Modifier
            .onFocusChanged { f = it.isFocused }
            .clickable { onClick() }
            .background(
                when {
                    selected -> colors.selectGreen
                    f -> colors.surfaceHigh
                    else -> colors.surface
                },
                shape,
            )
            .border(width = if (f) 3.dp else 0.dp, color = colors.focus, shape)
            .padding(horizontal = tv.tvSize(22), vertical = tv.tvSize(8)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected || f) Color.White else colors.subtext,
            fontSize = tv.tvFont(18),
        )
    }
}

private val SEARCH_KEY_ROWS = listOf(
    listOf('Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'),
    listOf('A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'),
    listOf('Z', 'X', 'C', 'V', 'B', 'N', 'M'),
)

@Composable
private fun SearchKeyboard(
    firstKeyFocus: FocusRequester,
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
) {
    val tv = LocalTvMetrics.current
    Column(verticalArrangement = Arrangement.spacedBy(tv.tvSize(10))) {
        SEARCH_KEY_ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(10))) {
                row.forEachIndexed { index, ch ->
                    SearchKeyButton(
                        label = ch.toString(),
                        modifier = Modifier.width(tv.tvSize(44)),
                        requestFocus = index == 0 && ch == 'Q',
                        focusRequester = firstKeyFocus,
                        onClick = { onKey(ch) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(10))) {
            SearchKeyButton(label = TvText.space, modifier = Modifier.weight(1.3f)) { onKey(' ') }
            SearchKeyButton(label = TvText.backspace, modifier = Modifier.weight(1f)) { onBackspace() }
            SearchKeyButton(label = TvText.clear, modifier = Modifier.weight(1f)) { onClear() }
            SearchKeyButton(label = TvText.search, modifier = Modifier.weight(1.3f)) { onSubmit() }
        }
    }
}

@Composable
private fun SearchKeyButton(
    label: String,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .height(tv.tvSize(40))
            .let { if (requestFocus && focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { f = it.isFocused }
            .clickable { onClick() }
            .background(if (f) colors.selectGreen else colors.surface, shape)
            .border(if (f) 3.dp else 0.dp, colors.focus, shape)
            .padding(horizontal = tv.tvSize(12)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (f) Color.White else colors.subtext,
            fontSize = tv.tvFont(18),
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 播放列表 Tab：展示当前队列，点击切歌，可清空。 */
@Composable
fun QueueScreen(vm: PlayerViewModel = viewModel()) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val list by vm.queueList.collectAsState()
    val curIndex by vm.queueIndex.collectAsState()
    Column(Modifier.fillMaxSize().padding(tv.tvSize(68))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tv.tvSize(20)),
        ) {
            Text(TvText.playlist, color = colors.text, fontSize = tv.tvFont(32))
            var f by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .onFocusChanged { f = it.isFocused }
                    .clickable { vm.clearQueue() }
                    .background(colors.surface, RoundedCornerShape(tv.tvSize(8)))
                    .border(
                        width = if (f) 3.dp else 0.dp,
                        color = colors.focus,
                        shape = RoundedCornerShape(tv.tvSize(8)),
                    )
                    .padding(horizontal = tv.tvSize(20), vertical = tv.tvSize(10)),
                contentAlignment = Alignment.Center,
            ) {
                Text(TvText.clearList, color = colors.subtext, fontSize = tv.tvFont(18))
            }
        }
        Spacer(Modifier.height(tv.tvSize(16)))
        if (list.isEmpty()) {
            Text(TvText.noMusic, color = colors.dimText, fontSize = tv.tvFont(22))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(tv.tvSize(8)),
            ) {
                itemsIndexed(list) { i, item ->
                    val isCur = i == curIndex
                    var f by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { f = it.isFocused }
                            .clickable { vm.playQueueAt(i) }
                            .background(
                                if (isCur) colors.surfaceHigh else if (f) colors.surface else Color.Transparent,
                                RoundedCornerShape(tv.tvSize(8)),
                            )
                            .padding(horizontal = tv.tvSize(20), vertical = tv.tvSize(12)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${if (isCur) "▶ " else ""}${item.name} - ${item.singer.ifBlank { TvText.unknownSinger }}",
                            color = if (isCur) colors.selectGreen else if (f) colors.text else colors.subtext,
                            fontSize = tv.tvFont(22),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabPlaceholder(name: String) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$name · 待实现", color = colors.text, fontSize = tv.tvFont(32))
    }
}
