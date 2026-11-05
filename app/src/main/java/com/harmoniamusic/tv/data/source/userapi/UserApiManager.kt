package com.harmoniamusic.tv.data.source.userapi

import android.content.Context
import android.util.Log
import com.harmoniamusic.tv.data.net.HttpClient
import com.harmoniamusic.tv.data.source.MusicItem
import com.harmoniamusic.tv.data.source.MusicSources
import com.harmoniamusic.tv.data.store.UserApiPrefs
import com.harmoniamusic.tv.data.store.UserApiStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.text.Charsets
import java.util.concurrent.ConcurrentHashMap

/**
 * 用户 API 管理中心：加载内置预设（assets 打包的 grass/juhe）与用户导入脚本，
 * 统一对外暴露「取流 / 测试」能力。对应 RN 版的 userApi 列表 + TV_PRESET_USER_API_CANDIDATES。
 *
 * 典型用法：搜索仍走内置直连源（wy/tx/kw/kg/mg 的搜索接口大多仍可返回列表），
 * 取流优先走这里（野草/juhe 等，绕过平台的会员/试听限制）。
 */
class UserApiManager(private val context: Context) {

    // 预设/导入脚本 id -> 实例
    private val apis = LinkedHashMap<String, JsUserApi>()
    private var loaded = false
    private val initMutex = Mutex()
    private val prefs = UserApiPrefs(context)
    private val importErrors = ConcurrentHashMap<Int, String>()
    private val presetErrors = ConcurrentHashMap<String, String>()

