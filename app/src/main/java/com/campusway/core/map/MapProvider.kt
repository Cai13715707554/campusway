package com.campusway.core.map

import android.content.Context
import com.campusway.core.data.JsonConfig
import com.campusway.core.model.Geo
import com.campusway.core.model.MapCacheInfo
import com.campusway.core.model.NavCoord
import com.campusway.core.model.NavProviders
import com.campusway.core.model.OutdoorLeg
import com.campusway.core.model.OutdoorRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 楼外导航服务商。
 *
 * 设计要点：**离线优先**。所有实现都必须能在没有网络、没有 Key 的情况下
 * 返回一个可用的结果（哪怕是直线估算），界面据此降级展示，
 * 而不是弹错误框让用户走不下去。
 */
interface MapProvider {

    val id: String

    val displayName: String

    /** 是否具备调用在线服务的能力（Key 已配置）。 */
    fun isConfigured(): Boolean

    /**
     * 规划楼外步行路线。
     * @return 永远不会返回 null；离线或失败时返回 [OutdoorRoute.isEstimated] = true 的估算结果。
     */
    suspend fun walkRoute(from: NavCoord, to: NavCoord): OutdoorRoute

    /** 缓存学校周边地图数据，供离线查看。返回缓存结果描述。 */
    suspend fun cacheAround(center: NavCoord, radiusMeters: Int, areaName: String): MapCacheInfo
}

/**
 * 离线服务商：用 Haversine 直线距离 + 绕行系数估算步行路线。
 *
 * 这是“主要功能可离线运行”的底线实现，也是其他服务商失败时的兜底。
 */
class OfflineMapProvider : MapProvider {

    override val id: String = NavProviders.OFFLINE
    override val displayName: String = "离线估算"

    override fun isConfigured(): Boolean = true

    override suspend fun walkRoute(from: NavCoord, to: NavCoord): OutdoorRoute = withContext(Dispatchers.Default) {
        if (!from.isValid || !to.isValid) {
            return@withContext OutdoorRoute(
                distanceMeters = 0.0,
                minutes = 0,
                legs = listOf(OutdoorLeg("未标定坐标，无法估算楼外距离", 0.0, 0)),
                providerId = id,
                isEstimated = true
            )
        }
        val straight = Geo.distanceMeters(from.latitude, from.longitude, to.latitude, to.longitude)
        val walkDistance = straight * DETOUR_FACTOR
        OutdoorRoute(
            distanceMeters = walkDistance,
            minutes = Geo.walkingMinutes(straight, detourFactor = DETOUR_FACTOR),
            legs = listOf(
                OutdoorLeg(
                    instruction = "沿校园道路向目标步行（离线直线估算）",
                    distanceMeters = walkDistance,
                    minutes = Geo.walkingMinutes(straight, detourFactor = DETOUR_FACTOR)
                )
            ),
            providerId = id,
            isEstimated = true
        )
    }

    override suspend fun cacheAround(center: NavCoord, radiusMeters: Int, areaName: String): MapCacheInfo =
        MapCacheInfo(providerId = id, areaName = areaName, radiusMeters = radiusMeters)

    private companion object {
        /** 校园内部道路通常不是直线，经验绕行系数取 1.25。 */
        const val DETOUR_FACTOR = 1.25
    }
}

/**
 * 在线地图服务的公共部分：HTTP GET + JSON 解析 + 本地缓存。
 *
 * 只依赖 JDK 的 [HttpURLConnection]，不引入 retrofit/okhttp 之类的第三方库，
 * 既减小体积，也避免依赖不可用导致的离线构建问题。
 */
