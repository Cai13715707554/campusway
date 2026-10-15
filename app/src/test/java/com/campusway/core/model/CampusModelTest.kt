package com.campusway.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校园数据模型：教室定位、搜索、点击命中。
 *
 * 导航页所有交互都建立在这几个方法上：
 * 点平面图 -> 命中建筑；点楼内示意图 -> 命中房间；搜索 -> 定位教室。
 */
class CampusModelTest {

    private val campus = Campus(
        name = "测试学校",
        landmarks = listOf(
            CampusLandmark("lm1", "第一食堂", NormRect(0.7f, 0.7f, 0.9f, 0.9f), LandmarkKind.CANTEEN)
        ),
        buildings = listOf(
            Building(
                id = "b1",
                name = "1 号教学楼",
                shortName = "1 号楼",
                rect = NormRect(0.1f, 0.1f, 0.4f, 0.4f),
                latitude = 30.1,
                longitude = 104.1,
                floors = listOf(
                    FloorPlan(
                        buildingId = "b1",
                        floorId = "b1-f1",
                        level = 1,
                        name = "1 层",
                        rooms = listOf(
                            Room("b1-f1-r101", "101 教室", "101", "b1-f1", "b1", NormRect(0.1f, 0.1f, 0.3f, 0.3f)),
                            Room("b1-f1-r-corridor", "走廊", "", "b1-f1", "b1", NormRect(0.05f, 0.05f, 0.95f, 0.95f), kind = RoomKind.CORRIDOR),
                            Room("b1-f1-r-stairs", "楼梯", "梯", "b1-f1", "b1", NormRect(0.6f, 0.6f, 0.7f, 0.7f), kind = RoomKind.STAIRS)
                        )
                    ),
                    FloorPlan(
                        buildingId = "b1",
                        floorId = "b1-f3",
                        level = 3,
                        name = "3 层",
                        rooms = listOf(
                            Room("b1-f3-r302", "302 网络实训室", "302", "b1-f3", "b1", NormRect(0.1f, 0.1f, 0.4f, 0.4f), kind = RoomKind.LAB)
                        )
                    )
                )
            )
        )
    )

    @Test
    fun `按 id 能找到教学楼`() {
        assertNotNull(campus.building("b1"))
        assertNull(campus.building("不存在"))
    }

    @Test
    fun `跨楼层定位教室`() {
        val location = campus.locateRoom("b1-f3-r302")
        assertNotNull(location)
        assertEquals("b1-f3", location!!.floor.floorId)
        assertEquals(RoomKind.LAB, location.room.kind)
    }

    @Test
    fun `定位不存在的教室返回空`() {
        assertNull(campus.locateRoom("不存在"))
        assertNull(campus.locateRoom(""))
    }

    @Test
    fun `默认楼层优先选一层`() {
        assertEquals("b1-f1", campus.building("b1")!!.defaultFloor()!!.floorId)
    }

    @Test
    fun `没有一层时选最低层`() {
        val building = Building(
            id = "b2",
            name = "地下楼",
            floors = listOf(
                FloorPlan(buildingId = "b2", floorId = "b2-f-1", level = -1, name = "地下一层"),
                FloorPlan(buildingId = "b2", floorId = "b2-f-2", level = -2, name = "地下二层")
            )
        )
        assertEquals("b2-f-2", building.defaultFloor()!!.floorId)
    }

    @Test
    fun `点击命中房间时避开大面积的走廊`() {
        val floor = campus.building("b1")!!.floor("b1-f1")!!
        // (0.2, 0.2) 同时在 101 教室和走廊范围内，应命中面积更小的教室
        val hit = floor.roomAt(0.2f, 0.2f)
        assertNotNull(hit)
        assertEquals("b1-f1-r101", hit!!.id)
    }

    @Test
    fun `点击空白处返回空`() {
        val floor = campus.building("b1")!!.floor("b1-f1")!!
        assertNull(floor.roomAt(0.99f, 0.01f))
    }

    @Test
    fun `搜索教室名可以命中`() {
        val hits = campus.search("302")
        assertTrue(hits.any { it is SearchHit.RoomHit && it.room.id == "b1-f3-r302" })
    }

    @Test
    fun `搜索教学楼名可以命中`() {
        val hits = campus.search("1 号")
        assertTrue(hits.any { it is SearchHit.BuildingHit })
    }

    @Test
    fun `搜索地标可以命中`() {
        val hits = campus.search("食堂")
        assertTrue(hits.any { it is SearchHit.LandmarkHit })
    }

