package com.campusway.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调试入口口令测试。
 *
 * 这类判断最容易写反（把错误口令放进去），而后果是调试面板被普通用户误开、
 * 或者开发者自己进不去，所以单独锁一下。
 */
class DebugAccessTest {

    @Test
    fun `正确口令通过`() {
        assertTrue(DebugAccess.check("1101"))
    }

    @Test
    fun `首尾空白不影响判断`() {
        // 手机输入法很容易带上空格
        assertTrue(DebugAccess.check(" 1101 "))
        assertTrue(DebugAccess.check("1101\n"))
    }

    @Test
    fun `错误口令不通过`() {
        assertFalse(DebugAccess.check(""))
        assertFalse(DebugAccess.check("1100"))
        assertFalse(DebugAccess.check("11011"))
        assertFalse(DebugAccess.check("1234"))
        assertFalse(DebugAccess.check("abcd"))
    }

    @Test
    fun `中间有空格不通过`() {
        assertFalse(DebugAccess.check("11 01"))
    }

    @Test
    fun `口令常量与需求一致`() {
        // 需求指定 1101，改动这里要同步需求
        assertTrue(DebugAccess.PASSWORD == "1101")
    }
}
