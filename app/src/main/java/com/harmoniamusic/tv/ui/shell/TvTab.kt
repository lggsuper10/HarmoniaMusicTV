package com.harmoniamusic.tv.ui.shell

import com.harmoniamusic.tv.ui.strings.TvText

/**
 * 顶栏 6 个 Tab，id 与 RN 版 `createTVTabs` 保持一致（home/mylist/new/queue/search/settings）。
 * 顺序即显示顺序：首页 → 我的音乐 → 排行榜 → 播放队列 → 搜索 → 设置。
 */
enum class TvTab(val id: String, val label: String) {
    HOME("home", TvText.home),
    MYLIST("mylist", TvText.myMusic),
    NEW("new", TvText.charts),
    SEARCH("search", TvText.search),
    SETTINGS("settings", TvText.settings),
}
