package com.harmoniamusic.tv.data.source.userapi

import android.content.Context
import android.util.Log
import com.harmoniamusic.tv.data.net.HttpClient
import com.harmoniamusic.tv.data.source.MusicItem
import com.harmoniamusic.tv.data.store.UserApiPrefs
import com.harmoniamusic.tv.data.store.UserApiStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.text.Charsets

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

    /** 加载所有预设与用户导入脚本（幂等，并发安全）。每个脚本各起一个 WebView 引擎。 */
    suspend fun init() = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (loaded) return@withContext
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
                    apis[preset.id] = JsUserApi(preset.id, host, engine)
                }
            }
            // 2) 用户导入脚本（局域网/扫码导入，落盘于 UserApiStore）
            val store = UserApiStore(context)
            store.list().forEachIndexed { idx, entry ->
                runCatching {
                    // 兼容「粘贴链接」导入：落盘的是 URL 时先下载真实脚本内容并回写（一次性迁移），
                    // 否则 URL 被当 JS 执行只是个标签语句，脚本永远不会上报音源。
                    val script = resolveScriptContent(entry)
                    if (script != entry) {
                        val all = store.list().toMutableList()
                        if (idx < all.size && all[idx] == entry) {
                            all[idx] = script
                            store.saveAll(all)
                        }
                    }
                    val engine = WebViewJsEngine(context)
                    val host = LxHost(currentScriptInfo = mapOf("id" to "imported_$idx"))
                    engine.load(script, host)
                    apis["imported_$idx"] = JsUserApi("imported_$idx", host, engine)
                }
            }
            loaded = true
        }
    }

    /** 所有脚本支持（上报）的音源 id 合并去重，如 [wy, tx, kw, kg, mg]。 */
    fun supportedSources(): List<String> = apis.values.flatMap { it.sourceIds }.distinct()

    /**
     * 导入内容可能是脚本链接（http/https）：下载真实脚本文本；否则原样返回。
     * 电视直连 GitHub 代理镜像时常因 TLS 被墙而失败，故对 GitHub raw 链接自动补多个
     * 镜像候选（gh-proxy.com / ghfast.top）依次尝试，失败时打日志便于排查。
     */
    private suspend fun resolveScriptContent(content: String): String {
        val t = content.trim()
        if (!t.startsWith("http://", true) && !t.startsWith("https://", true)) return content
        val candidates = buildList {
            add(t)
            Regex("""^(https?://)[^/]+(/raw\.githubusercontent\.com/.+)$""", RegexOption.IGNORE_CASE)
                .find(t)?.let { m ->
                    add("https://gh-proxy.com" + m.groupValues[2])
                    add("https://ghfast.top" + m.groupValues[2])
                }
        }.distinct()
        for (url in candidates) {
            runCatching { HttpClient.getRaw(url) }
                .onSuccess { if (it.isNotBlank()) return it }
                .onFailure {
                    Log.w(TAG, "下载音源脚本失败: $url -> ${it.javaClass.simpleName}: ${it.message}")
                }
        }
        return content
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

    /** 取流：优先选中的「优先取流源」，再遍历其他启用的脚本；禁用的不参与。 */
    suspend fun getMusicUrl(item: MusicItem, quality: String): String? {
        if (!loaded) init()
        val info = buildMusicInfo(item)
        val active = prefs.getActive()
        val ordered = apis.entries.sortedByDescending { it.key == active }
        for ((id, api) in ordered) {
            if (!prefs.isEnabled(id)) continue
            if (!api.sourceIds.contains(item.source)) continue
            val url = runCatching { api.getMusicUrl(info, quality) }.getOrNull()
            if (!url.isNullOrBlank()) return url
        }
        return null
    }

    /** 用户 API 脚本清单（含支持源/启用/选中状态），供设置页展示与管理。 */
    data class ApiInfo(
        val id: String,
        val name: String,
        val supportSources: List<String>,
        val enabled: Boolean,
        val active: Boolean,
    )

    /** 从 user-api 脚本文本识别脚本名（lx-music 约定：头部注释 @name，或常量 SCRIPT_NAME）。识别不到返回 null。 */
    private fun parseUserApiName(script: String?): String? {
        if (script.isNullOrBlank()) return null
        fun clean(s: String): String? = s.removeSuffix("*/").trim().ifBlank { null }
        Regex("""@name\s+([^\r\n*]+)""").find(script)?.groupValues?.get(1)
            ?.let { clean(it)?.let { n -> return n } }
        Regex("""SCRIPT_NAME\s*=\s*['"]([^'"]+)['"]""").find(script)?.groupValues?.get(1)
            ?.trim()?.ifBlank { null }?.let { return it }
        return null
    }

    fun listApis(): List<ApiInfo> {
        val scripts = UserApiStore(context).list()
        val presets = PresetUserApis.list.associateBy { it.id }
        return apis.map { (id, api) ->
            val preset = presets[id]
            val name = preset?.name ?: run {
                if (id.startsWith("imported_")) {
                    val idx = id.removePrefix("imported_").toIntOrNull()
                    parseUserApiName(scripts.getOrNull(idx ?: -1)) ?: "导入 ${idx?.plus(1) ?: id}"
                } else id
            }
            ApiInfo(
                id = id,
                name = name,
                supportSources = api.sourceIds,
                enabled = prefs.isEnabled(id),
                active = prefs.getActive() == id,
            )
        }
    }

    /** 选中某脚本为「优先取流源」（空串 = 自动兜底所有启用源）。 */
    fun setActive(id: String) = prefs.setActive(id)

    fun setEnabled(id: String, on: Boolean) = prefs.setEnabled(id, on)

    /** 删除第 idx 个导入脚本（落盘），随后由调用方 reload。 */
    fun removeImported(idx: Int) {
        val store = UserApiStore(context)
        val all = store.list().toMutableList()
        if (idx in all.indices) {
            all.removeAt(idx)
            store.saveAll(all)
        }
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
        loaded = false
    }

    companion object {
        private const val TAG = "UserApiManager"

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
