package com.harmoniamusic.tv.data.source.userapi

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.harmoniamusic.tv.data.net.HttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 基于 WebView 的 JS 引擎实现（零额外依赖，Android 必定可用，完整支持 async/await/Promise）。
 *
 * 与脚本的双向通信通过 addJavascriptInterface 暴露的 globalThis.nativeBridge 完成：
 *  - 脚本侧 lx.send / lx.request 调用 native 方法（上报 inited / 发起 HTTP）；
 *  - 宿主侧通过 evaluateJavascript 触发脚本 lx.on('request') 注册的 handler 并 await 其返回值。
 *
 * WebView 的创建与所有 evaluateJavascript 都切到主线程 Looper 执行，避免线程约束问题。
 */
class WebViewJsEngine(private val context: Context) : JsEngine {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webView: WebView? = null
    private val pendingActions = ConcurrentHashMap<Int, Continuation<String?>>()
    private val reqCounter = AtomicInteger(0)
    private var host: LxHost? = null
    private var initDeferred: CompletableDeferred<Boolean>? = null

    private val preloadJs: String = runCatching {
        context.applicationContext.assets
            .open("script/lx-host.js").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrDefault("")

    override suspend fun load(script: String, host: LxHost) {
        this.host = host
        host.attachEngine(this)
        val initDeferredLocal = CompletableDeferred<Boolean>()
        initDeferred = initDeferredLocal
        suspendCancellableCoroutine<Unit> { cont ->
            mainHandler.post {
                try {
                    val wv = WebView(context.applicationContext)
                    wv.settings.javaScriptEnabled = true
                    wv.settings.domStorageEnabled = true
                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            // 先注入宿主骨架（定义 globalThis.lx），再注入用户脚本
                            mainHandler.post {
                                view?.evaluateJavascript(preloadJs, null)
                                view?.evaluateJavascript(script, null)
                            }
                        }
                    }
                    wv.addJavascriptInterface(NativeBridge(this@WebViewJsEngine), "nativeBridge")
                    wv.loadUrl("about:blank")
                    webView = wv
                    cont.resume(Unit)
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }
        }
        // 等待脚本上报 inited；超时（12s）视作初始化失败但不阻塞
        withTimeoutOrNull(12_000) { initDeferredLocal.await() }
    }

    /** 触发脚本 request 事件并 await 其 handler 的返回值（JSON 字符串）。25s 超时防止脚本无响应时永久卡死。 */
    suspend fun callAction(requestInfo: Map<String, Any?>): String? {
        val reqId = reqCounter.incrementAndGet()
        return withTimeoutOrNull(25_000) {
            suspendCancellableCoroutine { cont ->
                pendingActions[reqId] = cont
                cont.invokeOnCancellation { pendingActions.remove(reqId) }
                mainHandler.post {
                    val wv = webView
                    if (wv == null) {
                        pendingActions.remove(reqId)
                        cont.resume(null)
                        return@post
                    }
                    val dataJson = JSONObject(requestInfo).toString()
                    val js = "lx.__emitRequest($reqId, ${JSONObject.quote(dataJson)})"
                    wv.evaluateJavascript(js, null)
                }
            }
        }
    }

    fun handleScriptSend(name: String, dataJson: String) {
        if (name == "inited" || name == "init") {
            runCatching { host?.captureSources(JSONObject(dataJson)) }
            initDeferred?.complete(true)
        }
        // updateAlert 等事件可在将来扩展
    }

    fun handleScriptRequest(reqId: Int, url: String, optionsJson: String) {
        scope.launch {
            val response = runCatching {
                val opts = JSONObject(optionsJson)
                val method = opts.optString("method", "GET").uppercase()
                val headers = opts.optJSONObject("headers").toMap()
                val body = opts.optString("body").takeIf { it.isNotBlank() }
                val text = when (method) {
                    "POST" -> HttpClient.postRaw(url, body ?: "", headers)
                    else -> HttpClient.getRaw(url, headers)
                }
                val bodyPart = runCatching { JSONObject(text) }.fold(
                    onSuccess = { it.toString() },
                    onFailure = { JSONObject.quote(text) },
                )
                "{\"statusCode\":200,\"statusMessage\":\"OK\",\"body\":$bodyPart}"
            }.getOrElse { e ->
                "{\"statusCode\":0,\"statusMessage\":${JSONObject.quote(e.message ?: "error")},\"body\":null}"
            }
            mainHandler.post {
                webView?.evaluateJavascript("lx.__resolveRequest($reqId, null, $response)", null)
            }
        }
    }

    fun handleRequestResult(reqId: Int, errJson: String?, resultJson: String?) {
        val cont = pendingActions.remove(reqId) ?: return
        cont.resume(resultJson)
    }

    override fun destroy() {
        scope.cancel()
        mainHandler.post {
            webView?.destroy()
            webView = null
        }
    }

    private fun JSONObject?.toMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        val keys = keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = optString(k)
        }
        return map
    }
}

/** 脚本与 Kotlin 之间的 native 桥（addJavascriptInterface 暴露为 globalThis.nativeBridge）。 */
class NativeBridge(private val engine: WebViewJsEngine) {
    @JavascriptInterface
    fun onScriptSend(name: String, dataJson: String) {
        engine.handleScriptSend(name, dataJson)
    }

    @JavascriptInterface
    fun request(reqId: Int, url: String, optionsJson: String) {
        engine.handleScriptRequest(reqId, url, optionsJson)
    }

    @JavascriptInterface
    fun onRequestResult(reqId: Int, errJson: String?, resultJson: String?) {
        engine.handleRequestResult(reqId, errJson, resultJson)
    }
}