abstract class BaseOnlineMapProvider(
    protected val context: Context,
    protected val cache: MapCacheStore,
    protected val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) : MapProvider {

    /** 构造请求 URL。 */
    protected abstract fun routeUrl(from: NavCoord, to: NavCoord): String?

    /** 把服务商响应解析为统一结果；返回 null 表示需要回退到离线估算。 */
    protected abstract fun parseRoute(body: String): OutdoorRoute?

    override suspend fun walkRoute(from: NavCoord, to: NavCoord): OutdoorRoute = withContext(Dispatchers.IO) {
        val url = routeUrl(from, to)
        if (url == null || !isConfigured()) return@withContext fallback(from, to)

        // 先看缓存：同一组起终点短时间内重复查询直接命中
        cache.readRoute(url)?.let { cached ->
            parseRoute(cached)?.let { return@withContext it }
        }

        val body = runCatching { httpGet(url) }.getOrNull()
        if (body.isNullOrBlank()) return@withContext fallback(from, to)

        val parsed = parseRoute(body)
        if (parsed == null) fallback(from, to) else {
            cache.writeRoute(url, body)
            parsed
        }
    }

    override suspend fun cacheAround(center: NavCoord, radiusMeters: Int, areaName: String): MapCacheInfo =
        withContext(Dispatchers.IO) {
            if (!isConfigured() || !center.isValid) {
                return@withContext MapCacheInfo(providerId = id, areaName = areaName)
            }
            // 把中心点周边按网格切分，逐个抓静态图瓦片落盘。
            // 这样即使之后完全离线，也能看到已经缓存过的学校周边。
            val tiles = TileGrid.tilesFor(center, radiusMeters, zoom = TILE_ZOOM)
            var ok = 0
            var bytes = 0L
            tiles.forEach { tile ->
                val url = tileUrl(tile) ?: return@forEach
                val data = runCatching { httpGetBytes(url) }.getOrNull() ?: return@forEach
                if (data.isNotEmpty()) {
                    cache.writeTile(id, tile.key, data)
                    ok++
                    bytes += data.size
                }
            }
            val info = MapCacheInfo(
                providerId = id,
                areaName = areaName,
                centerLatitude = center.latitude,
                centerLongitude = center.longitude,
                radiusMeters = radiusMeters,
                cachedAt = System.currentTimeMillis(),
                tileCount = ok,
                sizeBytes = bytes
            )
            cache.writeInfo(info)
            info
        }

    /** 静态图瓦片请求地址，由子类实现。 */
    protected abstract fun tileUrl(tile: TileGrid.Tile): String?

    /** 在线不可用时统一回退到离线估算。 */
    protected fun fallback(from: NavCoord, to: NavCoord): OutdoorRoute {
        val straight = if (from.isValid && to.isValid) {
            Geo.distanceMeters(from.latitude, from.longitude, to.latitude, to.longitude)
        } else 0.0
        val minutes = Geo.walkingMinutes(straight)
        return OutdoorRoute(
            distanceMeters = straight * 1.25,
            minutes = minutes,
            legs = listOf(OutdoorLeg("在线服务不可用，按直线距离估算", straight * 1.25, minutes)),
            providerId = NavProviders.OFFLINE,
            isEstimated = true
        )
    }

    /**
     * 发起 GET 请求并读取响应文本。
     *
     * 注意：HttpURLConnection 是抽象类，不能用构造器创建，
     * 必须由 `URL.openConnection()` 转型得到，所以这里没有 `HttpURLConnection(...)` 包裹。
     */
    protected fun httpGet(url: String): String =
        (URL(url).openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "CampusWay/0.1 (Android)")
            try {
                val code = responseCode
                if (code !in 200..299) error("HTTP $code")
                inputStream.bufferedReader().use { it.readText() }
            } finally {
                disconnect()
            }
        }

    /** 发起 GET 请求并读取响应字节（静态图瓦片用）。 */
    protected fun httpGetBytes(url: String): ByteArray =
        (URL(url).openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            try {
                val code = responseCode
                if (code !in 200..299) error("HTTP $code")
                inputStream.use { it.readBytes() }
            } finally {
                disconnect()
            }
        }

    protected fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** 从 JSON 对象里安全取字符串。 */
    protected fun JsonObject.str(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()

    /** 从 JSON 对象里安全取数字。 */
    protected fun JsonObject.num(key: String): Double? =
        runCatching { this[key]?.jsonPrimitive?.content?.toDouble() }.getOrNull()

    /** 从 JSON 对象里安全取数组。 */
    protected fun JsonObject.arr(key: String): JsonArray? =
        runCatching { this[key]?.jsonArray }.getOrNull()

    protected companion object {
        const val TIMEOUT_MS = 8000
        /** 缓存瓦片用的缩放级别，13 级在校园尺度下比较合适。 */
        const val TILE_ZOOM = 13
        const val HTTP_NOT_MODIFIED = 304
    }
}

/**
 * 高德地图 Web 服务实现。
 *
 * 使用「步行路径规划 2.0」接口，返回 JSON，逐段解析成引导文案。
 * 未配置 Key 或请求失败时自动回退到离线估算。
 */
