package com.campusway.core.data

import com.campusway.core.model.Building
import com.campusway.core.model.Campus
import com.campusway.core.model.CampusLandmark
import com.campusway.core.model.Course
import com.campusway.core.model.CourseEntry
import com.campusway.core.model.CourseTable
import com.campusway.core.model.FloorPathEdge
import com.campusway.core.model.FloorPathNode
import com.campusway.core.model.FloorPlan
import com.campusway.core.model.LandmarkKind
import com.campusway.core.model.NormPoint
import com.campusway.core.model.NormRect
import com.campusway.core.model.Period
import com.campusway.core.model.Room
import com.campusway.core.model.RoomKind
import com.campusway.core.model.SchoolCalendar
import com.campusway.core.model.TimeTable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 内置示例数据。
 *
 * 用途有两个：
 *  1. 作为 `assets/` 里随包 JSON 的数据源（由工具生成，应用启动时离线读取）；
 *  2. 作为单元测试的固定夹具。
 *
 * 现实里学校数据由用户在设置页导入，这里给出一所典型的职业学校布局，
 * 让新用户第一次打开就能看到完整可用的界面，而不是空白页。
 */
object SampleData {

    // ---------------------------------------------------------------- 时间表

    val timeTable: TimeTable = TimeTable.sample()

    // ---------------------------------------------------------------- 课程

    private const val C_NET = "c-net"
    private const val C_MATH = "c-math"
    private const val C_EN = "c-en"
    private const val C_PE = "c-pe"
    private const val C_POL = "c-pol"
    private const val C_CN = "c-cn"
    private const val C_PRACTICE = "c-practice"
    private const val C_DB = "c-db"

    // 教室 id 与 campus 里的房间 id 保持一致
    private const val R_101 = "b1-f1-r101"
    private const val R_102 = "b1-f1-r102"
    private const val R_201 = "b1-f2-r201"
    private const val R_202 = "b1-f2-r202"
    private const val R_302 = "b1-f3-r302"
    private const val R_PS = "b1-f1-rps"
    private const val R_BIG = "b2-f1-r101"
    private const val R_DB = "b2-f2-r201"

    val courses: List<Course> = listOf(
        Course(C_NET, "计算机网络", "王建国", R_302, colorIndex = 0, note = "带实训手册"),
        Course(C_MATH, "数学", "李慧", R_101, colorIndex = 1),
        Course(C_EN, "英语", "张敏", R_102, colorIndex = 2),
        Course(C_PE, "体育", "赵强", "", colorIndex = 3, note = "操场集合"),
        Course(C_POL, "职业道德与法律", "陈晓", R_201, colorIndex = 4),
        Course(C_CN, "语文", "刘芳", R_202, colorIndex = 5),
        Course(C_PRACTICE, "网络综合实训", "王建国", R_BIG, colorIndex = 6, note = "两节连上"),
        Course(C_DB, "数据库基础", "孙立", R_DB, colorIndex = 7)
    )

    /** 一周排课：周一到周五，覆盖“当前时段高亮”“即将到来”等展示场景。 */
    val courseEntries: List<CourseEntry> = buildList {
        // 周一
        add(CourseEntry(C_MATH, dayOfWeek = 1, periodIndex = 1))
        add(CourseEntry(C_NET, dayOfWeek = 1, periodIndex = 2))
        add(CourseEntry(C_EN, dayOfWeek = 1, periodIndex = 3))
        add(CourseEntry(C_POL, dayOfWeek = 1, periodIndex = 5))
        add(CourseEntry(C_PRACTICE, dayOfWeek = 1, periodIndex = 6))
        add(CourseEntry(C_PRACTICE, dayOfWeek = 1, periodIndex = 7))
        // 周二
        add(CourseEntry(C_CN, dayOfWeek = 2, periodIndex = 1))
        add(CourseEntry(C_MATH, dayOfWeek = 2, periodIndex = 2))
        add(CourseEntry(C_NET, dayOfWeek = 2, periodIndex = 3))
        add(CourseEntry(C_NET, dayOfWeek = 2, periodIndex = 4))
        add(CourseEntry(C_PE, dayOfWeek = 2, periodIndex = 6))
        // 周三
        add(CourseEntry(C_EN, dayOfWeek = 3, periodIndex = 1))
        add(CourseEntry(C_DB, dayOfWeek = 3, periodIndex = 2))
        add(CourseEntry(C_DB, dayOfWeek = 3, periodIndex = 3))
        add(CourseEntry(C_POL, dayOfWeek = 3, periodIndex = 5))
        add(CourseEntry(C_CN, dayOfWeek = 3, periodIndex = 8))
        // 周四
        add(CourseEntry(C_MATH, dayOfWeek = 4, periodIndex = 1))
        add(CourseEntry(C_EN, dayOfWeek = 4, periodIndex = 2))
        add(CourseEntry(C_PRACTICE, dayOfWeek = 4, periodIndex = 3, roomIdOverride = R_BIG))
        add(CourseEntry(C_PRACTICE, dayOfWeek = 4, periodIndex = 4, roomIdOverride = R_BIG))
        add(CourseEntry(C_PE, dayOfWeek = 4, periodIndex = 7))
        // 周五
        add(CourseEntry(C_NET, dayOfWeek = 5, periodIndex = 1))
        add(CourseEntry(C_DB, dayOfWeek = 5, periodIndex = 2))
        add(CourseEntry(C_CN, dayOfWeek = 5, periodIndex = 3))
        add(CourseEntry(C_POL, dayOfWeek = 5, periodIndex = 6))
    }

