package com.campusway.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 时间表/课程表编辑变换测试。
 *
 * 重点是保存时（[TimetableEditing.applyItems]）：
 * 节次按行顺序重编号、课程表引用的旧序号要跟着重映射；
 * 被删掉、或被改成时间点的节次，它上面的排课要一并丢弃——这些错了界面上不一定看得出来。
 */
class TimetableEditingTest {

    private fun period(index: Int, start: String, end: String, section: String) =
        Period(index = index, name = "第 $index 节", start = start, end = end, section = section)

    private val math = Course(id = "c1", name = "数学")
    private val english = Course(id = "c2", name = "英语")

    @Test
    fun `时间文本可以拆成开始与结束`() {
        assertEquals("08:00" to "08:45", TimetableEditing.splitTime("08:00 - 08:45"))
        assertEquals("08:00" to "08:45", TimetableEditing.splitTime("08:00—08:45"))
        assertEquals("06:40" to "", TimetableEditing.splitTime("06:40"))
        assertEquals("" to "", TimetableEditing.splitTime(""))
    }

    @Test
    fun `拆行时按午别分组且组内按时间排序`() {
        val table = TimeTable(
            periods = listOf(
                Period(2, "第 2 节", "09:00", "09:45", "上午"),
                Period(1, "第 1 节", "08:00", "08:45", "上午")
            ),
            timePoints = listOf(
                TimePoint("起床", "06:40", "", "上午"),
                TimePoint("午休", "12:30", "13:50", "下午")
            )
        )

        val items = TimetableEditing.itemsOf(table)

        assertEquals(listOf("起床", "第 1 节", "第 2 节", "午休"), items.map { it.name })
        assertEquals(listOf("上午", "上午", "上午", "下午"), items.map { it.section })
        assertEquals(listOf(false, true, true, false), items.map { it.isPeriod })
    }

    @Test
    fun `删除节次会连带丢弃它的排课并把后面的序号前移`() {
        val time = TimeTable(
            periods = listOf(
                period(1, "08:00", "08:45", "上午"),
                period(2, "08:55", "09:40", "上午"),
                period(3, "14:00", "14:45", "下午")
            )
        )
        val course = CourseTable(
            courses = listOf(math, english),
            entries = listOf(CourseEntry("c1", 1, 2), CourseEntry("c2", 1, 3))
        )

        val items = TimetableEditing.itemsOf(time).filterNot { it.originalIndex == 2 }
        val result = TimetableEditing.applyItems(time, course, items)

        assertEquals(listOf(1, 2), result.timeTable.periods.map { it.index })
        assertEquals(listOf("c2"), result.courseTable.entries.map { it.courseId })
        assertEquals(listOf(2), result.courseTable.entries.map { it.periodIndex })
    }

    @Test
    fun `把节次切成时间点会丢掉它上面的课`() {
        val time = TimeTable(
            periods = listOf(
                period(1, "08:00", "08:45", "上午"),
                period(2, "08:55", "09:40", "上午"),
                period(3, "14:00", "14:45", "下午")
            )
        )
        val course = CourseTable(
            courses = listOf(math, english),
            entries = listOf(CourseEntry("c1", 1, 1), CourseEntry("c2", 1, 3))
        )

        val items = TimetableEditing.itemsOf(time).map {
            if (it.originalIndex == 3) it.copy(isPeriod = false) else it
        }
        val result = TimetableEditing.applyItems(time, course, items)

        assertEquals(2, result.timeTable.periods.size)
        assertEquals(listOf("c1"), result.courseTable.entries.map { it.courseId })
        assertTrue(result.timeTable.timePoints.any { it.start == "14:00" })
    }

    @Test
    fun `把时间点切成节次会多出一个节次`() {
        val time = TimeTable(
            periods = listOf(period(1, "08:00", "08:45", "上午")),
            timePoints = listOf(TimePoint("大课间", "09:00", "09:20", "上午"))
        )

        val items = TimetableEditing.itemsOf(time).map {
            if (!it.isPeriod) it.copy(isPeriod = true) else it
        }
        val result = TimetableEditing.applyItems(time, CourseTable(), items)

        assertEquals(2, result.timeTable.periods.size)
        assertEquals(listOf("第 1 节", "大课间"), result.timeTable.periods.map { it.name })
        assertTrue(result.timeTable.timePoints.isEmpty())
    }

