package com.harmoniamusic.tv.data.queue

import com.harmoniamusic.tv.data.source.MusicItem
import kotlin.random.Random

/**
 * 运行时播放队列：只保存本次播放顺序，不是持久化歌单。
 * 持有曲目列表与当前索引，支持设置/追加/移除/清空/上下首，以及播放模式（顺序/单曲循环/随机）。
 */
class PlayQueue {
    private val _list = mutableListOf<MusicItem>()
    val list: List<MusicItem> get() = _list
    var index: Int = -1
        private set

    val current: MusicItem? get() = if (index in _list.indices) _list[index] else null
    val isEmpty: Boolean get() = _list.isEmpty()

    enum class Mode { ORDER, SINGLE, SHUFFLE }

    var mode: Mode = Mode.ORDER
        set(value) {
            field = value
            if (value == Mode.SHUFFLE) buildShuffle(fromIndex = index)
        }

    private var shuffleOrder: List<Int> = emptyList()
    private var shufflePos: Int = 0

    private fun buildShuffle(fromIndex: Int) {
        if (_list.isEmpty()) {
            shuffleOrder = emptyList()
            shufflePos = 0
            return
        }
        shuffleOrder = _list.indices.shuffled(Random)
        shufflePos = shuffleOrder.indexOfFirst { it == fromIndex }.let { if (it < 0) 0 else it }
    }

    fun setAll(items: List<MusicItem>, startIndex: Int = 0) {
        val snapshot = items.toList()
        _list.clear()
        _list.addAll(snapshot)
        index = if (_list.isEmpty()) -1 else startIndex.coerceIn(0, _list.lastIndex)
        if (mode == Mode.SHUFFLE) buildShuffle(fromIndex = index)
    }

    fun add(item: MusicItem) = _list.add(item)
    fun addAll(items: List<MusicItem>) = _list.addAll(items)

    /**
     * 追加到队尾，保留当前曲目和播放进度。
     * 队列原本为空时，把第一首设为当前项，但不自动开始播放。
     */
    fun append(items: List<MusicItem>) {
        if (items.isEmpty()) return
        val snapshot = items.toList()
        val hadCurrent = index in _list.indices
        _list.addAll(snapshot)
        if (!hadCurrent) index = 0
        if (mode == Mode.SHUFFLE) buildShuffle(fromIndex = index)
    }

    /** 原地刷新某首歌的展示信息（例如异步补到封面），不影响播放顺序。 */
    fun update(item: MusicItem) {
        val position = _list.indexOfFirst {
            it.source == item.source && it.songmid == item.songmid
        }
        if (position >= 0) _list[position] = item
    }

    fun removeAt(i: Int) {
        if (i !in _list.indices) return
        _list.removeAt(i)
        if (i < index) index--
        if (index >= _list.size) index = _list.lastIndex
        if (_list.isEmpty()) index = -1
        if (mode == Mode.SHUFFLE) buildShuffle(fromIndex = index)
    }

    fun clear() {
        _list.clear()
        index = -1
        shuffleOrder = emptyList()
        shufflePos = 0
    }

    /** 下一首。auto=true 表示曲终自动连播（单曲循环时回到当前曲）。 */
    fun next(auto: Boolean = false): MusicItem? {
        if (_list.isEmpty()) return null
        if (auto && mode == Mode.SINGLE) return current
        index = advance(forward = true)
        return current
    }

    fun prev(): MusicItem? {
        if (_list.isEmpty()) return null
        index = advance(forward = false)
        return current
    }

    private fun advance(forward: Boolean): Int {
        if (_list.isEmpty()) return -1
        val size = _list.size
        return when (mode) {
            Mode.SHUFFLE -> {
                if (shuffleOrder.isEmpty()) buildShuffle(index)
                shufflePos = (shufflePos + if (forward) 1 else size - 1) % size
                shuffleOrder[shufflePos]
            }
            else -> {
                val cur = if (index < 0) 0 else index
                (cur + if (forward) 1 else size - 1) % size
            }
        }
    }
}
