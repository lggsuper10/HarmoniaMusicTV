# HarmoniaMusicTV · 项目说明（AI 上下文）

> 用途：让后续接手本项目的 AI 快速建立"项目在做什么 / 代码怎么跑 / 改哪容易踩坑"的认知。
> 包名 `com.harmoniamusic.tv`，Android TV / 电视盒子音乐播放器，**无后端**，直连各音源在线播放。
> 由 RN 版本迁移而来，代码注释常标注"对应 RN 的 xxx"，命名/交互对齐 Apple Music TV。

---

## 1. 技术栈与构建基线

- 语言：**Kotlin**，UI：**Jetpack Compose for TV**（`androidx.tv:tv-material:1.0.0` + material3）。
- 播放引擎：**Media3 ExoPlayer**（`androidx.media3:media3-exoplayer:1.4.1`），替代 RN 自研内核。
- 图片：**Coil** `AsyncImage`；导航：`navigation-compose`（实际只用单 Shell + 叠加层，未大量用导航栈）。
- 构建：`compileSdk 34` / `minSdk 21` / `targetSdk 34`，Kotlin 1.9.24，Compose 编译器 1.5.14。
- 入口：`MainActivity` → `TvApp`（装 SplashScreen + `TvTheme`）→ `TvShell`。

---

## 2. 架构分层（模块地图）

```
UI 层
  TvShell.kt          6 个顶部 Tab 共用一屏 + 全屏叠加层（播放页/下载面板）
  TvScreens.kt        【巨型文件 ~200KB】Home/Charts/MyList/Search/Player 各 Screen
  SettingsScreen.kt   设置页双栏分类：音源与取流 / 播放与下载 / 应用更新 / 关于
  TvTopTabs.kt        顶部导航 Tab
状态层（ViewModel）
  PlayerViewModel.kt  【核心状态机】播放/搜索/榜单/收藏/队列/歌词/海报 的全部 StateFlow
  DownloadViewModel.kt 下载状态
仓库 / 数据层
  MusicRepository.kt  UI 唯一数据入口：聚合搜索/取流/驱动播放
  MusicSources.kt     内置音源注册表（wy/tx/kw/kg/mg）
  data/source/*       各音源实现（WySource/TxSource/KwSource/KgSource/MgSource）
  data/source/userapi/* 用户 API（JS 脚本扩展音源/取流）
  data/store/*        本地持久化（收藏/最近/用户歌单/音源设置/用户API）
  data/queue/PlayQueue.kt        播放队列 + 播放模式（顺序/单曲/随机）
  data/local/*        本地媒体解析（封面/歌词内嵌提取、局域网导入）
  player/TvMediaPlayer.kt        ExoPlayer 封装，对外暴露 StateFlow
```

**数据流总览**：UI 调用 `PlayerViewModel` → 触发 `MusicRepository` → 调 `MusicSources`/`UserApiManager` 取数据或地址 → `TvMediaPlayer` 交给 ExoPlayer。
**海报来源**：`MusicItem.img`（来自搜索/榜单的目录源封面 URL）；**歌词来源**：`MusicSources.getLyric` 或本地文件内嵌；**音频流**：优先用户 API 取流，回退内置直连源。

---

## 3. 核心逻辑一：播放拉流（Playback / Streaming）

### 3.1 触发与状态机
- 用户点歌进入 `PlayerViewModel.play / playList / playShuffled`（或队列/下一首 `next/prev`）。
- 统一走 `launchPlay(item)`（`PlayerViewModel.kt:775`）：
  1. `repo.stop()` 先停掉旧歌，避免新歌取流失败旧歌继续播。
  2. 设置 `_current`、写入最近播放、持久化 `last_play`。
  3. 重置歌词状态为 `LOADING`，**`_openPlayerSignal += 1`** → `TvShell` 监听该信号自动打开播放页（**即使取流失败也有可见反馈**，防止"点了没反应"）。
  4. 本地源（`LOCAL_SOURCE`）直接 `player.play(item.songmid)`；网络源走 `repo.play(item)`。
  5. 并发 `loadLyricAsync`（歌词）与 `resolveLocalArtworkAsync`（本地封面回填）。

