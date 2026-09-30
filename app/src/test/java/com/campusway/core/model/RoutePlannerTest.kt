package com.campusway.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 楼内路径规划测试。
 *
 * 需求明确“不做室内实时定位”，所以起点完全由用户/课表决定；
 * 这里验证最短路、起终点缺失、跨层等边界不会崩，且引导步骤是完整的。
 */
class RoutePlannerTest {

    /** 造一层结构：走廊一条线，两端各一个房间。 */
    private fun simpleFloor(floorId: String = "f1", level: Int = 1): FloorPlan = FloorPlan(
        buildingId = "b1",
        floorId = floorId,
        level = level,
        name = "$level 层",
        rooms = listOf(
            Room("r-a", "A 教室", "A", floorId, "b1", NormRect(0.05f, 0.10f, 0.25f, 0.30f)),
            Room("r-b", "B 教室", "B", floorId, "b1", NormRect(0.75f, 0.10f, 0.95f, 0.30f)),
            Room("gate", "大门", "门", floorId, "b1", NormRect(0.40f, 0.85f, 0.60f, 0.98f), RoomKind.EXIT)
        ),
        nodes = listOf(
            FloorPathNode("n1", NormPoint(0.15f, 0.45f)),
            FloorPathNode("n2", NormPoint(0.50f, 0.45f)),
            FloorPathNode("n3", NormPoint(0.85f, 0.45f))
        ),
        edges = listOf(
            FloorPathEdge("n1", "n2"),
            FloorPathEdge("n2", "n3")
        )
    )

    private fun simpleCampus(floors: List<FloorPlan>): Campus = Campus(
        name = "测试学校",
        buildings = listOf(
            Building(
                id = "b1",
                name = "1 号楼",
                floors = floors
            )
        )
    )

    @Test
    fun `最短路沿着走廊节点连接`() {
        val floor = simpleFloor()
        val path = RoutePlanner.shortestPath(floor, "n1", "n3")
        assertEquals(listOf("n1", "n2", "n3"), path)
    }

    @Test
    fun `同一起终点返回单元素路径`() {
        assertEquals(listOf("n2"), RoutePlanner.shortestPath(simpleFloor(), "n2", "n2"))
    }

    @Test
    fun `不存在的节点返回空`() {
        assertNull(RoutePlanner.shortestPath(simpleFloor(), "n1", "不存在"))
        assertNull(RoutePlanner.shortestPath(simpleFloor(), "不存在", "n1"))
    }

    @Test
    fun `不连通的图找不到路径`() {
        val floor = simpleFloor().copy(
            nodes = simpleFloor().nodes + FloorPathNode("isolated", NormPoint(0.5f, 0.95f)),
            edges = simpleFloor().edges
        )
        assertNull(RoutePlanner.shortestPath(floor, "n1", "isolated"))
    }

    @Test
    fun `能找到离目标最近的节点`() {
        val floor = simpleFloor()
        assertEquals("n1", RoutePlanner.nearestNode(floor, 0.05f, 0.5f))
        assertEquals("n3", RoutePlanner.nearestNode(floor, 0.99f, 0.5f))
    }

    @Test
    fun `空楼层不会崩`() {
        val empty = FloorPlan(buildingId = "b", floorId = "f", level = 1, name = "1 层")
        assertNull(RoutePlanner.nearestNode(empty, 0.5f, 0.5f))
        assertNull(RoutePlanner.shortestPath(empty, "a", "b"))
    }

    @Test
    fun `同层两间教室能规划出路径并高亮目标`() {
        val floor = simpleFloor()
        val campus = simpleCampus(listOf(floor))
        val building = campus.buildings.first()

        val route = RoutePlanner.planIndoor(campus, building, toRoomId = "r-b", fromRoomId = "r-a")
        assertNotNull(route)
        route!!
        assertEquals("b1", route.buildingId)
        assertEquals("f1", route.floorId)
        assertTrue(route.highlightedRoomIds.contains("r-b"))
        assertTrue(route.highlightedRoomIds.contains("r-a"))
        assertTrue(route.steps.isNotEmpty())
        // 折线应该有两段以上，能在图上画出来
        assertTrue(route.polylines["f1"].orEmpty().size >= 2)
    }

    @Test
    fun `目标教室不存在时返回空而不是抛异常`() {
        val campus = simpleCampus(listOf(simpleFloor()))
        assertNull(RoutePlanner.planIndoor(campus, campus.buildings.first(), toRoomId = "不存在"))
    }

    @Test
    fun `起点教室不在本楼时按从大门进入处理`() {
        val campus = simpleCampus(listOf(simpleFloor()))
        val route = RoutePlanner.planIndoor(
            campus = campus,
            building = campus.buildings.first(),
            toRoomId = "r-a",
            fromRoomId = "别的楼的教室"
        )
        assertNotNull(route)
        assertTrue(route!!.steps.first().instruction.contains("出入口"))
    }

    @Test
    fun `跨楼层会给出上楼指引`() {
        val campus = simpleCampus(listOf(simpleFloor("f1", 1), simpleFloor("f2", 2)))
        val building = campus.buildings.first()
        val route = RoutePlanner.planIndoor(campus, building, toRoomId = "r-b", fromRoomId = null)
        assertNotNull(route)
        val texts = route!!.steps.joinToString(" ") { it.instruction }
        assertTrue(texts.contains("2 层"))
    }

    @Test
    fun `楼层出入口位置优先取出口房间`() {
        val floor = simpleFloor()
        val point = RoutePlanner.entryPointOf(simpleCampus(listOf(floor)).buildings.first(), floor)
        assertNotNull(point)
        // 出口矩形中心是 (0.5, 0.915)
        assertEquals(0.5f, point!!.x, 0.001f)
        assertEquals(0.915f, point.y, 0.001f)
    }

    @Test
    fun `没有出口房间时回退到第一个节点`() {
        val floor = simpleFloor().copy(rooms = simpleFloor().rooms.filter { it.kind != RoomKind.EXIT })
        val point = RoutePlanner.entryPointOf(simpleCampus(listOf(floor)).buildings.first(), floor)
        assertEquals(0.15f, point!!.x, 0.001f)
    }

    @Test
    fun `曼哈顿距离计算正确`() {
        assertEquals(0.3f, RoutePlanner.manhattan(NormPoint(0.1f, 0.2f), NormPoint(0.2f, 0.4f)), 0.0001f)
    }

    @Test
    fun `归一化矩形命中判定与缩放正确`() {
        val rect = NormRect(0.2f, 0.2f, 0.6f, 0.6f)
        assertTrue(rect.contains(0.4f, 0.4f))
        assertFalse(rect.contains(0.7f, 0.4f))
        assertEquals(0.4f, rect.centerX, 0.0001f)
        assertEquals(0.4f, rect.centerY, 0.0001f)

        val pixels = rect.toPixels(200f, 100f)
        assertEquals(40f, pixels[0], 0.001f) // left
        assertEquals(20f, pixels[1], 0.001f) // top
        assertEquals(80f, pixels[2], 0.001f) // width
        assertEquals(40f, pixels[3], 0.001f) // height

        val inflated = rect.inflate(1.5f)
        assertTrue(inflated.left < rect.left)
        assertTrue(inflated.right > rect.right)
    }

    @Test
    fun `房间轮廓在没有多边形时退化为矩形四角`() {
        val room = Room("r", "房间", "房", "f", "b", NormRect(0f, 0f, 1f, 1f))
        assertEquals(4, room.outline().size)
    }
}
