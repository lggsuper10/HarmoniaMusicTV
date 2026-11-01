package com.harmoniamusic.tv.data.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.harmoniamusic.tv.data.source.LOCAL_SOURCE
import com.harmoniamusic.tv.data.source.MusicItem
import com.harmoniamusic.tv.data.source.MusicSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 本地歌曲的封面与歌词补全器。
 *
 * 封面：内嵌封面 -> 同目录封面图 -> 持久化缓存 -> 在线同曲匹配 -> UI 渐变占位。
 * 歌词：同名 .lrc -> 内嵌歌词 -> 持久化缓存 -> 在线同曲匹配 ->「暂无歌词」。
 *
 * 在线匹配必须同时满足歌名可信、以及歌手或时长校验，避免把 Live/翻唱/同名曲的
 * 封面和歌词套到本地歌曲上。网络请求只补媒体元数据，不缓存音频。
 */
class LocalMediaResolver(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cacheRoot = File(appContext.filesDir, "media_cache")
    private val coverDir = File(cacheRoot, "covers")
    private val lyricDir = File(cacheRoot, "lyrics")
    private val searchMutex = Mutex()
    private val memoryMatches = mutableMapOf<String, List<MusicItem>>()
    private val memoryNoMatches = mutableSetOf<String>()


    data class MatchLookup(
        val matches: List<MusicItem>,
        val networkReached: Boolean,
    )

    suspend fun resolveCover(item: MusicItem): String? = withContext(Dispatchers.IO) {
        if (item.source != LOCAL_SOURCE) return@withContext item.img
        val file = localFile(item) ?: return@withContext item.img?.takeIf { it.startsWith("http") }
        val key = cacheKey(file, item)
        val cached = File(coverDir, "$key.img").takeIf { it.isFile && it.length() > 0L }
        if (cached != null) return@withContext Uri.fromFile(cached).toString()

        // 扫描器已经把内嵌封面或同目录封面写进 img；先把这个可读文件持久化。
        item.img?.let { existing ->
            if (existing.startsWith("file://")) {
                val source = File(Uri.parse(existing).path.orEmpty())
                if (source.isFile) {
                    if (source.absolutePath.startsWith(coverDir.absolutePath)) {
                        return@withContext existing
                    }
                    copyImageToCache(source, key)?.let { return@withContext it }
                }
            }
        }

        findSidecarCover(file)?.let { source ->
            copyImageToCache(source, key)?.let { return@withContext it }
        }
        readEmbeddedCover(file)?.let { bytes ->
            saveImageBytes(bytes, key)?.let { return@withContext it }
        }

        if (isRecentMiss(COVER_MISS, key)) return@withContext null
        val lookup = matchesFor(item, key)
        if (!lookup.networkReached) return@withContext null

        for (candidate in lookup.matches) {
            val url = candidate.img?.takeIf { it.startsWith("http") } ?: continue
            downloadImage(url, key)?.let { return@withContext it }
        }
        markMiss(COVER_MISS, key)
        null
    }

    suspend fun resolveLyric(item: MusicItem): String? = withContext(Dispatchers.IO) {
        if (item.source != LOCAL_SOURCE) return@withContext null
        val file = localFile(item) ?: return@withContext null
        val key = cacheKey(file, item)
        val cached = File(lyricDir, "$key.lrc").takeIf { it.isFile && it.length() > 0L }
        if (cached != null) return@withContext readText(cached.readBytes())

        findSidecarLyric(file)?.let { source ->
            val text = readText(source.readBytes())
            if (isUsableLyric(text)) {
                saveLyric(text, key)
                return@withContext text
            }
        }
        readEmbeddedLyric(file)?.let { text ->
            if (isUsableLyric(text)) {
                saveLyric(text, key)
                return@withContext text
            }
        }

        if (isRecentMiss(LYRIC_MISS, key)) return@withContext null
        val lookup = matchesFor(item, key)
        if (!lookup.networkReached) return@withContext null
        for (candidate in lookup.matches) {
            val text = runCatching {
                MusicSources.getLyric(candidate.source, candidate.songmid)
            }.getOrNull()
            if (isUsableLyric(text)) {
                saveLyric(text!!, key)
                return@withContext text
            }
        }
        markMiss(LYRIC_MISS, key)
        null
    }

    /** 文件夹卡片的封面：优先已有歌曲封面，否则为第一首可匹配歌曲补一张在线封面。 */
    suspend fun resolveFolderCover(songs: List<MusicItem>): String? = withContext(Dispatchers.IO) {
        songs.firstOrNull { !it.img.isNullOrBlank() }?.img?.let { return@withContext it }
        val first = songs.firstOrNull() ?: return@withContext null
        resolveCover(first)
    }

    private suspend fun matchesFor(item: MusicItem, key: String): MatchLookup {
        memoryMatches[key]?.let { return MatchLookup(it, true) }
        if (key in memoryNoMatches) return MatchLookup(emptyList(), true)

        return searchMutex.withLock {
            memoryMatches[key]?.let { return@withLock MatchLookup(it, true) }
            if (key in memoryNoMatches) return@withLock MatchLookup(emptyList(), true)

            readCachedMatches(key)?.let { cached ->
                if (cached.isEmpty()) memoryNoMatches.add(key) else memoryMatches[key] = cached
                return@withLock MatchLookup(cached, true)
            }

            val searches = searchAllSources(item)
            val networkReached = searches.any { it.first }
            val candidates = searches.flatMap { it.second }
                .filter { it.source != LOCAL_SOURCE && it.songmid.isNotBlank() }
                .distinctBy { "${it.source}|${it.songmid}" }
                .mapNotNull { candidate -> score(item, candidate)?.let { candidate to it } }
                .sortedByDescending { it.second }
                .map { it.first }
                .distinctBy { "${it.source}|${it.songmid}" }

            if (networkReached) {
                writeCachedMatches(key, candidates)
                if (candidates.isEmpty()) memoryNoMatches.add(key)
                else memoryMatches[key] = candidates
            }
            MatchLookup(candidates, networkReached)
        }
    }

    private suspend fun searchAllSources(item: MusicItem): List<Pair<Boolean, List<MusicItem>>> {
        val query = searchQuery(item)
        if (query.isBlank()) return emptyList()
        return coroutineScope {
            MusicSources.builtin.values.map { source ->
                async(Dispatchers.IO) {
                    try {
                        true to source.search(query, 1, 10).list
                    } catch (t: Throwable) {
                        Log.d(TAG, "media match search failed: ${source.sourceId}", t)
                        false to emptyList<MusicItem>()
                    }
                }
            }.awaitAll()
        }
    }

    private fun score(item: MusicItem, candidate: MusicItem): Int? {
        val candidateTitle = normalized(candidate.name)
        if (candidateTitle.isBlank()) return null
        var best: Int? = null

        for (identity in localIdentities(item)) {
            val title = normalized(identity.title)
            if (title.isBlank()) continue
            val titleExact = title == candidateTitle
            val titleContains = !titleExact && (
                (title.length >= 2 && candidateTitle.contains(title)) ||
                    (candidateTitle.length >= 2 && title.contains(candidateTitle))
                ) && min(title.length, candidateTitle.length).toFloat() /
                max(title.length, candidateTitle.length) >= 0.72f
            if (!titleExact && !titleContains) continue

            val candidateArtist = normalizedArtist(candidate.singer)
            val artistMatch = identity.artist
                ?.let { normalizedArtist(it) }
                ?.let { artistSimilar(it, candidateArtist) }
                ?: false
            val localMs = intervalToMs(item.interval)
            val candidateMs = intervalToMs(candidate.interval)
            val diffMs = if (localMs != null && candidateMs != null) abs(localMs - candidateMs) else null
            if (diffMs != null && diffMs > DURATION_TOLERANCE_MS) continue
            val durationMatch = diffMs != null && diffMs <= DURATION_TOLERANCE_MS

            // 包含关系必须同时有歌手和时长佐证；完全同名也至少需要一个辅助条件。
            val reliable = if (titleExact) artistMatch || durationMatch
            else artistMatch && durationMatch
            if (!reliable) continue

            var points = if (titleExact) 100 else 65
            if (artistMatch) points += 45
            if (durationMatch) {
                points += when {
                    diffMs == null -> 10
                    diffMs <= 1_000L -> 35
                    else -> 25
                }
            }
            if (!candidate.img.isNullOrBlank()) points += 5
            best = max(best ?: Int.MIN_VALUE, points)
        }
        return best
    }

    private fun localIdentities(item: MusicItem): List<Identity> {
        val result = mutableListOf<Identity>()
        val artist = item.singer.trim().takeUnless { isUnknownArtist(it) }
        result.add(Identity(item.name, artist))

        // 常见文件名「歌手 - 歌名」在缺少标签时也能参与匹配，避免只搜整段文件名。
        if (artist == null) {
            val separators = listOf(" - ", " – ", " — ")
            for (separator in separators) {
                val index = item.name.lastIndexOf(separator)
                if (index <= 0) continue
                val left = item.name.substring(0, index).trim()
                val right = item.name.substring(index + separator.length).trim()
                if (left.isNotBlank() && right.isNotBlank()) {
                    result.add(Identity(right, left))
                    break
                }
            }
        }
        return result.distinctBy { "${it.title}|${it.artist.orEmpty()}" }
    }

    private fun searchQuery(item: MusicItem): String {
        val identity = localIdentities(item).firstOrNull() ?: return item.name
        return listOfNotNull(
            identity.title.takeIf { it.isNotBlank() },
            identity.artist?.takeIf { it.isNotBlank() },
        ).joinToString(" ")
    }

    private fun artistSimilar(local: String, candidate: String): Boolean {
        if (local.isBlank() || candidate.isBlank()) return false
        if (local == candidate || local.contains(candidate) || candidate.contains(local)) return true
        val localTokens = artistTokens(local)
        val candidateTokens = artistTokens(candidate)
        return localTokens.any { it.isNotBlank() && candidateTokens.any { other -> it == other } }
    }

    private fun artistTokens(value: String): List<String> =
        value.split(Regex("[/、,，&;；+]+|feat\\.?|ft\\.?|with", RegexOption.IGNORE_CASE))
            .map(::normalized)
            .filter { it.isNotBlank() }

    private fun normalized(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }

    private fun normalizedArtist(value: String): String {
        if (isUnknownArtist(value)) return ""
        return normalized(value)
    }

    private fun isUnknownArtist(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.isBlank() ||
            trimmed == "本地音乐" ||
            trimmed == "未知歌手" ||
            trimmed.equals("unknown", ignoreCase = true) ||
            trimmed.equals("unknown artist", ignoreCase = true)
    }

    private fun intervalToMs(value: String): Long? {
        val parts = value.trim().split(':').mapNotNull { it.toLongOrNull() }
        if (parts.size !in 2..3) return null
        val ms = when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1_000L
            else -> (parts[0] * 3_600 + parts[1] * 60 + parts[2]) * 1_000L
        }
        return ms.takeIf { it > 0L }
    }

    private fun localFile(item: MusicItem): File? {
        val path = runCatching { Uri.parse(item.songmid).path }.getOrNull()
            ?: item.songmid.takeIf { !it.contains("://") }
        return path?.let(::File)?.takeIf { it.isFile }
    }

    private fun cacheKey(file: File, item: MusicItem): String {
        val source = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
        return sha1(source.ifBlank { "${item.name}|${item.singer}|${item.interval}" })
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun findSidecarCover(file: File): File? = runCatching {
        file.parentFile?.listFiles()?.firstOrNull { candidate ->
            candidate.isFile &&
                candidate.name.substringAfterLast('.', "").lowercase() in IMAGE_EXT &&
                candidate.name.substringBeforeLast('.', "").lowercase() in COVER_NAMES
        }
    }.getOrNull()

    private fun findSidecarLyric(file: File): File? = runCatching {
        val base = file.nameWithoutExtension
        file.parentFile?.listFiles()?.firstOrNull { candidate ->
            candidate.isFile &&
                candidate.extension.equals("lrc", ignoreCase = true) &&
                candidate.nameWithoutExtension.equals(base, ignoreCase = true)
        }
    }.getOrNull()

    private fun readEmbeddedCover(file: File): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.embeddedPicture
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readEmbeddedLyric(file: File): String? = runCatching {
        when (file.extension.lowercase()) {
            "mp3" -> readId3Lyric(file)
            "flac" -> readFlacLyric(file)
            else -> null
        }
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    /** 读取 MP3 ID3v2 的 USLT（非同步歌词）帧。 */
    private fun readId3Lyric(file: File): String? = RandomAccessFile(file, "r").use { raf ->
        if (raf.length() < 10L) return@use null
        val header = ByteArray(10)
        raf.readFully(header)
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() ||
            header[2] != '3'.code.toByte()
        ) {
            return@use null
        }
        val version = header[3].toInt() and 0xff
        if (version !in 3..4) return@use null
        val tagSize = syncSafeInt(header, 6)
        val end = minOf(raf.length(), 10L + tagSize)
        var position = 10L
        while (position + 10L <= end) {
            raf.seek(position)
            val frameHeader = ByteArray(10)
            raf.readFully(frameHeader)
            if (frameHeader[0] == 0.toByte()) break
            val id = String(frameHeader, 0, 4, StandardCharsets.ISO_8859_1)
            val frameSize = if (version == 4) syncSafeInt(frameHeader, 4)
            else unsignedInt(frameHeader, 4)
            if (frameSize <= 0 || position + 10L + frameSize > end) break
            if (id == "USLT") {
                raf.seek(position + 10L)
                val data = ByteArray(frameSize)
                raf.readFully(data)
                decodeUslt(data)?.takeIf { it.isNotBlank() }?.let { return@use it }
            }
            position += 10L + frameSize
        }
        null
    }

    private fun decodeUslt(data: ByteArray): String? {
        if (data.size < 5) return null
        val encoding = data[0].toInt() and 0xff
        var start = 4
        when (encoding) {
            0, 3 -> {
                while (start < data.size && data[start] != 0.toByte()) start++
                start++
            }
            else -> {
                while (start + 1 < data.size) {
                    if (data[start] == 0.toByte() && data[start + 1] == 0.toByte()) {
                        start += 2
                        break
                    }
                    start += 2
                }
            }
        }
        if (start >= data.size) return null
        val charset = when (encoding) {
            0 -> StandardCharsets.ISO_8859_1
            1 -> StandardCharsets.UTF_16
            2 -> StandardCharsets.UTF_16BE
            else -> StandardCharsets.UTF_8
        }
        return String(data, start, data.size - start, charset)
            .trim('\u0000', '\uFEFF', '\r', '\n')
            .trim()
    }

    /** 读取 FLAC 的 Vorbis Comment（LYRICS / UNSYNCEDLYRICS 字段）。 */
    private fun readFlacLyric(file: File): String? = RandomAccessFile(file, "r").use { raf ->
        if (raf.length() < 8L) return@use null
        val marker = ByteArray(4)
        raf.readFully(marker)
        if (String(marker, StandardCharsets.ISO_8859_1) != "fLaC") return@use null
        var position = 4L
        while (position + 4L <= raf.length()) {
            raf.seek(position)
            val header = ByteArray(4)
            raf.readFully(header)
            val last = (header[0].toInt() and 0x80) != 0
            val type = header[0].toInt() and 0x7f
            val size = ((header[1].toInt() and 0xff) shl 16) or
                ((header[2].toInt() and 0xff) shl 8) or
                (header[3].toInt() and 0xff)
            if (size < 0 || position + 4L + size > raf.length()) break
            if (type == 4 && size in 1..MAX_LYRIC_BYTES) {
                val data = ByteArray(size)
                raf.readFully(data)
                parseVorbisLyric(data)?.let { return@use it }
            }
            position += 4L + size
            if (last) break
        }
        null
    }

    private fun parseVorbisLyric(data: ByteArray): String? {
        var offset = 0
        fun readLeInt(): Int? {
            if (offset + 4 > data.size) return null
            val value = (data[offset].toInt() and 0xff) or
                ((data[offset + 1].toInt() and 0xff) shl 8) or
                ((data[offset + 2].toInt() and 0xff) shl 16) or
                ((data[offset + 3].toInt() and 0xff) shl 24)
            offset += 4
            return value
        }
        val vendorLength = readLeInt() ?: return null
        if (vendorLength < 0 || offset + vendorLength > data.size) return null
        offset += vendorLength
        val count = readLeInt() ?: return null
        repeat(count.coerceAtMost(10_000)) {
            val length = readLeInt() ?: return null
            if (length < 0 || offset + length > data.size) return null
            val entry = String(data, offset, length, StandardCharsets.UTF_8)
            offset += length
            val key = entry.substringBefore('=', "").trim().uppercase()
            if (key == "LYRICS" || key == "UNSYNCEDLYRICS") {
                return entry.substringAfter('=', "").trim().takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    private fun syncSafeInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0x7f) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7f) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7f) shl 7) or
            (bytes[offset + 3].toInt() and 0x7f)

    private fun unsignedInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun copyImageToCache(source: File, key: String): String? = runCatching {
        if (!source.isFile || source.length() <= 0L || source.length() > MAX_IMAGE_BYTES) return null
        val bytes = source.inputStream().use { input ->
            val out = ByteArrayOutputStream()
            input.copyTo(out, 16 * 1024)
            out.toByteArray()
        }
        saveImageBytes(bytes, key)
    }.getOrNull()

    private fun saveImageBytes(bytes: ByteArray, key: String): String? = runCatching {
        if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES || !looksLikeImage(bytes)) return null
        coverDir.mkdirs()
        val out = File(coverDir, "$key.img")
        if (!out.exists() || out.length() != bytes.size.toLong()) out.writeBytes(bytes)
        Uri.fromFile(out).toString()
    }.getOrNull()

    private fun downloadImage(url: String, key: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "HarmoniaMusicTV/1.0")
        }
        try {
            if (conn.responseCode !in 200..299) return null
            val length = conn.contentLength.toLong().takeIf { it > 0L }
            if (length != null && length > MAX_IMAGE_BYTES) return null
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_IMAGE_BYTES) return null
                    out.write(buffer, 0, read)
                }
            }
            saveImageBytes(out.toByteArray(), key)
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val jpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val png = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        val webp = bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        val gif = bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte()
        return jpeg || png || webp || gif
    }

    private fun saveLyric(text: String, key: String) {
        runCatching {
            lyricDir.mkdirs()
            File(lyricDir, "$key.lrc").writeText(text, StandardCharsets.UTF_8)
        }
    }

    private fun isUsableLyric(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val contentLines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot {
                it.startsWith("[ti:") || it.startsWith("[ar:") || it.startsWith("[al:") ||
                    it.startsWith("[by:") || it.startsWith("[offset:")
            }
            .count()
        return contentLines > 0
    }

    private fun readText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        }
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            String(bytes, charset("GB18030"))
        }
    }

    private fun isRecentMiss(kind: String, key: String): Boolean {
        val at = prefs.getLong("$kind:$key", 0L)
        return at > 0L && System.currentTimeMillis() - at < MISS_TTL_MS
    }

    private fun markMiss(kind: String, key: String) {
        prefs.edit().putLong("$kind:$key", System.currentTimeMillis()).apply()
    }

    private fun readCachedMatches(key: String): List<MusicItem>? {
        val raw = prefs.getString("matches:$key", null) ?: return null
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val objectValue = array.optJSONObject(index) ?: return@mapNotNull null
                MusicItem(
                    source = objectValue.optString("source"),
                    songmid = objectValue.optString("songmid"),
                    name = objectValue.optString("name"),
                    singer = objectValue.optString("singer"),
                    albumName = objectValue.optString("albumName"),
                    interval = objectValue.optString("interval"),
                    img = objectValue.optString("img").takeIf { it.isNotBlank() },
                    qualities = emptyList(),
                )
            }
        }.getOrNull()
    }

    private fun writeCachedMatches(key: String, matches: List<MusicItem>) {
        val array = JSONArray()
        matches.take(12).forEach { item ->
            array.put(
                JSONObject().apply {
                    put("source", item.source)
                    put("songmid", item.songmid)
                    put("name", item.name)
                    put("singer", item.singer)
                    put("albumName", item.albumName)
                    put("interval", item.interval)
                    put("img", item.img.orEmpty())
                },
            )
        }
        prefs.edit().putString("matches:$key", array.toString()).apply()
    }

    private data class Identity(val title: String, val artist: String?)

    companion object {
        private const val TAG = "LocalMediaResolver"
        private const val PREFS = "local_media_cache"
        private const val COVER_MISS = "cover_miss"
        private const val LYRIC_MISS = "lyric_miss"
        private const val DURATION_TOLERANCE_MS = 3_000L
        private const val MAX_IMAGE_BYTES = 8L * 1024L * 1024L
        private const val MAX_LYRIC_BYTES = 4 * 1024 * 1024
        private const val MISS_TTL_MS = 7L * 24L * 60L * 60L * 1000L
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "bmp")
        private val COVER_NAMES = setOf(
            "cover", "folder", "front", "album", "albumart", "artwork", "poster", "封面", "专辑封面",
        )
    }
}
