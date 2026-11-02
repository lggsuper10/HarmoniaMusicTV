package com.harmoniamusic.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.harmoniamusic.tv.ui.state.LocalFolder
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.harmoniamusic.tv.data.source.BoardInfo
import com.harmoniamusic.tv.data.source.LOCAL_SOURCE
import com.harmoniamusic.tv.data.source.MusicItem
import com.harmoniamusic.tv.data.source.MusicQuality
import com.harmoniamusic.tv.data.source.MusicSources
import com.harmoniamusic.tv.data.source.PlaylistInfo
import com.harmoniamusic.tv.data.store.SourceStore
import com.harmoniamusic.tv.data.store.UserPlaylist
import com.harmoniamusic.tv.data.queue.PlayQueue
import androidx.compose.foundation.layout.PaddingValues
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.harmoniamusic.tv.ui.shell.TvTab
import com.harmoniamusic.tv.ui.state.DownloadViewModel
import com.harmoniamusic.tv.ui.state.LyricLoadState
import com.harmoniamusic.tv.ui.state.PlayerViewModel
import com.harmoniamusic.tv.ui.strings.TvText
import com.harmoniamusic.tv.ui.strings.PlaylistText
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
        CollectionDetailPane(
            state = CollectionDetailUiState(
                title = detailTitle,
                meta = detailMeta,
                cover = detailCover,
                songs = detailSongs,
                loading = detailLoading,
            ),
            onBack = { vm.closePlaylist() },
            onPlay = { index -> vm.playList(detailSongs, index, detailTitle) },
            onShuffle = { vm.playShuffled(detailSongs, detailTitle) },
            onAddToQueue = { vm.addToQueue(detailSongs, detailTitle) },
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
                HomeCover(url = item.img, size = tv.tvSize(220), corner = 20)
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
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable { if (enabled) onClick() }
            .alpha(if (enabled) 1f else 0.42f)
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
            color = when {
                !enabled -> colors.text
                focused -> Color.White
                else -> colors.subtext
            },
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

/** 封面图；无图时给占位块。corner 为圆角当量（tvSize 单位），可按位置单独加大弧角。 */
@Composable
private fun HomeCover(url: String?, size: Dp, corner: Int = 12) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val shape = RoundedCornerShape(tv.tvSize(corner))
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(
                    Brush.linearGradient(
                        listOf(colors.surfaceHigh, colors.bgWarm, colors.primarySoft),
                    ),
                    shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = colors.subtext.copy(alpha = 0.8f),
                modifier = Modifier.size(tv.tvSize(34)),
            )
        }
    }
}

/** 推荐歌单卡片：封面 + 标题 + 作者。 */
/**
 * 播放页封面：方形容器内完整展示原图，底层用同一张封面裁切并压暗补足留白。
 * 不用 Crop 直接画主图，避免人物脸部/海报文字被横向或纵向截断。
 */
@Composable
private fun PlayerArtwork(url: String?, size: Dp) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val shape = RoundedCornerShape(tv.tvSize(18))

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(colors.bgWarm)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(colors.surfaceHigh, colors.bgWarm, colors.primarySoft),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = colors.subtext.copy(alpha = 0.8f),
                    modifier = Modifier.size(tv.tvSize(54)),
                )
            }
        } else {
            // 底层铺满：只作为氛围色，不承担“展示完整封面”的职责。
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.64f),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.14f),
                                Color.Black.copy(alpha = 0.46f),
                            ),
                        ),
                    ),
            )
            // 上层完整显示：所有比例都使用 Fit，人物与海报文字不会被裁掉。
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(tv.tvSize(7)),
            )
        }
    }
}

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

/** 详情页共享的只读展示模型：推荐歌单、收藏和本地文件夹复用它。 */
private data class CollectionDetailUiState(
    val title: String,
    val meta: String,
    val cover: String?,
    val songs: List<MusicItem>,
    val loading: Boolean = false,
)

