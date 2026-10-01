package com.campusway.core.map

import com.campusway.core.model.NavProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * 导航 Key 的校验结果。
 *
 * 为什么要区分「Key 无效」和「网络不可用」：
 * 引导页要求首次必须联网校验 Key，如果把没网报成 Key 错误，
 * 用户会以为自己的 Key 填错了，怎么改都过不去。
 */
sealed interface KeyCheckResult {
    /** Key 可用。 */
    data class Valid(val providerId: String) : KeyCheckResult

    /** Key 被服务商拒绝（未申请、类型不对、绑定不符等）。 */
    data class InvalidKey(val providerId: String, val message: String) : KeyCheckResult

    /** 网络问题或请求失败，无法判断 Key 是否有效。 */
    data class NetworkError(val providerId: String, val message: String) : KeyCheckResult

    val isSuccess: Boolean get() = this is Valid
}

/**
 * 在线校验高德 / 腾讯的 Key。
 *
 * 做法：调用各家最轻量的一个接口（逆地理编码），只看返回状态码。
 * 不能复用 [MapProvider.walkRoute]，因为那条路径在失败时会回退成离线估算，
 * 无法区分"Key 错"和"没网"。
 */
class MapKeyValidator(
    private val timeoutMs: Int = 8000
) {

    suspend fun check(providerId: String, key: String): KeyCheckResult = withContext(Dispatchers.IO) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            return@withContext KeyCheckResult.InvalidKey(providerId, "Key 不能为空")
        }

        val url = when (providerId) {
            NavProviders.AMAP -> amapUrl(trimmed)
            NavProviders.TENCENT -> tencentUrl(trimmed)
            else -> return@withContext KeyCheckResult.Valid(providerId)
        }

        val body = runCatching { httpGet(url) }
        val text = body.getOrNull()
            ?: return@withContext KeyCheckResult.NetworkError(
                providerId,
                body.exceptionOrNull()?.message ?: "网络请求失败"
            )

        when (providerId) {
            NavProviders.AMAP -> KeyCheckParsers.parseAmap(text)
            NavProviders.TENCENT -> KeyCheckParsers.parseTencent(text)
            else -> KeyCheckResult.Valid(providerId)
        }
    }

    private fun amapUrl(key: String): String =
        "https://restapi.amap.com/v3/geocode/regeo?key=$key&location=104.06610,30.65825"

    private fun tencentUrl(key: String): String =
        "https://apis.map.qq.com/ws/geocoder/v1/?key=$key&location=30.65825,104.06610"

    private fun httpGet(url: String): String =
        (URL(url).openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", "CampusWay/0.1 (Android)")
            try {
                val code = responseCode
                // 服务商把错误信息放在响应体里，所以非 2xx 也要读出来交给解析器判断
                val stream = if (code in 200..299) inputStream else errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (text.isBlank()) error("HTTP $code")
                text
            } finally {
                disconnect()
            }
        }
}

/**
 * 响应解析。
 *
 * 单独抽出来是因为这是纯字符串 -> 结果 的映射，可以直接单元测试，
 * 不需要真的联网。
 */
object KeyCheckParsers {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 高德逆地理编码响应。
     * 成功：`{"status":"1","infocode":"10000",...}`
     * 失败：`{"status":"0","info":"INVALID_USER_KEY","infocode":"10001"}`
     */
    fun parseAmap(body: String): KeyCheckResult {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return KeyCheckResult.NetworkError(NavProviders.AMAP, "返回内容无法解析")

        val status = root.str("status")
        val infoCode = root.str("infocode")
        val info = root.str("info").orEmpty()

        return when {
            status == "1" && (infoCode == null || infoCode == "10000") ->
                KeyCheckResult.Valid(NavProviders.AMAP)

            infoCode == "10001" -> KeyCheckResult.InvalidKey(
                NavProviders.AMAP,
                "Key 无效（INVALID_USER_KEY），请确认已开通 Web 服务"
            )

            infoCode == "10003" -> KeyCheckResult.InvalidKey(
                NavProviders.AMAP,
                "今日调用量已超限（DAILY_QUERY_OVER_LIMIT）"
            )

            infoCode == "10009" -> KeyCheckResult.InvalidKey(
                NavProviders.AMAP,
                "Key 与平台类型不匹配（USERKEY_PLAT_NOMATCH），请用 Web 服务 Key"
            )

            status == "0" -> KeyCheckResult.InvalidKey(
                NavProviders.AMAP,
                if (info.isBlank()) "Key 校验失败" else "Key 校验失败：$info"
            )

            else -> KeyCheckResult.InvalidKey(NavProviders.AMAP, "Key 校验失败：$info")
        }
    }

    /**
     * 腾讯逆地理编码响应。
     * 成功：`{"status":0,"message":"query ok",...}`
     * 失败：`{"status":111,"message":"签名失败"}`
     */
    fun parseTencent(body: String): KeyCheckResult {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return KeyCheckResult.NetworkError(NavProviders.TENCENT, "返回内容无法解析")

        val status = root.str("status")?.toIntOrNull()
            ?: root["status"]?.jsonPrimitive?.content?.toIntOrNull()
        val message = root.str("message").orEmpty()

        return when (status) {
            0 -> KeyCheckResult.Valid(NavProviders.TENCENT)
            110 -> KeyCheckResult.InvalidKey(
                NavProviders.TENCENT,
                "请求来源未被授权（110），请检查 Key 的授权配置"
            )
            111 -> KeyCheckResult.InvalidKey(
                NavProviders.TENCENT,
                "签名校验失败（111）"
            )
            112 -> KeyCheckResult.InvalidKey(
                NavProviders.TENCENT,
                "IP 未被授权（112）"
            )
            199 -> KeyCheckResult.InvalidKey(
                NavProviders.TENCENT,
                "Key 无效或未开通 WebService API（199）"
            )
            null -> KeyCheckResult.InvalidKey(
                NavProviders.TENCENT,
                if (message.isBlank()) "Key 校验失败" else message
            )
            else -> KeyCheckResult.InvalidKey(
                NavProviders.TENCENT,
                if (message.isBlank()) "Key 校验失败（status=$status）" else "Key 校验失败：$message"
            )
        }
    }

    private fun JsonObject.str(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()
}