    @Test
    fun `搜索不区分大小写且忽略空白`() {
        assertTrue(campus.search("  ").isEmpty())
        val hits = campus.search("B1-F3-R302")
        assertTrue(hits.isNotEmpty())
    }

    @Test
    fun `搜索结果不含走廊与楼梯这类噪声`() {
        val hits = campus.search("梯")
        assertFalse(hits.any { it is SearchHit.RoomHit && it.room.kind == RoomKind.STAIRS })
    }

    @Test
    fun `搜索结果标题与副标题可读`() {
        val room = campus.search("302").first { it is SearchHit.RoomHit }
        assertEquals("302 网络实训室", room.title)
        assertTrue(room.subtitle.contains("3 层"))
    }

    @Test
    fun `搜索结果能定位到具体楼层与房间`() {
        val hit = campus.search("302").first()
        if (hit is SearchHit.RoomHit) {
            assertEquals("b1", hit.building.id)
            assertEquals("b1-f3", hit.floor.floorId)
        } else {
            throw AssertionError("应当命中教室")
        }
    }

    @Test
    fun `课表引用的教室都能在校园里定位`() {
        // 用本测试内的校园数据 + 自造课表，验证“引用 -> 定位”这条链路
        val table = CourseTable(
            courses = listOf(
                Course("c1", "网络", roomId = "b1-f1-r101"),
                Course("c2", "实训", roomId = "b1-f3-r302"),
                Course("c3", "体育", roomId = "")
            ),
            entries = listOf(
                CourseEntry("c1", 1, 1),
                CourseEntry("c2", 3, 2),
                CourseEntry("c3", 5, 3)
            )
        )
        val referenced = table.referencedRoomIds()
        // 空教室不会被当成引用
        assertEquals(setOf("b1-f1-r101", "b1-f3-r302"), referenced)
        referenced.forEach { roomId ->
            assertNotNull("课表引用的教室 $roomId 在校园数据里不存在", campus.locateRoom(roomId))
        }
    }

    @Test
    fun `内置示例数据自洽`() {
        // 示例数据会被构进 assets，课程引用的教室必须真实存在
        assertEquals(emptyList<String>(), com.campusway.core.data.SampleData.validate())
    }

    @Test
    fun `未标定经纬度的建筑不参与楼外导航`() {
        val noGeo = Building(id = "x", name = "未标定楼")
        assertFalse(noGeo.hasGeo)
        assertTrue(campus.building("b1")!!.hasGeo)
    }
}

/**
 * 地理计算：Haversine 距离与步行耗时估算。
 */
class GeoTest {

    @Test
    fun `同一点距离为零`() {
        assertEquals(0.0, Geo.distanceMeters(30.0, 104.0, 30.0, 104.0), 0.001)
    }

    @Test
    fun `一百米左右的位移误差在可接受范围`() {
        // 纬度方向 0.0009 度约等于 100 米
        val d = Geo.distanceMeters(30.0000, 104.0000, 30.0009, 104.0000)
        assertTrue("实际 $d", d in 95.0..105.0)
    }

    @Test
    fun `距离计算不受参数顺序影响`() {
        val a = Geo.distanceMeters(30.1, 104.1, 30.2, 104.2)
        val b = Geo.distanceMeters(30.2, 104.2, 30.1, 104.1)
        assertEquals(a, b, 0.001)
    }

    @Test
    fun `步行耗时至少一分钟`() {
        assertEquals(1, Geo.walkingMinutes(10.0))
        assertEquals(0, Geo.walkingMinutes(0.0))
    }

    @Test
    fun `七百五十米约十分钟`() {
        // 速度 75 米/分钟，直线 600 米 * 1.25 绕行 = 750 米 -> 10 分钟
        assertEquals(10, Geo.walkingMinutes(600.0))
    }

    @Test
    fun `距离格式化在公里处切换单位`() {
        assertEquals("350 米", Geo.formatDistance(350.0))
        assertEquals("1.2 公里", Geo.formatDistance(1234.0))
    }

    @Test
    fun `零坐标视为未标定`() {
        assertFalse(Geo.isValid(0.0, 0.0))
        assertFalse(Geo.isValid(0.0, 104.0))
        assertTrue(Geo.isValid(30.6, 104.0))
    }

    @Test
    fun `超出范围的经纬度视为非法`() {
        assertFalse(Geo.isValid(91.0, 104.0))
        assertFalse(Geo.isValid(30.0, 181.0))
    }
}
