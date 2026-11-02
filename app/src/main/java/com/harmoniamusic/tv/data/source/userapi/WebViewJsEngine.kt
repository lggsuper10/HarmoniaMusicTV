package com.harmoniamusic.tv.data.source.userapi

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
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

    @Volatile
    private var scriptError: String? = null

    private val preloadJs: String = runCatching {
        context.applicationContext.assets
            .open("script/lx-host.js").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrDefault("")

    override suspend fun load(script: String, host: LxHost) {
        this.host = host
        host.attachEngine(this)
        scriptError = null
        val needsTranspile = MODERN_JS_MARKERS.containsMatchIn(script)
        val babelSource = if (needsTranspile) babelJs() else ""
        if (needsTranspile) {
            Log.i(TAG, "检测到现代 JS 语法，启用兼容转译（${script.length} bytes）")
            if (babelSource.isBlank()) Log.e(TAG, "Babel 兼容转译器加载失败")
        }
        val initDeferredLocal = CompletableDeferred<Boolean>()
        initDeferred = initDeferredLocal
        suspendCancellableCoroutine<Unit> { cont ->
            mainHandler.post {
                try {
                    val wv = WebView(context.applicationContext)
                    wv.settings.javaScriptEnabled = true
                    wv.settings.domStorageEnabled = true
                    wv.webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            val text = "${message.message()} (${message.sourceId()}:${message.lineNumber()})"
                            when (message.messageLevel()) {
                                ConsoleMessage.MessageLevel.ERROR -> Log.e(TAG, text)
                                ConsoleMessage.MessageLevel.WARNING -> Log.w(TAG, text)
                                else -> Unit
                            }
                            return true
                        }
                    }
                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            // 先注入宿主骨架，再按需加载 Babel 转译，最后执行脚本。
                            mainHandler.post { view?.let { injectScript(it, script, babelSource) } }
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
        val ready = withTimeoutOrNull(12_000) { initDeferredLocal.await() }
        if (ready != true) {
            val reason = scriptError ?: "超时或未上报 inited"
            Log.e(TAG, "脚本初始化未完成: $reason")
        }
    }

    private fun injectScript(view: WebView, script: String, babelSource: String) {
        view.evaluateJavascript(preloadJs) {
            // 先探测 WebView 是否原生支持可选链(?.)与空值合并(??)。
            // Android 9 的 WebView 多数已通过 Play 更新到 Chrome 80+，原生即支持这些语法；
            // 此时直接 eval 即可。强制 babel 转译反而会把本可运行的脚本转成坏代码
            // （表现为 Promise[...] is not a function / Cannot read property 'body' of null）。
            // 仅在不支持的旧 WebView 上才用 babel 兜底。
            val probe = "(function(){try{var a;var ok=(function(){return a?.b})()===void 0;return ok&&((0,null)??true)===true}catch(e){return false}})()"
            view.evaluateJavascript(probe) { result ->
                val modern = result == "true"
                if (!modern && babelSource.isNotBlank()) {
                    Log.i(TAG, "WebView 不支持可选链，启用 babel 转译兜底")
                    view.evaluateJavascript(babelSource) {
                        view.evaluateJavascript(transpiledScript(script), null)
                    }
                } else {
                    if (!modern) {
                        Log.w(TAG, "WebView 不支持可选链且无 babel 兜底，脚本可能无法运行")
                    }
                    view.evaluateJavascript(directScript(script), null)
                }
            }
        }
    }

    private fun directScript(script: String): String {
        val source = JSONObject.quote(script)
        return "(function(){try{(0,eval)($source);}catch(e){" +
            "try{if(window.nativeBridge)window.nativeBridge.onScriptError(String((e&&e.stack)||e));}catch(_){}}})()"
    }

    private fun transpiledScript(script: String): String {
        val source = JSONObject.quote(script)
        return """
            (function(){
              try {
                if (typeof Babel === 'undefined') throw new Error('Babel 兼容转译器未加载');
                var output = Babel.transform($source, {
                  presets: [['env', { targets: { chrome: '66' }, bugfixes: true }]],
                  sourceType: 'script',
                  compact: false
                }).code;
                (0, eval)(output);
              } catch (e) {
                try {
                  if (window.nativeBridge) window.nativeBridge.onScriptError(String((e && e.stack) || e));
                } catch (_) {}
              }
            })();
        """.trimIndent()
    }

    private fun babelJs(): String {
        babelCache?.let { return it }
        synchronized(BABEL_LOCK) {
            babelCache?.let { return it }
            val text = runCatching {
                context.applicationContext.assets
                    .open(BABEL_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrDefault("")
            babelCache = text
            return text
        }
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
        Log.d(TAG, "handleScriptSend name=$name data=${dataJson.take(160)}")
        if (name == "inited" || name == "init") {
            runCatching { host?.captureSources(JSONObject(dataJson)) }
                .onFailure { Log.e(TAG, "解析 inited 音源列表失败", it) }
            initDeferred?.complete(true)
        }
        // updateAlert 等事件可在将来扩展
    }

    fun handleScriptError(message: String) {
        val reason = message.trim().take(4_000)
        scriptError = reason
        Log.e(TAG, "脚本执行失败: $reason")
        initDeferred?.complete(false)
    }

    fun loadError(): String? = scriptError

    fun handleScriptRequest(reqId: Int, url: String, optionsJson: String) {
        scope.launch {
            val opts = JSONObject(optionsJson)
            val method = opts.optString("method", "GET").uppercase()
            val response = runCatching {
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
            val status = runCatching { JSONObject(response).optInt("statusCode", -1) }.getOrDefault(-1)
            Log.d(TAG, "request $method ${url.take(60)} -> status=$status")
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

    private companion object {
        const val TAG = "WebViewJsEngine"
        const val BABEL_ASSET = "script/babel-standalone-7.26.4.min.js"
        val MODERN_JS_MARKERS = Regex("""\?\.|\?\?|\?\?=|\|\|=|&&=""")
        val BABEL_LOCK = Any()

        @Volatile
        var babelCache: String? = null
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

    @JavascriptInterface
    fun onScriptError(message: String) {
        engine.handleScriptError(message)
    }
}
