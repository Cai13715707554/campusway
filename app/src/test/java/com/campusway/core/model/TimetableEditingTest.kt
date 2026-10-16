package com.campusway.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 时间表/课程表编辑变换测试。
 *
 * 重点是增删上课节次时，节次序号要连续、课程表里引用的节次序号要一起重映射——
 * 这两点错了界面上不一定看得出来，却会让课程挂到错误的空档上。
 */
class TimetableEditingTest {

    private fun p(index: Int, name: String = "第 $index 节") =
        Period(index = index, name = name, start = "08:0$index", end = "08:4$index", section = "上午")

    private val c1 = Course(id = "c1", name = "数学")
    private val c2 = Course(id = "c2", name = "英语")

    @Test
    fun `新增节次会把后面的序号和课程一起往后挪`() {
        val table = TimeTable(periods = listOf(p(1), p(2), p(3)))
        val courseTable = CourseTable(
            courses = listOf(c1, c2),
            entries = listOf(CourseEntry("c1", 1, 2), CourseEntry("c2", 1, 3))
        )

        val (result, newIndex) = TimetableEditing.addPeriodAfter(table, courseTable, afterIndex = 1)

        assertEquals(2, newIndex)
        assertEquals(listOf(1, 2, 3, 4), result.timeTable.periods.map { it.index })
        // 插在中间的是新空节次
        assertEquals("第 2 节", result.timeTable.period(2)?.name)
        assertEquals("第 3 节", result.timeTable.period(3)?.name)
        assertEquals(listOf(3, 4), result.courseTable.entries.map { it.periodIndex })
    }

    @Test
    fun `在末尾追加节次不影响已有课程`() {
        val table = TimeTable(periods = listOf(p(1), p(2)))
        val courseTable = CourseTable(courses = listOf(c1), entries = listOf(CourseEntry("c1", 1, 1)))

        val (result, newIndex) = TimetableEditing.addPeriodAfter(table, courseTable, afterIndex = 2)

        assertEquals(3, newIndex)
        assertEquals(listOf(1, 2, 3), result.timeTable.periods.map { it.index })
        assertEquals(listOf(1), result.courseTable.entries.map { it.periodIndex })
    }

    @Test
    fun `删除节次会把后面的序号和课程一起往前挪`() {
        val table = TimeTable(periods = listOf(p(1), p(2), p(3)))
        val courseTable = CourseTable(
            courses = listOf(c1, c2),
            entries = listOf(CourseEntry("c1", 1, 2), CourseEntry("c2", 1, 3))
        )

        val result = TimetableEditing.removePeriod(table, courseTable, index = 2)

        assertEquals(listOf(1, 2), result.timeTable.periods.map { it.index })
        // 被删节次（2）上的排课消失，第 3 节的课前移到第 2 节
        assertEquals(listOf("c2"), result.courseTable.entries.map { it.courseId })
        assertEquals(listOf(2), result.courseTable.entries.map { it.periodIndex })
    }

    @Test
    fun `修改节次的名称时间与午别`() {
        val table = TimeTable(periods = listOf(p(1), p(2)))

        val updated = TimetableEditing.updatePeriod(
            table,
            Period(index = 2, name = "上午第2节", start = "09:00", end = "09:45", section = "下午")
        )

        val period = updated.period(2)
        assertEquals("上午第2节", period?.name)
        assertEquals("09:00", period?.start)
        assertEquals("09:45", period?.end)
        assertEquals("下午", period?.section)
        // 其它节次不受影响
        assertEquals("08:01", updated.period(1)?.start)
    }

    @Test
    fun `时间点可以修改删除与新增`() {
        var table = TimeTable(timePoints = listOf(TimePoint("起床", "06:40")))

        table = TimetableEditing.addTimePoint(table, TimePoint("早餐", "07:00"))
        assertEquals(2, table.timePoints.size)

        table = TimetableEditing.updateTimePointAt(table, 0, TimePoint("起床", "06:30"))
        assertEquals("06:30", table.timePoints[0].start)

        table = TimetableEditing.removeTimePointAt(table, 1)
        assertEquals(1, table.timePoints.size)
        assertEquals("起床", table.timePoints[0].name)
    }

    @Test
    fun `设置某天某节的课程与清空`() {
        var table = CourseTable(courses = listOf(c1, c2))

        table = CourseEditing.setEntry(table, dayOfWeek = 2, periodIndex = 3, courseId = "c1")
        assertEquals(1, table.entries.size)
        assertEquals(2, table.entries[0].dayOfWeek)
        assertEquals(3, table.entries[0].periodIndex)

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

        table = CourseEditing.upsertCourse(table, c1)
        assertEquals(1, table.courses.size)

        table = CourseEditing.upsertCourse(table, c1.copy(name = "高等数学"))
        assertEquals(1, table.courses.size)
        assertEquals("高等数学", table.courses[0].name)
    }

    @Test
    fun `删除课程会连带删除它的排课`() {
        var table = CourseTable(courses = listOf(c1, c2))
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

        // 已有 id 数量与列表长度不一致时也不能撞车
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
                        FloorPlan(
                            buildingId = "b1",
                            floorId = "b1-f1",
                            level = 1,
                            name = "1 层",
                            rooms = listOf(room)
                        )
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
