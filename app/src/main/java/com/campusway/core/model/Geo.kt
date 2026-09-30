package com.campusway.core.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 地理计算工具：楼外导航的直线距离、步行耗时估算，以及坐标系换算。
 */
object Geo {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** 经纬度是否有效标定（0 视为未标定）。 */
    fun isValid(latitude: Double, longitude: Double): Boolean =
        latitude != 0.0 && longitude != 0.0 &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    /**
     * 两点间大圆距离（米），Haversine 公式。
     * 用于离线估算楼外步行距离，接通高德/腾讯时以服务商返回的路线距离为准。
     */
    fun distanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
        val c = 2 * asin(min(1.0, sqrt(a)))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * 按步行速度估算耗时（分钟），不足 1 分钟按 1 分钟计。
     * @param detourFactor 绕行系数，直线距离乘以它更接近实际步行距离。
     */
    fun walkingMinutes(distanceMeters: Double, speedMetersPerMinute: Double = 75.0, detourFactor: Double = 1.25): Int {
        if (distanceMeters <= 0) return 0
        val real = distanceMeters * detourFactor
        return ((real / speedMetersPerMinute).roundToInt()).coerceAtLeast(1)
    }

    /** 米数格式化为“350 米”“1.2 公里”。 */
    fun formatDistance(meters: Double): String = when {
        meters < 1000 -> "${meters.roundToInt()} 米"
        else -> "%.1f 公里".format(meters / 1000)
    }
}
