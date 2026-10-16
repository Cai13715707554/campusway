package com.campusway

/**
 * 构建信息。
 *
 * 刻意不用 BuildConfig：那需要打开 buildConfig 开关并为每个变体生成类，
 * 而这里只需要版本号这类静态信息，写成常量最省事，
 * 改了版本号同步改这里和 `app/build.gradle.kts` 即可（README 有说明）。
 *
 * 版本号还有一个实际用途：设置页「关于」会显示它。
 * 装的是不是最新构建，一眼就能确认，省得在"改了半天没生效"上绕弯。
 */
object BuildInfo {
    const val VERSION_NAME = "0.4.0"

    /** 版本变更记录，倒序，便于在关于页直接看到最近改了什么。 */
    val CHANGE_LOG: List<String> = listOf(
        "0.4.0 导入后可编辑课程表与时间表：改课程/排课，增删节次与时间点",
        "0.3.0 引导页可导入时间表，并自选要显示哪些午别/项目；全不显示则显示占位图",
        "0.2.0 导入表格式功课表；设置新增调试入口（口令 1101）与测试数据开关",
        "0.1.1 引导页实时预览字号与主题；导航 Key 改为必填",
        "0.1.0 首个版本：课表、校园导航、楼内导览、设置与新用户引导"
    )

    const val BUILD_TYPE = "debug/release（由构建变体决定）"
    const val MIN_SDK = "Android 8.0 (API 26)"
}