    val courseTable: CourseTable = CourseTable(courses = courses, entries = courseEntries)

    // ---------------------------------------------------------------- 校园

    /** 楼内路径网格：一条沿走廊的折线，串起各房间门口。 */
    private fun corridorNodes(
        prefix: String,
        y: Float,
        xs: List<Float>
    ): List<FloorPathNode> = xs.mapIndexed { i, x -> FloorPathNode("$prefix-n$i", NormPoint(x, y)) }

    private fun chainEdges(prefix: String, count: Int): List<FloorPathEdge> =
        (0 until count - 1).map { FloorPathEdge("$prefix-n$it", "$prefix-n${it + 1}") }

    private fun room(
        id: String,
        name: String,
        short: String,
        floorId: String,
        buildingId: String,
        rect: NormRect,
        kind: RoomKind = RoomKind.CLASSROOM,
        nearestExit: String = ""
    ) = Room(
        id = id,
        name = name,
        shortName = short,
        floorId = floorId,
        buildingId = buildingId,
        rect = rect,
        kind = kind,
        nearestExitId = nearestExit
    )

    private const val B1 = "b1"
    private const val B2 = "b2"

    /** 1 号教学楼：3 层，每层一条走廊、两侧教室，两端有楼梯。 */
    private fun building1(): Building {
        val floors = listOf(1, 2, 3).map { level ->
            val floorId = "b1-f$level"
            val prefix = "b1f$level"
            val isGround = level == 1
            // 房间 id 规则：楼-层-房号，与课表里引用的常量严格对应
            fun rid(no: String) = "$floorId-$no"
            val rooms = mutableListOf(
                room(rid("r101"), "10${level}教室", "10${level}", floorId, B1, NormRect(0.06f, 0.10f, 0.30f, 0.30f)),
                room(rid("r102"), "10${level + 1}教室", "10${level + 1}", floorId, B1, NormRect(0.06f, 0.36f, 0.30f, 0.56f)),
                room(rid("r103"), "10${level + 2}教室", "10${level + 2}", floorId, B1, NormRect(0.06f, 0.62f, 0.30f, 0.82f)),
                room(rid("r201"), "20${level}教室", "20${level}", floorId, B1, NormRect(0.70f, 0.10f, 0.94f, 0.30f)),
                room(rid("r302"), "30${level}专业教室", "30${level}", floorId, B1, NormRect(0.70f, 0.36f, 0.94f, 0.56f), RoomKind.LAB),
                room(rid("r401"), "40${level}办公室", "40${level}", floorId, B1, NormRect(0.70f, 0.62f, 0.94f, 0.82f), RoomKind.OFFICE),
                room(rid("rps"), "卫生间", "卫", floorId, B1, NormRect(0.36f, 0.86f, 0.50f, 0.96f), RoomKind.RESTROOM),
                room(rid("rs1"), "东楼梯", "梯", floorId, B1, NormRect(0.33f, 0.06f, 0.42f, 0.22f), RoomKind.STAIRS),
                room(rid("rs2"), "西楼梯", "梯", floorId, B1, NormRect(0.58f, 0.06f, 0.67f, 0.22f), RoomKind.STAIRS)
            )
            if (isGround) {
                rooms += room(
                    rid("gate"), "1 号教学楼正门", "正门", floorId, B1,
                    NormRect(0.40f, 0.90f, 0.60f, 1.0f), RoomKind.EXIT
                )
            }
            FloorPlan(
                buildingId = B1,
                floorId = floorId,
                level = level,
                name = "$level 层",
                aspectRatio = 1.4f,
                rooms = rooms,
                nodes = corridorNodes(prefix, 0.45f, listOf(0.15f, 0.35f, 0.50f, 0.65f, 0.85f)),
                edges = chainEdges(prefix, 5)
            )
        }
        return Building(
            id = B1,
            name = "1 号教学楼",
            shortName = "1 号楼",
            rect = NormRect(0.08f, 0.16f, 0.36f, 0.42f),
            floorCount = 3,
            // 示例坐标：便于演示楼外导航；真实使用时由学校自行标定
            latitude = 30.658_10,
            longitude = 104.065_90,
            floors = floors
        )
    }

