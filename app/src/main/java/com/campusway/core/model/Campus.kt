package com.campusway.core.model

import kotlinx.serialization.Serializable

/**
 * 归一化坐标点（0..1）。
 *
 * 教学楼内示意图与校园平面图都用归一化坐标描述几何形状：
 * 好处是与屏幕分辨率、缩放级别解耦，同一份数据既能离线矢量渲染，
 * 也能叠加在导入的位图底图上。
 */
@Serializable
data class NormPoint(val x: Float, val y: Float)

/**
 * 归一化矩形。
 */
@Serializable
data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /** 缩放到给定画布尺寸后的像素矩形（左、上、宽、高）。 */
    fun toPixels(canvasWidth: Float, canvasHeight: Float): FloatArray = floatArrayOf(
        left * canvasWidth,
        top * canvasHeight,
        width * canvasWidth,
        height * canvasHeight
    )

    /** 以自身中心缩放 [factor] 倍，用于点击命中区域外扩。 */
    fun inflate(factor: Float): NormRect {
        val dx = width * (factor - 1f) / 2f
        val dy = height * (factor - 1f) / 2f
        return NormRect(left - dx, top - dy, right + dx, bottom + dy)
    }

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    companion object {
        val Empty = NormRect(0f, 0f, 0f, 0f)

        /** 由中心的点与宽高构造。 */
        fun centered(cx: Float, cy: Float, width: Float, height: Float): NormRect =
            NormRect(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f)
    }
}

/**
 * 楼内示意图上的一块区域：教室、走廊、楼梯、卫生间、出口……
 */
@Serializable
data class Room(
    /** 全局唯一 id，例如 `b1-f3-r302`，课表与搜索都引用它。 */
    val id: String,
    /** 房间号/名称，例如“302 计算机网络实训室”。 */
    val name: String,
    /** 房间简称，画在平面图上，例如“302”。 */
    val shortName: String = "",
    val floorId: String = "",
    val buildingId: String = "",
    /** 平面图上的位置。 */
    val rect: NormRect = NormRect.Empty,
    /** 可选的多边形轮廓（用于不规则房间），为空时用 [rect]。 */
    val polygon: List<NormPoint> = emptyList(),
    val kind: RoomKind = RoomKind.CLASSROOM,
    /** 该房间所属的楼层出入口 id，用于楼内路径串联。 */
    val nearestExitId: String = "",
    val note: String = ""
) {
    /** 用于展示的短名称。 */
    val displayName: String get() = shortName.ifBlank { name }

    /** 轮廓点：优先多边形，否则退化为矩形的四个角。 */
    fun outline(): List<NormPoint> =
        polygon.ifEmpty {
            listOf(
                NormPoint(rect.left, rect.top),
                NormPoint(rect.right, rect.top),
                NormPoint(rect.right, rect.bottom),
                NormPoint(rect.left, rect.bottom)
            )
        }
}

@Serializable
enum class RoomKind {
    CLASSROOM,
    LAB,
    OFFICE,
    RESTROOM,
    STAIRS,
    ELEVATOR,
    EXIT,
    CORRIDOR,
    OTHER
}

/**
 * 楼内路径节点与连线，用于在示意图上画出前往目标教室的路线。
 */
@Serializable
data class FloorPathNode(
    val id: String,
    val point: NormPoint
)

@Serializable
data class FloorPathEdge(
    val fromId: String,
    val toId: String
)

/**
 * 楼层示意图数据。
 */
@Serializable
data class FloorPlan(
    val buildingId: String,
    val floorId: String,
    /** 楼层号，例如 1、2、-1（地下）。 */
    val level: Int,
    /** 楼层名称，例如“1 层”“实训层”。 */
    val name: String,
    /** 平面图宽高比（宽/高），矢量渲染与位图铺满时都要用。 */
    val aspectRatio: Float = 1.5f,
    /** 可选底图，放在 assets 或 drawable 里的相对路径，为空则纯矢量渲染。 */
    val baseImage: String = "",
    val rooms: List<Room> = emptyList(),
    val nodes: List<FloorPathNode> = emptyList(),
    val edges: List<FloorPathEdge> = emptyList()
) {
    private val roomById: Map<String, Room> by lazy { rooms.associateBy { it.id } }
    private val nodeById: Map<String, FloorPathNode> by lazy { nodes.associateBy { it.id } }

    fun room(id: String): Room? = roomById[id]
    fun node(id: String): FloorPathNode? = nodeById[id]

    /** 命中测试：返回包含该归一化点的房间（面积最小的优先，避免走廊盖住教室）。 */
    fun roomAt(x: Float, y: Float): Room? =
        rooms.filter { room ->
            val rect = room.rect
            rect.contains(x, y)
        }.minByOrNull { it.rect.width * it.rect.height }

    /** 邻接表，用于最短路计算。 */
    fun adjacency(): Map<String, List<String>> {
        val map = LinkedHashMap<String, MutableList<String>>()
        nodes.forEach { map.getOrPut(it.id) { mutableListOf() } }
        edges.forEach { e ->
            map.getOrPut(e.fromId) { mutableListOf() }.add(e.toId)
            map.getOrPut(e.toId) { mutableListOf() }.add(e.fromId)
        }
        return map
    }
}

/**
 * 教学楼。
 */
