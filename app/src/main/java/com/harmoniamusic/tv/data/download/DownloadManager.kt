package com.harmoniamusic.tv.data.download

import android.content.Context
import android.os.Environment
import com.harmoniamusic.tv.data.source.MusicItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 下载管理器（对应 RN `core/download` 的简化版）。
 * 当前保存到应用专属 Music 目录；进度通过 StateFlow 暴露。
 * 注：RN 版支持 SAF 选目录，M4 后续可接 Storage Access Framework。
 */
data class DownloadTask(
    val item: MusicItem,
    val url: String,
    val progress: Int = 0,
    val state: String = "pending", // pending | downloading | done | error
)

class DownloadManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun start(item: MusicItem, url: String) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        dir.mkdirs()
        val ext = url.substringAfterLast('.', "mp3")
            .substringBefore('?')
            .takeIf { it.length in 2..4 } ?: "mp3"
        val safe = item.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val file = File(dir, "$safe.$ext")
        _tasks.value = _tasks.value + DownloadTask(item, url, 0, "downloading")
        scope.launch {
            val ok = runCatching { streamTo(url, file) { p -> updateProgress(item, p) } }.isSuccess
            _tasks.value = _tasks.value.map {
                if (it.item.songmid == item.songmid) it.copy(state = if (ok) "done" else "error") else it
            }
        }
    }

    private fun updateProgress(item: MusicItem, progress: Int) {
        _tasks.value = _tasks.value.map {
            if (it.item.songmid == item.songmid) it.copy(progress = progress) else it
        }
    }

    private fun streamTo(url: String, file: File, onProgress: (Int) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply { connect() }
        val total = conn.contentLength.takeIf { it > 0 } ?: -1
        conn.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(8192)
                var read: Int
                var done = 0L
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    done += read
                    if (total > 0) onProgress((done * 100 / total).toInt())
                }
            }
        }
        conn.disconnect()
    }
}
