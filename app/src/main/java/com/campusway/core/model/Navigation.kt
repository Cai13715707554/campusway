package com.campusway.core.model

import kotlinx.serialization.Serializable

/**
 * 真实世界坐标（WGS84 / GCJ-02 由来源决定）。
 */
@Serializable
data class NavCoord(
    val latitude: Double,
    val longitude: Double
) {
    val isValid: Boolean get() = Geo.isValid(latitude, longitude)
}

/**
 * 一次导航任务的目标，描述“去哪个建筑的哪个房间”。
 */
data class NavTarget(
    val buildingId: String,
    val roomId: String? = null,
    val label: String
)

/**
 * 楼外导航的一段路。
 */
data class OutdoorLeg(
    val instruction: String,
    val distanceMeters: Double,
    val minutes: Int,
    val roadName: String = ""
)

/**
 * 楼外导航结果。[isEstimated] 为 true 时表示没有联网服务，用的是直线距离离线估算。
 */
data class OutdoorRoute(
    val distanceMeters: Double,
    val minutes: Int,
    val legs: List<OutdoorLeg> = emptyList(),
    val providerId: String = NavProviders.OFFLINE,
    val isEstimated: Boolean = true
) {
    val summary: String
        get() = buildString {
            append(Geo.formatDistance(distanceMeters))
            append(" · 约 ")
            append(minutes)
            append(" 分钟")
            if (isEstimated) append("（离线估算）")
        }
}

/**
 * 完整引导：楼外段 + 楼内段，串起来就是“从校门到教室”。
 */
data class GuidancePlan(
    val target: NavTarget,
    val outdoor: OutdoorRoute? = null,
    val indoor: RoutePlanner.IndoorRoute? = null,
    val providerId: String = NavProviders.OFFLINE
) {
    val totalMinutes: Int get() = (outdoor?.minutes ?: 0) + (indoor?.totalMinutes ?: 0)
}

/**
 * 可用的导航服务商标识。
 */
object NavProviders {
    const val OFFLINE = "offline"
    const val AMAP = "amap"
    const val TENCENT = "tencent"

    val all = listOf(OFFLINE, AMAP, TENCENT)

    fun displayName(id: String): String = when (id) {
        AMAP -> "高德地图"
        TENCENT -> "腾讯地图"
        else -> "离线估算"
    }
}

/**
 * 联网地图数据的缓存状态，用于设置页展示与离线可用性判断。
 */
@Serializable
data class MapCacheInfo(
    val providerId: String = NavProviders.OFFLINE,
    /** 已缓存的区域名称，例如学校名。 */
    val areaName: String = "",
    val centerLatitude: Double = 0.0,
    val centerLongitude: Double = 0.0,
    /** 缓存半径（米）。 */
    val radiusMeters: Int = 0,
    /** 缓存时间戳（毫秒）。 */
    val cachedAt: Long = 0L,
    /** 已下载的瓦片数量。 */
    val tileCount: Int = 0,
    /** 占用的磁盘字节数。 */
    val sizeBytes: Long = 0L
) {
    val hasCache: Boolean get() = cachedAt > 0L

    /** 人类可读的占用大小。 */
    val sizeText: String
        get() = when {
            sizeBytes <= 0 -> "0 B"
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "%.0f KB".format(sizeBytes / 1024.0)
            else -> "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
        }
}
