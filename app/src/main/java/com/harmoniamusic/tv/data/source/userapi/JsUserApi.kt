package com.harmoniamusic.tv.data.source.userapi

import org.json.JSONObject

/**
 * JS 引擎抽象：加载脚本、把脚本对 globalThis.lx 的读写桥接回 [LxHost]、销毁释放。
 * 具体实现见 [WebViewJsEngine]（零依赖，基于 Android WebView）。
 */
interface JsEngine {
    suspend fun load(script: String, host: LxHost)
    fun destroy()
}

/**
 * lx-music user-api 宿主桥（引擎无关）。负责记录脚本上报的音源清单，并分发取流/搜索请求给引擎。
 */
class LxHost(
    val env: String = "android",
    val version: String = "1.0.0",
    val currentScriptInfo: Map<String, Any?> = emptyMap(),
) {
    private val sourcesMeta = mutableMapOf<String, LxSourceMeta>()
    private var engine: WebViewJsEngine? = null

    fun attachEngine(e: WebViewJsEngine) { engine = e }

    /** 脚本通过 lx.send('inited', {sources}) 上报后解析并记录支持的音源/动作。 */
    fun captureSources(data: JSONObject?) {
        val sources = data?.optJSONObject("sources") ?: return
        val keys = sources.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val m = sources.optJSONObject(id) ?: continue
            sourcesMeta[id] = LxSourceMeta(
                name = m.optString("name", id),
                type = m.optString("type", "music"),
                actions = m.optJSONArray("actions")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }
                } ?: emptyList(),
                qualitys = m.optJSONObject("qualitys"),
            )
        }
    }

    fun getSourceIds(): List<String> = sourcesMeta.keys.toList()
    fun getSourceMeta(id: String): LxSourceMeta? = sourcesMeta[id]
    fun supportsAction(source: String, action: String): Boolean =
        sourcesMeta[source]?.actions?.contains(action) ?: false

    /** 分发一次 action 给脚本并等待结果（JSON 字符串）。 */
    suspend fun callAction(action: String, source: String, info: Map<String, Any?>): String? {
        val req = mapOf("action" to action, "source" to source, "info" to info)
        return engine?.callAction(req)
    }
}

/**
 * 一个 user-api 音源实例（对应 RN 的 loadScript + global.lx.apis）。
 * 管理脚本来源、宿主桥与引擎，对外暴露音乐取流能力。
 */
class JsUserApi(
    val id: String,
    private val host: LxHost,
    private val engine: WebViewJsEngine,
) {
    /** 本脚本支持（上报）的音源 id 列表，如 [wy, tx, kw, kg, mg]。 */
    val sourceIds: List<String> get() = host.getSourceIds()

    /**
     * 取播放地址。musicInfo 需含 source/songmid 及对应平台子对象（结构见 UserApiManager.buildMusicInfo）。
     * 返回播放 url 字符串，或 null（不支持 / 脚本未返回）。
     */
    suspend fun getMusicUrl(musicInfo: Map<String, Any?>, type: String): String? {
        val source = (musicInfo["source"] as? String) ?: return null
        if (!host.supportsAction(source, "musicUrl")) return null
        val info = mapOf("musicInfo" to musicInfo, "type" to type)
        val result = host.callAction("musicUrl", source, info) ?: return null
        return parseUrlResult(result)
    }

    fun destroy() = engine.destroy()

    /** 最近一次脚本初始化/执行错误，供设置页显示诊断信息。 */
    fun loadError(): String? = engine.loadError()
}

/** 解析脚本 request handler 的返回值（可能是 url 字符串，或 {url:...}/{data:...} 对象）。 */
fun parseUrlResult(result: String?): String? {
    val trimmed = result?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val candidate = runCatching {
        val obj = JSONObject(trimmed)
        extractUrlValue(obj)
    }.getOrElse {
        // 可能是 JSON 字符串字面量，如 "http://..."
        trimmed.removeSurrounding("\"")
    }
    return normalizeStreamUrl(candidate)
}

private fun extractUrlValue(obj: JSONObject, depth: Int = 0): String? {
    if (depth > 3) return null
    for (key in listOf("url", "data", "result")) {
        when (val value = obj.opt(key)) {
            is String -> normalizeStreamUrl(value)?.let { return it }
            is JSONObject -> extractUrlValue(value, depth + 1)?.let { return it }
        }
    }
    return null
}

/**
 * 脚本桥接会把 JS 的 undefined/null 序列化为字符串 "null"。
 * 这里统一拒绝空值和非 http(s) 地址，避免把 "null" 交给 ExoPlayer 当成文件名打开。
 */
fun normalizeStreamUrl(value: String?): String? {
    val candidate = value?.trim()
        ?.removeSurrounding("\"")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    if (candidate.equals("null", ignoreCase = true) ||
        candidate.equals("undefined", ignoreCase = true) ||
        candidate.equals("none", ignoreCase = true)
    ) return null
    if (!candidate.startsWith("http://", ignoreCase = true) &&
        !candidate.startsWith("https://", ignoreCase = true)
    ) return null
    return candidate
}
