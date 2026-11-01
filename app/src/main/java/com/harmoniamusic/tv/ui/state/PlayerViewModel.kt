package com.harmoniamusic.tv.ui.state

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harmoniamusic.tv.data.local.LocalMediaResolver
import com.harmoniamusic.tv.data.queue.PlayQueue
import com.harmoniamusic.tv.data.repository.MusicRepository
import com.harmoniamusic.tv.data.source.userapi.UserApiManager
import com.harmoniamusic.tv.data.source.BoardInfo
import com.harmoniamusic.tv.data.source.MusicItem
import com.harmoniamusic.tv.data.source.PlaylistInfo
import com.harmoniamusic.tv.data.source.SearchResult
import com.harmoniamusic.tv.data.store.FavoritesStore
import com.harmoniamusic.tv.data.store.RecentStore
import com.harmoniamusic.tv.data.store.SourceStore
import com.harmoniamusic.tv.data.store.UserPlaylist
import com.harmoniamusic.tv.data.store.UserPlaylistStore
import com.harmoniamusic.tv.data.source.MusicSources
import com.harmoniamusic.tv.player.TvMediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.harmoniamusic.tv.data.store.LocalFoldersStore
import com.harmoniamusic.tv.data.source.LOCAL_SOURCE
import com.harmoniamusic.tv.data.source.scanLocalDir
import java.io.File
import org.json.JSONObject
import kotlin.random.Random

enum class LyricLoadState { IDLE, LOADING, FOUND, EMPTY }