class AmapMapProvider(
    context: Context,
    cache: MapCacheStore,
    private val keyProvider: () -> String
) : BaseOnlineMapProvider(context, cache) {

    override val id: String = NavProviders.AMAP
    override val displayName: String = "高德地图"

    override fun isConfigured(): Boolean = keyProvider().isNotBlank()

    override fun routeUrl(from: NavCoord, to: NavCoord): String? {
        val key = keyProvider()
        if (key.isBlank()) return null
        val origin = "${from.longitude},${from.latitude}"
        val destination = "${to.longitude},${to.latitude}"
        return "https://restapi.amap.com/v5/direction/walking" +
            "?key=$key&origin=$origin&destination=$destination&show_fields=cost,navi,polyline"
    }

    override fun parseRoute(body: String): OutdoorRoute? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        // status=1 表示成功
        if (root.str("status") != "1") return null
        val route = root["route"]?.jsonObject ?: return null
        val paths = route.arr("paths") ?: return null
        val first = paths.firstOrNull()?.jsonObject ?: return null

        val distance = first.num("distance") ?: 0.0
        val durationSeconds = first.num("duration") ?: (distance / 1.25)
        val minutes = (durationSeconds / 60.0).toInt().coerceAtLeast(1)

        val legs = mutableListOf<OutdoorLeg>()
        first.arr("steps")?.forEach { stepElement ->
            val step = stepElement.jsonObject
            legs += OutdoorLeg(
                instruction = step.str("instruction").orEmpty(),
                distanceMeters = step.num("step_distance") ?: step.num("distance") ?: 0.0,
                minutes = 0,
                roadName = step.str("road_name").orEmpty()
            )
        }
        if (legs.isEmpty()) legs += OutdoorLeg("按高德规划路线步行", distance, minutes)

        OutdoorRoute(
            distanceMeters = distance,
            minutes = minutes,
            legs = legs,
            providerId = id,
            isEstimated = false
        )
    }.getOrNull()

    override fun tileUrl(tile: TileGrid.Tile): String? {
        val key = keyProvider()
        if (key.isBlank()) return null
        // 静态地图接口，按瓦片中心点取一张小图作为离线底图
        return "https://restapi.amap.com/v3/staticmap" +
            "?key=$key&location=${tile.centerLongitude},${tile.centerLatitude}" +
            "&zoom=$TILE_ZOOM&size=512*512&scale=1"
    }
}

/**
 * 腾讯地图 Web 服务实现。
 */
class TencentMapProvider(
    context: Context,
    cache: MapCacheStore,
    private val keyProvider: () -> String
) : BaseOnlineMapProvider(context, cache) {

    override val id: String = NavProviders.TENCENT
    override val displayName: String = "腾讯地图"

    override fun isConfigured(): Boolean = keyProvider().isNotBlank()

    override fun routeUrl(from: NavCoord, to: NavCoord): String? {
        val key = keyProvider()
        if (key.isBlank()) return null
        val f = "${from.latitude},${from.longitude}"
        val t = "${to.latitude},${to.longitude}"
        return "https://apis.map.qq.com/ws/direction/v1/walking/" +
            "?key=$key&from=$f&to=$t&output=json"
    }

    override fun parseRoute(body: String): OutdoorRoute? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        if (root.num("status")?.toInt() != 0) return null
        val result = root["result"]?.jsonObject ?: return null
        val routes = result.arr("routes") ?: return null
        val first = routes.firstOrNull()?.jsonObject ?: return null

        val distance = first.num("distance") ?: 0.0
        val minutes = ((first.num("duration") ?: 0.0) / 60.0).toInt().coerceAtLeast(1)

        val legs = mutableListOf<OutdoorLeg>()
        first.arr("steps")?.forEach { stepElement ->
            val step = stepElement.jsonObject
            legs += OutdoorLeg(
                instruction = step.str("instruction").orEmpty(),
                distanceMeters = step.num("distance") ?: 0.0,
                minutes = 0,
                roadName = step.str("road_name").orEmpty()
            )
        }
        if (legs.isEmpty()) legs += OutdoorLeg("按腾讯地图规划路线步行", distance, minutes)

        OutdoorRoute(
            distanceMeters = distance,
            minutes = minutes,
            legs = legs,
            providerId = id,
            isEstimated = false
        )
    }.getOrNull()

    override fun tileUrl(tile: TileGrid.Tile): String? {
        val key = keyProvider()
        if (key.isBlank()) return null
        // 腾讯静态图接口
        return "https://apis.map.qq.com/ws/staticmap/v2/" +
            "?key=$key&center=${tile.centerLatitude},${tile.centerLongitude}" +
            "&zoom=$TILE_ZOOM&size=512*512"
    }
}

/**
 * 把一片区域切成规则网格，用于批量抓取静态图瓦片。
 */
object TileGrid {

    data class Tile(
        val x: Int,
        val y: Int,
        val centerLatitude: Double,
        val centerLongitude: Double
    ) {
        val key: String get() = "${x}_$y"
    }