@Serializable
data class Building(
    val id: String,
    val name: String,
    /** 在校园平面图上的位置。 */
    val rect: NormRect = NormRect.Empty,
    val shortName: String = "",
    /** 楼层数，仅用于展示。 */
    val floorCount: Int = 1,
    /** 真实经纬度，接入高德/腾讯导航时使用；为 0 表示未标定。 */
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    /** 可选 3D 模型文件（.glb/.gltf）在 assets 中的路径。 */
    val model3dPath: String = "",
    /** 楼层列表，按 level 排序。 */
    val floors: List<FloorPlan> = emptyList()
) {
    val displayName: String get() = shortName.ifBlank { name }

    /** 是否标定了真实经纬度，可参与楼外导航。 */
    val hasGeo: Boolean get() = latitude != 0.0 && longitude != 0.0

    fun floor(floorId: String): FloorPlan? = floors.firstOrNull { it.floorId == floorId }

    /** 在所有楼层里找房间。 */
    fun findRoom(roomId: String): Room? =
        floors.firstNotNullOfOrNull { it.room(roomId) }

    /** 默认展示的楼层：优先 1 层，否则最低层。 */
    fun defaultFloor(): FloorPlan? =
        floors.firstOrNull { it.level == 1 } ?: floors.minByOrNull { it.level }
}

/**
 * 校园平面图上的一个功能点（校门、食堂、宿舍……）。
 */
@Serializable
data class CampusLandmark(
    val id: String,
    val name: String,
    val rect: NormRect = NormRect.Empty,
    val kind: LandmarkKind = LandmarkKind.OTHER,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
) {
    val hasGeo: Boolean get() = latitude != 0.0 && longitude != 0.0
}

@Serializable
enum class LandmarkKind {
    GATE,
    CANTEEN,
    DORMITORY,
    LIBRARY,
    GYM,
    OFFICE,
    PARKING,
    OTHER
}

/**
 * 校园：包含平面图与所有教学楼。
 */
@Serializable
data class Campus(
    val name: String = "我的学校",
    /** 校园平面图宽高比。 */
    val aspectRatio: Float = 1.4f,
    /** 可选平面图底图。 */
    val baseImage: String = "",
    val landmarks: List<CampusLandmark> = emptyList(),
    val buildings: List<Building> = emptyList(),
    /** 校园中心经纬度，联网缓存周边地图时用。 */
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
) {
    val hasGeo: Boolean get() = latitude != 0.0 && longitude != 0.0

    fun building(id: String): Building? = buildings.firstOrNull { it.id == id }

    /** 全校园范围内按房间 id 查找，返回（楼，层，房间）。 */
    fun locateRoom(roomId: String): RoomLocation? {
        buildings.forEach { building ->
            building.floors.forEach { floor ->
                floor.room(roomId)?.let { room ->
                    return RoomLocation(building, floor, room)
                }
            }
        }
        return null
    }

    /** 按关键字搜索教学楼与教室。 */
    fun search(keyword: String): List<SearchHit> {
        val key = keyword.trim()
        if (key.isEmpty()) return emptyList()
        val hits = mutableListOf<SearchHit>()
        buildings.forEach { building ->
            if (building.name.contains(key, true) || building.shortName.contains(key, true)) {
                hits += SearchHit.BuildingHit(building)
            }
            building.floors.forEach { floor ->
                floor.rooms.forEach { room ->
                    if (room.kind == RoomKind.CORRIDOR || room.kind == RoomKind.STAIRS) return@forEach
                    if (room.name.contains(key, true) || room.shortName.contains(key, true) ||
                        room.id.contains(key, true)
                    ) {
                        hits += SearchHit.RoomHit(building, floor, room)
                    }
                }
            }
        }
        landmarks.forEach { landmark ->
            if (landmark.name.contains(key, true)) hits += SearchHit.LandmarkHit(landmark)
        }
        return hits
    }
}

/** 房间在校园中的完整定位。 */
data class RoomLocation(
    val building: Building,
    val floor: FloorPlan,
    val room: Room
)

/**
 * 关于路径节点接入的说明（给后续维护者）：
 *
 * RoutePlanner 用「离房间中心最近的路径节点」把房间接到走廊网格上，
 * 所以房间与走廊节点的距离会直接影响画出来的路线观感。
 * 示例数据里最下排的卫生间/楼梯距走廊约 0.3~0.45（归一化坐标），
 * 线条会略微绕一点，但引导与绘制都正确。
 * 若想让路线更贴合，只需把 SampleData 中对应楼层的 corridorNodes 的 y 值
 * 往那一排房间方向挪一点，不需要改任何算法。
 */

/** 搜索结果。 */
sealed interface SearchHit {
    data class BuildingHit(val building: Building) : SearchHit
    data class RoomHit(val building: Building, val floor: FloorPlan, val room: Room) : SearchHit
    data class LandmarkHit(val landmark: CampusLandmark) : SearchHit
}

/** 搜索结果标题，用于列表展示。 */
val SearchHit.title: String
    get() = when (this) {
        is SearchHit.BuildingHit -> building.name
        is SearchHit.RoomHit -> room.name
        is SearchHit.LandmarkHit -> landmark.name
    }

/** 搜索结果副标题。 */
val SearchHit.subtitle: String
    get() = when (this) {
        is SearchHit.BuildingHit -> "教学楼"
        is SearchHit.RoomHit -> "${building.displayName} · ${floor.name}"
        is SearchHit.LandmarkHit -> "校园地点"
    }
