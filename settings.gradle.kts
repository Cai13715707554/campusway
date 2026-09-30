pluginManagement {
    repositories {
        // 优先本地缓存，保证离线／弱网也能构建
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        // 高德/腾讯地图 SDK 的官方仓库（需要联网时才会用到）
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "campusway"
include(":app")