    @Test
    fun `默认命名会随序号重排自定义名称不动`() {
        val items = listOf(
            TimeItem("k1", null, true, name = "第 2 节", timeText = "09:00 - 09:45", section = "上午"),
            TimeItem("k2", 1, true, name = "早读", timeText = "08:00 - 08:45", section = "上午")
        )

        val result = TimetableEditing.applyItems(TimeTable(), CourseTable(), items)

        assertEquals(listOf("第 1 节", "早读"), result.timeTable.periods.map { it.name })
    }

    @Test
    fun `设置某天某节的课程与清空`() {
        var table = CourseTable(courses = listOf(math, english))

        table = CourseEditing.setEntry(table, dayOfWeek = 2, periodIndex = 3, courseId = "c1")
        assertEquals(1, table.entries.size)
        assertEquals(2, table.entries[0].dayOfWeek)

        // 同一格换一门课：只保留一条
        table = CourseEditing.setEntry(table, dayOfWeek = 2, periodIndex = 3, courseId = "c2")
        assertEquals(1, table.entries.size)
        assertEquals("c2", table.entries[0].courseId)

        // 清空
        table = CourseEditing.setEntry(table, dayOfWeek = 2, periodIndex = 3, courseId = null)
        assertTrue(table.entries.isEmpty())
    }

    @Test
    fun `新增或更新课程`() {
        var table = CourseTable()

        table = CourseEditing.upsertCourse(table, math)
        assertEquals(1, table.courses.size)

        table = CourseEditing.upsertCourse(table, math.copy(name = "高等数学"))
        assertEquals(1, table.courses.size)
        assertEquals("高等数学", table.courses[0].name)
    }

    @Test
    fun `删除课程会连带删除它的排课`() {
        var table = CourseTable(courses = listOf(math, english))
        table = CourseEditing.setEntry(table, 1, 1, "c1")
        table = CourseEditing.setEntry(table, 1, 2, "c2")

        table = CourseEditing.removeCourse(table, "c1")

        assertEquals(listOf("c2"), table.courses.map { it.id })
        assertEquals(listOf("c2"), table.entries.map { it.courseId })
    }

    @Test
    fun `新课程 id 不与已有重复`() {
        val existing = listOf(
            Course(id = "course-1", name = "语文"),
            Course(id = "course-2", name = "数学"),
            Course(id = "course-3", name = "英语")
        )
        assertEquals("course-4", CourseEditing.newCourseId(existing))

        val sparse = listOf(
            Course(id = "course-1", name = "语文"),
            Course(id = "course-2", name = "数学")
        )
        assertEquals("course-3", CourseEditing.newCourseId(sparse))
    }

    @Test
    fun `按名称或简称匹配校园房间`() {
        val room = Room(
            id = "b1-f1-302",
            name = "302 网络实训室",
            shortName = "302",
            floorId = "b1-f1",
            buildingId = "b1",
            rect = NormRect(0.1f, 0.1f, 0.3f, 0.3f)
        )
        val campus = Campus(
            buildings = listOf(
                Building(
                    id = "b1",
                    name = "1 号楼",
                    floors = listOf(
                        FloorPlan(buildingId = "b1", floorId = "b1-f1", level = 1, name = "1 层", rooms = listOf(room))
                    )
                )
            )
        )

        assertEquals("b1-f1-302", CourseEditing.matchRoomId(campus, "302"))
        assertEquals("b1-f1-302", CourseEditing.matchRoomId(campus, "302 网络实训室"))
        assertEquals("", CourseEditing.matchRoomId(campus, "不存在的教室"))
        assertEquals("", CourseEditing.matchRoomId(null, "302"))
    }
}
