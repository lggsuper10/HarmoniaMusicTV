package com.harmoniamusic.tv.ui.shell

import com.harmoniamusic.tv.ui.strings.TvText

/**
 * 顶栏 6 个 Tab，id 与 RN 版 `createTVTabs` 保持一致（home/new/queue/mylist/search/settings）。
 */
enum class TvTab(val id: String, val label: String) {
    HOME("home", TvText.recommend),
    NEW("new", TvText.charts),
    QUEUE("queue", TvText.playlist),
    MYLIST("mylist", TvText.mySonglists),
    SEARCH("search", TvText.search),
    SETTINGS("settings", TvText.settings),
}
