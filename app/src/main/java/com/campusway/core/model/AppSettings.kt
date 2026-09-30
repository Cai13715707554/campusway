package com.campusway.core.model

import kotlinx.serialization.Serializable

/**
 * 主题模式：跟随系统 / 强制浅色 / 强制深色。
 */
@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    val displayName: String
        get() = when (this) {
            SYSTEM -> "跟随系统"
            LIGHT -> "浅色"
            DARK -> "深色"
        }
}

/**
 * 字体缩放档位（鸿蒙风 UI 里字号整体缩放）。
 */
@Serializable
enum class FontScale(val value: Float, val displayName: String) {
    SMALL(0.9f, "小"),
    NORMAL(1.0f, "标准"),
    LARGE(1.1f, "大"),
    EXTRA_LARGE(1.25f, "特大")
}

/**
 * 课程表格线显示方式：按节次或按时间。
 */
@Serializable
enum class TimetableAxis {
    PERIOD,
    TIME;

    val displayName: String
        get() = if (this == PERIOD) "按节次" else "按时间"
}

/**
 * 应用设置。
 *
 * 所有会影响离线可用性的配置都在这里，满足“主要功能可离线运行”：
 * 地图密钥、学校坐标属于可选增强，不配置也能正常使用课表与平面图导览。
 */
@Serializable
data class AppSettings(
    /** 主题模式。 */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 是否启用鸿蒙风格的动效，性能弱的设备可关闭。 */
    val animationsEnabled: Boolean = true,
    /** 字体缩放。 */
    val fontScale: FontScale = FontScale.NORMAL,
    /** 课程表按节次还是按时间显示。 */
    val timetableAxis: TimetableAxis = TimetableAxis.PERIOD,
    /** 课间是否显示“即将上课”提醒。 */
    val showUpcomingReminder: Boolean = true,
    /** 提前多少分钟提醒即将上课。 */
    val reminderLeadMinutes: Int = 15,

    /** 楼外导航服务商：offline / amap / tencent。 */
    val navProvider: String = NavProviders.OFFLINE,
    /** 高德 Web 服务 Key。 */
    val amapKey: String = "",
    /** 腾讯地图 Key。 */
    val tencentKey: String = "",
    /** 是否在联网时自动缓存学校周边地图，供离线查看。 */
    val cacheMapWhenOnline: Boolean = true,
    /** 缓存半径（米）。 */
    val mapCacheRadiusMeters: Int = 2000,
    /** 是否允许使用系统定位作为楼外导航起点。 */
    val useLocationAsOrigin: Boolean = false,

    /** 是否已完成新用户引导。 */
    val onboardingCompleted: Boolean = false,
    /** 用户称呼，用于首页问候。 */
    val userName: String = "",
    /** 学校名称。 */
    val schoolName: String = "",
    /** 学校中心经纬度。 */
    val schoolLatitude: Double = 0.0,
    val schoolLongitude: Double = 0.0,
    /** 是否显示 3D 校园模型（半透明叠加在平面图上）。 */
    val show3dModel: Boolean = false,
    /** 3D 模型透明度 0..1。 */
    val model3dOpacity: Float = 0.45f
) {
    val schoolHasGeo: Boolean get() = Geo.isValid(schoolLatitude, schoolLongitude)

    /** 当前服务商是否已配置可用的 Key。 */
    val providerConfigured: Boolean
        get() = when (navProvider) {
            NavProviders.AMAP -> amapKey.isNotBlank()
            NavProviders.TENCENT -> tencentKey.isNotBlank()
            else -> true
        }

    /** 面向用户的离线可用性说明。 */
    val offlineCapabilityText: String
        get() = when {
            navProvider == NavProviders.OFFLINE -> "离线模式：课表、校园平面图与楼内导览全部可用"
            !providerConfigured -> "未填写 ${NavProviders.displayName(navProvider)} Key，楼外导航将使用离线估算"
            !schoolHasGeo -> "已填写 Key，但未标定学校坐标，仍使用离线估算"
            else -> "联网时使用 ${NavProviders.displayName(navProvider)}，并缓存学校周边地图"
        }

    companion object {
        val Default = AppSettings()
    }
}
