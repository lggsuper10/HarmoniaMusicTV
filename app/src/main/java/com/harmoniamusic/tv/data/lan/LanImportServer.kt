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
 * 填写可选显示名称，并粘贴 user-api 脚本 URL/内容后 POST 到 `/import`，回调 [onImport] 持久化。
 * 未引入 ZXing，故页面直接给出网址文本（电视端展示可后续补二维码）。
 */
/**
 * [onImport] 同步（挂起）导入并返回错误信息；返回 null 表示成功，非 null 为失败原因。
 * 这样网页能拿到真实结果而不是「已提交」假成功。
 */
class LanImportServer(private val onImport: suspend (String, String?) -> String?) {

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

    private suspend fun handle(socket: Socket) = socket.use { s ->
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
            val form = parseForm(body)
            val api = form["api"].orEmpty().trim()
            val name = form["name"]?.trim()?.ifBlank { null }
            if (api.isBlank()) {
                page("导入内容为空", failed = true)
            } else {
                val err = onImport(api, name)
                if (err == null) {
                    page("导入成功：电视端已加载该音源，可在「设置 → 取流音源·用户 API」中查看/启用。", failed = false)
                } else {
                    page("导入失败：${err.replace("<", "&lt;")}", failed = true)
                }
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

    private fun page(message: String, failed: Boolean): String = """
        <!DOCTYPE html><html><head><meta charset="utf-8"><title>导入结果</title>
        <style>body{font-family:sans-serif;max-width:640px;margin:40px auto;padding:0 16px}
        .ok{color:#1a7f37}.err{color:#c0392b}</style></head><body>
        <h2 class="${if (failed) "err" else "ok"}">${if (failed) "✗ " else "✓ "}$message</h2>
        <p><a href="/">返回重新导入</a></p></body></html>
    """.trimIndent()

    /** 解析 `application/x-www-form-urlencoded` 表单。 */
    private fun parseForm(body: String): Map<String, String> =
        body.split("&").mapNotNull { pair ->
            val key = pair.substringBefore('=', "").trim()
            if (key.isBlank()) return@mapNotNull null
            val value = URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
            key to value
        }.toMap()

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
            <script>
            document.addEventListener('DOMContentLoaded', function(){
              var form = document.querySelector('form');
              form.addEventListener('submit', function(e){
                var ta = document.querySelector('textarea[name=api]');
                var api = ta.value.trim();
                if (/^https?:\/\//i.test(api)) {
                  e.preventDefault();
                  var btn = form.querySelector('button');
                  var orig = btn.textContent;
                  btn.disabled = true; btn.textContent = '正在浏览器端获取脚本…';
                  var raw = api
                    .replace(/^https?:\/\/(ghproxy\.net|gh-proxy\.com|ghfast\.top)\/https:\/\//i, 'https://')
                    .replace(/^https?:\/\/(ghproxy\.net|gh-proxy\.com|ghfast\.top)\//i, 'https://');
                  fetch(raw, {mode:'cors'}).then(function(r){ if(!r.ok) throw new Error('HTTP '+r.status); return r.text(); })
                    .then(function(t){ ta.value = t; btn.disabled=false; btn.textContent=orig; form.submit(); })
                    .catch(function(err){ btn.disabled=false; btn.textContent=orig;
                      if(!confirm('浏览器获取脚本失败（'+err.message+'），改由电视端直接下载（常因网络受限失败）。继续？')) return;
                      form.submit(); });
                }
              });
            });
            </script>
            </head><body>
            <h2>HarmoniaMusic 音源导入</h2>
            <p>推荐：粘贴<strong>完整脚本内容</strong>（而非 URL）可 100% 成功。若只贴 URL，将优先由本机浏览器获取（需能访问 GitHub），失败再交由电视端下载。</p>
            <form method="post" action="/import">
            <label>显示名称（可选）</label><br>
            <input name="name" maxlength="40" placeholder="例如：星海音乐源" style="width:100%;padding:8px;margin:6px 0 18px">
            <label>脚本 URL 或内容</label><br>
            <textarea name="api" placeholder="https://.../latest.js 或完整 user-api 脚本"></textarea><br><br>
            <button type="submit">提交导入</button>
            </form></body></html>
        """.trimIndent()
    }
}