    /**
     * 以 [center] 为中心、[radiusMeters] 为半径，按 [zoom] 级瓦片的尺度生成网格。
     * 最多返回 [maxTiles] 张，避免用户误设超大半径把流量打满。
     */
    fun tilesFor(center: NavCoord, radiusMeters: Int, zoom: Int, maxTiles: Int = 64): List<Tile> {
        if (!center.isValid || radiusMeters <= 0) return emptyList()

        // Web 墨卡托：zoom 级下每像素代表的地面距离
        val metersPerPixel = 156543.03392 * kotlin.math.cos(Math.toRadians(center.latitude)) /
            Math.pow(2.0, zoom.toDouble())
        val tileMeters = metersPerPixel * TILE_PIXELS
        if (tileMeters <= 0) return emptyList()

        val radiusTiles = kotlin.math.ceil(radiusMeters / tileMeters).toInt().coerceIn(1, 8)
        val latPerTile = tileMeters / 111_320.0
        val lonPerTile = latPerTile / kotlin.math.cos(Math.toRadians(center.latitude)).coerceAtLeast(1e-6)

        val tiles = mutableListOf<Tile>()
        for (dy in -radiusTiles..radiusTiles) {
            for (dx in -radiusTiles..radiusTiles) {
                tiles += Tile(
                    x = dx,
                    y = dy,
                    centerLatitude = center.latitude + dy * latPerTile,
                    centerLongitude = center.longitude + dx * lonPerTile
                )
                if (tiles.size >= maxTiles) return tiles
            }
        }
        return tiles
    }

    /** 单张瓦片的像素边长，与静态图接口请求的 size 保持一致。 */
    const val TILE_PIXELS = 512
}

/**
 * 联网地图数据的本地缓存。
 *
 * 两块内容：
 *  - 路线响应缓存（纯文本，按 URL 哈希命名，有有效期）；
 *  - 静态图瓦片（二进制），离线时可直接读取展示。
 */
class MapCacheStore(context: Context) {

    private val root: File = File(context.applicationContext.filesDir, "map_cache").apply { mkdirs() }
    private val routeDir: File = File(root, "routes").apply { mkdirs() }
    private val tileDir: File = File(root, "tiles").apply { mkdirs() }

    private val json = JsonConfig.instance

    /** 读取缓存的路线响应，超过 [ROUTE_TTL_MS] 视为过期。 */
    fun readRoute(url: String): String? {
        val file = File(routeDir, hashName(url))
        if (!file.exists()) return null
        val fresh = System.currentTimeMillis() - file.lastModified() < ROUTE_TTL_MS
        return if (fresh) runCatching { file.readText() }.getOrNull() else null
    }

    fun writeRoute(url: String, body: String) {
        runCatching { File(routeDir, hashName(url)).writeText(body) }
    }

    fun readTile(providerId: String, key: String): File? {
        val file = File(File(tileDir, providerId), "$key.png")
        return if (file.exists()) file else null
    }

    fun writeTile(providerId: String, key: String, data: ByteArray) {
        val dir = File(tileDir, providerId).apply { mkdirs() }
        runCatching { File(dir, "$key.png").writeBytes(data) }
    }

    /** 缓存信息文件，供设置页展示“已缓存多少”。 */
    fun readInfo(): MapCacheInfo? {
        val file = File(root, "info.json")
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(MapCacheInfo.serializer(), file.readText()) }.getOrNull()
    }

    fun writeInfo(info: MapCacheInfo) {
        runCatching { File(root, "info.json").writeText(json.encodeToString(MapCacheInfo.serializer(), info)) }
    }

    /** 统计缓存占用。 */
    fun stats(): Pair<Int, Long> {
        // 注意：File 没有接收 File 的构造重载，所以直接用 tileDir 而不是 File(tileDir)
        val tiles = tileDir.walkTopDown().filter { it.isFile }.toList()
        return tiles.size to tiles.sumOf { it.length() }
    }

    fun clear() {
        runCatching { root.deleteRecursively() }
        root.mkdirs()
        routeDir.mkdirs()
        tileDir.mkdirs()
    }

    private fun hashName(url: String): String =
        url.hashCode().toUInt().toString(16) + "_" + url.length

    private companion object {
        /** 路线缓存有效期：1 天。校园内路线变化很小，够用。 */
        const val ROUTE_TTL_MS = 24L * 60 * 60 * 1000
    }
}

/**
 * 服务商工厂：根据设置里的 providerId 返回实现。
 * 任何情况下都至少返回 [OfflineMapProvider]。
 */
object MapProviderFactory {

    fun create(
        providerId: String,
        context: Context,
        cache: MapCacheStore,
        amapKey: () -> String,
        tencentKey: () -> String
    ): MapProvider = when (providerId) {
        NavProviders.AMAP -> AmapMapProvider(context, cache, amapKey)
        NavProviders.TENCENT -> TencentMapProvider(context, cache, tencentKey)
        else -> OfflineMapProvider()
    }
}