    /** 2 号实训楼：2 层，大空间实训室。 */
    private fun building2(): Building {
        val floors = listOf(1, 2).map { level ->
            val floorId = "b2-f$level"
            val prefix = "b2f$level"
            fun rid(no: String) = "$floorId-$no"
            val rooms = mutableListOf(
                room(rid("r101"), "综合实训室 A", "实A", floorId, B2, NormRect(0.06f, 0.12f, 0.42f, 0.46f), RoomKind.LAB),
                room(rid("r102"), "综合实训室 B", "实B", floorId, B2, NormRect(0.06f, 0.54f, 0.42f, 0.88f), RoomKind.LAB),
                room(rid("r201"), "机房 ${level}0${level}", "机${level}0${level}", floorId, B2, NormRect(0.54f, 0.12f, 0.94f, 0.42f), RoomKind.LAB),
                room(rid("r202"), "器材室", "器材", floorId, B2, NormRect(0.54f, 0.50f, 0.80f, 0.72f), RoomKind.OTHER),
                room(rid("rps"), "卫生间", "卫", floorId, B2, NormRect(0.82f, 0.50f, 0.94f, 0.72f), RoomKind.RESTROOM),
                room(rid("rs1"), "楼梯", "梯", floorId, B2, NormRect(0.44f, 0.06f, 0.52f, 0.22f), RoomKind.STAIRS)
            )
            if (level == 1) {
                rooms += room(
                    rid("gate"), "2 号实训楼入口", "入口", floorId, B2,
                    NormRect(0.40f, 0.90f, 0.60f, 1.0f), RoomKind.EXIT
                )
            }
            FloorPlan(
                buildingId = B2,
                floorId = floorId,
                level = level,
                name = "$level 层",
                aspectRatio = 1.3f,
                rooms = rooms,
                nodes = corridorNodes(prefix, 0.50f, listOf(0.20f, 0.48f, 0.70f, 0.88f)),
                edges = chainEdges(prefix, 4)
            )
        }
        return Building(
            id = B2,
            name = "2 号实训楼",
            shortName = "2 号楼",
            rect = NormRect(0.44f, 0.16f, 0.70f, 0.40f),
            floorCount = 2,
            latitude = 30.658_42,
            longitude = 104.066_45,
            floors = floors
        )
    }

    val campus: Campus = Campus(
        name = "示例职业学校",
        aspectRatio = 1.5f,
        latitude = 30.658_25,
        longitude = 104.066_10,
        landmarks = listOf(
            CampusLandmark("lm-gate", "学校正门", NormRect(0.44f, 0.90f, 0.56f, 1.0f), LandmarkKind.GATE, 30.657_20, 104.066_10),
            CampusLandmark("lm-canteen", "第一食堂", NormRect(0.76f, 0.56f, 0.94f, 0.74f), LandmarkKind.CANTEEN, 30.657_80, 104.067_05),
            CampusLandmark("lm-dorm", "学生宿舍 3 栋", NormRect(0.76f, 0.20f, 0.94f, 0.40f), LandmarkKind.DORMITORY, 30.658_90, 104.067_10),
            CampusLandmark("lm-lib", "图书馆", NormRect(0.08f, 0.56f, 0.30f, 0.76f), LandmarkKind.LIBRARY, 30.657_70, 104.065_20),
            CampusLandmark("lm-gym", "体育馆", NormRect(0.40f, 0.60f, 0.66f, 0.84f), LandmarkKind.GYM, 30.657_40, 104.066_20),
            CampusLandmark("lm-office", "行政楼", NormRect(0.08f, 0.06f, 0.30f, 0.24f), LandmarkKind.OFFICE, 30.659_00, 104.065_30)
        ),
        buildings = listOf(building1(), building2())
    )

    /** 默认校历：以“本周一”为第 1 教学周，保证示例课表立刻可见。 */
    fun calendarFor(date: LocalDate = LocalDate.now()): SchoolCalendar = SchoolCalendar(
        termStartDate = date.with(java.time.DayOfWeek.MONDAY).format(DateTimeFormatter.ISO_LOCAL_DATE),
        totalWeeks = 20
    )

    /** 校验示例数据自洽性：课程引用的教室都必须真实存在。 */
    fun validate(): List<String> {
        val problems = mutableListOf<String>()
        val periodIndexes = timeTable.periods.map { it.index }.toSet()
        courseEntries.forEach { entry ->
            if (entry.periodIndex !in periodIndexes) {
                problems += "课程 ${entry.courseId} 引用了不存在的节次 ${entry.periodIndex}"
            }
            val roomId = courseTable.effectiveRoomId(entry)
            if (roomId.isNotBlank() && campus.locateRoom(roomId) == null) {
                problems += "课程 ${entry.courseId} 引用了不存在的教室 $roomId"
            }
        }
        problems += timeTable.validate().map { "时间表：$it" }
        return problems
    }
}
