package com.harmoniamusic.tv.data.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 下载更新 APK 到缓存目录，返回文件。 */
object UpdateDownloader {
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "update").apply { mkdirs() }
            val safe = info.version.replace(Regex("[^0-9a-zA-Z.-]"), "_")
            val file = File(dir, "zl-music-tv-$safe-${info.asset.abi}.apk")
            val conn = (URL(info.asset.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 45000
                connect()
            }
            val total = conn.contentLength.toLong()
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
            file
        }.getOrNull()
    }
}
