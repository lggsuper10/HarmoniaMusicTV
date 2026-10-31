package com.harmoniamusic.tv.data.source

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** 本地歌曲的 source 标识，播放时据此走本地 URI 分支。 */
const val LOCAL_SOURCE = "local"

private val AUDIO_EXT = setOf(
    "mp3", "flac", "wav", "m4a", "aac", "ogg", "wma", "opus", "ape", "wv", "m4r", "mid",
)

/**
 * 递归扫描一个 SAF 树形目录（含子目录），返回其中所有音频文件对应的 [MusicItem]。
 * 在 IO 线程执行（DocumentFile 的 binder 调用不应阻塞主线程）。
 * [songmid] 直接存文件的 content URI 字符串，播放时原样交给 ExoPlayer。
 */
suspend fun scanLocalFolder(context: Context, treeUri: String): List<MusicItem> =
    withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return@withContext emptyList()
        val result = mutableListOf<MusicItem>()
        val stack = ArrayDeque<DocumentFile>().apply { addAll(root.listFiles()) }
        while (stack.isNotEmpty()) {
            val file = stack.removeLast()
            if (file.isDirectory) {
                file.listFiles().forEach { stack.addLast(it) }
            } else if (file.isFile) {
                val name = file.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in AUDIO_EXT) {
                    result.add(
                        MusicItem(
                            source = LOCAL_SOURCE,
                            songmid = file.uri.toString(),
                            name = name.substringBeforeLast('.', name),
                            singer = "本地音乐",
                            albumName = "",
                            interval = "",
                            img = null,
                            qualities = emptyList(),
                        ),
                    )
                }
            }
        }
        result
    }

/** 可读取的存储根目录：内部存储 + 外接 U 盘 / SD 卡（用于目录选择器顶层）。 */
fun storageRoots(): List<File> {
    val roots = mutableListOf<File>()
    runCatching { Environment.getExternalStorageDirectory() }
        .getOrNull()
        ?.takeIf { it.exists() && it.canRead() }
        ?.let { roots.add(it) }
    runCatching { File("/storage").listFiles() }.getOrNull()?.forEach { f ->
        val n = f.name
        if (n != "emulated" && n != "self" && !n.startsWith(".") && f.isDirectory && f.canRead()) {
            roots.add(f)
        }
    }
    return roots.distinctBy { it.absolutePath }
}

/** 列出目录下可进入的子目录（跳过隐藏目录与系统目录）。 */
fun listSubDirs(dir: File): List<File> =
    runCatching {
        dir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") && it.name != "LOST.DIR" }
            ?.sortedBy { it.name.lowercase() }
    }.getOrNull().orEmpty()

/** 统计目录下（递归）音频文件数量，用于目录选择器里给出预期。 */
fun countAudioFiles(dir: File, limit: Int = 20000): Int {
    var n = 0
    val stack = ArrayDeque<File>().apply { add(dir) }
    while (stack.isNotEmpty() && n < limit) {
        val d = stack.removeLast()
        val children = runCatching { d.listFiles() }.getOrNull() ?: continue
        for (f in children) {
            if (f.isDirectory) stack.addLast(f)
            else if (f.isFile && f.name.substringAfterLast('.', "").lowercase() in AUDIO_EXT) n++
        }
    }
    return n
}

/**
 * 递归扫描用户选中的某个目录（含子目录），只扫描该目录范围。
 * [songmid] 存 file:// URI，播放时直接交给 ExoPlayer。
 */
suspend fun scanLocalDir(context: Context, dir: File): List<MusicItem> = withContext(Dispatchers.IO) {
    val result = mutableListOf<MusicItem>()
    val folderCovers = HashMap<String, String?>()
    val stack = ArrayDeque<File>().apply { add(dir) }
    while (stack.isNotEmpty() && result.size < 20000) {
        val d = stack.removeLast()
        val children = runCatching { d.listFiles() }.getOrNull() ?: continue
        val folderCover = folderCovers.getOrPut(d.absolutePath) { findFolderCover(d) }
        for (f in children) {
            val name = f.name
            if (name.startsWith(".")) continue
            if (f.isDirectory) {
                stack.addLast(f)
            } else if (f.isFile && name.substringAfterLast('.', "").lowercase() in AUDIO_EXT) {
                val meta = readLocalMeta(context, f, folderCover)
                result.add(
                    MusicItem(
                        source = LOCAL_SOURCE,
                        songmid = Uri.fromFile(f).toString(),
                        name = meta.title ?: name.substringBeforeLast('.', name),
                        singer = meta.artist ?: "本地音乐",
                        albumName = meta.album.orEmpty(),
                        interval = durationLabel(meta.durationMs),
                        img = meta.cover,
                        qualities = emptyList(),
                    ),
                )
            }
        }
    }
    result.sortedBy { it.name }
}

private val COVER_NAMES = setOf(
    "cover", "folder", "front", "album", "albumart", "artwork", "poster", "封面", "专辑封面",
)
private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "bmp")

/** 同目录下的封面图（cover.jpg / folder.jpg 等），没有则返回 null。 */
private fun findFolderCover(dir: File): String? = runCatching {
    dir.listFiles()?.firstOrNull { f ->
        f.isFile &&
            f.name.substringAfterLast('.', "").lowercase() in IMAGE_EXT &&
            f.name.substringBeforeLast('.', "").lowercase() in COVER_NAMES
    }?.let { Uri.fromFile(it).toString() }
}.getOrNull()

/** 本地音频元数据（标签内嵌封面优先，其次同目录封面图）。 */
private data class LocalMeta(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val cover: String? = null,
)

private val metaCache = ConcurrentHashMap<String, LocalMeta>()

private fun readLocalMeta(context: Context, file: File, folderCover: String?): LocalMeta {
    val key = file.absolutePath + "|" + file.length() + "|" + file.lastModified()
    metaCache[key]?.let { return it }
    val retriever = MediaMetadataRetriever()
    val meta = try {
        retriever.setDataSource(file.absolutePath)
        LocalMeta(
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()?.takeIf { it.isNotEmpty() },
            artist = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))
                ?.trim()?.takeIf { it.isNotEmpty() },
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.trim()?.takeIf { it.isNotEmpty() },
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull(),
            cover = retriever.embeddedPicture
                ?.let { saveEmbeddedCover(context, file, it) }
                ?: folderCover,
        )
    } catch (t: Throwable) {
        LocalMeta(cover = folderCover)
    } finally {
        runCatching { retriever.release() }
    }
    metaCache[key] = meta
    return meta
}

/** 把标签内嵌封面写到缓存目录，返回可直接交给 Coil/ExoPlayer 的 file:// URI。 */
private fun saveEmbeddedCover(context: Context, file: File, bytes: ByteArray): String? = runCatching {
    val dir = File(context.filesDir, "media_cache/covers").apply { mkdirs() }
    val key = (file.absolutePath + "|" + file.length() + "|" + file.lastModified())
        .hashCode().toString(16)
    val out = File(dir, "embedded_$key.img")
    if (!out.exists() || out.length() != bytes.size.toLong()) out.writeBytes(bytes)
    Uri.fromFile(out).toString()
}.getOrNull()

private fun durationLabel(ms: Long?): String =
    if (ms == null || ms <= 0L) "" else "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