/**
 * 播放与搜索的 UI 状态持有者（对应 RN 的全局播放内核 + store）。
 * 拥有 TvMediaPlayer、PlayQueue 与 MusicRepository，是首页/播放页/队列页共用状态源。
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val player = TvMediaPlayer(app)
    private val userApi = UserApiManager.shared(app)
    private val repo = MusicRepository(player, userApi)

    private val queue = PlayQueue()
    private val mediaResolver = LocalMediaResolver(app)
    private val favoritesStore = FavoritesStore(getApplication())
    private val recentStore = RecentStore(getApplication())
    private val userPlaylistStore = UserPlaylistStore(getApplication())
    private val localFoldersStore = LocalFoldersStore(getApplication())
    private val sourceStore = SourceStore(getApplication())

    /** 播放错误（取流失败 / 版权受限 / 解码失败）；null 表示正常。 */
    private val _playbackError = MutableStateFlow(false)
    val playbackError = _playbackError.asStateFlow()

    /** 实际命中并交给播放器的流源，便于区分设置项与失败后的回退。 */
    private val _playbackStreamSource = MutableStateFlow<String?>(null)
    val playbackStreamSource = _playbackStreamSource.asStateFlow()

    private var playJob: Job? = null

    /**
     * “有曲目被点播”的信号（自增计数）；Shell 监听它来把播放页（PlayerScreen）打开，
     * 保证即使取流失败，点歌也有可见反馈，而不是“没反应”。
     */
    private val _openPlayerSignal = MutableStateFlow(0)
    val openPlayerSignal = _openPlayerSignal.asStateFlow()

    /** 首页「播放」按钮：直接把播放页叠加到最上层。 */
    fun openPlayer() {
        _openPlayerSignal.value += 1
    }

    init {
        // ExoPlayer 异步出错（URL 失效/解码失败）时同步到 _playbackError
        viewModelScope.launch {
            player.error.collect { _playbackError.value = it != null }
        }
        // 曲终自动连播（跳过初始发射的 0）
        viewModelScope.launch {
            player.ended.drop(1).collect { onTrackEnded() }
        }
    }

    private val _activeSource = MutableStateFlow(sourceStore.getActive())
    val activeSource = _activeSource.asStateFlow()

    /** 切换目录音源；aggregate 表示搜索聚合全部内置源。 */
    fun setActiveSource(id: String) {
        sourceStore.setActive(id)
        _activeSource.value = id

        val nextSearchSource = searchSourceFor(id)
        if (_tvSearchSource.value != nextSearchSource) {
            _tvSearchSource.value = nextSearchSource
            if (tvSearchKeyword.isNotBlank()) searchTv(tvSearchKeyword, 1)
        }

        val nextChartSource = chartSourceFor(id)
        if (_chartSourceId.value != nextChartSource) {
            _chartSourceId.value = nextChartSource
            boardCache.clear()
            boardLoadToken++
            _boards.value = emptyList()
            _selectedBangid.value = null
            _boardList.value = emptyList()
            _boardError.value = null
            loadBoards(force = true)
        }
    }

    /** 当前选中源；聚合时返回 null，供搜索/榜单按源过滤。 */
    private fun activeSourceId(): String? =
        _activeSource.value.takeIf { it != SourceStore.AGGREGATE && it.isNotBlank() }

    private fun searchSourceFor(sourceId: String): String =
        sourceId.takeIf { MusicSources.builtin.containsKey(it) } ?: SOURCE_ALL

    private fun chartSourceFor(sourceId: String): String =
        sourceId.takeIf { MusicSources.builtin.containsKey(it) }
            ?: MusicSources.builtin.keys.firstOrNull()
            ?: "wy"

    private val _sourceTests =
        MutableStateFlow<List<MusicSources.SourceTestResult>>(emptyList())
    val sourceTests = _sourceTests.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting = _isTesting.asStateFlow()

    /** 触发对所有音源的连通性测试：内置直连源 + 用户 API（grass/juhe/导入脚本）。 */
    fun testSources() {
        if (_isTesting.value) return
        _isTesting.value = true
        viewModelScope.launch {
            runCatching { userApi.init() } // 确保用户 API 已加载
            val builtinResults = MusicSources.testAll()
            val apiResults = userApi.testAll().map { (id, ok) ->
                MusicSources.SourceTestResult(
                    id = id,
                    ok = ok,
                    ms = 0,
                    detail = if (ok) {
                        "支持源: ${userApi.getApiSources()[id]?.joinToString("/") ?: ""}"
                    } else "未初始化",
                )
            }
            _sourceTests.value = builtinResults + apiResults
            _isTesting.value = false
        }
    }

    /** 导入新用户 API 后运行时重新加载脚本（销毁旧引擎并按最新预设+导入重建）。 */
    fun reloadUserApi() {
        viewModelScope.launch(Dispatchers.IO) { runCatching { userApi.reload() } }
    }

    /** 等待 reload 完成的版本（设置页刷新列表用）。 */
    suspend fun reloadUserApiAndAwait() = runCatching { userApi.reload() }

    /** 用户 API 脚本清单（含支持源/启用/选中状态），供设置页管理。 */
    fun userApiList(): List<UserApiManager.ApiInfo> = userApi.listApis()

    /** 确保脚本已加载并返回最新清单（设置页进入时调用，否则导入项可能尚未加载、列表缺失）。 */
    suspend fun ensureUserApiLoaded(): List<UserApiManager.ApiInfo> {
        runCatching { userApi.init() }
        return userApiList()
    }

    fun setActiveUserApi(id: String) = userApi.setActive(id)
    fun setUserApiEnabled(id: String, on: Boolean) = userApi.setEnabled(id, on)
    fun removeImportedUserApi(idx: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            userApi.removeImported(idx)
            runCatching { userApi.reload() }
        }
    }

    val isPlaying = player.isPlaying
    val positionMs = player.positionMs
    val durationMs = player.durationMs

    private val _searchResults = MutableStateFlow<Map<String, SearchResult>>(emptyMap())
    val searchResults = _searchResults.asStateFlow()

    private val _current = MutableStateFlow<MusicItem?>(null)
    val current = _current.asStateFlow()

    init {
        // 恢复“最后一次播放的歌”，让推荐页首屏即显示（不自动续播，仅展示）
        _current.value = loadLastPlay()
    }

    private val _lyric = MutableStateFlow("")
    val lyric = _lyric.asStateFlow()

    private val _lyricState = MutableStateFlow(LyricLoadState.IDLE)
    val lyricState = _lyricState.asStateFlow()

    private var playToken = 0

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val chartPrefs = getApplication<Application>()
        .getSharedPreferences("tv_chart_state", Context.MODE_PRIVATE)

    private val _chartSourceId = MutableStateFlow(chartSourceFor(sourceStore.getActive()))
    val chartSourceId = _chartSourceId.asStateFlow()

    private val _boards = MutableStateFlow<List<BoardInfo>>(emptyList())
    val boards = _boards.asStateFlow()

    private val _boardsLoading = MutableStateFlow(false)
    val boardsLoading = _boardsLoading.asStateFlow()

    private val _boardsError = MutableStateFlow<String?>(null)
    val boardsError = _boardsError.asStateFlow()

    private val _selectedBangid = MutableStateFlow(
        chartPrefs.getString("selected_bangid", null),
    )
    val selectedBangid = _selectedBangid.asStateFlow()

    private val _boardList = MutableStateFlow<List<MusicItem>>(emptyList())
    val boardList = _boardList.asStateFlow()

    private val _boardLoading = MutableStateFlow(false)
    val boardLoading = _boardLoading.asStateFlow()

    private val _boardError = MutableStateFlow<String?>(null)
    val boardError = _boardError.asStateFlow()

    private val _boardCovers = MutableStateFlow<Map<String, String>>(emptyMap())
    val boardCovers = _boardCovers.asStateFlow()

    private val boardCache = mutableMapOf<String, List<MusicItem>>()
    private var boardsLoadToken = 0
    private var boardLoadToken = 0

    private val _favorites = MutableStateFlow<List<MusicItem>>(emptyList())
    val favorites = _favorites.asStateFlow()

    private val _recent = MutableStateFlow(recentStore.get())
    val recent = _recent.asStateFlow()

    private val _userPlaylists = MutableStateFlow(userPlaylistStore.get())
    val userPlaylists = _userPlaylists.asStateFlow()

    fun loadRecent() {
        _recent.value = recentStore.get()
    }

    fun removeRecent(item: MusicItem) {
        recentStore.remove(item)
        _recent.value = recentStore.get()
    }

    fun clearRecent() {
        recentStore.clear()
        _recent.value = emptyList()
    }

    private val _localFolders = MutableStateFlow<List<LocalFolder>>(emptyList())
    val localFolders = _localFolders.asStateFlow()

    private val _localSongs = MutableStateFlow<List<MusicItem>>(emptyList())
    val localSongs = _localSongs.asStateFlow()

    fun loadFavorites() {
        _favorites.value = favoritesStore.get()
    }

    fun loadUserPlaylists() {
        _userPlaylists.value = userPlaylistStore.get()
    }

    fun createUserPlaylist(name: String): UserPlaylist? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        val playlist = userPlaylistStore.create(trimmed)
        _userPlaylists.value = userPlaylistStore.get()
        return playlist
    }

    fun createUserPlaylistWithSong(name: String, item: MusicItem): UserPlaylist? {
        val playlist = createUserPlaylist(name) ?: return null
        userPlaylistStore.addSong(playlist.id, item)
        _userPlaylists.value = userPlaylistStore.get()
        return playlist
    }

    fun addToUserPlaylist(playlistId: String, item: MusicItem): Boolean {
        val added = userPlaylistStore.addSong(playlistId, item)
        _userPlaylists.value = userPlaylistStore.get()
        return added
    }

    fun renameUserPlaylist(playlistId: String, name: String) {
        userPlaylistStore.rename(playlistId, name)
        _userPlaylists.value = userPlaylistStore.get()
    }

    fun removeUserPlaylist(playlistId: String) {
        userPlaylistStore.delete(playlistId)
        _userPlaylists.value = userPlaylistStore.get()
    }

    fun removeSongFromUserPlaylist(playlistId: String, item: MusicItem) {
        userPlaylistStore.removeSong(playlistId, item)
        _userPlaylists.value = userPlaylistStore.get()
    }

    fun addFavorite(item: MusicItem) {
        favoritesStore.add(item)
        _favorites.value = favoritesStore.get()
    }

    fun removeFavorite(item: MusicItem) {
        favoritesStore.remove(item)
        _favorites.value = favoritesStore.get()
    }

    fun clearFavorites() {
        favoritesStore.clear()
        _favorites.value = emptyList()
    }

    /** 重新加载已添加的本地目录（绝对路径）及其中的歌曲，只扫描这些目录。 */
    fun loadLocal() {
        viewModelScope.launch(Dispatchers.IO) {
            val paths = localFoldersStore.get()
            val folders = paths.map { path ->
                val songs = runCatching { scanLocalDir(getApplication(), File(path)) }
                    .getOrDefault(emptyList())
                    .sortedBy { it.name }
                LocalFolder(
                    uri = path,
                    name = File(path).name.ifBlank { path },
                    songs = songs,
                    cover = songs.firstOrNull { !it.img.isNullOrBlank() }?.img,
                )
            }
            val songs = folders.flatMap { it.songs }
                .distinctBy { it.songmid }
                .sortedBy { it.name }
            _localFolders.value = folders
            _localSongs.value = songs

            // 扫描结果通常比历史记录/收藏更完整，顺便用新读到的内嵌封面回填旧卡片。
            recentStore.updateArtwork(songs)
            favoritesStore.updateArtwork(songs)
            userPlaylistStore.updateArtwork(songs)
            _recent.value = recentStore.get()
            _favorites.value = favoritesStore.get()
            _userPlaylists.value = userPlaylistStore.get()

            // 没有内嵌/同目录封面的文件夹，用第一首歌做一次受控的在线封面匹配。
            folders.filter { it.cover.isNullOrBlank() && it.songs.isNotEmpty() }.forEach { folder ->
                val cover = runCatching { mediaResolver.resolveFolderCover(folder.songs) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                if (cover == null) return@forEach
                _localFolders.value = _localFolders.value.map {
                    if (it.uri == folder.uri) it.copy(cover = cover) else it
                }
            }
        }
    }

    /** 添加一个本地目录（用户在应用内目录选择器中选中的绝对路径）。 */
    fun addLocalFolder(path: String) {
        localFoldersStore.add(path)
        loadLocal()
    }

    /** 移除一个本地目录并刷新。 */
    fun removeLocalFolder(path: String) {
        localFoldersStore.remove(path)
        loadLocal()
    }

    private val _queueList = MutableStateFlow<List<MusicItem>>(emptyList())
    val queueList = _queueList.asStateFlow()

    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex = _queueIndex.asStateFlow()

    /** 当前播放队列的来源集合名；队列本身仍是运行时状态，不落盘成歌单。 */
    private val _queueSource = MutableStateFlow<String?>(null)
    val queueSource = _queueSource.asStateFlow()

    fun search(keyword: String) {
        if (keyword.isBlank()) return
        _isLoading.value = true
        viewModelScope.launch {
            runCatching { repo.search(keyword, 1, 25, activeSourceId()) }
                .onSuccess { _searchResults.value = it }
            _isLoading.value = false
        }
    }

    // ---- TV 搜索页状态（对应 RN screens/TV/Search.tsx）----

    data class TvSearchPageInfo(
        val page: Int = 1,
        val maxPage: Int = 1,
        val total: Int = 0,
        val limit: Int = 30,
    )

    private val _tvSearchSource = MutableStateFlow(searchSourceFor(sourceStore.getActive()))
    val tvSearchSource = _tvSearchSource.asStateFlow()

    private val _tvSearchResults = MutableStateFlow<List<MusicItem>>(emptyList())
    val tvSearchResults = _tvSearchResults.asStateFlow()

    private val _tvSearchPage = MutableStateFlow(TvSearchPageInfo())
    val tvSearchPage = _tvSearchPage.asStateFlow()

    private val _tvSearchLoading = MutableStateFlow(false)
    val tvSearchLoading = _tvSearchLoading.asStateFlow()

    /** 当前搜索结果对应的关键词；切回搜索页时用于恢复输入框和结果标题。 */
    private val _tvSearchQuery = MutableStateFlow(String())
    val tvSearchQuery = _tvSearchQuery.asStateFlow()

    private val _tvSearchError = MutableStateFlow<String?>(null)
    val tvSearchError = _tvSearchError.asStateFlow()

    private val _tvSearchHistory = MutableStateFlow(loadTvSearchHistory())
    val tvSearchHistory = _tvSearchHistory.asStateFlow()

    private var tvSearchSeq = 0
    private var tvSearchKeyword = ""

    /** 切换搜索来源；有关键词时立即用新来源重搜第一页（对应 RN handleSourceChange）。 */
    fun setTvSearchSource(id: String) {
        if (_tvSearchSource.value == id) return
        _tvSearchSource.value = id
        if (tvSearchKeyword.isNotBlank()) searchTv(tvSearchKeyword, 1)
    }

    /**
     * TV 搜索页搜索；page > 1 为加载更多（追加结果）。
     * seq 序号守卫丢弃过期慢响应，避免旧请求晚到清空新结果（对应 RN searchSeqRef）。
     */
    fun searchTv(keyword: String, page: Int = 1) {
        val kw = keyword.trim()
        if (kw.isBlank()) return
        tvSearchKeyword = kw
        _tvSearchQuery.value = kw
        _tvSearchError.value = null
        if (page == 1) {
            _tvSearchResults.value = emptyList()
            _tvSearchPage.value = TvSearchPageInfo()
        }
        val seq = ++tvSearchSeq
        val source = _tvSearchSource.value
        _tvSearchLoading.value = true
        viewModelScope.launch {
            runCatching {
                MusicSources.searchAll(kw, page, 30, source.takeIf { it != SOURCE_ALL })
            }.onSuccess { map ->
                if (seq != tvSearchSeq) return@launch
                val merged = if (source == SOURCE_ALL) {
                    mergeAllResults(map)
                } else {
                    map[source] ?: SearchResult(source, emptyList(), 0, 1, 30)
                }
                if (page > 1) {
                    _tvSearchResults.value = _tvSearchResults.value + merged.list
                } else {
                    _tvSearchResults.value = merged.list
                    recordTvSearchHistory(kw)
                }
                _tvSearchPage.value = TvSearchPageInfo(
                    page = page,
                    maxPage = maxOf(merged.allPage, 1),
                    total = merged.total,
                    limit = merged.limit,
                )
                _tvSearchError.value = null
            }.onFailure { error ->
                if (seq != tvSearchSeq) return@onFailure
                _tvSearchError.value = error.message
                    ?.takeIf { it.isNotBlank() }
                    ?: "搜索失败，请稍后重试"
            }
            if (seq == tvSearchSeq) _tvSearchLoading.value = false
        }
    }

    /** 清空搜索结果与关键词（键盘"清空"在结果存在时调用）。 */
    fun clearTvSearch() {
        tvSearchSeq++
        tvSearchKeyword = ""
        _tvSearchQuery.value = String()
        _tvSearchError.value = null
        _tvSearchLoading.value = false
        _tvSearchResults.value = emptyList()
        _tvSearchPage.value = TvSearchPageInfo()
    }

    fun clearTvSearchHistory() {
        _tvSearchHistory.value = emptyList()
        prefs(HISTORY_PREFS).edit().remove(KEY_HISTORY).apply()
    }

    /** 聚合源结果合并：按歌名+歌手去重，total 求和、页数取最大（对应 RN setLists）。 */
    private fun mergeAllResults(map: Map<String, SearchResult>): SearchResult {
        val seen = HashSet<String>()
        val list = mutableListOf<MusicItem>()
        var total = 0
        var maxPage = 1
        var limit = 30
        map.values.forEach { r ->
            total += r.total
            if (r.allPage > maxPage) maxPage = r.allPage
            if (r.limit > 0) limit = r.limit
            r.list.forEach { item ->
                if (seen.add("${item.name}|${item.singer}")) list.add(item)
            }
        }
        return SearchResult(SOURCE_ALL, list, total, maxPage, limit)
    }

    private fun prefs(name: String) =
        getApplication<Application>().getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun loadTvSearchHistory(): List<String> =
        prefs(HISTORY_PREFS).getString(KEY_HISTORY, null)
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private fun recordTvSearchHistory(kw: String) {
        val next = (listOf(kw) + _tvSearchHistory.value.filter { it != kw }).take(8)
        _tvSearchHistory.value = next
        prefs(HISTORY_PREFS).edit().putString(KEY_HISTORY, next.joinToString("\n")).apply()
    }

    /**
     * 加载排行榜目录。排行榜使用页面自己的音源，不跟随全局播放音源静默变化。
     * force=false 时保留已经加载好的目录，避免每次回到 Tab 都重新请求。
     */
    fun loadBoards(force: Boolean = false) {
        if (!force && _boards.value.isNotEmpty()) return
        if (!force && _boardsLoading.value) return

        val sourceId = _chartSourceId.value
        val token = ++boardsLoadToken
        _boardsLoading.value = true
        _boardsError.value = null
        viewModelScope.launch {
            val result = runCatching { repo.getBoards(sourceId) }
            if (token != boardsLoadToken) return@launch
            _boardsLoading.value = false
            result.onSuccess { list ->
                _boards.value = list
                if (list.isEmpty()) {
                    _selectedBangid.value = null
                    _boardList.value = emptyList()
                    _boardLoading.value = false
                    _boardError.value = null
                    return@onSuccess
                }
                val target = _selectedBangid.value
                    ?.takeIf { saved -> list.any { it.bangid == saved } }
                    ?: list.first().bangid
                _selectedBangid.value = target
                chartPrefs.edit().putString("selected_bangid", target).apply()
                loadBoard(target)
            }.onFailure { error ->
                _boardsError.value = error.message ?: "加载排行榜失败"
            }
        }
    }

    /** 切换当前榜单：先清空/恢复缓存，再按请求序号防止旧响应覆盖新榜单。 */
    fun selectBoard(bangid: String, force: Boolean = false) {
        val changed = _selectedBangid.value != bangid
        _selectedBangid.value = bangid
        chartPrefs.edit().putString("selected_bangid", bangid).apply()
        if (!changed && !force) {
            if (_boardList.value.isEmpty() && !_boardLoading.value) loadBoard(bangid)
            return
        }
        loadBoard(bangid)
    }

    private val _recommendPlaylists = MutableStateFlow<List<PlaylistInfo>>(emptyList())
    val recommendPlaylists = _recommendPlaylists.asStateFlow()

    private val _playlistTitle = MutableStateFlow("")
    val playlistTitle = _playlistTitle.asStateFlow()

    private val _playlistCover = MutableStateFlow<String?>(null)
    val playlistCover = _playlistCover.asStateFlow()

    private val _playlistMeta = MutableStateFlow("")
    val playlistMeta = _playlistMeta.asStateFlow()

    private val _playlistSongs = MutableStateFlow<List<MusicItem>>(emptyList())
    val playlistSongs = _playlistSongs.asStateFlow()

    private val _playlistLoading = MutableStateFlow(false)
    val playlistLoading = _playlistLoading.asStateFlow()

    /** 首页首次进入时拉推荐歌单（只拉一次）。 */
    fun loadRecommendPlaylists() {
        if (_recommendPlaylists.value.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { repo.recommendPlaylists(12) }
                .onSuccess { _recommendPlaylists.value = it }
        }
    }

    /** 打开某个推荐歌单并载入曲目与详情信息。 */
    fun openPlaylist(playlist: PlaylistInfo) {
        _playlistTitle.value = playlist.name
        _playlistCover.value = playlist.cover
        _playlistMeta.value = buildString {
            append(MusicSources.displayName(playlist.source))
            append(" · 歌单")
            if (playlist.trackCount > 0) append(" · ${playlist.trackCount} 首")
            if (playlist.creator.isNotBlank()) append(" · ${playlist.creator}")
        }
        _playlistSongs.value = emptyList()
        _playlistLoading.value = true
        viewModelScope.launch {
            runCatching { repo.playlistSongs(playlist.source, playlist.id) }
                .onSuccess { _playlistSongs.value = it }
            _playlistLoading.value = false
        }
    }

    fun closePlaylist() {
        _playlistSongs.value = emptyList()
        _playlistTitle.value = ""
        _playlistCover.value = null
        _playlistMeta.value = ""
        _playlistLoading.value = false
    }

    fun loadBoard(bangid: String) {
        val sourceId = _chartSourceId.value
        val cacheKey = "$sourceId|$bangid"
        val cached = boardCache[cacheKey]
        val token = ++boardLoadToken
        _boardError.value = null

        if (cached != null) {
            _boardList.value = cached
            _boardLoading.value = false
        } else {
            _boardList.value = emptyList()
            _boardLoading.value = true
        }

        viewModelScope.launch {
            val result = runCatching { repo.getBoard(sourceId, bangid, 1, 200) }
            if (token != boardLoadToken) return@launch
            result.onSuccess { search ->
                val list = search.list
                boardCache[cacheKey] = list
                _boardList.value = list
                _boardLoading.value = false
                list.firstOrNull()?.img?.takeIf { it.isNotBlank() }?.let { cover ->
                    _boardCovers.value = _boardCovers.value + (cacheKey to cover)
                }
            }.onFailure { error ->
                _boardLoading.value = false
                _boardError.value = error.message ?: "加载榜单歌曲失败"
                if (cached == null) _boardList.value = emptyList()
            }
        }
    }

    /** 播放单首（队列仅含这一首）。 */
    fun play(item: MusicItem, sourceTitle: String? = null) {
        queue.setAll(listOf(item), 0)
        _queueSource.value = sourceTitle
        syncQueue()
        launchPlay(item)
    }

    /** 播放一个集合，从 startIndex 开始，并记录队列来源。 */
    fun playList(items: List<MusicItem>, startIndex: Int = 0, sourceTitle: String? = null) {
        if (items.isEmpty()) return
        queue.setAll(items, startIndex)
        _queueSource.value = sourceTitle
        syncQueue()
        queue.current?.let { launchPlay(it) }
    }

    /** 随机播放一个集合；队列仍保存集合全部歌曲，播放模式切到随机。 */
    fun playShuffled(items: List<MusicItem>, sourceTitle: String? = null) {
        if (items.isEmpty()) return
        _playMode.value = PlayQueue.Mode.SHUFFLE
        queue.mode = PlayQueue.Mode.SHUFFLE
        queue.setAll(items, Random.nextInt(items.size))
        _queueSource.value = sourceTitle
        syncQueue()
        queue.current?.let { launchPlay(it) }
    }

    /** 切到队列第 i 首播放。 */
    fun playQueueAt(i: Int) {
        if (i !in queue.list.indices) return
        queue.setAll(queue.list, i)
        syncQueue()
        queue.current?.let { launchPlay(it) }
    }

    fun removeFromQueue(i: Int) {
        queue.removeAt(i)
        if (queue.isEmpty) _queueSource.value = null
        syncQueue()
    }

    fun clearQueue() {
        queue.clear()
        _queueSource.value = null
        syncQueue()
    }

    /** 把歌曲追加到播放队列尾部，不打断当前正在播放的歌曲，也不改变当前项。 */
    fun addToQueue(items: List<MusicItem>, sourceTitle: String? = null) {
        if (items.isEmpty()) return
        val wasEmpty = queue.isEmpty
        queue.append(items)
        if (wasEmpty && sourceTitle != null) _queueSource.value = sourceTitle
        syncQueue()
    }

    fun next(auto: Boolean = false) {
        queue.next(auto)?.let { launchPlay(it) }
        syncQueue()
    }

    fun prev() {
        queue.prev()?.let { launchPlay(it) }
        syncQueue()
    }

    /** 曲终自动连播（受播放模式约束：单曲循环回到当前曲）。 */
    private fun onTrackEnded() = next(auto = true)

    // ---- 播放模式（顺序 / 单曲循环 / 随机）----
    private val _playMode = MutableStateFlow(PlayQueue.Mode.ORDER)
    val playMode = _playMode.asStateFlow()

    fun cyclePlayMode() {
        _playMode.value = when (_playMode.value) {
            PlayQueue.Mode.ORDER -> PlayQueue.Mode.SINGLE
            PlayQueue.Mode.SINGLE -> PlayQueue.Mode.SHUFFLE
            PlayQueue.Mode.SHUFFLE -> PlayQueue.Mode.ORDER
        }
        queue.mode = _playMode.value
    }

    // ---- 收藏 ----
    fun isFavorite(item: MusicItem): Boolean =
        _favorites.value.any { it.source == item.source && it.songmid == item.songmid }

    fun toggleFavorite(item: MusicItem) {
        if (isFavorite(item)) removeFavorite(item) else addFavorite(item)
    }

    private fun launchPlay(item: MusicItem) {
        val token = ++playToken
        playJob?.cancel()
        repo.stop()
        _current.value = item
        recentStore.add(item)
        _recent.value = recentStore.get()
        saveLastPlay(item)
        _lyric.value = ""
        _lyricState.value = LyricLoadState.LOADING
        _playbackError.value = false
        _playbackStreamSource.value = if (item.source == LOCAL_SOURCE) "本地文件" else null
        // 通知 Shell 打开播放页，保证点歌有可见反馈（无论是否真正播放成功）
        _openPlayerSignal.value += 1
        playJob = viewModelScope.launch {
            if (item.source == LOCAL_SOURCE) {
                runCatching { player.play(item.songmid) }
                    .onFailure {
                        _playbackError.value = true
                        Log.e("ZlPlay", "本地播放失败: ${item.name}", it)
                    }
            } else {
                runCatching { repo.play(item) }
                    .onSuccess { stream ->
                        if (token == playToken) _playbackStreamSource.value = stream.sourceLabel
                    }
                    .onFailure {
                        if (token == playToken) {
                            _playbackError.value = true
                            Log.e("ZlPlay", "播放失败: ${item.name} source=${item.source}", it)
                        }
                    }
            }
        }
        loadLyricAsync(item, token)
        if (item.source == LOCAL_SOURCE) resolveLocalArtworkAsync(item, token)
    }

    private fun loadLyricAsync(item: MusicItem, token: Int) {
        viewModelScope.launch {
            val text = if (item.source == LOCAL_SOURCE) {
                runCatching { mediaResolver.resolveLyric(item) }.getOrNull()
            } else {
                runCatching { repo.getLyric(item.source, item.songmid) }.getOrNull()
            }
            if (token != playToken) return@launch
            _lyric.value = text.orEmpty()
            _lyricState.value = if (text.isNullOrBlank()) {
                LyricLoadState.EMPTY
            } else {
                LyricLoadState.FOUND
            }
        }
    }

    private fun resolveLocalArtworkAsync(item: MusicItem, token: Int) {
        viewModelScope.launch {
            val cover = runCatching { mediaResolver.resolveCover(item) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: return@launch
            if (token != playToken) return@launch
            val updated = item.copy(img = cover)
            _current.value = updated
            queue.update(updated)
            syncQueue()
            recentStore.updateArtwork(updated)
            favoritesStore.updateArtwork(updated)
            userPlaylistStore.updateArtwork(listOf(updated))
            _recent.value = recentStore.get()
            _favorites.value = favoritesStore.get()
            _userPlaylists.value = userPlaylistStore.get()
            saveLastPlay(updated)
        }
    }

    private fun syncQueue() {
        _queueList.value = queue.list.toList()
        _queueIndex.value = queue.index
    }

    /** 播放/暂停切换：未在播放时，若播放器已载入音源则 resume，否则重新取流播放当前曲。 */
    fun togglePlay() {
        if (isPlaying.value) {
            pause()
        } else {
            val item = _current.value ?: return
            if (player.hasMedia() && !_playbackError.value) resume() else launchPlay(item)
        }
    }

    fun pause() = repo.pause()
    fun resume() = repo.resume()
    fun seekBy(deltaMs: Long) = repo.seekBy(deltaMs)

    override fun onCleared() = player.release()

    /** 持久化“最后一次播放的歌”，使推荐页首屏在下次打开时仍保留该曲。 */
    private fun saveLastPlay(item: MusicItem) {
        runCatching {
            val j = JSONObject().apply {
                put("source", item.source); put("songmid", item.songmid)
                put("name", item.name); put("singer", item.singer)
                put("albumName", item.albumName); put("interval", item.interval)
                put("img", item.img ?: "")
            }
            prefs(PLAY_STATE_PREFS).edit().putString(KEY_LAST_PLAY, j.toString()).apply()
        }
    }

    private fun loadLastPlay(): MusicItem? {
        val raw = prefs(PLAY_STATE_PREFS).getString(KEY_LAST_PLAY, null) ?: return null
        return runCatching {
            JSONObject(raw).let {
                MusicItem(
                    source = it.getString("source"),
                    songmid = it.getString("songmid"),
                    name = it.getString("name"),
                    singer = it.getString("singer"),
                    albumName = it.getString("albumName"),
                    interval = it.getString("interval"),
                    img = it.getString("img").ifEmpty { null },
                    qualities = emptyList(),
                )
            }
        }.getOrNull()
    }

    companion object {
        const val SOURCE_ALL = "all"
        private const val HISTORY_PREFS = "tv_search_history"
        private const val KEY_HISTORY = "history"
        private const val PLAY_STATE_PREFS = "play_state"
        private const val KEY_LAST_PLAY = "last_play"
    }
}

/** 已添加的本地目录（持久化 URI + 展示名）。 */
data class LocalFolder(
    val uri: String,
    val name: String,
    val songs: List<MusicItem> = emptyList(),
    val cover: String? = null,
)
