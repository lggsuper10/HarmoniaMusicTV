package com.harmoniamusic.tv.data.lan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * 局域网导入服务（对应 RN `core/init/server` 的极简版）。
 *
 * 起一个 HTTP 服务：手机浏览器打开 `http://<east-ip>:<port>` 可看到一个文本表单，
 * 粘贴 user-api 脚本 JSON 后 POST 到 `/import`，回调 [onImport] 持久化。
 * 未引入 ZXing，故页面直接给出网址文本（电视端展示可后续补二维码）。
 */
class LanImportServer(private val onImport: (String) -> Unit) {

    private var serverSocket: ServerSocket? = null
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 启动并返回可访问 URL；失败返回 null。 */
    fun start(port: Int = 8080): String? {
        if (serverSocket != null) return url(port)
        return runCatching {
            val ss = ServerSocket(port)
            serverSocket = ss
            job = scope.launch {
                while (isActive) {
                    val socket = runCatching { ss.accept() }.getOrNull() ?: break
                    launch { runCatching { handle(socket) } }
                }
            }
            url(port)
        }.getOrNull()
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handle(socket: Socket) = socket.use { s ->
        val input: BufferedReader = s.getInputStream().bufferedReader()
        val requestLine = input.readLine() ?: return@use
        val parts = requestLine.split(" ")
        val method = parts.getOrNull(0) ?: ""
        val path = parts.getOrNull(1) ?: "/"

        var contentLength = 0
        while (true) {
            val line = input.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
        }

        val body = if (method == "POST" && contentLength > 0) {
            val buf = CharArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val r = input.read(buf, read, contentLength - read)
                if (r < 0) break
                read += r
            }
            String(buf, 0, read)
        } else ""

        val html: String = if (method == "POST" && path.startsWith("/import")) {
            val api = parseApi(body)
            if (api.isBlank()) {
                "<html><body>导入内容为空</body></html>"
            } else {
                onImport(api)
                "<html><body>导入成功，可在电视端查看。</body></html>"
            }
        } else {
            FORM_HTML
        }

        val bytes = html.toByteArray(Charsets.UTF_8)
        val out = s.getOutputStream()
        out.write(
            ("HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n").toByteArray(Charsets.UTF_8),
        )
        out.write(bytes)
        out.flush()
    }

    /** 解析 `application/x-www-form-urlencoded` 里的 `api=` 字段。 */
    private fun parseApi(body: String): String {
        return body.split("&").firstOrNull { it.startsWith("api=") }
            ?.substringAfter("api=")
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?.trim()
            ?: ""
    }

    private fun url(port: Int): String = "http://${localIp() ?: "127.0.0.1"}:$port"

    private fun localIp(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()
    }

    companion object {
        private val FORM_HTML = """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8"><title>导入音源</title>
            <style>body{font-family:sans-serif;max-width:640px;margin:40px auto;padding:0 16px}
            textarea{width:100%;height:260px;font-family:monospace}button{padding:10px 24px;font-size:16px}</style>
            </head><body>
            <h2>HarmoniaMusic 音源导入</h2>
            <p>粘贴 user-api 脚本 JSON 后点击提交。</p>
            <form method="post" action="/import">
            <textarea name="api" placeholder="在此粘贴脚本内容"></textarea><br><br>
            <button type="submit">提交导入</button>
            </form></body></html>
        """.trimIndent()
    }
}