### 3.2 取流优先级（`MusicRepository.play`，`MusicRepository.kt:42`）
```
play(item, quality="128k"):
  1) UserApiManager.resolveMusicUrl(item, quality)
     - 先遍历设置中选中的用户源，再遍历其他已启用且支持 item.source 的用户脚本
     - 禁用、加载失败、不支持该目录源的脚本跳过
     - 只有返回非空 http/https 地址才算命中；首个合法结果立即返回
     - 全部未命中 → null
  2) 用户源未命中时，回退到“同一目录源”的内置直连：
     MusicSources.builtin[item.source].getMusicUrl(item.songmid, quality)
     - 不做跨内置源回退（目录源是 wy 就只尝试内置 wy，不会改试 mg/tx）
  3) 用户源与同目录内置源都未返回合法地址 → 抛 IllegalArgumentException("取流失败…")
  → currentCoroutineContext().ensureActive()  // 丢弃已被新点歌取消的旧任务
  → player.play(stream.url)  // 合法地址交给 ExoPlayer
```
- **“选中用户源”只决定优先级，不是唯一源**：它失败后仍会尝试其他已启用且声明支持该目录源的用户脚本，最后才回退内置同源。
- **回退只发生在“没有拿到合法 URL”阶段**：若用户源已返回 URL，后续 ExoPlayer 播放/拉流才报 403、地址过期等错误，当前不会再次切换内置源。
- 命中来源记录在 `_playbackStreamSource`，播放页右上角只显示具体实际来源：用户脚本显示 `providerName`；内置源显示 `${MusicSources.displayName(item.source)}直连`（如 `网易云直连`）。不要显示目录源名、笼统的“内置直连”，也不要用“正在取流/取流失败”替换来源标签。
- 播放页下方状态行独立显示 `正在播放 / 准备中… / 无法播放：版权受限或取流失败`；来源名与播放状态不能混用。
- 用户源解析失败（抛错或未返回合法地址）和 ExoPlayer 异步 `Player.Listener.onPlayerError` 都会同步成 `_playbackError`。换歌时 `launchPlay` 会取消上一条取流任务并 `repo.stop()`，因此新歌失败后不会继续播放旧歌。

### 3.3 内置直连取流（以网易云 `WySource` 为例）
- `getMusicUrl(songmid, quality)`：quality 映射 `flac24bit→hires / flac→lossless / 320k→higher / 128k→standard`。
- 请求走 **eapi 优先**（`/eapi/batch`，匿名可取免费曲），**weapi 兜底**（`/weapi/song/enhance/player/url/v1`，经 `WyCrypto.weapi` 加密）。
- `parseUrl` **同时兼容两种响应结构**：weapi 的 `data` 为数组；eapi batch 的 `data` 为按 URL 聚合的对象再嵌一层数组。旧实现只认一种导致网易云永远取流失败，改这里要保留双结构兼容。
- 其他源（tx/kw/kg/mg）结构类似，各实现 `MusicSource` 接口。

### 3.4 用户 API 取流（JS 脚本扩展）
- `UserApiManager`（`userapi/UserApiManager.kt`）管理预设脚本（assets 打包 `grass`/`juhe`）+ 用户导入脚本（局域网/扫码导入，落盘 `UserApiStore`）。**全应用共享单例**，避免每个 ViewModel 重复起 WebView。
- `resolveMusicUrl(item, quality)`：构造 lx-music 风格 `musicInfo`（顶层字段 + 各平台子对象；mg 子键为 `mgd`），按"选中的优先取流源 → 其他启用的脚本"顺序遍历 **JS 引擎**（`WebViewJsEngine` 每个脚本各起一个 WebView），命中即返回 `StreamResolution(url, providerName)`。
- 导入内容若是 `http(s)` 链接会立即下载并校验真实脚本，失败必须把 HTTP 状态/网络原因返回设置页；GitHub raw 链接依次尝试直连与 `ghproxy.net` / `gh-proxy.com` / `ghfast.top` 镜像。
- 导入显示名称优先级：用户填写 → 脚本 `@name` / `SCRIPT_NAME` → URL 文件名或仓库目录 → 首行注释 → `导入 N`。`UserApiStore` 以 `{script,name}` 对象持久化并兼容旧字符串数组；导入项支持重命名，不要退回只存脚本文本。
- **关键认知**：用户 API **只负责音频取流**；海报/歌词仍来自目录源（见第 5 节设置页说明文案）。

### 3.5 ExoPlayer 封装（`TvMediaPlayer.kt`）
- 暴露 `isPlaying / positionMs / durationMs / error / ended`（均为 `StateFlow`，500ms 轮询同步进度）。
- `play(url)`：`setMediaItem(fromUri(url))` → `prepare()` → `playWhenReady=true`。
- `stop()` 必须清空 `currentUrl` 与 mediaItems（曲终自动连播靠 `ended` 自增信号 → `onTrackEnded()` → `next(auto=true)`，受播放模式约束，单曲循环回当前曲）。

---

## 4. 核心逻辑二：歌词（Lyrics）

