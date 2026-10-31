package com.harmoniamusic.tv.data.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 极简 HTTP 客户端，等价于 RN 的 `utils/request.js` 的 httpFetch（POST form + JSON 响应）。
 * 用 HttpURLConnection，避免额外依赖；网络调用统一切到 IO 调度器。
 */
object HttpClient {
    private const val TAG = "HttpClient"
    const val DEFAULT_UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36"
    const val DEFAULT_TIMEOUT = 15000

    /** GET 请求并返回原始文本（用于 JSONP 等需要自行解析的场景）。 */
    suspend fun getRaw(
        url: String,
        headers: Map<String, String> = emptyMap(),
        connectTimeoutMs: Int = DEFAULT_TIMEOUT,
        readTimeoutMs: Int = DEFAULT_TIMEOUT,
    ): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", UA)
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream.bufferedReader(StandardCharsets.UTF_8).use(BufferedReader::readText)
        Log.d(TAG, "GET $url -> $code")
        conn.disconnect()
        text
    }

    /** GET 请求，可选自定义超时（网络测试场景需要更短）。 */
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        connectTimeoutMs: Int = DEFAULT_TIMEOUT,
        readTimeoutMs: Int = DEFAULT_TIMEOUT,
    ): JSONObject = JSONObject(
        getRaw(url, headers, connectTimeoutMs, readTimeoutMs),
    )

    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
        connectTimeoutMs: Int = DEFAULT_TIMEOUT,
        readTimeoutMs: Int = DEFAULT_TIMEOUT,
    ): JSONObject = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            doOutput = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
        }
        val body = form.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }
        conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream.bufferedReader(StandardCharsets.UTF_8).use(BufferedReader::readText)
        Log.d(TAG, "POST $url -> $code")
        conn.disconnect()
        JSONObject(text)
    }

    /** POST 原始 body（如 JSON），用于 user-api 脚本（juhe 等）以 JSON 体提交。 */
    suspend fun postRaw(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        connectTimeoutMs: Int = DEFAULT_TIMEOUT,
        readTimeoutMs: Int = DEFAULT_TIMEOUT,
    ): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            doOutput = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
        }
        conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream.bufferedReader(StandardCharsets.UTF_8).use(BufferedReader::readText)
        Log.d(TAG, "POST(raw) $url -> $code")
        conn.disconnect()
        text
    }

    private val UA = DEFAULT_UA
}
