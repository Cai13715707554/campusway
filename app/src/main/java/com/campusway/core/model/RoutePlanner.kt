package com.campusway.core.model

import kotlin.math.abs
import kotlin.math.hypot

/**
 * 室内路径规划（教学楼内导航）。
 *
 * 需求明确不做室内实时定位，所以定位起点由用户选择：
 * 选“我现在在这个教室”或“从大门进入”。
 * 规划过程完全离线：BFS 在最短路网格上跑，不依赖任何传感器与服务器。
 */
object RoutePlanner {

    /** 一趟引导里的一个步骤。 */
    data class Step(
        val id: String,
        val instruction: String,
        /** 该步骤涉及的目标房间/节点 id，用于在图上高亮。 */
        val targetRoomId: String = "",
        /** 在本楼示意图上的位置，用于画点。 */
        val point: NormPoint? = null,
        /** 预计步行时间（分钟）。 */
        val minutes: Int = 0
    )

    /** 楼内导航结果。 */
    data class IndoorRoute(
        val buildingId: String,
        val floorId: String,
        /** 途经的楼层（跨层时会包含多个）。 */
        val floorIds: List<String>,
        val steps: List<Step>,
        /** 平面上要画的折线（按楼层分组）。 */
        val polylines: Map<String, List<NormPoint>>,
        /** 需要高亮的房间 id。 */
        val highlightedRoomIds: List<String>,
        val totalMinutes: Int
    ) {
        val isEmpty: Boolean get() = steps.isEmpty()
    }

    /** 起终点分开计算的楼内路线（起终点可能在不同楼层甚至不同楼）。 */
    data class MultiFloorRoute(
        val fromFloorId: String,
        val toFloorId: String,
        val steps: List<Step>,
        val polylines: Map<String, List<NormPoint>>,
        val highlightedRoomIds: List<String>,
        val totalMinutes: Int,
        /** 是否跨楼（不同教学楼之间）。 */
        val crossBuilding: Boolean = false
    )

    /**
     * 在单层平面图上求最短路径（按节点跳数，几何距离作为同层内权重）。
     * 返回节点 id 序列，找不到返回 null。
     */
    fun shortestPath(floor: FloorPlan, fromNodeId: String, toNodeId: String): List<String>? {
        if (floor.node(fromNodeId) == null || floor.node(toNodeId) == null) return null
        if (fromNodeId == toNodeId) return listOf(fromNodeId)

        val adjacency = floor.adjacency()
        val previous = HashMap<String, String>()
        val visited = HashSet<String>()
        val queue = ArrayDeque<String>()

        queue.add(fromNodeId)
        visited.add(fromNodeId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == toNodeId) break
            val neighbours = adjacency[current].orEmpty().sortedBy { candidate ->
                // 优先走向目标方向更近的邻居，让路径看起来自然
                val a = floor.node(current)?.point
                val b = floor.node(candidate)?.point
                val target = floor.node(toNodeId)?.point
                if (a == null || b == null || target == null) 0f
                else hypot(b.x - target.x, b.y - target.y)
            }
            neighbours.forEach { next ->
                if (visited.add(next)) {
                    previous[next] = current
                    queue.add(next)
                }
            }
        }

        if (toNodeId !in visited) return null