- 加载入口 `loadLyricAsync(item)`（`PlayerViewModel.kt:813`）：
  - 本地源 → `LocalMediaResolver.resolveLyric(item)`（从音频文件内嵌/同目录提取）。
  - 网络源 → `repo.getLyric(source, songmid)` → `MusicSources.getLyric` → 内置源 `getLyric`（如 `WySource.getLyric` 请求 `/weapi/song/lyric`，并用 `mergeLyric` 按时间戳把翻译行 `tlyric` 拼到原歌词 `lrc` 行后）。
  - 结果写入 `_lyric`（原始 LRC 字符串）、`_lyricState`（`LyricLoadState.IDLE/LOADING/FOUND/EMPTY`）。
    `launchPlay` 时先置 `LOADING`；拿到文本后按是否为空置 `FOUND`/`EMPTY`。
- 渲染 `LyricsView`（`TvScreens.kt:1344`）：
  - `parseLrc(raw)`（`:1440`）用正则 `\[(\d+):(\d+(?:\.\d+)?)\]` 解析为 `(时间ms, 文本)` 列表，**一行多时间戳会展开多行**；若无时间戳（部分源返回纯文本）则按行铺开、每行间隔 4s 仍可读，**不显示"暂无歌词"**。
  - 当前行 `currentIndex = lines.indexOfLast { it.first <= positionMs }`，随播放进度 `animateScrollToItem` 自动滚动；`BoxWithConstraints` 的动态上下 padding 让当前行保持居中。
  - 当前行绿色加粗并带左侧强调条；相邻行按距离降低透明度，顶部/底部加渐隐遮罩，避免歌词贴边生硬截断。
- 注意：歌词解析与播放进度完全解耦，靠 `positionMs` 驱动，不依赖时钟。

---

## 5. 核心逻辑三：海报 / 封面（Poster / Album Art）

- **封面数据来源** = `MusicItem.img`（由各音源 `parseItem` 填，如 `WySource` 取 `al.picUrl`）。本地曲目的封面不来自 URL，而是 `resolveLocalArtworkAsync` 用 `LocalMediaResolver.resolveCover` 从文件元数据/同目录提取，回填 `item.img` 并同步更新 最近/收藏/用户歌单 三处 store（`PlayerViewModel.kt:830`）。本地文件夹封面：优先取第一首有 `img` 的歌，否则 `resolveFolderCover` 做一次受控在线匹配。
- **渲染组件**（均用 Coil `AsyncImage`）：
  - `PlayerArtwork`（`TvScreens.kt:402`，播放页主图）：**双层**——底层同图 `Crop` + 黑色竖向渐变（仅作氛围，alpha 0.64）；上层同图 `Fit`（完整显示，避免人物脸/海报文字被裁切）。无图时渐变 + `MusicNote` 占位。
  - 沉浸式背景（`PlayerScreen`，`:832`）：封面 `Crop` alpha 0.22 + 黑色竖向渐变（0.42→0.66→bgDeep 0.9）——即 Apple Music TV 风格的**专辑图虚化动态背景**（注：当前是"封面图 + 暗化遮罩"，并非提取主色填充）。
  - `HomeCover`（`:363`）：列表/卡片方形 `Crop`，无图给渐变 + 音符占位。
  - 推荐歌单卡片封面取 `PlaylistInfo.cover`。
- **海报与歌词、音频流三者来源独立**：设置页文案明确"歌曲信息、海报、歌词来自上方目录源；音频流优先来自下方用户 API"。改动任一类数据时要避免误以为同源。
- **播放页信息层级**：右上角只长期显示实际音源徽标（`音源 · 星海音乐源 / 网易云直连`）；准备中、已暂停、失败放在下方状态位，错误时提供“重试”。来源标签不能随播放状态变化。
- **播放页控制栏**：主传输键（上一首/播放暂停/下一首）居中；左侧为模式、喜欢、加入歌单、下载等图标化次要操作；右侧为返回。播放中 6 秒无按键自动收起控制栏，任意按键唤出；暂停/报错时保持可见。
- **播放页焦点表达**：未聚焦按钮统一深色底与弱描边；只有当前焦点按钮使用 `selectGreen` 实心、白色 3–4dp 外圈、1.08 倍缩放，并在图标下显示操作名称。主播放键未聚焦时只保留绿色细描边和较大尺寸，禁止常驻绿色实心，否则无法判断遥控器实际焦点。

---

## 6. TV 专属坑（改 UI/焦点前必读）

