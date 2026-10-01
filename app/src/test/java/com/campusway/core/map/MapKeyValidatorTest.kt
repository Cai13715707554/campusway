package com.campusway.core.map

import com.campusway.core.model.NavProviders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导航 Key 校验结果解析测试。
 *
 * 这是纯字符串到结果的映射，不需要联网，因此可以完整覆盖：
 * 引导页要求「首次必须联网校验 Key」，如果这里把成功/失败判反，
 * 用户就会被卡在引导页出不去，或者带着无效 Key 进应用。
 */
class MapKeyValidatorTest {

    // ---------------------------------------------------------- 高德

    @Test
    fun `高德返回成功时判定为有效`() {
        val body = """{"status":"1","info":"OK","infocode":"10000","regeocode":{}}"""
        val result = KeyCheckParsers.parseAmap(body)
        assertTrue(result is KeyCheckResult.Valid)
        assertEquals(NavProviders.AMAP, (result as KeyCheckResult.Valid).providerId)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `高德返回 INVALID_USER_KEY 判定为 Key 无效`() {
        val body = """{"status":"0","info":"INVALID_USER_KEY","infocode":"10001"}"""
        val result = KeyCheckParsers.parseAmap(body)
        assertTrue(result is KeyCheckResult.InvalidKey)
        assertTrue((result as KeyCheckResult.InvalidKey).message.contains("INVALID_USER_KEY"))
        assertFalse(result.isSuccess)
    }

    @Test
    fun `高德 Key 与平台类型不匹配时给出可读提示`() {
        val body = """{"status":"0","info":"USERKEY_PLAT_NOMATCH","infocode":"10009"}"""
        val result = KeyCheckParsers.parseAmap(body)
        assertTrue(result is KeyCheckResult.InvalidKey)
        // 这条最容易踩：用户拿了 Android 端 Key 来填 Web 服务
        assertTrue((result as KeyCheckResult.InvalidKey).message.contains("Web 服务"))
    }

    @Test
    fun `高德超出调用量也会被识别`() {
        val body = """{"status":"0","info":"DAILY_QUERY_OVER_LIMIT","infocode":"10003"}"""
        assertTrue(KeyCheckParsers.parseAmap(body) is KeyCheckResult.InvalidKey)
    }

    @Test
    fun `高德返回未知错误时仍给出提示而不是崩溃`() {
        val body = """{"status":"0","info":"SOMETHING_ELSE","infocode":"99999"}"""
        val result = KeyCheckParsers.parseAmap(body)
        assertTrue(result is KeyCheckResult.InvalidKey)
        assertTrue((result as KeyCheckResult.InvalidKey).message.contains("SOMETHING_ELSE"))
    }

    // ---------------------------------------------------------- 腾讯

    @Test
    fun `腾讯返回 status 0 判定为有效`() {
        val body = """{"status":0,"message":"query ok","result":{}}"""
        val result = KeyCheckParsers.parseTencent(body)
        assertTrue(result is KeyCheckResult.Valid)
        assertEquals(NavProviders.TENCENT, (result as KeyCheckResult.Valid).providerId)
    }

    @Test
    fun `腾讯未开通 WebService 判定为 Key 无效`() {
        val body = """{"status":199,"message":"此key未开启WebServiceAPI功能"}"""
        val result = KeyCheckParsers.parseTencent(body)
        assertTrue(result is KeyCheckResult.InvalidKey)
        assertTrue((result as KeyCheckResult.InvalidKey).message.contains("199"))
    }

    @Test
    fun `腾讯签名失败与来源未授权都能识别`() {
        assertTrue(
            KeyCheckParsers.parseTencent("""{"status":111,"message":"签名失败"}""")
                is KeyCheckResult.InvalidKey
        )
        assertTrue(
            KeyCheckParsers.parseTencent("""{"status":110,"message":"请求来源未被授权"}""")
                is KeyCheckResult.InvalidKey
        )
    }

    @Test
    fun `腾讯缺少 status 字段时按失败处理`() {
        val result = KeyCheckParsers.parseTencent("""{"message":"oops"}""")
        assertTrue(result is KeyCheckResult.InvalidKey)
    }

    // ---------------------------------------------------------- 异常输入

    @Test
    fun `返回内容不是 JSON 时判定为网络异常而不是 Key 错误`() {
        // 这一点很关键：把网络问题误报成 Key 错误，用户会一直改 Key 却怎么也过不去
        val amap = KeyCheckParsers.parseAmap("<html>502 Bad Gateway</html>")
        assertTrue(amap is KeyCheckResult.NetworkError)
        assertFalse(amap.isSuccess)

        val tencent = KeyCheckParsers.parseTencent("")
        assertTrue(tencent is KeyCheckResult.NetworkError)
    }

    @Test
    fun `空响应体不会被当成校验通过`() {
        assertFalse(KeyCheckParsers.parseAmap("{}").isSuccess)
        assertFalse(KeyCheckParsers.parseTencent("{}").isSuccess)
    }

    @Test
    fun `未知服务商视为无需校验`() {
        val result = KeyCheckResult.Valid(NavProviders.OFFLINE)
        assertTrue(result.isSuccess)
    }
}