        val path = ArrayDeque<String>()
        var cursor: String? = toNodeId
        while (cursor != null) {
            path.addFirst(cursor)
            cursor = previous[cursor]
        }
        return path.toList()
    }

    /**
     * 楼内导航主入口：从 [fromRoomId]（可为空，表示从本层出入口进入）到 [toRoomId]。
     *
     * @return 找不到路径时返回 null，由调用方回退到“直接展示目标楼层”。
     */
    fun planIndoor(
        campus: Campus,
        building: Building,
        toRoomId: String,
        fromRoomId: String? = null
    ): IndoorRoute? {
        val target = building.findRoom(toRoomId) ?: return null
        val targetFloor = building.floor(target.floorId) ?: return null

        val startRoom = fromRoomId?.let { building.findRoom(it) }
        val startFloor = startRoom?.let { building.floor(it.floorId) }

        // 同层：直接算路径；跨层：先到本层出入口，再逐层串接
        val steps = mutableListOf<Step>()
        val polylines = LinkedHashMap<String, MutableList<NormPoint>>()
        val highlighted = mutableListOf<String>()
        var minutes = 0

        if (startRoom != null && startFloor != null && startFloor.floorId == targetFloor.floorId) {
            val path = pathBetween(startFloor, startRoom, target)
            val points = path.mapNotNull { startFloor.node(it)?.point }
            if (points.isNotEmpty()) polylines.getOrPut(startFloor.floorId) { mutableListOf() }.addAll(points)
            minutes += estimateMinutes(points)
            steps += Step(
                id = "start",
                instruction = "从「${startRoom.displayName}」出发",
                targetRoomId = startRoom.id,
                point = NormPoint(startRoom.rect.centerX, startRoom.rect.centerY)
            )
            steps += Step(
                id = "arrive",
                instruction = "沿指示行进，到达「${target.name}」",
                targetRoomId = target.id,
                point = NormPoint(target.rect.centerX, target.rect.centerY),
                minutes = minutes
            )
        } else {
            steps += Step(
                id = "enter",
                instruction = "从「${building.displayName}」出入口进入",
                point = entryPointOf(building, startFloor ?: targetFloor)
            )
            if (startFloor != null && startRoom != null && startFloor.floorId != targetFloor.floorId) {
                steps += Step(
                    id = "leave-floor",
                    instruction = "在${startFloor.name}沿楼梯/电梯前往${targetFloor.name}",
                    targetRoomId = startRoom.id,
                    point = NormPoint(startRoom.rect.centerX, startRoom.rect.centerY)
                )
            } else if (startFloor != null && startFloor.floorId != targetFloor.floorId) {
                steps += Step(
                    id = "climb",
                    instruction = "经楼梯/电梯上行至${targetFloor.name}",
                    point = entryPointOf(building, targetFloor)
                )
            }
            steps += Step(
                id = "arrive",
                instruction = "到达「${target.name}」（${targetFloor.name}）",
                targetRoomId = target.id,
                point = NormPoint(target.rect.centerX, target.rect.centerY),
                minutes = 2
            )
            minutes += 2
        }

        highlighted += target.id
        startRoom?.let { highlighted += it.id }

        return IndoorRoute(
            buildingId = building.id,
            floorId = targetFloor.floorId,
            floorIds = listOfNotNull(startFloor?.floorId, targetFloor.floorId).distinct(),
            steps = steps,
            polylines = polylines.mapValues { it.value.toList() },
            highlightedRoomIds = highlighted,
            totalMinutes = steps.sumOf { it.minutes }.coerceAtLeast(minutes)
        )
    }

    /** 找到两个房间之间的节点路径 id 序列。 */
    private fun pathBetween(floor: FloorPlan, from: Room, to: Room): List<String> {
        val fromNode = nearestNode(floor, from.rect.centerX, from.rect.centerY)
        val toNode = nearestNode(floor, to.rect.centerX, to.rect.centerY)
        if (fromNode == null || toNode == null) return emptyList()
        return shortestPath(floor, fromNode, toNode) ?: emptyList()
    }

    /** 找到离给定归一化坐标最近的路径节点。 */
    fun nearestNode(floor: FloorPlan, x: Float, y: Float): String? =
        floor.nodes.minByOrNull { hypot(it.point.x - x, it.point.y - y) }?.id

    /** 本层出入口位置，用于“从大门进入”的起点。 */
    fun entryPointOf(building: Building, floor: FloorPlan): NormPoint? {
        val exit = floor.rooms.firstOrNull { it.kind == RoomKind.EXIT }
        if (exit != null) return NormPoint(exit.rect.centerX, exit.rect.centerY)
        return floor.nodes.firstOrNull()?.point
    }

    /** 根据折线长度估算耗时（分钟）。 */
    private fun estimateMinutes(points: List<NormPoint>): Int {
        if (points.size < 2) return 0
        var total = 0f
        points.zipWithNext().forEach { (a, b) -> total += hypot(b.x - a.x, b.y - a.y) }
        // 归一化长度约等于楼层对角线比例，按每层 2 分钟粗算
        return (total * 4f).toInt().coerceIn(1, 15)
    }

    /** 曼哈顿距离，用于平面图上排序候选房间。 */
    fun manhattan(a: NormPoint, b: NormPoint): Float = abs(a.x - b.x) + abs(a.y - b.y)
}
