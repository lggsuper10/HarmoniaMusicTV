package com.harmoniamusic.tv.data.source.userapi

import org.json.JSONObject

/**
 * lx-music user-api（.js 音源）脚本协议类型。
 * 协议来自真实预设脚本（grass / tv-default-user-api.js）：脚本读取全局 `globalThis.lx`，
 * 从中解构出 EVENT_NAMES / request / on / send / env / version / currentScriptInfo / utils。
 */

/** 脚本调用 lx.request(url, options, cb) 时的 options（对应 RN handleRequestData）。 */
data class LxRequestOptions(
    val method: String = "get",
    val headers: Map<String, String> = emptyMap(),
    val form: Map<String, String>? = null,
    val data: Any? = null,
    val binary: Boolean = false,
    val timeout: Long = 13_000,
)

/** 宿主回传给脚本的响应（lx.request 回调的 response 对象）。 */
data class LxResponse(
    val statusCode: Int,
    val statusMessage: String = "",
    val headers: Map<String, String> = emptyMap(),
    val body: Any?, // JSONObject 或 String
)

/** 脚本通过 lx.send(EVENT_NAMES.inited, { sources }) 上报的单个音源元信息。 */
data class LxSourceMeta(
    val name: String,
    val type: String, // 'music'
    val actions: List<String>, // ['musicUrl','lyric','pic']
    val qualitys: JSONObject?, // 音质表
)