- **叠加层焦点**：`TvShell` 中播放页/下载面板显示时（`overlayVisible`）会 **unmount 底层 Tab**（`if (!overlayVisible) ...` 包裹）。否则方向键焦点会跑到被遮挡的不可见元素，表现为"焦点消失 / 按 OK 没反应"。任何新增全屏叠加层都要遵循此模式。
- **返回键分级**（`TvShell` `BackHandler`）：播放页→下载面板→关闭歌单（`playlistTitle` 非空）→焦点回顶栏（不切页）→再按才弹退出确认。误触直接退出是被刻意避免的。
- **进入播放页两次 `withFrameNanos{}` 延迟再 `requestFocus`**：让组合树先布局再抢焦点（TV 常见时序坑）。
- **SplashScreen**：`MainActivity` 用 `installSplashScreen` + `setKeepOnScreenCondition { !ready }`，首帧就绪前不消失，避免深色空白屏。
- **搜索/榜单竞态**：`searchTv`/`loadBoard` 用 `seq`/`token` 序号守卫，丢弃过期慢响应，避免旧请求覆盖新结果。

---

## 7. 音源体系速查

- 内置直连（`MusicSources.builtin`）：`wy`=网易云、`tx`=QQ音乐、`kw`=酷我、`kg`=酷狗、`mg`=咪咕；`local`=本地。
- 每个源实现 `MusicSource`：`search / getMusicUrl / getBoards / getBoard / getLyric / getRecommendPlaylists / ping`。
- 用户 API（`userapi/`）：`UserApiManager` 总管；`JsUserApi` 包一个 JS 引擎、`WebViewJsEngine` 提供宿主（`LxHost`）；`PresetUserApis` 内置 `grass`/`juhe`/`xinghai`（星海音乐源，asset `script/tv-default-xinghai.js`）；`ScriptTypes` 定义脚本上报结构。
- 音质档位：`flac24bit / flac / 320k / 128k`（默认播放 `128k`）。

---

## 8. 状态与持久化

- 全局状态集中在 `PlayerViewModel`（大量 `StateFlow`）：`current / lyric / lyricState / isPlaying / positionMs / durationMs / playbackError / playbackStreamSource / searchResults / tvSearchResults / boards / boardList / favorites / recent / userPlaylists / localFolders / queueList / playMode` 等。
- 持久化 store（`data/store/`）：`FavoritesStore` / `RecentStore` / `UserPlaylistStore` / `SourceStore`（音源/聚合选择）/ `UserApiStore`（导入脚本 + 自定义名称）/ `UserApiPrefs`（启用/选中）/ `LocalFoldersStore`。
- `last_play` 存 SharedPreferences，`TvShell` 启动时 `_current` 恢复为"最后一次播放的歌"（仅展示，不自动续播）。

---

## 9. 约定与命名

- 注释用**中文 KDoc**，常写"对应 RN 的 xxx"。
- 大文件优先原地改：`TvScreens.kt`（~200KB）几乎包含所有 Screen，改 UI 先在此文件定位对应 Composable；`PlayerViewModel.kt` 是状态中枢。
- 资源字符串集中在 `ui/strings/TvText.kt`（避免硬编码文案扩散）。
- 颜色/尺寸走 `ui/theme/` 的 `LocalTvColors` / `LocalTvMetrics`（`tvSize`/`tvFont` 做大屏自适应），不要写死 dp。
- 设置页遵循 TV 双栏：左侧分类导航、右侧当前分类卡片；选中态不要用绿色实底，只有当前焦点控件使用 `selectGreen` 实心 + 白色外圈。

---

## 10. 已知调试线索（改动前先确认）

- 仓库根目录大量 `*.png`、`anr_latest.txt`、`*_logcat.txt`、`ui_dump.xml`、`*_screen.png`、`chart_*.png`、`focus*.png`：说明近期在排查 **焦点丢失 / ANR / UI 层级 / 榜单布局 / 启动页** 等问题。动 UI 或焦点逻辑前，先确认是否与这些排查相关，避免回退已修的坑。
- 网易云取流曾因 `parseUrl` 不兼容 eapi batch 结构而"永远失败"，已修（见 3.3），不要再简化该解析。

---

## 11. 给后续 AI 的快速上手清单

1. 想加/改某音源：从 `data/source/<xx>/XxSource.kt` 实现 `MusicSource`，并在 `MusicSources.builtin` 注册。
2. 想加用户可扩展音源：写 JS 脚本，经"设置→用户API"导入；无需改 Kotlin（除非要新宿主能力）。
3. 改播放行为：先看 `PlayerViewModel.launchPlay` / `MusicRepository.play` / `TvMediaPlayer`。
4. 改歌词：解析在 `parseLrc`，加载在 `loadLyricAsync`，渲染在 `LyricsView`。
5. 改海报/背景：组件在 `PlayerArtwork` / `HomeCover` / `PlayerScreen` 沉浸式背景；数据在 `MusicItem.img` 与本地 `resolveCover`。
6. 改 TV 交互/焦点/返回：集中在 `TvShell.kt`，务必保持叠加层 unmount 与焦点时序约定。
