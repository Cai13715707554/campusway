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
 *
 * 档位差距刻意拉大：最初只差 10%，在实际机型上几乎看不出变化，
 * 用户会以为"调了没生效"。现在相邻档位至少差 20%，
 * 并在界面上直接显示百分比，点一下就能确认生效。
 */
@Serializable
enum class FontScale(val value: Float, val displayName: String) {
    SMALL(0.85f, "小"),
    NORMAL(1.0f, "标准"),
    LARGE(1.2f, "大"),
    EXTRA_LARGE(1.45f, "特大");

    /** 展示用的百分比，例如“120%”。 */
    val percentText: String get() = "${Math.round(value * 100)}%"

    /** 选择项上的完整标签，例如“大 · 120%”。 */
    val optionLabel: String get() = "$displayName · $percentText"
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
 * 楼外导航的服务商在高德与腾讯之间二选一（引导页强制至少填一个 Key）。
 * 「离线估算」不再作为可选项出现在界面上，它只是没网时**自动降级**的兜底行为，
 * 这样用户不用理解这个概念，也不会误选成"离线模式"导致楼外导航永远不联网。
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

    /** 楼外导航服务商，取值见 [NavProviders]，只在高德与腾讯之间选择。 */
    val navProvider: String = NavProviders.AMAP,
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
    /**
     * 是否使用内置测试数据（示例学校与示例课表）。
     *
     * 只在设置的调试入口里开关，**默认关闭**。
     * 关闭时应用只用用户自己导入的数据；打开后忽略用户数据，
     * 直接加载内置示例，方便开发与演示。
     */
    val useTestData: Boolean = false,
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

    /** 至少填写了一个可用的 Key（引导页的准入条件）。 */
    val hasAnyKey: Boolean get() = amapKey.isNotBlank() || tencentKey.isNotBlank()

    /** 当前服务商是否已配置可用的 Key。 */
    val providerConfigured: Boolean
        get() = when (navProvider) {
            NavProviders.AMAP -> amapKey.isNotBlank()
            NavProviders.TENCENT -> tencentKey.isNotBlank()
            else -> hasAnyKey
        }

    /**
     * 楼外导航的当前能力说明。
     *
     * 措辞刻意强调"没网会自动降级"，让用户明白不需要手动切到离线模式。
     */
    val offlineCapabilityText: String
        get() = when {
            !providerConfigured ->
                "未填写地图 Key，楼外导航暂时只能用离线估算（可在设置里补填）"

            !schoolHasGeo ->
                "已填写 Key，但还没标定学校坐标，楼外导航暂时按离线估算"

            else ->
                "联网时用 ${NavProviders.displayName(navProvider)} 规划路线并缓存学校周边；" +
                    "没网时自动降级为离线估算，课表与楼内导览不受影响"
        }

    companion object {
        val Default = AppSettings()
    }
}
