package com.campusway.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 导航服务商选择的规则测试。
 *
 * 引导页要求"必须填一个 Key 才能进入"，并且保存的服务商必须和填的 Key 对得上——
 * 否则会出现"选了高德但高德 Key 为空"，楼外导航直接哑掉却看不出原因。
 */
class NavProvidersTest {

    @Test
    fun `只填高德时用高德`() {
        assertEquals(
            NavProviders.AMAP,
            NavProviders.resolve(amapKey = "ak", tencentKey = "", preferred = NavProviders.TENCENT)
        )
    }

    @Test
    fun `只填腾讯时用腾讯`() {
        assertEquals(
            NavProviders.TENCENT,
            NavProviders.resolve(amapKey = "", tencentKey = "tk", preferred = NavProviders.AMAP)
        )
    }

    @Test
    fun `两个都填时用用户选的那个`() {
        assertEquals(
            NavProviders.TENCENT,
            NavProviders.resolve(amapKey = "ak", tencentKey = "tk", preferred = NavProviders.TENCENT)
        )
        assertEquals(
            NavProviders.AMAP,
            NavProviders.resolve(amapKey = "ak", tencentKey = "tk", preferred = NavProviders.AMAP)
        )
    }

    @Test
    fun `两个都没填时保持偏好值`() {
        // 这种状态在界面上进不去（按钮被禁用），但纯函数也不能返回空值
        assertEquals(
            NavProviders.AMAP,
            NavProviders.resolve(amapKey = "", tencentKey = "", preferred = NavProviders.AMAP)
        )
    }

    @Test
    fun `空白字符不算填了 Key`() {
        assertEquals(
            NavProviders.TENCENT,
            NavProviders.resolve(amapKey = "   ", tencentKey = "tk", preferred = NavProviders.AMAP)
        )
        assertEquals(
            NavProviders.AMAP,
            NavProviders.resolve(amapKey = "ak", tencentKey = "\n\t", preferred = NavProviders.TENCENT)
        )
    }

    @Test
    fun `界面可选服务商里没有离线估算`() {
        assertEquals(listOf(NavProviders.AMAP, NavProviders.TENCENT), NavProviders.selectable)
    }

    @Test
    fun `默认值里两个 Key 都为空`() {
        assertEquals("", AppSettings.Default.amapKey)
        assertEquals("", AppSettings.Default.tencentKey)
        assertEquals(NavProviders.AMAP, AppSettings.Default.navProvider)
    }
}
