package com.harmoniamusic.tv.data.update

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 某 ABI 的安装包。 */
data class UpdateAsset(val abi: String, val name: String, val url: String, val size: Long)

/** 一次更新的完整信息。 */
data class UpdateInfo(
    val currentVersion: String,
    val version: String,
    val tagName: String,
    val releaseUrl: String,
    val body: String,
    val asset: UpdateAsset,
    val supportedAbis: List<String>,
)

private data class RemoteInfo(
    val version: String,
    val tagName: String,
    val releaseUrl: String,
    val body: String,
    val assets: List<UpdateAsset>,
)

/**
 * 自更新检查（对应 RN `screens/TV/update.ts`）。
 * 先试 manifest 镜像，失败再退回 GitHub Release API。
 */
object UpdateChecker {
    private val MANIFEST_URLS = listOf(
        "https://raw.githubusercontent.com/kaosZL/HarmoniaMusic/master/publish/tv-version.json",
        "https://cdn.jsdelivr.net/gh/kaosZL/HarmoniaMusic@master/publish/tv-version.json",
    )
    private const val RELEASE_LATEST = "https://api.github.com/repos/kaosZL/HarmoniaMusic/releases/latest"
    private const val RELEASE_PREFIX = "https://github.com/kaosZL/HarmoniaMusic/releases/tag/"
    private val ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "universal")

    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val current = currentVersion(context)
        val remote = getRemoteInfo() ?: return@withContext null
        if (compareVer(current, remote.version) >= 0) return@withContext null
        val supported = Build.SUPPORTED_ABIS.filter { it in ABIS }.ifEmpty { listOf("universal") }
        val asset = pickAsset(remote.assets, supported) ?: return@withContext null
        UpdateInfo(current, remote.version, remote.tagName, remote.releaseUrl, remote.body, asset, supported)
    }

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0.0.0"

    private fun getRemoteInfo(): RemoteInfo? {
        for (u in MANIFEST_URLS) {
            val json = fetchJson(u, "application/json") ?: continue
            runCatching { normalizeManifest(json) }.getOrNull()?.let { return it }
        }
        val json = fetchJson(RELEASE_LATEST, "application/vnd.github+json") ?: return null
        return runCatching { normalizeRelease(json) }.getOrNull()
    }

    private fun normalizeManifest(json: JSONObject): RemoteInfo {
        val tag = json.optString("tagName").ifBlank { json.optString("tag_name") }
        val version = json.optString("version").ifBlank { normalizeVersion(tag) }
        val assets = normalizeAssets(json.optJSONArray("assets"))
        require(assets.isNotEmpty()) { "NO_APK_ASSET" }
        val releaseUrl = json.optString("releaseUrl")
            .ifBlank { json.optString("html_url") }
            .ifBlank { RELEASE_PREFIX + tag }
        return RemoteInfo(version, tag, releaseUrl, json.optString("body"), assets)
    }

    private fun normalizeRelease(json: JSONObject): RemoteInfo {
        val tag = json.optString("tag_name")
        val version = normalizeVersion(tag.ifBlank { json.optString("name") })
        val assets = normalizeAssets(json.optJSONArray("assets"))
        require(assets.isNotEmpty()) { "NO_APK_ASSET" }
        val releaseUrl = json.optString("html_url").ifBlank { RELEASE_PREFIX + tag }
        return RemoteInfo(version, tag, releaseUrl, json.optString("body"), assets)
    }

    private fun normalizeAssets(arr: JSONArray?): List<UpdateAsset> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name")
            val abi = o.optString("abi").lowercase().takeIf { it in ABIS } ?: abiFromName(name)
            val url = o.optString("url").ifBlank { o.optString("browser_download_url") }
            if (abi == null || url.isBlank()) null
            else UpdateAsset(abi, name, url, o.optLong("size", 0))
        }
    }

    private fun abiFromName(name: String): String? {
        val lower = name.lowercase()
        return ABIS.firstOrNull { lower.endsWith("-$it.apk") }
    }

    private fun pickAsset(assets: List<UpdateAsset>, supported: List<String>): UpdateAsset? {
        for (abi in supported) assets.firstOrNull { it.abi == abi }?.let { return it }
        return assets.firstOrNull { it.abi == "universal" } ?: assets.firstOrNull()
    }

    private fun normalizeVersion(source: String): String {
        val m = Regex("(\\d+(?:\\.\\d+){1,3})").find(source)
            ?: throw IllegalArgumentException("INVALID_VERSION")
        return m.value
    }

    private fun compareVer(a: String, b: String): Int {
        val pa = a.split(".").mapNotNull { it.toIntOrNull() }
        val pb = b.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun fetchJson(url: String, accept: String): JSONObject? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", accept)
            connectTimeout = 15000
            readTimeout = 15000
            connect()
        }
        if (conn.responseCode != 200) {
            conn.disconnect()
            return@runCatching null
        }
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        JSONObject(text)
    }.getOrNull()
}
