package com.campusway

/**
 * 构建信息。
 *
 * 刻意不用 BuildConfig：那需要打开 buildConfig 开关并为每个变体生成类，
 * 而这里只需要版本号这类静态信息，写成常量最省事，
 * 改了版本号同步改这里和 `app/build.gradle.kts` 即可（README 有说明）。
 */
object BuildInfo {
    const val VERSION_NAME = "0.1.0"
    const val BUILD_TYPE = "debug/release（由构建变体决定）"
    const val MIN_SDK = "Android 8.0 (API 26)"
}