    /** 加载所有预设与用户导入脚本（幂等，并发安全）。每个脚本各起一个 WebView 引擎。 */
    suspend fun init() = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (loaded) return@withContext
            importErrors.clear()
            presetErrors.clear()
            // 1) 内置预设（assets 打包，不可被用户修改）
            for (preset in PresetUserApis.list) {
                runCatching {
                    val script = context.applicationContext.assets
                        .open(preset.assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val engine = WebViewJsEngine(context)
                    val host = LxHost(
                        currentScriptInfo = mapOf(
                            "id" to preset.id,
                            "name" to preset.name,
                            "author" to preset.author,
                        ),
                    )
                    engine.load(script, host)
                    val api = JsUserApi(preset.id, host, engine)
                    apis[preset.id] = api
                    logLoadedSources(preset.id, api, engine)
                }.onFailure {
                    presetErrors[preset.id] = it.message?.lineSequence()?.firstOrNull()?.take(180)
                        ?: it.javaClass.simpleName
                    Log.e(TAG, "预设音源加载失败: id=${preset.id}", it)
                }
            }
            // 2) 用户导入脚本（局域网/扫码导入，落盘于 UserApiStore）
            val store = UserApiStore(context)
            store.listEntries().forEachIndexed { idx, entry ->
                val id = "imported_$idx"
                runCatching {
                    // 兼容旧版「粘贴链接」导入：落盘的是 URL 时先下载真实脚本文本并回写。
                    val script = resolveScriptContent(entry.script)
                    val detectedName = normalizeName(entry.name) ?: deriveName(script, entry.script)
                    if (script != entry.script) store.updateScriptAt(idx, script)
                    if (entry.name.isNullOrBlank() && detectedName != null) {
                        store.renameAt(idx, detectedName)
                    }
                    val engine = WebViewJsEngine(context)
                    val host = LxHost(
                        currentScriptInfo = mapOf(
                            "id" to id,
                            "name" to (detectedName ?: id),
                        ),
                    )
                    engine.load(script, host)
                    val api = JsUserApi(id, host, engine)
                    apis[id] = api
                    logLoadedSources(id, api, engine)
                }.onFailure {
                    importErrors[idx] = it.message?.lineSequence()?.firstOrNull()?.take(180)
                        ?: "脚本加载失败"
                    Log.e(TAG, "导入音源加载失败: id=$id", it)
                }
            }
            loaded = true
        }
    }

    private fun logLoadedSources(id: String, api: JsUserApi, engine: WebViewJsEngine) {
        val sources = api.sourceIds
        if (sources.isEmpty()) {
            Log.e(TAG, "音源脚本未上报 sources: id=$id, error=${engine.loadError() ?: "无"}")
        } else {
            Log.i(TAG, "音源脚本加载完成: id=$id, sources=${sources.joinToString("/")}")
        }
    }

    /** 所有脚本支持（上报）的音源 id 合并去重，如 [wy, tx, kw, kg, mg]。 */
    fun supportedSources(): List<String> = apis.values.flatMap { it.sourceIds }.distinct()

    data class ImportResult(val name: String? = null, val error: String? = null)

    /** 导入脚本：立即下载并校验内容，成功后持久化自定义名称或自动生成名称。 */
    suspend fun importScript(content: String, displayName: String?): ImportResult = withContext(Dispatchers.IO) {
        val script = runCatching { resolveScriptContent(content) }.getOrElse {
            return@withContext ImportResult(error = it.message ?: "音源导入失败")
        }
        val store = UserApiStore(context)
        val entries = store.listEntries()
        val requestedName = normalizeName(displayName)
        val existing = entries.indexOfFirst { it.script == script }
        if (existing >= 0) {
            val existingName = normalizeName(entries[existing].name)
                ?: deriveName(script, content)
                ?: "导入 ${existing + 1}"
            if (requestedName != null) store.renameAt(existing, requestedName)
            return@withContext ImportResult(name = requestedName ?: existingName)
        }
        val name = requestedName
            ?: deriveName(script, content)
            ?: "导入 ${entries.size + 1}"
        store.add(script, name)
        ImportResult(name = name)
    }

    /**
     * 导入内容可能是脚本链接（http/https）：下载真实脚本文本；否则校验后原样返回。
     * GitHub raw 地址会依次尝试直连与常见镜像，所有失败原因最终反馈到设置页。
     */
    private suspend fun resolveScriptContent(content: String): String {
        val value = content.trim()
        if (value.isEmpty()) throw IllegalArgumentException("脚本内容为空")
        if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
            if (!looksLikeUserApi(value)) throw IllegalArgumentException("内容不是有效的 user-api 脚本")
            return value
        }

        var lastError = "网络不可用"
        for (url in scriptCandidates(value)) {
            val response = try {
                HttpClient.getRawResponse(
                    url = url,
                    connectTimeoutMs = 20_000,
                    readTimeoutMs = 30_000,
                )
            } catch (error: Exception) {
                lastError = "${error.javaClass.simpleName}: ${error.message ?: "连接失败"}"
                Log.w(TAG, "下载音源脚本失败: $url -> $lastError")
                continue
            }
            if (response.code !in 200..299) {
                lastError = "HTTP ${response.code}"
                Log.w(TAG, "下载音源脚本失败: $url -> HTTP ${response.code}")
                continue
            }
            val script = response.body.trim()
            if (!looksLikeUserApi(script)) {
                lastError = if (script.startsWith("<", true)) {
                    "服务器返回网页而不是脚本（HTTP ${response.code}）"
                } else {
                    "返回内容不是有效的 user-api 脚本（HTTP ${response.code}）"
                }
                Log.w(TAG, "音源脚本内容无效: $url -> $lastError")
                continue
            }
            Log.i(TAG, "音源脚本下载完成: $url (${script.length} chars)")
            return script
        }
        throw IllegalStateException("脚本下载失败：$lastError")
    }

    private fun scriptCandidates(url: String): List<String> {
        val candidates = linkedSetOf(url)
        val marker = "/raw.githubusercontent.com/"
        val pathStart = url.indexOf(marker, ignoreCase = true)
        if (pathStart >= 0) {
            val suffix = url.substring(pathStart + marker.length)
            if (suffix.isNotBlank()) {
                candidates += "https://raw.githubusercontent.com/$suffix"
                candidates += "https://ghproxy.net/https://raw.githubusercontent.com/$suffix"
                candidates += "https://ghproxy.net/raw.githubusercontent.com/$suffix"
                candidates += "https://gh-proxy.com/https://raw.githubusercontent.com/$suffix"
                candidates += "https://ghfast.top/https://raw.githubusercontent.com/$suffix"
            }
        }
        return candidates.toList()
    }

    private fun looksLikeUserApi(script: String): Boolean {
        val text = script.trim()
        if (text.length < 20) return false
        if (text.startsWith("<", ignoreCase = true)) return false
        val sample = text.take(8_000).lowercase()
        return listOf("@name", "script_name", "event_names", "getmusicurl", "globalthis.lx", "lx.")
            .any { marker -> sample.contains(marker) }
    }

    /**
     * 构造传给脚本的 musicInfo（lx-music 聚合结构：顶层字段 + 各平台子对象）。
     * grass 等脚本按 source 从对应子对象取 songmid，故需补齐子对象。
     */
    fun buildMusicInfo(item: MusicItem): Map<String, Any?> {
        val childKey = if (item.source == "mg") "mgd" else item.source
        val child = mapOf(
            "songmid" to item.songmid,
            "musicid" to item.songmid,
            "name" to item.name,
            "singer" to item.singer,
            "album" to item.albumName,
        )
        return mapOf(
            "source" to item.source,
            "songmid" to item.songmid,
            "name" to item.name,
            "singer" to item.singer,
            "album" to item.albumName,
            "pic" to (item.img ?: ""),
            childKey to child,
        )
    }

    data class StreamResolution(
        val url: String,
        val providerId: String,
        val providerName: String,
    )

    /** 某次取流尝试的结果，用于在播放失败时说明究竟试过哪些源。 */
    data class StreamAttempt(
        val providerName: String,
        val result: String,
    )

    data class ResolutionReport(
        val stream: StreamResolution?,
        val configuredSourceLabel: String,
        val attempts: List<StreamAttempt>,
    )

    /**
     * 取流：优先选中的「优先取流源」，再遍历其他启用的脚本；禁用的不参与。
     * 返回值同时带上配置项与尝试记录，供播放页展示可排查的失败原因。
     */
    suspend fun resolveMusicUrlReport(item: MusicItem, quality: String): ResolutionReport {
        if (!loaded) init()
        val info = buildMusicInfo(item)
        val active = prefs.getActive()
        val apiList = listApis()
        val displayNames = apiList.associate { it.id to it.name }
        val configuredSourceLabel = active
            .takeIf { it.isNotBlank() }
            ?.let { displayNames[it] ?: apiDisplayName(it) }
            ?: "自动（所有已启用用户源）"
        val attempts = mutableListOf<StreamAttempt>()

        if (active.isNotBlank() && active !in apis) {
            val error = apiList.firstOrNull { it.id == active }?.error
            attempts += StreamAttempt(
                providerName = configuredSourceLabel,
                result = error?.lineSequence()?.firstOrNull()?.take(100) ?: "脚本未加载",
            )
        }

        val ordered = apis.entries.sortedByDescending { it.key == active }
        for ((id, api) in ordered) {
            val providerName = displayNames[id] ?: apiDisplayName(id)
            val isPreferred = id == active
            if (!prefs.isEnabled(id)) {
                if (isPreferred) attempts += StreamAttempt(providerName, "已停用")
                continue
            }
            if (!api.sourceIds.contains(item.source)) {
                if (isPreferred) {
                    val reportedSources = api.sourceIds
                    val reason = if (reportedSources.isEmpty()) {
                        val loadError = api.loadError()?.lineSequence()?.firstOrNull()?.take(80)
                        if (loadError.isNullOrBlank()) "脚本未上报任何音源" else "脚本未上报音源（$loadError）"
                    } else {
                        "未上报${MusicSources.displayName(item.source)}音源"
                    }
                    attempts += StreamAttempt(
                        providerName,
                        reason,
                    )
                }
                continue
            }
            val result = runCatching { api.getMusicUrl(info, quality) }
                .onFailure { Log.w(TAG, "用户 API 取流异常: source=${item.source} api=$id", it) }
            val url = normalizeStreamUrl(result.getOrNull())
            if (url != null) {
                val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
                Log.i(TAG, "用户 API 取流命中: source=${item.source} api=$id host=$host")
                return ResolutionReport(
                    stream = StreamResolution(
                        url = url,
                        providerId = id,
                        providerName = providerName,
                    ),
                    configuredSourceLabel = configuredSourceLabel,
                    attempts = attempts,
                )
            }
            val failure = result.exceptionOrNull()?.let {
                "取流异常（${it.javaClass.simpleName}: ${it.message?.lineSequence()?.firstOrNull().orEmpty()}）"
            } ?: "未返回可用地址"
            attempts += StreamAttempt(providerName, failure)
            Log.w(TAG, "用户 API 未返回可用地址: source=${item.source} api=$id reason=$failure")
        }
        return ResolutionReport(
            stream = null,
            configuredSourceLabel = configuredSourceLabel,
            attempts = attempts,
        )
    }

    /** 兼容只需要命中的流源的调用方。 */
    suspend fun resolveMusicUrl(item: MusicItem, quality: String): StreamResolution? =
        resolveMusicUrlReport(item, quality).stream

    /** 兼容只需要 URL 的调用方（如下载）。 */
    suspend fun getMusicUrl(item: MusicItem, quality: String): String? =
        resolveMusicUrl(item, quality)?.url

    private fun apiDisplayName(id: String): String =
        listApis().firstOrNull { it.id == id }?.name ?: id

    /** 用户 API 脚本清单（含支持源/启用/选中状态），供设置页展示与管理。 */
    data class ApiInfo(
        val id: String,
        val name: String,
        val supportSources: List<String>,
        val enabled: Boolean,
        val active: Boolean,
        val error: String? = null,
    )

    /** 从 user-api 脚本文本识别脚本名（lx-music 约定：头部注释 @name，或常量 SCRIPT_NAME）。 */
    private fun parseUserApiName(script: String?): String? {
        if (script.isNullOrBlank()) return null
        Regex("""@name\s+([^\r\n*]+)""").find(script)?.groupValues?.get(1)
            ?.let { return normalizeName(it) }
        Regex("""SCRIPT_NAME\s*=\s*['"]([^'"]+)['"]""").find(script)?.groupValues?.get(1)
            ?.let { return normalizeName(it) }
        return null
    }

    private fun normalizeName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.lineSequence().firstOrNull()
            ?.removePrefix("@name")
            ?.removeSuffix("*/")
            ?.trim()
            ?.replace(Regex("""\s+"""), " ")
            ?.take(40)
            ?.ifBlank { null }
    }

    /**
     * 未填写显示名称时的兜底命名顺序：脚本内名称、URL 文件名/仓库目录、首行注释、导入序号。
     */
    private fun deriveName(script: String, source: String): String? {
        parseUserApiName(script)?.let { return it }
        if (source.startsWith("http://", true) || source.startsWith("https://", true)) {
            deriveNameFromUrl(source)?.let { return it }
        }
        Regex("""(?m)^\s*(?:/\*+|\*|//)\s*(?!@)([^\r\n*]{2,48})""")
            .find(script.take(2_000))
            ?.groupValues?.get(1)
            ?.let { return normalizeName(it) }
        return null
    }

    private fun deriveNameFromUrl(url: String): String? = runCatching {
        val segments = java.net.URI(url).path.orEmpty()
            .split('/')
            .filter { it.isNotBlank() }
        val fileName = segments.lastOrNull()
            ?.substringBeforeLast('.', missingDelimiterValue = segments.lastOrNull().orEmpty())
            ?.replace('-', ' ')
            ?.replace('_', ' ')
        val generated = if (fileName.isNullOrBlank() || fileName.lowercase() in LOW_VALUE_NAMES) {
            segments.getOrNull(segments.size - 2)?.replace('-', ' ')?.replace('_', ' ')
        } else {
            fileName
        }
        normalizeName(generated)
    }.getOrNull()

    fun listApis(): List<ApiInfo> {
        val entries = UserApiStore(context).listEntries()
        val result = mutableListOf<ApiInfo>()
        PresetUserApis.list.forEach { preset ->
            val api = apis[preset.id]
            result += ApiInfo(
                id = preset.id,
                name = preset.name,
                supportSources = api?.sourceIds.orEmpty(),
                enabled = prefs.isEnabled(preset.id),
                active = prefs.getActive() == preset.id,
                error = presetErrors[preset.id]
                    ?: api?.loadError()?.lineSequence()?.firstOrNull()?.take(160)
                    ?: if (api == null) "脚本未加载" else null,
            )
        }
        entries.forEachIndexed { index, entry ->
            val id = "imported_$index"
            val api = apis[id]
            val name = normalizeName(entry.name)
                ?: parseUserApiName(entry.script)
                ?: "导入 ${index + 1}"
            result += ApiInfo(
                id = id,
                name = name,
                supportSources = api?.sourceIds.orEmpty(),
                enabled = prefs.isEnabled(id),
                active = prefs.getActive() == id,
                error = importErrors[index]
                    ?: api?.loadError()?.lineSequence()?.firstOrNull()?.take(160)
                    ?: if (api == null) "脚本未加载" else null,
            )
        }
        return result
    }

    /** 选中某脚本为「优先取流源」（空串 = 自动兜底所有启用源）。 */
    fun setActive(id: String) = prefs.setActive(id)

    fun setEnabled(id: String, on: Boolean) = prefs.setEnabled(id, on)

    /** 重命名已导入脚本；预设脚本名称固定。 */
    fun renameImported(idx: Int, name: String) {
        val clean = normalizeName(name) ?: return
        UserApiStore(context).renameAt(idx, clean)
    }

    /** 删除第 idx 个导入脚本（落盘），随后由调用方 reload。 */
    fun removeImported(idx: Int) {
        val store = UserApiStore(context)
        if (idx !in store.listEntries().indices) return
        store.removeAt(idx)
        importErrors.remove(idx)
        if (prefs.getActive().startsWith("imported_")) prefs.setActive("")
    }

    /** 各脚本（预设/导入）支持（上报）的音源 id 列表，便于设置页展示。 */
    fun getApiSources(): Map<String, List<String>> =
        apis.mapValues { (_, api) -> api.sourceIds }

    /** 测试所有脚本是否初始化成功（上报了音源）。返回 脚本id -> 是否可用。 */
    fun testAll(): Map<String, Boolean> =
        apis.mapValues { (_, api) -> api.sourceIds.isNotEmpty() }

    /** 运行时重新加载：销毁现有引擎，按最新「预设 + 导入」脚本重建。导入新源后调用。 */
    suspend fun reload() {
        initMutex.withLock {
            apis.values.forEach { it.destroy() }
            apis.clear()
            loaded = false
        }
        init()
    }

    fun destroy() {
        apis.values.forEach { it.destroy() }
        apis.clear()
        importErrors.clear()
        presetErrors.clear()
        loaded = false
    }

    companion object {
        private const val TAG = "UserApiManager"
        private val LOW_VALUE_NAMES = setOf("latest", "index", "main", "master", "min", "source", "api")

        @Volatile
        private var sharedInstance: UserApiManager? = null

        /** 全应用共享一个实例，避免播放/下载等 ViewModel 各自重复起 WebView 引擎。 */
        fun shared(context: Context): UserApiManager =
            sharedInstance ?: synchronized(this) {
                sharedInstance ?: UserApiManager(context.applicationContext).also {
                    sharedInstance = it
                }
            }
    }
}
