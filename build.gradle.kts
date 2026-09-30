// 顶层构建文件：只声明插件版本，具体配置在 app/build.gradle.kts
//
// 版本组合说明（2026-10 校验，与本地 Android Studio / Gradle 缓存一致）：
//   AGP        8.5.2   —— 需要 Gradle 8.7+、JDK 17
//   Kotlin     2.1.20  —— Compose 编译器插件随 Kotlin 版本发布
//   Compose    1.7.6（BOM 2024.12.01）
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20" apply false
}
