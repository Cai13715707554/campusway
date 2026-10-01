package com.campusway.app

/**
 * 调试入口的准入判断。
 *
 * 单独抽出来是为了能直接单元测试（不需要 Android 环境）。
 *
 * 说明：口令写在代码里，反编译即可看到，因此它**不是安全边界**，
 * 只是防止普通用户误触调试面板。面板里只有"测试数据开关"和几个数据操作，
 * 不涉及账号或隐私数据。
 */
object DebugAccess {

    /** 调试口令。 */
    const val PASSWORD = "1101"

    /** 口令是否正确（忽略首尾空白）。 */
    fun check(input: String): Boolean = input.trim() == PASSWORD
}
