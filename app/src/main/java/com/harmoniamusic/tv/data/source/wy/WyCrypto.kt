package com.harmoniamusic.tv.data.source.wy

import android.util.Base64
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云音源加密，忠实移植自 RN `utils/musicSdk/wy/utils/crypto.js`。
 *
 * 关键点（来自原生桥 `nativeModules/crypto.ts`）：
 *  - `AES_MODE.CBC_128_PKCS7Padding` = "AES/CBC/PKCS7Padding"（Java 即 PKCS5Padding）
 *  - `AES_MODE.ECB_128_NoPadding`   = "AES"（Java 默认 ECB + PKCS5Padding，名字里的 NoPadding 是误导）
 *  - 各 key 是 ASCII 字符串，btoa 即其 ASCII 字节。
 */
object WyCrypto {
    private val PRESET_KEY = "0CoJUm6Qyw8W8jud".toAscii()   // 16 bytes
    private val IV = "0102030405060708".toAscii()           // 16 bytes
    private val EAPI_KEY = "e82ckenh8dichen8".toAscii()     // 16 bytes
    private const val PUBLIC_KEY_B64 =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB"

    private fun String.toAscii(): ByteArray = toByteArray(Charsets.US_ASCII)

    private fun b64Encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun b64Decode(str: String): ByteArray =
        Base64.decode(str, Base64.NO_WRAP)

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun aesCbc(input: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(IV))
        return cipher.doFinal(input)
    }

    private fun aesEcb(input: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(input)
    }

    private fun rsaNoPaddingHex(data: ByteArray): String {
        val keySpec = X509EncodedKeySpec(b64Decode(PUBLIC_KEY_B64))
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        // 前置零填充到 128 字节（仿 JS Buffer.concat([alloc(128-len), buf])）
        val padded = ByteArray(128 - data.size) + data
        val out = cipher.doFinal(padded)
        return out.joinToString("") { "%02x".format(it) }
    }

    private fun randomSecretKey(): String {
        // 等价于 JS String(Math.random()).substring(2, 18)，取 16 位
        val s = kotlin.random.Random.nextDouble().toString()
        return if (s.length >= 18) s.substring(2, 18) else s
    }

    /** weapi 加密：返回 (params, encSecKey)。 */
    fun weapi(obj: Map<String, Any>): Pair<String, String> {
        val text = org.json.JSONObject(obj).toString()
        val secretKey = randomSecretKey()

        // inner = base64( AES_CBC( base64(text) ) )
        val innerB64 = b64Encode(text.toByteArray(Charsets.UTF_8))
        val innerEnc = aesCbc(b64Decode(innerB64), PRESET_KEY)
        val innerEncB64 = b64Encode(innerEnc)

        // outer = base64( AES_CBC( ascii(innerEncB64) ) )
        val outerInput = innerEncB64.toAscii()
        val outerEnc = aesCbc(outerInput, secretKey.toAscii())
        val params = b64Encode(outerEnc)

        val encSecKey = rsaNoPaddingHex(secretKey.toAscii().reversedArray())
        return params to encSecKey
    }

    /** eapi 加密：返回 { params: "<HEX大写>" }。 */
    fun eapi(url: String, obj: Map<String, Any>): Map<String, String> {
        val text = org.json.JSONObject(obj).toString()
        val message = "nobody${url}use${text}md5forencrypt"
        val digest = md5Hex(message)
        val data = "${url}-36cd479b6b5-${text}-36cd479b6b5-${digest}"

        // eapi 内部对 data 做 base64 再 AES；base64 抵消，故直接对 data 字节做 AES/ECB
        val enc = aesEcb(data.toByteArray(Charsets.UTF_8), EAPI_KEY)
        val params = enc.joinToString("") { "%02x".format(it) }.uppercase()
        return mapOf("params" to params)
    }
}