/** 集合详情：左侧封面与操作，右侧带序号的歌曲列表。 */
@Composable
private fun CollectionDetailPane(
    state: CollectionDetailUiState,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onShuffle: (() -> Unit)? = null,
    onAddToQueue: () -> Unit,
    onRemoveTrack: ((MusicItem) -> Unit)? = null,
    removeTrackLabel: String = TvText.removeFromFavorites,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val context = LocalContext.current
    val playAllRequester = remember { FocusRequester() }
    val songs = state.songs

    BackHandler { onBack() }

    LaunchedEffect(state.title) {
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
            HomePill(TvText.back) { onBack() }
            Spacer(Modifier.height(tv.tvSize(20)))
            HomeCover(url = state.cover, size = tv.tvSize(300))
            Spacer(Modifier.height(tv.tvSize(18)))
            Text(
                text = state.meta,
                color = colors.dimText,
                fontSize = tv.tvFont(18),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(tv.tvSize(8)))
            Text(
                text = state.title,
                color = colors.text,
                fontSize = tv.tvFont(38),
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(tv.tvSize(22)))
            Row(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(14))) {
                HomePill(
                    label = TvText.playAll,
                    modifier = Modifier.focusRequester(playAllRequester),
                ) {
                    if (songs.isNotEmpty()) onPlay(0)
                }
                if (onShuffle != null) {
                    HomePill(TvText.random) {
                        if (songs.isNotEmpty()) onShuffle()
                    }
                }
                HomePill(TvText.addToQueue) {
                    if (songs.isNotEmpty()) {
                        onAddToQueue()
                        Toast.makeText(context, "已加入播放队列", Toast.LENGTH_SHORT).show()
                    }
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
                    text = if (state.loading) "加载中…" else "已加载 ${songs.size} 首",
                    color = colors.dimText,
                    fontSize = tv.tvFont(18),
                )
            }
            Spacer(Modifier.height(tv.tvSize(8)))
            Text(
                text = "OK 播放整个集合 · 点击歌曲从该曲开始",
                color = colors.dimText,
                fontSize = tv.tvFont(17),
            )
            Spacer(Modifier.height(tv.tvSize(10)))
            if (state.loading && songs.isEmpty()) {
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
                        if (onRemoveTrack == null) {
                            DetailSongRow(index, song) { onPlay(index) }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DetailSongRow(
                                    index = index,
                                    item = song,
                                    modifier = Modifier.weight(1f),
                                ) { onPlay(index) }
                                Spacer(Modifier.width(tv.tvSize(10)))
                                SettingActionButton(removeTrackLabel) {
                                    onRemoveTrack(song)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 集合详情中的歌曲行：序号、歌名、歌手与时长。 */
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
    val lyricState by vm.lyricState.collectAsState()
    val error by vm.playbackError.collectAsState()
    val playbackStreamSource by vm.playbackStreamSource.collectAsState()
    val mode by vm.playMode.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val userPlaylists by vm.userPlaylists.collectAsState()
    val downloadVm: DownloadViewModel = viewModel()
    val downloadMessage by downloadVm.message.collectAsState()
    val toastContext = LocalContext.current
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var interactionTick by remember { mutableStateOf(0) }
    val controlsToggleRequester = remember { FocusRequester() }
    var downloadQualityItem by remember { mutableStateOf<MusicItem?>(null) }
    var downloadQualityOptions by remember { mutableStateOf<List<MusicQuality>>(emptyList()) }
    var addToPlaylistSong by remember { mutableStateOf<MusicItem?>(null) }
    var pendingPlaylistSong by remember { mutableStateOf<MusicItem?>(null) }

    fun requestDownload(item: MusicItem?) {
        if (item == null) {
            Toast.makeText(toastContext, TvText.chooseSongFirst, Toast.LENGTH_SHORT).show()
            return
        }
        if (item.source == LOCAL_SOURCE) {
            Toast.makeText(toastContext, TvText.downloadLocalSkip, Toast.LENGTH_SHORT).show()
            return
        }
        val options = downloadQualitiesFor(item)
        when (options.size) {
            0 -> downloadVm.download(item, "128k")
            1 -> downloadVm.download(item, options.first().type)
            else -> {
                downloadQualityItem = item
                downloadQualityOptions = options
            }
        }
    }

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
        vm.loadUserPlaylists()
    }

    LaunchedEffect(controlsVisible) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching {
            if (controlsVisible) playRequester.requestFocus()
            else controlsToggleRequester.requestFocus()
        }
    }

    // 播放中一段时间无操作自动收纳控制栏；暂停或报错时保持可见。
    LaunchedEffect(controlsVisible, isPlaying, error, interactionTick) {
        if (!controlsVisible || !isPlaying || error) return@LaunchedEffect
        delay(6_000)
        controlsVisible = false
    }

    // 来源与播放状态完全分离：来源保持稳定，状态仅在准备/暂停/失败时显示。
    val statusText = when {
        error -> "无法播放：版权受限或取流失败"
        isPlaying -> ""
        pos > 0 && dur > 0 -> TvText.paused
        else -> TvText.preparing
    }
    val isFav = current?.let { item ->
        favorites.any { it.source == item.source && it.songmid == item.songmid }
    } ?: false

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgDeep)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                interactionTick++
                if (!controlsVisible) {
                    controlsVisible = true
                    // 首次唤出时吞掉 OK，避免它同时触发右上角控制栏开关而立刻收起。
                    if (event.key == Key.DirectionCenter || event.key == Key.Enter) true else false
                } else {
                    false
                }
            },
    ) {
        // 沉浸式背景：封面大图 + 暗化遮罩
        if (!current?.img.isNullOrBlank()) {
            AsyncImage(
                model = current?.img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.22f),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.42f),
                            Color.Black.copy(alpha = 0.66f),
                            colors.bgDeep.copy(alpha = 0.9f),
                        ),
                    ),
                ),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(
                start = tv.tvSize(70),
                end = tv.tvSize(70),
                top = tv.tvSize(44),
                bottom = tv.tvSize(24),
            ),
            verticalArrangement = Arrangement.spacedBy(tv.tvSize(16)),
        ) {
            // 顶部：歌名 / 歌手·专辑 + 来源与状态
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        current?.name ?: TvText.unknownSong,
                        color = colors.text,
                        fontSize = tv.tvFont(48),
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
                        val streamSourceName = when {
                            current == null -> ""
                            current?.source == LOCAL_SOURCE -> "本地文件"
                            else -> playbackStreamSource.orEmpty()
                        }
                        if (streamSourceName.isNotBlank()) {
                            PlayerSourceBadge(streamSourceName)
                            Spacer(Modifier.height(tv.tvSize(8)))
                        }
                        if (error) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(tv.tvSize(10)),
                            ) {
                                Text(
                                    statusText,
                                    color = colors.danger,
                                    fontSize = tv.tvFont(15),
                                    maxLines = 1,
                                )
                                PlayerInlineAction(TvText.chartRetry) { vm.togglePlay() }
                            }
                        } else if (statusText.isNotBlank()) {
                            Text(statusText, color = colors.dimText, fontSize = tv.tvFont(15))
                        }
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
                PlayerArtwork(url = current?.img, size = tv.tvSize(320))
                LyricsView(
                    raw = lyric,
                    positionMs = pos,
                    loadState = lyricState,
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

                // 主流播放器控制栏：主播放键居中，次要操作在两侧。
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        horizontalArrangement = Arrangement.spacedBy(tv.tvSize(6)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerUtilityButton(
                            icon = when (mode) {
                                PlayQueue.Mode.ORDER -> Icons.Rounded.Repeat
                                PlayQueue.Mode.SINGLE -> Icons.Rounded.RepeatOne
                                PlayQueue.Mode.SHUFFLE -> Icons.Rounded.Shuffle
                            },
                            label = playModeLabel(mode),
                        ) { vm.cyclePlayMode() }
                        PlayerUtilityButton(
                            icon = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            label = if (isFav) TvText.liked else TvText.like,
                            active = isFav,
                        ) { current?.let { vm.toggleFavorite(it) } }
                        PlayerUtilityButton(
                            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            label = PlaylistText.addToPlaylist,
                        ) {
                            if (current == null) {
                                Toast.makeText(toastContext, TvText.chooseSongFirst, Toast.LENGTH_SHORT).show()
                            } else {
                                addToPlaylistSong = current
                            }
                        }
                        PlayerUtilityButton(
                            icon = Icons.Rounded.Download,
                            label = TvText.download,
                        ) { requestDownload(current) }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(tv.tvSize(18)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ControlIconButton(
                            icon = Icons.Rounded.SkipPrevious,
                            contentDescription = TvText.controlPrev,
                            label = TvText.controlPrev,
                        ) { vm.prev() }
                        ControlIconButton(
                            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) TvText.pause else TvText.playNow,
                            label = if (isPlaying) TvText.pause else TvText.play,
                            modifier = Modifier.focusRequester(playRequester),
                            primary = true,
                        ) {
                            vm.togglePlay()
                        }
                        ControlIconButton(
                            icon = Icons.Rounded.SkipNext,
                            contentDescription = TvText.controlNext,
                            label = TvText.controlNext,
                        ) { vm.next() }
                    }
                    PlayerUtilityButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        label = TvText.back,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) { onBack() }
                }
            }
        }
        addToPlaylistSong?.let { song ->
            AddToPlaylistDialog(
                song = song,
                playlists = userPlaylists,
                onDismiss = { addToPlaylistSong = null },
                onAdd = { playlist ->
                    val added = vm.addToUserPlaylist(playlist.id, song)
                    addToPlaylistSong = null
                    val message = if (added) {
                        PlaylistText.playlistAdded.replace("{name}", playlist.name)
                    } else {
                        PlaylistText.playlistAlreadyAdded
                    }
                    Toast.makeText(toastContext, message, Toast.LENGTH_SHORT).show()
                },
                onCreateNew = {
                    addToPlaylistSong = null
                    pendingPlaylistSong = song
                },
            )
        }
        pendingPlaylistSong?.let { song ->
            PlaylistNameDialog(
                title = PlaylistText.newPlaylist,
                initialName = "",
                confirmLabel = PlaylistText.createAction,
                onDismiss = { pendingPlaylistSong = null },
                onConfirm = { name ->
                    val playlist = vm.createUserPlaylistWithSong(name, song)
                    pendingPlaylistSong = null
                    if (playlist != null) {
                        Toast.makeText(
                            toastContext,
                            PlaylistText.playlistAdded.replace("{name}", playlist.name),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        }
        downloadQualityItem?.let { item ->
            DownloadQualityDialog(
                item = item,
                qualities = downloadQualityOptions,
                onDismiss = {
                    downloadQualityItem = null
                    downloadQualityOptions = emptyList()
                },
                onSelect = { quality ->
                    downloadVm.download(item, quality.type)
                    downloadQualityItem = null
                    downloadQualityOptions = emptyList()
                },
            )
        }
    }
}

@Composable
private fun AddToPlaylistDialog(
    song: MusicItem,
    playlists: List<UserPlaylist>,
    onDismiss: () -> Unit,
    onAdd: (UserPlaylist) -> Unit,
    onCreateNew: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val firstRequester = remember { FocusRequester() }

    LaunchedEffect(playlists.size) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { firstRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(tv.tvSize(780))
                    .fillMaxHeight(0.82f)
                    .background(colors.bgWarm, RoundedCornerShape(tv.tvSize(16)))
                    .border(2.dp, colors.focus, RoundedCornerShape(tv.tvSize(16)))
                    .padding(tv.tvSize(34)),
                verticalArrangement = Arrangement.spacedBy(tv.tvSize(10)),
            ) {
                Text(
                    text = PlaylistText.addToPlaylistTitle,
                    color = colors.text,
                    fontSize = tv.tvFont(30),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${song.name} · ${song.singer.ifBlank { TvText.unknownSinger }}",
                    color = colors.subtext,
                    fontSize = tv.tvFont(18),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = PlaylistText.addToPlaylistHint,
                    color = colors.dimText,
                    fontSize = tv.tvFont(15),
                )
                Spacer(Modifier.height(tv.tvSize(2)))

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = PlaylistText.emptyPlaylists,
                                color = colors.text,
                                fontSize = tv.tvFont(24),
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(tv.tvSize(8)))
                            Text(
                                text = PlaylistText.emptyPlaylistsHint,
                                color = colors.dimText,
                                fontSize = tv.tvFont(16),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(tv.tvSize(6)),
                    ) {
                        itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                            PlaylistChoiceRow(
                                playlist = playlist,
                                included = playlist.songs.any {
                                    it.source == song.source && it.songmid == song.songmid
                                },
                                modifier = if (index == 0) Modifier.focusRequester(firstRequester) else Modifier,
                                onClick = { onAdd(playlist) },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tv.tvSize(12)),
                ) {
                    HomePill(
                        label = PlaylistText.newPlaylist,
                        modifier = Modifier
                            .weight(1f)
                            .let { if (playlists.isEmpty()) it.focusRequester(firstRequester) else it },
                    ) { onCreateNew() }
                    HomePill(TvText.cancelAction, Modifier.weight(1f)) { onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun PlaylistChoiceRow(
    playlist: UserPlaylist,
    included: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(9))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(if (focused) colors.surfaceHigh else colors.surface, shape)
            .border(if (focused) 3.dp else 0.dp, colors.focus, shape)
            .padding(horizontal = tv.tvSize(14), vertical = tv.tvSize(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeCover(url = playlist.songs.firstOrNull()?.img, size = tv.tvSize(56), corner = 8)
        Spacer(Modifier.width(tv.tvSize(12)))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = if (focused) Color.White else colors.text,
                fontSize = tv.tvFont(20),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(playlist.songs.size)
                    append(" 首")
                    if (included) append(" · 已加入")
                },
                color = if (included) colors.selectGreen else colors.dimText,
                fontSize = tv.tvFont(15),
            )
        }
        Text(
            text = if (included) "✓" else "+",
            color = if (included) colors.selectGreen else colors.primary,
            fontSize = tv.tvFont(24),
            fontWeight = FontWeight.Bold,
        )
    }
}
private val downloadQualityOrder = listOf("flac24bit", "flac", "320k", "128k")

private fun downloadQualitiesFor(item: MusicItem): List<MusicQuality> =
    item.qualities
        .filter { it.type.isNotBlank() }
        .distinctBy { it.type }
        .sortedBy { quality ->
            downloadQualityOrder.indexOf(quality.type).let { if (it < 0) Int.MAX_VALUE else it }
        }

private fun downloadQualityLabel(type: String): String = when (type) {
    "flac24bit" -> TvText.downloadQuality24bit
    "flac" -> TvText.downloadQualityFlac
    "320k" -> TvText.downloadQuality320k
    "128k" -> TvText.downloadQuality128k
    else -> type
}

@Composable
private fun DownloadQualityDialog(
    item: MusicItem,
    qualities: List<MusicQuality>,
    onDismiss: () -> Unit,
    onSelect: (MusicQuality) -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val shape = RoundedCornerShape(tv.tvSize(18))
    val firstRequester = remember { FocusRequester() }

    LaunchedEffect(item.songmid, qualities) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { firstRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(tv.tvSize(560))
                    .background(colors.bgWarm, shape)
                    .border(2.dp, colors.focus, shape)
                    .padding(tv.tvSize(30)),
                verticalArrangement = Arrangement.spacedBy(tv.tvSize(10)),
            ) {
                Text(
                    text = TvText.selectDownloadQuality,
                    color = colors.text,
                    fontSize = tv.tvFont(30),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${item.name} · ${item.singer.ifBlank { TvText.unknownSinger }}",
                    color = colors.dimText,
                    fontSize = tv.tvFont(18),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(tv.tvSize(8)))
                qualities.forEachIndexed { index, quality ->
                    DownloadQualityChoice(
                        label = downloadQualityLabel(quality.type),
                        detail = quality.size?.takeIf { it.isNotBlank() },
                        focusRequester = if (index == 0) firstRequester else null,
                        onClick = { onSelect(quality) },
                    )
                }
                Spacer(Modifier.height(tv.tvSize(4)))
                DownloadQualityChoice(
                    label = TvText.cancelAction,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun DownloadQualityChoice(
    label: String,
    detail: String? = null,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(10))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(if (focused) colors.selectGreen else colors.surface, shape)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) colors.focus else colors.border,
                shape = shape,
            )
            .padding(horizontal = tv.tvSize(20), vertical = tv.tvSize(13)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (focused) colors.bgDeep else colors.text,
            fontSize = tv.tvFont(20),
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        detail?.let {
            Text(
                text = it,
                color = if (focused) colors.bgDeep else colors.dimText,
                fontSize = tv.tvFont(16),
            )
        }
    }
}

/** 播放页右上角音源徽标：只表达实际取流源，不承载播放状态。 */
@Composable
private fun PlayerSourceBadge(label: String) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.30f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(horizontal = tv.tvSize(14), vertical = tv.tvSize(7)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(tv.tvSize(7))
                .background(colors.selectGreen, CircleShape),
        )
        Spacer(Modifier.width(tv.tvSize(8)))
        Text(
            text = "${TvText.source} · $label",
            color = colors.text.copy(alpha = 0.92f),
            fontSize = tv.tvFont(15),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 播放页错误状态里的轻量操作，例如“重试”。 */
@Composable
private fun PlayerInlineAction(label: String, onClick: () -> Unit) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(if (focused) colors.selectGreen else colors.surfaceHigh, shape)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Color.White else colors.line,
                shape = shape,
            )
            .padding(horizontal = tv.tvSize(12), vertical = tv.tvSize(4)),
    ) {
        Text(
            text = label,
            color = if (focused) colors.bgDeep else colors.text,
            fontSize = tv.tvFont(13),
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 图标优先的次要操作按钮：圆形图标 + 小标签，聚焦时轻微放大。 */
@Composable
private fun PlayerUtilityButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(160),
        label = "playerUtilityScale",
    )
    Column(
        modifier = modifier
            .width(tv.tvSize(76))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(vertical = tv.tvSize(2)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(tv.tvSize(44))
                .background(
                    when {
                        focused -> colors.selectGreen
                        active -> colors.primarySoft
                        else -> colors.surfaceHigh
                    },
                    CircleShape,
                )
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = when {
                        focused -> Color.White
                        active -> colors.selectGreen.copy(alpha = 0.7f)
                        else -> colors.line
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    focused -> colors.bgDeep
                    active -> colors.selectGreen
                    else -> colors.subtext
                },
                modifier = Modifier.size(tv.tvSize(23)),
            )
        }
        Spacer(Modifier.height(tv.tvSize(3)))
        Text(
            text = label,
            color = if (focused || active) colors.text else colors.dimText,
            fontSize = tv.tvFont(12),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
            .background(if (focused) colors.selectGreen else colors.surface, shape)
            .border(if (focused) 3.dp else 0.dp, Color.White, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (visible) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
            contentDescription = if (visible) "隐藏控制栏" else "显示控制栏",
            tint = if (focused) colors.bgDeep else colors.subtext,
            modifier = Modifier.size(tv.tvSize(30)),
        )
    }
}

/** 带时间轴高亮的歌词：根据当前播放进度定位当前行并自动滚动居中。 */
@Composable
private fun LyricsView(
    raw: String,
    positionMs: Long,
    loadState: LyricLoadState,
    modifier: Modifier = Modifier,
) {
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
            Text(
                text = if (loadState == LyricLoadState.LOADING) "正在匹配歌词…" else TvText.noLyric,
                color = colors.dimText,
                fontSize = tv.tvFont(22),
            )
        }
    } else {
        BoxWithConstraints(modifier) {
            val centerPadding = (maxHeight / 2 - tv.tvSize(28)).coerceAtLeast(tv.tvSize(32))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(tv.tvSize(10)),
                contentPadding = PaddingValues(vertical = centerPadding),
            ) {
                items(lines.size) { i ->
                    val (_, text) = lines[i]
                    val cur = i == currentIndex
                    val distance = if (currentIndex >= 0) kotlin.math.abs(i - currentIndex) else 99
                    val targetAlpha = when {
                        cur -> 1f
                        distance == 1 -> 0.64f
                        distance == 2 -> 0.38f
                        else -> 0.20f
                    }
                    val alpha by animateFloatAsState(
                        targetValue = targetAlpha,
                        animationSpec = tween(240),
                        label = "lyricLineAlpha",
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(tv.tvSize(3))
                                .height(if (cur) tv.tvSize(30) else 0.dp)
                                .background(
                                    if (cur) colors.selectGreen else Color.Transparent,
                                    RoundedCornerShape(50),
                                ),
                        )
                        Spacer(Modifier.width(tv.tvSize(12)))
                        Text(
                            text = text,
                            color = if (cur) colors.selectGreen else colors.text.copy(alpha = alpha),
                            fontSize = if (cur) tv.tvFont(28) else tv.tvFont(20),
                            lineHeight = tv.tvFont(if (cur) 36 else 28),
                            fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(tv.tvSize(72))
                    .background(
                        Brush.verticalGradient(
                            listOf(colors.bgDeep.copy(alpha = 0.94f), Color.Transparent),
                        ),
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(tv.tvSize(84))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, colors.bgDeep.copy(alpha = 0.96f)),
                        ),
                    ),
            )
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
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(420),
        label = "seekProgress",
    )
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
            .border(if (focused) 2.dp else 0.dp, colors.focus, RoundedCornerShape(tv.tvSize(10)))
            .padding(horizontal = tv.tvSize(16), vertical = tv.tvSize(8)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tv.tvSize(16)),
    ) {
        Text(
            text = formatTime(positionMs),
            color = if (focused) colors.text else colors.dimText,
            fontSize = tv.tvFont(17),
            modifier = Modifier.width(tv.tvSize(62)),
        )
        BoxWithConstraints(
            modifier = Modifier.weight(1f).height(tv.tvSize(20)),
            contentAlignment = Alignment.CenterStart,
        ) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(if (focused) tv.tvSize(8) else tv.tvSize(6)),
                color = colors.selectGreen,
                trackColor = colors.surfaceHigh,
            )
            val thumbSize = if (focused) 14 else 10
            val maxOffset = (maxWidth - tv.tvSize(thumbSize)).coerceAtLeast(0.dp)
            Box(
                modifier = Modifier
                    .offset(x = maxOffset * animatedProgress)
                    .size(tv.tvSize(thumbSize))
                    .background(if (focused) colors.focus else colors.selectGreen, CircleShape),
            )
        }
        Text(
            text = formatTime(durationMs),
            color = colors.dimText,
            fontSize = tv.tvFont(17),
            modifier = Modifier.width(tv.tvSize(62)),
        )
        Text(
            text = "◀ 左右快进 ▶",
            color = if (focused) colors.focus else Color.Transparent,
            fontSize = tv.tvFont(14),
            modifier = Modifier.width(tv.tvSize(112)),
        )
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
    if (result.isEmpty()) {
        // 部分音源会返回无时间轴的纯文本歌词；按行铺开仍可阅读，聊胜于只显示“暂无歌词”。
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot {
                it.startsWith("[ti:") || it.startsWith("[ar:") || it.startsWith("[al:") ||
                    it.startsWith("[by:") || it.startsWith("[offset:")
            }
            .mapIndexed { index, line -> (index * 4_000L) to line }
            .toList()
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

@Composable
private fun ControlIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    label: String? = null,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val buttonSize = if (primary) 76 else 62
    val iconSize = if (primary) 40 else 30
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(160),
        label = "playerTransportScale",
    )
    Column(
        modifier = modifier
            .width(tv.tvSize(if (primary) 100 else 82))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(vertical = tv.tvSize(2)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(tv.tvSize(buttonSize))
                .background(
                    when {
                        focused -> colors.selectGreen
                        primary -> colors.surfaceHigh
                        else -> colors.surface
                    },
                    CircleShape,
                )
                .border(
                    width = when {
                        focused -> 4.dp
                        primary -> 2.dp
                        else -> 1.dp
                    },
                    color = when {
                        focused -> Color.White
                        primary -> colors.selectGreen.copy(alpha = 0.58f)
                        else -> Color.White.copy(alpha = 0.10f)
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = when {
                    focused -> colors.bgDeep
                    primary -> colors.selectGreen
                    else -> colors.subtext
                },
                modifier = Modifier.size(tv.tvSize(iconSize)),
            )
        }
        Spacer(Modifier.height(tv.tvSize(4)))
        Text(
            text = label ?: contentDescription,
            color = when {
                focused -> Color.White
                primary -> colors.selectGreen
                else -> colors.dimText
            },
            fontSize = tv.tvFont(12),
            fontWeight = if (focused || primary) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

// ---- 其余 Tab ----
/** 排行榜 Tab：横向榜单预览 + 榜单摘要 + 强化排名的歌曲列表。 */
@Composable
fun ChartsScreen(
    focusTick: Int = 0,
    vm: PlayerViewModel = viewModel(),
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val context = LocalContext.current
    val boards by vm.boards.collectAsState()
    val boardsLoading by vm.boardsLoading.collectAsState()
    val boardsError by vm.boardsError.collectAsState()
    val boardList by vm.boardList.collectAsState()
    val boardLoading by vm.boardLoading.collectAsState()
    val boardError by vm.boardError.collectAsState()
    val selectedBangid by vm.selectedBangid.collectAsState()
    val boardCovers by vm.boardCovers.collectAsState()
    val chartSourceId by vm.chartSourceId.collectAsState()
    val current by vm.current.collectAsState()

    val selectedBoard = boards.firstOrNull { it.bangid == selectedBangid }
    val selectedBoardName = selectedBoard?.name ?: TvText.ranking
    val sourceName = MusicSources.displayName(chartSourceId)
    val coverKey = selectedBangid?.let { "$chartSourceId|$it" }
    val selectedCover = coverKey?.let { boardCovers[it] } ?: boardList.firstOrNull()?.img

    val boardRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val playAllRequester = remember { FocusRequester() }
    var actionFocusTick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { vm.loadBoards() }
    LaunchedEffect(focusTick) {
        if (focusTick <= 0 || boards.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        val target = selectedBangid ?: boards.firstOrNull()?.bangid ?: return@LaunchedEffect
        runCatching { boardRequesters[target]?.requestFocus() }
    }
    LaunchedEffect(actionFocusTick) {
        if (actionFocusTick <= 0) return@LaunchedEffect
        withFrameNanos { }
        runCatching { playAllRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tv.tvSize(68), vertical = tv.tvSize(12)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = TvText.ranking,
                color = colors.text,
                fontSize = tv.tvFont(28),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(tv.tvSize(10)))
            Box(
                modifier = Modifier
                    .background(colors.selectGreen.copy(alpha = 0.16f), RoundedCornerShape(tv.tvSize(7)))
                    .padding(horizontal = tv.tvSize(9), vertical = tv.tvSize(3)),
            ) {
                Text(
                    text = sourceName,
                    color = colors.selectGreen,
                    fontSize = tv.tvFont(13),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = TvText.chartHint,
                color = colors.dimText,
                fontSize = tv.tvFont(14),
            )
        }
        Spacer(Modifier.height(tv.tvSize(6)))

        when {
            boardsLoading && boards.isEmpty() -> ChartRailMessage(TvText.loading)
            boardsError != null && boards.isEmpty() -> ChartRailMessage(
                message = TvText.loadFailed,
                actionLabel = TvText.chartRetry,
                onAction = { vm.loadBoards(force = true) },
            )
            boards.isEmpty() -> ChartRailMessage(TvText.chartNoBoards)
            else -> {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tv.tvSize(12)),
                ) {
                    itemsIndexed(boards, key = { _, board -> board.bangid }) { index, board ->
                        ChartBoardCard(
                            board = board,
                            index = index,
                            cover = boardCovers["$chartSourceId|${board.bangid}"],
                            selected = selectedBangid == board.bangid,
                            focusRequester = boardRequesters.getOrPut(board.bangid) { FocusRequester() },
                            onFocused = { vm.selectBoard(board.bangid) },
                            onClick = {
                                vm.selectBoard(board.bangid)
                                actionFocusTick++
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(tv.tvSize(8)))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(tv.tvSize(12)))
                .border(1.dp, colors.border, RoundedCornerShape(tv.tvSize(12)))
                .padding(horizontal = tv.tvSize(12), vertical = tv.tvSize(9)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeCover(url = selectedCover, size = tv.tvSize(60), corner = 9)
            Spacer(Modifier.width(tv.tvSize(12)))
            Column(Modifier.weight(1f)) {
                Text(
                    text = selectedBoardName,
                    color = colors.text,
                    fontSize = tv.tvFont(23),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(tv.tvSize(2)))
                Text(
                    text = when {
                        boardLoading && boardList.isEmpty() -> TvText.chartLoadingSongs
                        boardError != null && boardList.isEmpty() -> TvText.loadFailed
                        else -> "$sourceName${TvText.dot}${boardList.size}${TvText.dot}${TvText.songs}"
                    },
                    color = if (boardError != null && boardList.isEmpty()) colors.danger else colors.dimText,
                    fontSize = tv.tvFont(14),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(tv.tvSize(8)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomePill(
                    label = TvText.playAll,
                    modifier = Modifier.focusRequester(playAllRequester),
                    enabled = boardList.isNotEmpty(),
                ) {
                    vm.playList(boardList, 0, selectedBoardName)
                }
                HomePill(
                    label = TvText.random,
                    enabled = boardList.isNotEmpty(),
                ) {
                    vm.playShuffled(boardList, selectedBoardName)
                }
                HomePill(
                    label = TvText.addToQueue,
                    enabled = boardList.isNotEmpty(),
                ) {
                    vm.addToQueue(boardList, selectedBoardName)
                    Toast.makeText(context, "已加入播放队列", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Spacer(Modifier.height(tv.tvSize(5)))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = tv.tvSize(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = TvText.songList,
                color = colors.subtext,
                fontSize = tv.tvFont(16),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = TvText.chartSongHint,
                color = colors.dimText,
                fontSize = tv.tvFont(13),
            )
        }
        Spacer(Modifier.height(tv.tvSize(2)))

        when {
            boardLoading && boardList.isEmpty() -> ChartListMessage(TvText.chartLoadingSongs)
            boardError != null && boardList.isEmpty() -> ChartListMessage(
                message = TvText.loadFailed,
                actionLabel = TvText.chartRetry,
                onAction = { selectedBangid?.let { vm.selectBoard(it, force = true) } },
            )
            boardList.isEmpty() -> ChartListMessage(TvText.chartNoSongs)
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(tv.tvSize(2)),
                ) {
                    itemsIndexed(
                        boardList,
                        key = { _, item -> "${item.source}:${item.songmid}" },
                    ) { index, item ->
                        ChartSongRow(
                            index = index,
                            item = item,
                            isCurrent = current?.source == item.source && current?.songmid == item.songmid,
                            onPlay = { vm.playList(boardList, index, selectedBoardName) },
                            onAddToQueue = {
                                vm.addToQueue(listOf(item), selectedBoardName)
                                Toast.makeText(context, "已加入播放队列", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartRailMessage(
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(tv.tvSize(84))
            .background(colors.surface, RoundedCornerShape(tv.tvSize(12)))
            .border(1.dp, colors.border, RoundedCornerShape(tv.tvSize(12)))
            .padding(horizontal = tv.tvSize(20)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = colors.dimText, fontSize = tv.tvFont(17))
        Spacer(Modifier.weight(1f))
        if (actionLabel != null) HomePill(actionLabel) { onAction() }
    }
}

@Composable
private fun ChartListMessage(
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(tv.tvSize(72))
            .padding(horizontal = tv.tvSize(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = colors.dimText, fontSize = tv.tvFont(17))
        Spacer(Modifier.weight(1f))
        if (actionLabel != null) HomePill(actionLabel) { onAction() }
    }
}

@Composable
private fun ChartBoardCard(
    board: BoardInfo,
    index: Int,
    cover: String?,
    selected: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.045f else 1f,
        animationSpec = tween(120),
        label = "chartBoardScale",
    )
    val shape = RoundedCornerShape(tv.tvSize(12))
    Column(
        modifier = Modifier
            .width(tv.tvSize(158))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable { onClick() }
            .background(
                when {
                    focused -> colors.surfaceHigh
                    selected -> colors.surface
                    else -> Color.Transparent
                },
                shape,
            )
            .then(
                when {
                    focused -> Modifier.border(3.dp, colors.focus, shape)
                    selected -> Modifier.border(2.dp, colors.selectGreen, shape)
                    else -> Modifier
                },
            )
            .padding(tv.tvSize(5)),
    ) {
        ChartBoardArtwork(
            board = board,
            index = index,
            cover = cover,
            modifier = Modifier.fillMaxWidth().height(tv.tvSize(78)),
        )
        Spacer(Modifier.height(tv.tvSize(4)))
        Text(
            text = board.name,
            color = if (focused || selected) colors.text else colors.subtext,
            fontSize = tv.tvFont(17),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (selected) TvText.chartCurrent else sourceLabel(index),
            color = if (selected) colors.selectGreen else colors.dimText,
            fontSize = tv.tvFont(12),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChartBoardArtwork(
    board: BoardInfo,
    index: Int,
    cover: String?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val shape = RoundedCornerShape(tv.tvSize(8))
    val accent = when (index % 5) {
        0 -> colors.primary
        1 -> colors.purple
        2 -> colors.orange
        3 -> colors.green
        else -> colors.blue
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(accent.copy(alpha = 0.72f), colors.bgWarm, colors.bgDeep)),
            ),
    ) {
        if (!cover.isNullOrBlank()) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f), Color.Black.copy(alpha = 0.7f)),
                        ),
                    ),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.align(Alignment.Center).size(tv.tvSize(38)),
            )
            Text(
                text = board.name.removeSuffix("榜").take(2),
                color = Color.White.copy(alpha = 0.86f),
                fontSize = tv.tvFont(26),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(tv.tvSize(9)),
            )
        }
        Text(
            text = "TOP",
            color = Color.White,
            fontSize = tv.tvFont(11),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(tv.tvSize(6))
                .background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(tv.tvSize(5)))
                .padding(horizontal = tv.tvSize(5), vertical = tv.tvSize(2)),
        )
    }
}

private fun sourceLabel(index: Int): String = when (index) {
    0 -> "每日更新"
    1 -> "热门趋势"
    2 -> "新歌速递"
    else -> "精选榜单"
}

@Composable
private fun ChartSongRow(
    index: Int,
    item: MusicItem,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val topThree = index < 3
    val shape = RoundedCornerShape(tv.tvSize(8))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Menu) {
                    onAddToQueue()
                    true
                } else {
                    false
                }
            }
            .clickable { onPlay() }
            .background(
                when {
                    focused -> colors.surfaceHigh
                    isCurrent -> colors.selectGreen.copy(alpha = 0.1f)
                    else -> Color.Transparent
                },
                shape,
            )
            .then(
                when {
                    focused -> Modifier.border(3.dp, colors.focus, shape)
                    isCurrent -> Modifier.border(1.dp, colors.selectGreen, shape)
                    else -> Modifier
                },
            )
            .padding(horizontal = tv.tvSize(12), vertical = tv.tvSize(5)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "%02d".format(index + 1),
            color = when {
                focused -> colors.selectGreen
                topThree -> colors.orange
                else -> colors.dimText
            },
            fontSize = if (topThree) tv.tvFont(22) else tv.tvFont(15),
            fontWeight = if (topThree) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(tv.tvSize(42)),
        )
        HomeCover(url = item.img, size = tv.tvSize(36), corner = 7)
        Spacer(Modifier.width(tv.tvSize(10)))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = if (focused) Color.White else colors.text,
                fontSize = tv.tvFont(18),
                fontWeight = if (focused || topThree) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.singer.ifBlank { TvText.unknownSinger },
                color = colors.subtext,
                fontSize = tv.tvFont(13),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            PlayingBars(color = colors.selectGreen)
            Spacer(Modifier.width(tv.tvSize(10)))
        }
        if (item.interval.isNotBlank()) {
            Text(
                text = item.interval,
                color = colors.dimText,
                fontSize = tv.tvFont(14),
            )
        }
    }
}

/** 我的音乐 Tab：收藏集合 + 本地文件夹。播放队列不在这里。 */
@Composable
fun MyListScreen(vm: PlayerViewModel = viewModel()) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val favorites by vm.favorites.collectAsState()
    val localFolders by vm.localFolders.collectAsState()
    val recent by vm.recent.collectAsState()
    val current by vm.current.collectAsState()
    val userPlaylists by vm.userPlaylists.collectAsState()

    val favoritesRequester = remember { FocusRequester() }
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    var openFolder by remember { mutableStateOf<LocalFolder?>(null) }
    var showFavorites by rememberSaveable { mutableStateOf(false) }
    var actionFolder by remember { mutableStateOf<LocalFolder?>(null) }
    var removeFolder by remember { mutableStateOf<LocalFolder?>(null) }
    var showManager by remember { mutableStateOf(false) }
    var hadDetail by remember { mutableStateOf(false) }
    var openUserPlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var playlistAction by remember { mutableStateOf<UserPlaylist?>(null) }
    var renamePlaylist by remember { mutableStateOf<UserPlaylist?>(null) }
    var deletePlaylist by remember { mutableStateOf<UserPlaylist?>(null) }
    var recentToRemove by remember { mutableStateOf<MusicItem?>(null) }
    var showClearRecent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.loadFavorites()
        vm.loadLocal()
        vm.loadRecent()
        vm.loadUserPlaylists()
    }
    LaunchedEffect(showFavorites, openFolder, openUserPlaylistId) {
        if (showFavorites || openFolder != null || openUserPlaylistId != null) {
            hadDetail = true
        } else if (hadDetail) {
            withFrameNanos { }
            withFrameNanos { }
            runCatching { favoritesRequester.requestFocus() }
            hadDetail = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) showPicker = true
    }

    if (showFavorites) {
        CollectionDetailPane(
            state = CollectionDetailUiState(
                title = TvText.favorites,
                meta = "${favorites.size} 首 · 收藏歌曲",
                cover = favorites.firstOrNull()?.img,
                songs = favorites,
            ),
            onBack = { showFavorites = false },
            onPlay = { index -> vm.playList(favorites, index, TvText.favorites) },
            onShuffle = { vm.playShuffled(favorites, TvText.favorites) },
            onAddToQueue = {
                if (favorites.isNotEmpty()) {
                    vm.addToQueue(favorites, TvText.favorites)
                    Toast.makeText(context, "已加入播放队列", Toast.LENGTH_SHORT).show()
                }
            },
            onRemoveTrack = { vm.removeFavorite(it) },
        )
        return
    }

    val openUserPlaylist = userPlaylists.firstOrNull { it.id == openUserPlaylistId }
    openUserPlaylist?.let { playlist ->
        CollectionDetailPane(
            state = CollectionDetailUiState(
                title = playlist.name,
                meta = "${playlist.songs.size} 首 · 我的歌单",
                cover = playlist.songs.firstOrNull()?.img,
                songs = playlist.songs,
            ),
            onBack = { openUserPlaylistId = null },
            onPlay = { index -> vm.playList(playlist.songs, index, playlist.name) },
            onShuffle = { vm.playShuffled(playlist.songs, playlist.name) },
            onAddToQueue = {
                if (playlist.songs.isNotEmpty()) {
                    vm.addToQueue(playlist.songs, playlist.name)
                    Toast.makeText(context, "已加入播放队列", Toast.LENGTH_SHORT).show()
                }
            },
            onRemoveTrack = { vm.removeSongFromUserPlaylist(playlist.id, it) },
            removeTrackLabel = PlaylistText.removeFromPlaylist,
        )
        return
    }

    openFolder?.let { folder ->
        CollectionDetailPane(
            state = CollectionDetailUiState(
                title = folder.name,
                meta = "${folder.songs.size} 首 · 本地文件夹",
                cover = folder.cover ?: folder.songs.firstOrNull()?.img,
                songs = folder.songs,
            ),
            onBack = { openFolder = null },
            onPlay = { index -> vm.playList(folder.songs, index, folder.name) },
            onShuffle = { vm.playShuffled(folder.songs, folder.name) },
            onAddToQueue = {
                if (folder.songs.isNotEmpty()) {
                    vm.addToQueue(folder.songs, folder.name)
                    Toast.makeText(
                        context,
                        "已将 ${folder.name} 加入播放队列（${folder.songs.size} 首）",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tv.tvSize(68), vertical = tv.tvSize(12)),
        verticalArrangement = Arrangement.spacedBy(tv.tvSize(7)),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = TvText.library,
                    color = colors.text,
                    fontSize = tv.tvFont(30),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${favorites.size} 首收藏 · ${localFolders.size} 个文件夹",
                    color = colors.dimText,
                    fontSize = tv.tvFont(15),
                )
                Spacer(Modifier.width(tv.tvSize(16)))
                HomePill(TvText.manage) { showManager = true }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = PlaylistText.myPlaylists,
                    color = colors.text,
                    fontSize = tv.tvFont(25),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(tv.tvSize(14)))
                Text(
                    text = if (userPlaylists.isEmpty()) {
                        PlaylistText.emptyPlaylistsHint
                    } else {
                        PlaylistText.playlistManageHint
                    },
                    color = colors.dimText,
                    fontSize = tv.tvFont(15),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                HomePill(PlaylistText.newPlaylist) { showCreatePlaylist = true }
            }
        }
        item {
            UserPlaylistRail(
                playlists = userPlaylists,
                onOpen = { openUserPlaylistId = it.id },
                onManage = { playlistAction = it },
                onCreate = { showCreatePlaylist = true },
            )
        }
        item {
            Text(
                text = PlaylistText.favoriteAndLocal,
                color = colors.text,
                fontSize = tv.tvFont(25),
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            LibraryCollectionRail(
                favorites = favorites,
                folders = localFolders,
                favoritesModifier = Modifier.focusRequester(favoritesRequester),
                onOpenFavorites = { showFavorites = true },
                onOpenFolder = { openFolder = it },
                onManageFolder = { actionFolder = it },
                onAddFolder = {
                    if (hasStoragePermission(context)) {
                        showPicker = true
                    } else {
                        permissionLauncher.launch(storagePermission())
                    }
                },
            )
        }
        if (recent.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = TvText.recentPlayed,
                        color = colors.text,
                        fontSize = tv.tvFont(22),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "OK 从该曲开始播放",
                        color = colors.dimText,
                        fontSize = tv.tvFont(14),
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = PlaylistText.recentManageHint,
                        color = colors.dimText,
                        fontSize = tv.tvFont(14),
                    )
                    Spacer(Modifier.weight(1f))
                    HomePill(PlaylistText.clearRecent) { showClearRecent = true }
                }
            }
            item {
                RecentTrackRail(
                    recent = recent,
                    current = current,
                    onPlay = { index -> vm.playList(recent, index, TvText.recentPlayed) },
                    onRemoveRequest = { recentToRemove = it },
                )
            }
        }
    }

    if (showCreatePlaylist) {
        PlaylistNameDialog(
            title = PlaylistText.newPlaylist,
            initialName = "",
            confirmLabel = PlaylistText.createAction,
            onDismiss = { showCreatePlaylist = false },
            onConfirm = { name ->
                val playlist = vm.createUserPlaylist(name)
                showCreatePlaylist = false
                if (playlist != null) {
                    Toast.makeText(
                        context,
                        PlaylistText.playlistCreated.replace("{name}", playlist.name),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }

    playlistAction?.let { playlist ->
        PlaylistActionDialog(
            playlist = playlist,
            onDismiss = { playlistAction = null },
            onOpen = {
                playlistAction = null
                openUserPlaylistId = playlist.id
            },
            onRename = {
                playlistAction = null
                renamePlaylist = playlist
            },
            onDelete = {
                playlistAction = null
                deletePlaylist = playlist
            },
        )
    }

    renamePlaylist?.let { playlist ->
        PlaylistNameDialog(
            title = PlaylistText.renamePlaylist,
            initialName = playlist.name,
            confirmLabel = PlaylistText.saveAction,
            onDismiss = { renamePlaylist = null },
            onConfirm = { name ->
                vm.renameUserPlaylist(playlist.id, name)
                renamePlaylist = null
                Toast.makeText(context, "歌单已重命名", Toast.LENGTH_SHORT).show()
            },
        )
    }

    deletePlaylist?.let { playlist ->
        ConfirmActionDialog(
            title = "${PlaylistText.deletePlaylist}「${playlist.name}」？",
            message = PlaylistText.deletePlaylistConfirm,
            confirmLabel = PlaylistText.deletePlaylist,
            onCancel = { deletePlaylist = null },
            onConfirm = {
                vm.removeUserPlaylist(playlist.id)
                if (openUserPlaylistId == playlist.id) openUserPlaylistId = null
                deletePlaylist = null
                Toast.makeText(context, "歌单已删除", Toast.LENGTH_SHORT).show()
            },
        )
    }

    recentToRemove?.let { item ->
        ConfirmActionDialog(
            title = "${PlaylistText.deleteRecent}「${item.name}」？",
            message = "只会从最近播放中移除，不影响喜欢、歌单和本地文件。",
            confirmLabel = PlaylistText.deleteRecent,
            onCancel = { recentToRemove = null },
            onConfirm = {
                vm.removeRecent(item)
                recentToRemove = null
                Toast.makeText(context, PlaylistText.recentDeleted, Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (showClearRecent) {
        ConfirmActionDialog(
            title = PlaylistText.clearRecent,
            message = PlaylistText.clearRecentConfirm,
            confirmLabel = PlaylistText.clearRecent,
            onCancel = { showClearRecent = false },
            onConfirm = {
                vm.clearRecent()
                showClearRecent = false
                Toast.makeText(context, PlaylistText.recentCleared, Toast.LENGTH_SHORT).show()
            },
        )
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

    if (showManager) {
        LibraryManagerDialog(
            folders = localFolders,
            onDismiss = { showManager = false },
            onRescan = {
                vm.loadLocal()
                Toast.makeText(context, "正在重新扫描本地文件夹", Toast.LENGTH_SHORT).show()
            },
            onRemoveRequest = { folder ->
                showManager = false
                removeFolder = folder
            },
        )
    }

    actionFolder?.let { folder ->
        FolderActionDialog(
            folder = folder,
            onDismiss = { actionFolder = null },
            onOpen = {
                actionFolder = null
                openFolder = folder
            },
            onAddToQueue = {
                actionFolder = null
                if (folder.songs.isNotEmpty()) {
                    vm.addToQueue(folder.songs, folder.name)
                    Toast.makeText(
                        context,
                        "已将 ${folder.name} 加入播放队列（${folder.songs.size} 首）",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onRescan = {
                actionFolder = null
                vm.loadLocal()
                Toast.makeText(context, "正在重新扫描本地文件夹", Toast.LENGTH_SHORT).show()
            },
            onRemoveRequest = {
                actionFolder = null
                removeFolder = folder
            },
        )
    }

    removeFolder?.let { folder ->
        RemoveFolderConfirmDialog(
            folderName = folder.name,
            onCancel = { removeFolder = null },
            onConfirm = {
                vm.removeLocalFolder(folder.uri)
                removeFolder = null
                Toast.makeText(context, "已移除文件夹：${folder.name}", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun UserPlaylistRail(
    playlists: List<UserPlaylist>,
    onOpen: (UserPlaylist) -> Unit,
    onManage: (UserPlaylist) -> Unit,
    onCreate: () -> Unit,
) {
    val tv = LocalTvMetrics.current
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(tv.tvSize(216)),
        horizontalArrangement = Arrangement.spacedBy(tv.tvSize(14)),
    ) {
        items(playlists, key = { it.id }) { playlist ->
            UserPlaylistCard(
                playlist = playlist,
                onOpen = { onOpen(playlist) },
                onManage = { onManage(playlist) },
            )
        }
        item { AddPlaylistCard(onClick = onCreate) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserPlaylistCard(
    playlist: UserPlaylist,
    onOpen: () -> Unit,
    onManage: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    var longPressTriggered by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(12))
    Column(
        modifier = Modifier
            .width(tv.tvSize(166))
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onOpen, onLongClick = onManage)
            .onKeyEvent { event ->
                val confirmKey = event.key == Key.DirectionCenter || event.key == Key.Enter
                when {
                    event.type == KeyEventType.KeyDown && confirmKey -> {
                        if (event.nativeKeyEvent.repeatCount > 0) {
                            if (!longPressTriggered) {
                                longPressTriggered = true
                                onManage()
                            }
                            true
                        } else {
                            longPressTriggered = false
                            false
                        }
                    }
                    event.type == KeyEventType.KeyUp && confirmKey && longPressTriggered -> {
                        longPressTriggered = false
                        true
                    }
                    event.type == KeyEventType.KeyUp && event.key == Key.Menu -> {
                        onManage()
                        true
                    }
                    else -> false
                }
            }
            .background(if (focused) colors.surfaceHigh else colors.surface, shape)
            .border(if (focused) 3.dp else 0.dp, colors.focus, shape)
            .padding(tv.tvSize(8)),
    ) {
        Box {
            HomeCover(url = playlist.songs.firstOrNull()?.img, size = tv.tvSize(146), corner = 10)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(tv.tvSize(8))
                    .background(Color.Black.copy(alpha = 0.62f), CircleShape)
                    .padding(horizontal = tv.tvSize(8), vertical = tv.tvSize(4)),
            ) {
                Text(
                    text = "${playlist.songs.size}",
                    color = Color.White,
                    fontSize = tv.tvFont(14),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(tv.tvSize(6)))
        Text(
            text = playlist.name,
            color = colors.text,
            fontSize = tv.tvFont(18),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (playlist.songs.isEmpty()) PlaylistText.emptyPlaylists else "${playlist.songs.size} 首 · 长按管理",
            color = colors.dimText,
            fontSize = tv.tvFont(14),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddPlaylistCard(onClick: () -> Unit) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(12))
    Column(
        modifier = Modifier
            .width(tv.tvSize(166))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(if (focused) colors.surfaceHigh else colors.surface, shape)
            .border(
                width = if (focused) 3.dp else 2.dp,
                color = if (focused) colors.focus else colors.border,
                shape = shape,
            )
            .padding(tv.tvSize(8)),
    ) {
        Box(
            modifier = Modifier.size(tv.tvSize(146)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = if (focused) colors.selectGreen else colors.dimText,
                fontSize = tv.tvFont(54),
            )
        }
        Spacer(Modifier.height(tv.tvSize(6)))
        Text(
            text = PlaylistText.newPlaylist,
            color = if (focused) Color.White else colors.text,
            fontSize = tv.tvFont(18),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = PlaylistText.createPlaylistHint,
            color = colors.dimText,
            fontSize = tv.tvFont(13),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibraryCollectionRail(
    favorites: List<MusicItem>,
    folders: List<LocalFolder>,
    favoritesModifier: Modifier,
    onOpenFavorites: () -> Unit,
    onOpenFolder: (LocalFolder) -> Unit,
    onManageFolder: (LocalFolder) -> Unit,
    onAddFolder: () -> Unit,
) {
    val tv = LocalTvMetrics.current
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(tv.tvSize(216)),
        horizontalArrangement = Arrangement.spacedBy(tv.tvSize(14)),
    ) {
        item {
            FavoriteCollectionCard(
                count = favorites.size,
                cover = favorites.firstOrNull()?.img,
                modifier = favoritesModifier,
                onClick = onOpenFavorites,
            )
        }
        items(folders, key = { it.uri }) { folder ->
            LocalFolderCard(
                folder = folder,
                onOpen = { onOpenFolder(folder) },
                onManage = { onManageFolder(folder) },
            )
        }
        item { AddFolderCard(onClick = onAddFolder) }
    }
}

/** 收藏集合与本地文件夹使用同一种卡片，避免收藏被误认为独立页面。 */
@Composable
private fun FavoriteCollectionCard(
    count: Int,
    cover: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .width(tv.tvSize(166))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(
                if (focused) colors.surfaceHigh else colors.surface,
                RoundedCornerShape(tv.tvSize(12)),
            )
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = colors.focus,
                shape = RoundedCornerShape(tv.tvSize(12)),
            )
            .padding(tv.tvSize(8)),
    ) {
        Box {
            HomeCover(url = cover, size = tv.tvSize(146), corner = 10)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(tv.tvSize(8))
                    .background(Color.Black.copy(alpha = 0.58f), CircleShape)
                    .padding(horizontal = tv.tvSize(8), vertical = tv.tvSize(4)),
            ) {
                Text("\u2665", color = colors.selectGreen, fontSize = tv.tvFont(15))
            }
        }
        Spacer(Modifier.height(tv.tvSize(6)))
        Text(
            text = TvText.favorites,
            color = colors.text,
            fontSize = tv.tvFont(18),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (count > 0) "$count 首 · 收藏" else "暂无收藏",
            color = colors.dimText,
            fontSize = tv.tvFont(14),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentTrackRail(
    recent: List<MusicItem>,
    current: MusicItem?,
    onPlay: (Int) -> Unit,
    onRemoveRequest: (MusicItem) -> Unit,
) {
    val tv = LocalTvMetrics.current
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(tv.tvSize(188)),
        horizontalArrangement = Arrangement.spacedBy(tv.tvSize(14)),
    ) {
        itemsIndexed(recent, key = { _, item -> "${item.source}:${item.songmid}" }) { index, item ->
            val isCurrent = current?.source == item.source && current.songmid == item.songmid
            RecentTrackCard(
                item = item,
                isCurrent = isCurrent,
                onClick = { onPlay(index) },
                onRemoveRequest = { onRemoveRequest(item) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentTrackCard(
    item: MusicItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemoveRequest: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    var longPressTriggered by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(tv.tvSize(12))
    Box(
        modifier = Modifier
            .width(tv.tvSize(170))
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onClick, onLongClick = onRemoveRequest)
            .onKeyEvent { event ->
                val confirmKey = event.key == Key.DirectionCenter || event.key == Key.Enter
                when {
                    event.type == KeyEventType.KeyDown && confirmKey -> {
                        if (event.nativeKeyEvent.repeatCount > 0) {
                            if (!longPressTriggered) {
                                longPressTriggered = true
                                onRemoveRequest()
                            }
                            true
                        } else {
                            longPressTriggered = false
                            false
                        }
                    }
                    event.type == KeyEventType.KeyUp && confirmKey && longPressTriggered -> {
                        longPressTriggered = false
                        true
                    }
                    event.type == KeyEventType.KeyUp && event.key == Key.Menu -> {
                        onRemoveRequest()
                        true
                    }
                    else -> false
                }
            }
            .background(
                if (focused) colors.surfaceHigh else colors.surface,
                cardShape,
            )
            .border(
                width = when {
                    focused -> 3.dp
                    isCurrent -> 2.dp
                    else -> 0.dp
                },
                color = if (focused) colors.focus else colors.selectGreen,
                shape = cardShape,
            )
            .padding(tv.tvSize(6)),
    ) {
        Box(
            modifier = Modifier
                .size(tv.tvSize(158))
                .clip(RoundedCornerShape(tv.tvSize(10))),
        ) {
            HomeCover(url = item.img, size = tv.tvSize(158), corner = 10)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f), Color.Black.copy(alpha = 0.9f)),
                        ),
                    ),
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(tv.tvSize(9))
                        .background(colors.selectGreen, CircleShape)
                        .padding(horizontal = tv.tvSize(7), vertical = tv.tvSize(6)),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayingBars(color = colors.bgDeep)
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = tv.tvSize(11), vertical = tv.tvSize(10)),
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = tv.tvFont(17),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.singer.ifBlank { TvText.unknownSinger },
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = tv.tvFont(14),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocalFolderCard(
    folder: LocalFolder,
    onOpen: () -> Unit,
    onManage: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(tv.tvSize(166))
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onOpen, onLongClick = onManage)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Menu) {
                    onManage()
                    true
                } else {
                    false
                }
            }
            .background(
                if (focused) colors.surfaceHigh else colors.surface,
                RoundedCornerShape(tv.tvSize(12)),
            )
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = colors.focus,
                shape = RoundedCornerShape(tv.tvSize(12)),
            )
            .padding(tv.tvSize(8)),
    ) {
        HomeCover(
            url = folder.cover ?: folder.songs.firstOrNull()?.img,
            size = tv.tvSize(146),
            corner = 10,
        )
        Spacer(Modifier.height(tv.tvSize(6)))
        Text(
            text = folder.name,
            color = colors.text,
            fontSize = tv.tvFont(18),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${folder.songs.size} 首 · MENU 管理",
            color = colors.dimText,
            fontSize = tv.tvFont(14),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddFolderCard(onClick: () -> Unit) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(tv.tvSize(166))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(
                if (focused) colors.surfaceHigh else colors.surface,
                RoundedCornerShape(tv.tvSize(12)),
            )
            .border(
                width = if (focused) 3.dp else 2.dp,
                color = if (focused) colors.focus else colors.border,
                shape = RoundedCornerShape(tv.tvSize(12)),
            )
            .padding(tv.tvSize(8)),
    ) {
        Box(
            modifier = Modifier.size(tv.tvSize(146)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = if (focused) colors.selectGreen else colors.dimText,
                fontSize = tv.tvFont(54),
            )
        }
        Spacer(Modifier.height(tv.tvSize(6)))
        Text(
            text = TvText.addLocalSongDisk,
            color = if (focused) Color.White else colors.text,
            fontSize = tv.tvFont(18),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "电视或 U 盘目录",
            color = colors.dimText,
            fontSize = tv.tvFont(14),
        )
    }
}

@Composable
private fun LibraryManagerDialog(
    folders: List<LocalFolder>,
    onDismiss: () -> Unit,
    onRescan: () -> Unit,
    onRemoveRequest: (LocalFolder) -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val firstRescanRequester = remember { FocusRequester() }
    val closeRequester = remember { FocusRequester() }

    LaunchedEffect(folders.size) {
        withFrameNanos { }
        withFrameNanos { }
        val requester = if (folders.isEmpty()) closeRequester else firstRescanRequester
        runCatching { requester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(tv.tvSize(760))
                    .background(colors.bgWarm, RoundedCornerShape(tv.tvSize(16)))
                    .border(2.dp, colors.focus, RoundedCornerShape(tv.tvSize(16)))
                    .padding(tv.tvSize(34)),
                verticalArrangement = Arrangement.spacedBy(tv.tvSize(10)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = TvText.manageLocalMusic,
                        color = colors.text,
                        fontSize = tv.tvFont(30),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${folders.size} 个文件夹",
                        color = colors.dimText,
                        fontSize = tv.tvFont(16),
                    )
                }
                Text(
                    text = TvText.manageLocalMusicHint,
                    color = colors.subtext,
                    fontSize = tv.tvFont(16),
                )
                Spacer(Modifier.height(tv.tvSize(2)))

                if (folders.isEmpty()) {
                    Text(
                        text = TvText.manageEmptyFolders,
                        color = colors.dimText,
                        fontSize = tv.tvFont(18),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(tv.tvSize(82 * folders.size.coerceIn(1, 3))),
                        verticalArrangement = Arrangement.spacedBy(tv.tvSize(8)),
                    ) {
                        itemsIndexed(folders, key = { _, folder -> folder.uri }) { index, folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.surface, RoundedCornerShape(tv.tvSize(10)))
                                    .padding(horizontal = tv.tvSize(16), vertical = tv.tvSize(12)),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = folder.name,
                                        color = colors.text,
                                        fontSize = tv.tvFont(20),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${folder.songs.size} 首",
                                        color = colors.dimText,
                                        fontSize = tv.tvFont(15),
                                    )
                                }
                                HomePill(
                                    label = TvText.rescanFolder,
                                    modifier = if (index == 0) {
                                        Modifier.focusRequester(firstRescanRequester)
                                    } else {
                                        Modifier
                                    },
                                ) { onRescan() }
                                Spacer(Modifier.width(tv.tvSize(10)))
                                HomePill(TvText.removeFolder) { onRemoveRequest(folder) }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(tv.tvSize(4)))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    HomePill(
                        label = TvText.closeAction,
                        modifier = Modifier.focusRequester(closeRequester),
                    ) { onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun FolderActionDialog(
    folder: LocalFolder,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onAddToQueue: () -> Unit,
    onRescan: () -> Unit,
    onRemoveRequest: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val firstRequester = remember { FocusRequester() }

    LaunchedEffect(folder.uri) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { firstRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(tv.tvSize(560))
                    .background(colors.bgWarm, RoundedCornerShape(tv.tvSize(16)))
                    .border(2.dp, colors.focus, RoundedCornerShape(tv.tvSize(16)))
                    .padding(tv.tvSize(34)),
                verticalArrangement = Arrangement.spacedBy(tv.tvSize(12)),
            ) {
                Text(
                    text = TvText.folderActionTitle,
                    color = colors.text,
                    fontSize = tv.tvFont(30),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = folder.name,
                    color = colors.subtext,
                    fontSize = tv.tvFont(20),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${folder.songs.size} 首本地歌曲",
                    color = colors.dimText,
                    fontSize = tv.tvFont(16),
                )
                Spacer(Modifier.height(tv.tvSize(4)))
                HomePill("打开", Modifier.fillMaxWidth().focusRequester(firstRequester)) {
                    onOpen()
                }
                HomePill(TvText.addToQueue, Modifier.fillMaxWidth()) { onAddToQueue() }
                HomePill(TvText.rescanFolder, Modifier.fillMaxWidth()) { onRescan() }
                HomePill(TvText.removeFolder, Modifier.fillMaxWidth()) { onRemoveRequest() }
                HomePill(TvText.cancelAction, Modifier.fillMaxWidth()) { onDismiss() }
            }
        }
    }
}

@Composable
private fun RemoveFolderConfirmDialog(
    folderName: String,
    onCancel: () -> Unit,
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
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(tv.tvSize(640))
                    .background(colors.bgWarm, RoundedCornerShape(tv.tvSize(16)))
                    .border(2.dp, colors.focus, RoundedCornerShape(tv.tvSize(16)))
                    .padding(tv.tvSize(40)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "移除「$folderName」？",
                    color = colors.text,
                    fontSize = tv.tvFont(30),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(tv.tvSize(14)))
                Text(
                    text = TvText.removeFolderConfirm,
                    color = colors.subtext,
                    fontSize = tv.tvFont(19),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(tv.tvSize(30)))
                Row(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(18))) {
                    HomePill(TvText.cancelAction, Modifier.focusRequester(cancelRequester)) { onCancel() }
                    HomePill(TvText.removeFolder) { onConfirm() }
                }
            }
        }
    }
}

@Composable
private fun PlaylistActionDialog(
    playlist: UserPlaylist,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val firstRequester = remember { FocusRequester() }

    LaunchedEffect(playlist.id) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { firstRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(tv.tvSize(560))
                    .background(colors.bgWarm, RoundedCornerShape(tv.tvSize(16)))
                    .border(2.dp, colors.focus, RoundedCornerShape(tv.tvSize(16)))
                    .padding(tv.tvSize(34)),
                verticalArrangement = Arrangement.spacedBy(tv.tvSize(12)),
            ) {
                Text(
                    text = PlaylistText.playlistActionTitle,
                    color = colors.text,
                    fontSize = tv.tvFont(30),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = playlist.name,
                    color = colors.subtext,
                    fontSize = tv.tvFont(20),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${playlist.songs.size} 首歌曲",
                    color = colors.dimText,
                    fontSize = tv.tvFont(16),
                )
                Spacer(Modifier.height(tv.tvSize(4)))
                HomePill("打开", Modifier.fillMaxWidth().focusRequester(firstRequester)) {
                    onOpen()
                }
                HomePill(PlaylistText.renamePlaylist, Modifier.fillMaxWidth()) { onRename() }
                HomePill(PlaylistText.deletePlaylist, Modifier.fillMaxWidth()) { onDelete() }
                HomePill(TvText.cancelAction, Modifier.fillMaxWidth()) { onDismiss() }
            }
        }
    }
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onCancel: () -> Unit,
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
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(tv.tvSize(640))
                    .background(colors.bgWarm, RoundedCornerShape(tv.tvSize(16)))
                    .border(2.dp, colors.focus, RoundedCornerShape(tv.tvSize(16)))
                    .padding(tv.tvSize(40)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    color = colors.text,
                    fontSize = tv.tvFont(28),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(tv.tvSize(14)))
                Text(
                    text = message,
                    color = colors.subtext,
                    fontSize = tv.tvFont(18),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(tv.tvSize(30)))
                Row(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(18))) {
                    HomePill(TvText.cancelAction, Modifier.focusRequester(cancelRequester)) { onCancel() }
                    HomePill(confirmLabel) { onConfirm() }
                }
            }
        }
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var name by remember(initialName) { mutableStateOf(initialName) }
    var fieldFocused by remember { mutableStateOf(false) }
    val fieldRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun submit() {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) onConfirm(trimmed)
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
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(tv.tvSize(720))
                    .background(colors.bgWarm, RoundedCornerShape(tv.tvSize(16)))
                    .border(2.dp, colors.focus, RoundedCornerShape(tv.tvSize(16)))
                    .padding(tv.tvSize(38)),
                verticalArrangement = Arrangement.spacedBy(tv.tvSize(12)),
            ) {
                Text(
                    text = title,
                    color = colors.text,
                    fontSize = tv.tvFont(30),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = PlaylistText.playlistName,
                    color = colors.subtext,
                    fontSize = tv.tvFont(17),
                )
                BasicTextField(
                    value = name,
                    onValueChange = { if (it.length <= 24) name = it },
                    singleLine = true,
                    textStyle = TextStyle(color = colors.text, fontSize = tv.tvFont(24)),
                    cursorBrush = SolidColor(colors.selectGreen),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tv.tvSize(66))
                        .focusRequester(fieldRequester)
                        .onFocusChanged { fieldFocused = it.isFocused }
                        .background(colors.surface, RoundedCornerShape(tv.tvSize(10)))
                        .border(
                            width = if (fieldFocused) 3.dp else 1.dp,
                            color = if (fieldFocused) colors.focus else colors.border,
                            shape = RoundedCornerShape(tv.tvSize(10)),
                        )
                        .padding(horizontal = tv.tvSize(18), vertical = tv.tvSize(14)),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (name.isEmpty()) {
                                Text(
                                    text = PlaylistText.playlistNameHint,
                                    color = colors.dimText,
                                    fontSize = tv.tvFont(21),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                Text(
                    text = PlaylistText.textInputHint,
                    color = colors.dimText,
                    fontSize = tv.tvFont(15),
                )
                Spacer(Modifier.height(tv.tvSize(4)))
                Row(horizontalArrangement = Arrangement.spacedBy(tv.tvSize(18))) {
                    HomePill(TvText.cancelAction) { onDismiss() }
                    HomePill(confirmLabel) { submit() }
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
                    "此文件夹共 $audioCount 首音乐（含子文件夹），按「添加此文件夹」添加"
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
                SettingActionButton(TvText.importThisFolder) { onConfirm(dir) }
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
 * 搜索页：顶部搜索栏 + 搜索发现 / 大尺寸结果列表 + 按需展开的电视键盘。
 * 进入时优先聚焦搜索框；键盘只在需要输入时占用屏幕空间。
 */
@Composable
fun SearchScreen(vm: PlayerViewModel = viewModel()) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var text by rememberSaveable { mutableStateOf(String()) }
    var keyboardVisible by rememberSaveable { mutableStateOf(false) }
    var focusResultsAfterSearch by remember { mutableStateOf(false) }

    val results by vm.tvSearchResults.collectAsState()
    val pageInfo by vm.tvSearchPage.collectAsState()
    val loading by vm.tvSearchLoading.collectAsState()
    val source by vm.tvSearchSource.collectAsState()
    val history by vm.tvSearchHistory.collectAsState()
    val activeQuery by vm.tvSearchQuery.collectAsState()
    val error by vm.tvSearchError.collectAsState()
    val current by vm.current.collectAsState()

    val searchFieldFocus = remember { FocusRequester() }
    val firstKeyFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }

    val hotWords = remember {
        listOf(
            "周杰伦", "林俊杰", "陈奕迅", "邓紫棋",
            "薛之谦", "毛不易", "五月天", "周深",
        )
    }
    val sourceTabs = remember {
        listOf(PlayerViewModel.SOURCE_ALL) + MusicSources.builtin.keys
    }

    fun submit(keyword: String = text) {
        val query = keyword.trim()
        if (query.isBlank()) {
            keyboardVisible = true
            return
        }
        text = query
        keyboardVisible = false
        focusResultsAfterSearch = true
        vm.searchTv(query, 1)
    }

    LaunchedEffect(Unit) {
        if (text.isBlank() && activeQuery.isNotBlank()) text = activeQuery
        withFrameNanos { }
        withFrameNanos { }
        runCatching { searchFieldFocus.requestFocus() }
    }
    LaunchedEffect(activeQuery) {
        if (activeQuery.isNotBlank() && text.isBlank()) text = activeQuery
    }
    LaunchedEffect(keyboardVisible) {
        if (!keyboardVisible) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        runCatching { firstKeyFocus.requestFocus() }
    }
    LaunchedEffect(focusResultsAfterSearch, loading, results.size) {
        if (!focusResultsAfterSearch || loading) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        if (results.isNotEmpty()) {
            runCatching { firstResultFocus.requestFocus() }
        } else {
            runCatching { searchFieldFocus.requestFocus() }
        }
        focusResultsAfterSearch = false
    }

    BackHandler(enabled = keyboardVisible) {
        keyboardVisible = false
        runCatching { searchFieldFocus.requestFocus() }
    }

    val showingResults = activeQuery.isNotBlank() || loading || results.isNotEmpty() || error != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tv.tvSize(64), vertical = tv.tvSize(22)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tv.tvSize(20)),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = TvText.searchTitle,
                    color = colors.text,
                    fontSize = tv.tvFont(36),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(tv.tvSize(4)))
                Text(
                    text = if (activeQuery.isNotBlank()) {
                        if (loading) "${TvText.searching} · $activeQuery"
                        else "${pageInfo.total} ${TvText.songs} · “$activeQuery”"
                    } else {
                        TvText.searchLandingHint
                    },
                    color = colors.dimText,
                    fontSize = tv.tvFont(17),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (activeQuery.isNotBlank()) {
                SearchTextAction(TvText.clear) {
                    text = String()
                    keyboardVisible = false
                    vm.clearTvSearch()
                    runCatching { searchFieldFocus.requestFocus() }
                }
            }
        }
        Spacer(Modifier.height(tv.tvSize(14)))
        SearchInputBar(
            text = text,
            focusRequester = searchFieldFocus,
            onEdit = { keyboardVisible = true },
            onSubmit = { submit() },
        )
        Spacer(Modifier.height(tv.tvSize(16)))

        if (showingResults) {
            SearchSourceRail(
                source = source,
                sourceTabs = sourceTabs,
                pageInfo = pageInfo,
                loading = loading,
                onSelect = { vm.setTvSearchSource(it) },
            )
            Spacer(Modifier.height(tv.tvSize(10)))
            SearchResultsPane(
                query = activeQuery,
                results = results,
                current = current,
                pageInfo = pageInfo,
                loading = loading,
                error = error,
                firstResultFocus = firstResultFocus,
                onRetry = { submit(activeQuery) },
                onLoadMore = { vm.searchTv(activeQuery, pageInfo.page + 1) },
                onPlay = { index -> vm.playList(results, index, TvText.searchResult) },
                onAddToQueue = { vm.addToQueue(listOf(it), TvText.searchResult) },
                modifier = Modifier.weight(1f),
            )
        } else {
            SearchDiscovery(
                history = history,
                hotWords = hotWords,
                onPick = { submit(it) },
                onClearHistory = { vm.clearTvSearchHistory() },
                modifier = Modifier.weight(1f),
            )
        }

        if (keyboardVisible) {
            Spacer(Modifier.height(tv.tvSize(12)))
            SearchKeyboardPanel(
                firstKeyFocus = firstKeyFocus,
                onKey = { text += it },
                onBackspace = { if (text.isNotEmpty()) text = text.dropLast(1) },
                onClear = {
                    text = String()
                    vm.clearTvSearch()
                },
                onSubmit = { submit() },
            )
        }
    }
}

@Composable
private fun SearchInputBar(
    text: String,
    focusRequester: FocusRequester,
    onEdit: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(16))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tv.tvSize(14)),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(tv.tvSize(68))
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .clickable { onEdit() }
                .background(if (focused) colors.surfaceHigh else colors.surface, shape)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) colors.selectGreen else colors.border,
                    shape = shape,
                )
                .padding(horizontal = tv.tvSize(22)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = if (focused) colors.selectGreen else colors.subtext,
                modifier = Modifier.size(tv.tvSize(28)),
            )
            Spacer(Modifier.width(tv.tvSize(14)))
            Text(
                text = text.ifBlank { TvText.searchPlaceholder },
                color = if (text.isBlank()) colors.dimText else colors.text,
                fontSize = tv.tvFont(23),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = TvText.searchInputHint,
                color = colors.dimText,
                fontSize = tv.tvFont(14),
                maxLines = 1,
            )
        }
        SettingActionButton(TvText.search) { onSubmit() }
    }
}

@Composable
private fun SearchSourceRail(
    source: String,
    sourceTabs: List<String>,
    pageInfo: PlayerViewModel.TvSearchPageInfo,
    loading: Boolean,
    onSelect: (String) -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tv.tvSize(10)),
    ) {
        Text(
            text = TvText.searchSource,
            color = colors.dimText,
            fontSize = tv.tvFont(16),
        )
        sourceTabs.forEach { id ->
            SearchSourceChip(
                label = if (id == PlayerViewModel.SOURCE_ALL) TvText.allSources
                else MusicSources.displayName(id),
                selected = source == id,
                onClick = { onSelect(id) },
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = if (loading) TvText.searching
            else "${pageInfo.page}/${pageInfo.maxPage} · ${pageInfo.total} ${TvText.songs}",
            color = colors.dimText,
            fontSize = tv.tvFont(15),
            maxLines = 1,
        )
    }
}

@Composable
private fun SearchResultsPane(
    query: String,
    results: List<MusicItem>,
    current: MusicItem?,
    pageInfo: PlayerViewModel.TvSearchPageInfo,
    loading: Boolean,
    error: String?,
    firstResultFocus: FocusRequester,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onPlay: (Int) -> Unit,
    onAddToQueue: (MusicItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tv.tvSize(18)),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = TvText.searchResult,
                    color = colors.text,
                    fontSize = tv.tvFont(26),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(tv.tvSize(3)))
                Text(
                    text = if (loading) TvText.searching
                    else "${pageInfo.total} ${TvText.songs} · ${TvText.searchPlayHint}",
                    color = colors.dimText,
                    fontSize = tv.tvFont(15),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.width(tv.tvSize(130)).height(tv.tvSize(3)),
                    color = colors.selectGreen,
                    trackColor = colors.surfaceHigh,
                )
            }
        }
        Spacer(Modifier.height(tv.tvSize(10)))

        when {
            loading && results.isEmpty() -> SearchLoadingSkeleton(
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            error != null && results.isEmpty() -> SearchMessageState(
                title = TvText.loadFailed,
                hint = error,
                actionLabel = TvText.chartRetry,
                onAction = onRetry,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            results.isEmpty() -> SearchMessageState(
                title = TvText.searchNoResult,
                hint = if (query.isBlank()) TvText.searchNoResultHint
                else "“$query” · ${TvText.searchNoResultHint}",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(tv.tvSize(7)),
                ) {
                    itemsIndexed(
                        items = results,
                        key = { index, item -> "${item.source}:${item.songmid}:$index" },
                    ) { index, item ->
                        SearchResultCard(
                            index = index,
                            item = item,
                            isCurrent = current?.let {
                                it.source == item.source && it.songmid == item.songmid
                            } == true,
                            focusRequester = if (index == 0) firstResultFocus else null,
                            onClick = { onPlay(index) },
                            onAddToQueue = { onAddToQueue(item) },
                        )
                    }
                    if (pageInfo.page < pageInfo.maxPage) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = tv.tvSize(8)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (loading) {
                                    Text(
                                        text = TvText.searching,
                                        color = colors.dimText,
                                        fontSize = tv.tvFont(17),
                                    )
                                } else {
                                    SettingActionButton(TvText.loadMore, onLoadMore)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(tv.tvSize(8))) }
                }
                if (error != null) {
                    Text(
                        text = error,
                        color = colors.danger,
                        fontSize = tv.tvFont(14),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = tv.tvSize(4)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSourceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(18))
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(
                when {
                    selected -> colors.selectGreen
                    focused -> colors.surfaceHigh
                    else -> colors.surface
                },
                shape,
            )
            .border(if (focused) 2.dp else 0.dp, colors.focus, shape)
            .padding(horizontal = tv.tvSize(17), vertical = tv.tvSize(7)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected || focused) Color.White else colors.subtext,
            fontSize = tv.tvFont(16),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SearchTextAction(label: String, onClick: () -> Unit) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(12))
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(if (focused) colors.surfaceHigh else colors.surface, shape)
            .border(if (focused) 2.dp else 0.dp, colors.focus, shape)
            .padding(horizontal = tv.tvSize(18), vertical = tv.tvSize(8)),
    ) {
        Text(
            text = label,
            color = if (focused) Color.White else colors.subtext,
            fontSize = tv.tvFont(16),
        )
    }
}

@Composable
private fun SearchResultCard(
    index: Int,
    item: MusicItem,
    isCurrent: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(10))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { base ->
                if (focusRequester != null) base.focusRequester(focusRequester) else base
            }
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Menu) {
                    onAddToQueue()
                    true
                } else {
                    false
                }
            }
            .clickable { onClick() }
            .background(
                when {
                    focused -> colors.surfaceHigh
                    isCurrent -> colors.selectGreen.copy(alpha = 0.1f)
                    else -> colors.surface
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> colors.focus
                    isCurrent -> colors.selectGreen
                    else -> colors.line
                },
                shape = shape,
            )
            .padding(horizontal = tv.tvSize(14), vertical = tv.tvSize(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "%02d".format(index + 1),
            color = if (focused) colors.selectGreen else colors.dimText,
            fontSize = tv.tvFont(15),
            modifier = Modifier.width(tv.tvSize(38)),
        )
        HomeCover(url = item.img, size = tv.tvSize(52), corner = 9)
        Spacer(Modifier.width(tv.tvSize(14)))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = if (focused) Color.White else colors.text,
                fontSize = tv.tvFont(20),
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(tv.tvSize(2)))
            Text(
                text = buildString {
                    append(item.singer.ifBlank { TvText.unknownSinger })
                    append(TvText.dot)
                    append(item.albumName.ifBlank { TvText.unknownAlbum })
                },
                color = colors.subtext,
                fontSize = tv.tvFont(14),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SearchSourceBadge(item.source)
        Spacer(Modifier.width(tv.tvSize(14)))
        if (item.interval.isNotBlank()) {
            Text(
                text = item.interval,
                color = colors.dimText,
                fontSize = tv.tvFont(15),
                modifier = Modifier.width(tv.tvSize(54)),
            )
        }
        if (isCurrent) {
            Spacer(Modifier.width(tv.tvSize(8)))
            Text(
                text = TvText.nowPlaying,
                color = colors.selectGreen,
                fontSize = tv.tvFont(13),
                maxLines = 1,
            )
        }
        if (focused) {
            Spacer(Modifier.width(tv.tvSize(8)))
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = colors.selectGreen,
                modifier = Modifier.size(tv.tvSize(24)),
            )
        }
    }
}

