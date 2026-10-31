package com.harmoniamusic.tv.data.queue

import com.harmoniamusic.tv.data.source.MusicItem
import kotlin.random.Random

/**
 * 播放队列（对应 RN 的 TEMP 播放列表 / core/list）。
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
        _list.clear()
        _list.addAll(items)
        index = if (_list.isEmpty()) -1 else startIndex.coerceIn(0, _list.lastIndex)
        if (mode == Mode.SHUFFLE) buildShuffle(fromIndex = index)
    }

    fun add(item: MusicItem) = _list.add(item)
    fun addAll(items: List<MusicItem>) = _list.addAll(items)

    fun removeAt(i: Int) {
        if (i !in _list.indices) return
        _list.removeAt(i)
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