@Composable
private fun SearchSourceBadge(source: String) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val shape = RoundedCornerShape(tv.tvSize(6))
    Box(
        modifier = Modifier
            .background(colors.primarySoft, shape)
            .padding(horizontal = tv.tvSize(8), vertical = tv.tvSize(3)),
    ) {
        Text(
            text = MusicSources.displayName(source),
            color = colors.primaryHigh,
            fontSize = tv.tvFont(12),
            maxLines = 1,
        )
    }
}

@Composable
private fun SearchLoadingSkeleton(modifier: Modifier = Modifier) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val shape = RoundedCornerShape(tv.tvSize(10))
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(tv.tvSize(7)),
    ) {
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tv.tvSize(64))
                    .background(colors.surface, shape)
                    .padding(horizontal = tv.tvSize(14)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(tv.tvSize(46))
                        .background(colors.surfaceHigh, RoundedCornerShape(tv.tvSize(8))),
                )
                Spacer(Modifier.width(tv.tvSize(14)))
                Column(verticalArrangement = Arrangement.spacedBy(tv.tvSize(7))) {
                    Box(
                        Modifier
                            .width(tv.tvSize(330))
                            .height(tv.tvSize(14))
                            .background(colors.surfaceHigh, RoundedCornerShape(tv.tvSize(4))),
                    )
                    Box(
                        Modifier
                            .width(tv.tvSize(210))
                            .height(tv.tvSize(10))
                            .background(colors.surface, RoundedCornerShape(tv.tvSize(4))),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchMessageState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = colors.dimText,
                modifier = Modifier.size(tv.tvSize(44)),
            )
            Spacer(Modifier.height(tv.tvSize(14)))
            Text(
                text = title,
                color = colors.text,
                fontSize = tv.tvFont(26),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(tv.tvSize(6)))
            Text(
                text = hint,
                color = colors.dimText,
                fontSize = tv.tvFont(17),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(tv.tvSize(16)))
                SettingActionButton(actionLabel, onAction)
            }
        }
    }
}

@Composable
private fun SearchDiscovery(
    history: List<String>,
    hotWords: List<String>,
    onPick: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(tv.tvSize(22)),
    ) {
        DiscoverPanel(
            title = TvText.searchHistoryLabel,
            actionLabel = if (history.isNotEmpty()) TvText.clear else null,
            onAction = onClearHistory,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            if (history.isEmpty()) {
                Text(
                    text = TvText.searchEmptyHistory,
                    color = colors.dimText,
                    fontSize = tv.tvFont(18),
                )
            } else {
                history.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(tv.tvSize(10)),
                    ) {
                        row.forEach { keyword ->
                            SearchDiscoveryButton(
                                label = keyword,
                                leading = TvText.searchHistoryLabel,
                                modifier = Modifier.weight(1f),
                                onClick = { onPick(keyword) },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        DiscoverPanel(
            title = TvText.hotSearch,
            actionLabel = TvText.searchDiscovery,
            onAction = null,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            hotWords.chunked(2).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tv.tvSize(10)),
                ) {
                    row.forEachIndexed { columnIndex, keyword ->
                        SearchDiscoveryButton(
                            label = keyword,
                            leading = "%02d".format(rowIndex * 2 + columnIndex + 1),
                            modifier = Modifier.weight(1f),
                            onClick = { onPick(keyword) },
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DiscoverPanel(
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val shape = RoundedCornerShape(tv.tvSize(18))
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = colors.text,
                fontSize = tv.tvFont(24),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            if (actionLabel != null) {
                if (onAction != null) {
                    SearchTextAction(actionLabel, onAction)
                } else {
                    Text(
                        text = actionLabel,
                        color = colors.dimText,
                        fontSize = tv.tvFont(15),
                    )
                }
            }
        }
        Spacer(Modifier.height(tv.tvSize(10)))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(colors.surface, shape)
                .border(1.dp, colors.line, shape)
                .padding(horizontal = tv.tvSize(18), vertical = tv.tvSize(16)),
            verticalArrangement = Arrangement.spacedBy(tv.tvSize(9)),
        ) {
            content()
            Spacer(Modifier.weight(1f))
            Text(
                text = TvText.searchLandingHint,
                color = colors.dimText,
                fontSize = tv.tvFont(14),
            )
        }
    }
}

@Composable
private fun SearchDiscoveryButton(
    label: String,
    leading: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(10))
    Row(
        modifier = modifier
            .height(tv.tvSize(52))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(if (focused) colors.surfaceHigh else colors.glass, shape)
            .border(if (focused) 2.dp else 0.dp, colors.focus, shape)
            .padding(horizontal = tv.tvSize(14)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = leading,
            color = if (focused) colors.selectGreen else colors.dimText,
            fontSize = tv.tvFont(13),
            maxLines = 1,
            modifier = Modifier.width(tv.tvSize(62)),
        )
        Text(
            text = label,
            color = if (focused) Color.White else colors.text,
            fontSize = tv.tvFont(18),
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private val SEARCH_KEYBOARD_ROWS = listOf(
    listOf('Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'),
    listOf('A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'),
    listOf('Z', 'X', 'C', 'V', 'B', 'N', 'M'),
)

@Composable
private fun SearchKeyboardPanel(
    firstKeyFocus: FocusRequester,
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val shape = RoundedCornerShape(tv.tvSize(18))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.line, shape)
            .padding(horizontal = tv.tvSize(18), vertical = tv.tvSize(12)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.88f).align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(tv.tvSize(7)),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = TvText.tvKeyboard,
                    color = colors.subtext,
                    fontSize = tv.tvFont(15),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "按返回键收起",
                    color = colors.dimText,
                    fontSize = tv.tvFont(13),
                )
            }
            SEARCH_KEYBOARD_ROWS.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tv.tvSize(8)),
                ) {
                    row.forEachIndexed { index, char ->
                        SearchKeyboardKey(
                            label = char.toString(),
                            modifier = Modifier.weight(1f),
                            focusRequester = if (rowIndex == 0 && index == 0) firstKeyFocus else null,
                            onClick = { onKey(char) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tv.tvSize(8)),
            ) {
                SearchKeyboardKey(
                    label = TvText.space,
                    modifier = Modifier.weight(1.3f),
                    onClick = { onKey(' ') },
                )
                SearchKeyboardKey(
                    label = TvText.backspace,
                    modifier = Modifier.weight(1f),
                    onClick = onBackspace,
                )
                SearchKeyboardKey(
                    label = TvText.clear,
                    modifier = Modifier.weight(1f),
                    onClick = onClear,
                )
                SearchKeyboardKey(
                    label = TvText.search,
                    modifier = Modifier.weight(1.3f),
                    onClick = onSubmit,
                )
            }
        }
    }
}

@Composable
private fun SearchKeyboardKey(
    label: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(8))
    Box(
        modifier = modifier
            .height(tv.tvSize(36))
            .let { base ->
                if (focusRequester != null) base.focusRequester(focusRequester) else base
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(if (focused) colors.selectGreen else colors.surfaceHigh, shape)
            .border(if (focused) 2.dp else 0.dp, colors.focus, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (focused) Color.White else colors.subtext,
            fontSize = tv.tvFont(16),
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Playlist tab: focusable queue on the left, selected track artwork on the right. */
@Composable
fun QueueScreen(vm: PlayerViewModel = viewModel()) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val list by vm.queueList.collectAsState()
    val curIndex by vm.queueIndex.collectAsState()
    val source by vm.queueSource.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    var previewIndex by remember(list) {
        mutableStateOf(
            when {
                list.isEmpty() -> -1
                curIndex in list.indices -> curIndex
                else -> 0
            },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tv.tvSize(68), vertical = tv.tvSize(24)),
        horizontalArrangement = Arrangement.spacedBy(tv.tvSize(48)),
    ) {
        Column(Modifier.weight(1.12f).fillMaxHeight()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tv.tvSize(18)),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = TvText.playlist,
                        color = colors.text,
                        fontSize = tv.tvFont(38),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(tv.tvSize(6)))
                    Text(
                        text = buildString {
                            append(list.size)
                            append(TvText.dot)
                            append(TvText.songs)
                            source?.takeIf { it.isNotBlank() }?.let {
                                append(TvText.dot)
                                append(TvText.queueSourcePrefix)
                                append(it)
                            }
                        },
                        color = colors.primary,
                        fontSize = tv.tvFont(17),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(tv.tvSize(4)))
                    Text(
                        text = TvText.queueExplain,
                        color = colors.dimText,
                        fontSize = tv.tvFont(16),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (list.isNotEmpty()) {
                    HomePill(TvText.clearList) { vm.clearQueue() }
                }
            }
            Spacer(Modifier.height(tv.tvSize(18)))
            if (list.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(colors.surface, RoundedCornerShape(tv.tvSize(16)))
                        .padding(tv.tvSize(28)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Column {
                        Text(TvText.noMusic, color = colors.text, fontSize = tv.tvFont(28))
                        Spacer(Modifier.height(tv.tvSize(8)))
                        Text(
                            text = TvText.emptyQueueHint,
                            color = colors.dimText,
                            fontSize = tv.tvFont(18),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(tv.tvSize(8)),
                ) {
                    itemsIndexed(list) { index, item ->
                        QueueTrackRow(
                            index = index,
                            item = item,
                            isCurrent = index == curIndex,
                            isSelected = index == previewIndex,
                            isPlaying = isPlaying,
                            onFocused = { previewIndex = index },
                            onClick = { vm.playQueueAt(index) },
                        )
                    }
                }
            }
        }

        QueuePosterPane(
            item = list.getOrNull(previewIndex),
            index = previewIndex,
            total = list.size,
            isCurrent = previewIndex >= 0 && previewIndex == curIndex,
            isPlaying = isPlaying,
            modifier = Modifier.weight(0.88f).fillMaxHeight(),
        )
    }
}

@Composable
private fun QueueTrackRow(
    index: Int,
    item: MusicItem,
    isCurrent: Boolean,
    isSelected: Boolean,
    isPlaying: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.tvSize(10))
    val background = when {
        focused -> colors.surfaceHigh
        isCurrent -> colors.primarySoft
        isSelected -> colors.surface
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable { onClick() }
            .background(background, shape)
            .border(
                width = when {
                    focused -> 3.dp
                    isSelected -> 1.dp
                    else -> 0.dp
                },
                color = if (focused) colors.focus else colors.border,
                shape = shape,
            )
            .padding(horizontal = tv.tvSize(14), vertical = tv.tvSize(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(tv.tvSize(42)),
            contentAlignment = Alignment.Center,
        ) {
            if (isCurrent && isPlaying) {
                PlayingBars(color = colors.selectGreen)
            } else {
                Text(
                    text = "%02d".format(index + 1),
                    color = if (isCurrent) colors.selectGreen else colors.dimText,
                    fontSize = tv.tvFont(17),
                )
            }
        }
        Spacer(Modifier.width(tv.tvSize(10)))
        HomeCover(url = item.img, size = tv.tvSize(46))
        Spacer(Modifier.width(tv.tvSize(12)))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = when {
                    focused -> Color.White
                    isCurrent -> colors.selectGreen
                    else -> colors.text
                },
                fontSize = tv.tvFont(20),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(tv.tvSize(3)))
            Text(
                text = item.singer.ifBlank { TvText.unknownSinger },
                color = colors.subtext,
                fontSize = tv.tvFont(16),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.interval.isNotBlank()) {
            Spacer(Modifier.width(tv.tvSize(10)))
            Text(
                text = item.interval,
                color = colors.dimText,
                fontSize = tv.tvFont(16),
            )
        }
    }
}

@Composable
private fun QueuePosterPane(
    item: MusicItem?,
    index: Int,
    total: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val shape = RoundedCornerShape(tv.tvSize(20))

    BoxWithConstraints(
        modifier = modifier
            .background(
                Brush.verticalGradient(listOf(colors.bgWarm, colors.bgDeep)),
                shape,
            )
            .border(1.dp, colors.border, shape)
            .padding(tv.tvSize(26)),
    ) {
        if (item == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("\u266A", color = colors.dimText, fontSize = tv.tvFont(64))
                Spacer(Modifier.height(tv.tvSize(18)))
                Text(
                    text = TvText.noMusic,
                    color = colors.text,
                    fontSize = tv.tvFont(30),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(tv.tvSize(10)))
                Text(
                    text = TvText.emptyQueueHint,
                    color = colors.dimText,
                    fontSize = tv.tvFont(17),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            val compact = maxHeight < tv.tvSize(460)
            val posterSize = minOf(
                maxWidth * 0.88f,
                maxHeight * if (compact) 0.54f else 0.50f,
            )
            val stateLabel = when {
                isCurrent && isPlaying -> TvText.nowPlaying
                isCurrent -> TvText.paused
                else -> TvText.selected
            }
            val stateColor = when {
                isCurrent && isPlaying -> colors.selectGreen
                isCurrent -> colors.warn
                else -> colors.primary
            }

            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stateLabel,
                        color = stateColor,
                        fontSize = tv.tvFont(16),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${index + 1}${TvText.dot}$total",
                        color = colors.dimText,
                        fontSize = tv.tvFont(16),
                    )
                }
                Spacer(Modifier.height(tv.tvSize(14)))
                QueuePosterCover(url = item.img, size = posterSize)
                Spacer(Modifier.height(tv.tvSize(18)))
                Text(
                    text = item.name,
                    color = colors.text,
                    fontSize = tv.tvFont(30),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(tv.tvSize(6)))
                Text(
                    text = item.singer.ifBlank { TvText.unknownSinger },
                    color = colors.subtext,
                    fontSize = tv.tvFont(20),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact) {
                    Spacer(Modifier.height(tv.tvSize(4)))
                    Text(
                        text = item.albumName.ifBlank { TvText.unknownAlbum },
                        color = colors.dimText,
                        fontSize = tv.tvFont(17),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(tv.tvSize(12)))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = MusicSources.displayName(item.source),
                            color = colors.primary,
                            fontSize = tv.tvFont(16),
                        )
                        Spacer(Modifier.weight(1f))
                        if (item.interval.isNotBlank()) {
                            Text(
                                text = item.interval,
                                color = colors.dimText,
                                fontSize = tv.tvFont(16),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (!compact) {
                    Text(
                        text = TvText.selectedSongHint,
                        color = colors.dimText,
                        fontSize = tv.tvFont(15),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun QueuePosterCover(url: String?, size: Dp) {
    val colors = LocalTvColors.current
    val tv = LocalTvMetrics.current
    val shape = RoundedCornerShape(tv.tvSize(14))
    Box(
        modifier = Modifier
            .size(size)
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("\u266A", color = colors.dimText, fontSize = tv.tvFont(54))
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
