package com.campusway.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 今日课表与“当前/即将”判定的测试。
 *
 * 这是课表页最核心、也最容易写错的逻辑：
 * 需求要求高亮“现在的时间段”与“即将到来的时间段”，
 * 状态边界（正好开始、正好结束、提前 30 分钟）必须有明确行为。
 */
class TodayScheduleTest {

    private val timeTable = TimeTable(
        periods = listOf(
            Period(1, "第1节", "08:00", "08:45", "上午"),
            Period(2, "第2节", "09:00", "09:45", "上午"),
            Period(3, "第3节", "10:00", "10:45", "上午"),
            Period(4, "第4节", "14:00", "14:45", "下午")
        )
    )

    // 2026-09-07 是周一
    private val monday = LocalDate.of(2026, 9, 7)

    private val courseTable = CourseTable(
        courses = listOf(
            Course("c1", "计算机网络", "王老师", "r302", 0),
            Course("c2", "数学", "李老师", "r101", 1),
            Course("c3", "体育", "赵老师", "", 2)
        ),
        entries = listOf(
            CourseEntry("c1", dayOfWeek = 1, periodIndex = 1),
            CourseEntry("c2", dayOfWeek = 1, periodIndex = 2),
            CourseEntry("c3", dayOfWeek = 1, periodIndex = 4),
            // 只在第 5 周之后生效的课，用于验证周次过滤
            CourseEntry("c1", dayOfWeek = 1, periodIndex = 3, weekRange = WeekRange(5, 20))
        )
    )

    @Test
    fun `上课中判定为进行中`() {
        val lessons = TodaySchedule.build(monday, courseTable, timeTable, LocalTime.of(8, 10))
        val first = lessons.first { it.period.index == 1 }
        assertEquals(LessonStatus.ONGOING, first.status)
        assertTrue(first.progress > 0f && first.progress < 1f)
        // 8:10 距离 8:45 下课还有 35 分钟
        assertEquals(35L, first.minutesFromNow)
    }

    @Test
    fun `正好在开始时间判定为进行中`() {
        val lessons = TodaySchedule.build(monday, courseTable, timeTable, LocalTime.of(8, 0))
        assertEquals(LessonStatus.ONGOING, lessons.first { it.period.index == 1 }.status)
    }

    @Test
    fun `正好在结束时间判定为已结束`() {
        val lessons = TodaySchedule.build(monday, courseTable, timeTable, LocalTime.of(8, 45))
        assertEquals(LessonStatus.FINISHED, lessons.first { it.period.index == 1 }.status)
    }

    @Test
    fun `三十分钟内开始判定为即将开始`() {
        // 8:31 时，9:00 的第 2 节在 30 分钟窗口内
        val lessons = TodaySchedule.build(monday, courseTable, timeTable, LocalTime.of(8, 31))
        assertEquals(LessonStatus.UPCOMING, lessons.first { it.period.index == 2 }.status)
    }

    @Test
    fun `超过三十分钟判定为稍后`() {
        // 8:20 时，9:00 还有 40 分钟，不算“即将”
        val lessons = TodaySchedule.build(monday, courseTable, timeTable, LocalTime.of(8, 20))
        assertEquals(LessonStatus.LATER, lessons.first { it.period.index == 2 }.status)
    }

    @Test
    fun `课程按开始时间排序`() {
        val lessons = TodaySchedule.build(monday, courseTable, timeTable, LocalTime.of(7, 0))
        assertEquals(listOf(1, 2, 3, 4), lessons.map { it.period.index })
    }

    @Test
    fun `当前课与下一节课可以正确取出`() {
        val lessons = TodaySchedule.build(monday, courseTable, timeTable, LocalTime.of(8, 10))
        assertEquals("计算机网络", TodaySchedule.current(lessons)?.course?.name)
        // 8:10 时 9:00 的第 2 节还有 50 分钟，属于 LATER，next() 仍应返回它
        assertEquals("数学", TodaySchedule.next(lessons)?.course?.name)
    }

    @Test
    fun `没有正在进行时当前课为空`() {
        val lessons = TodaySchedule.build(monday, courseTable, timeTable, LocalTime.of(7, 0))
        assertNull(TodaySchedule.current(lessons))
        assertNotNull(TodaySchedule.next(lessons))
    }

    @Test
    fun `引用不存在的节次会被安全跳过`() {
        val broken = CourseTable(
            courses = listOf(Course("c9", "幽灵课")),
            entries = listOf(CourseEntry("c9", dayOfWeek = 1, periodIndex = 99))
        )
        assertEquals(emptyList<TodayLesson>(), TodaySchedule.build(monday, broken, timeTable, LocalTime.of(9, 0)))
    }

    @Test
    fun `引用不存在的课程会被安全跳过`() {
        val broken = CourseTable(
            courses = emptyList(),
            entries = listOf(CourseEntry("不存在", dayOfWeek = 1, periodIndex = 1))
        )
        assertEquals(emptyList<TodayLesson>(), TodaySchedule.build(monday, broken, timeTable, LocalTime.of(9, 0)))
    }

    @Test
    fun `没有课的星期返回空列表`() {
        val sunday = LocalDate.of(2026, 9, 13)
        assertEquals(emptyList<TodayLesson>(), TodaySchedule.build(sunday, courseTable, timeTable, LocalTime.of(9, 0)))
    }

    @Test
    fun `已结束的课程进度为一`() {
        val lessons = TodaySchedule.build(monday, courseTable, timeTable, LocalTime.of(12, 0))
        val first = lessons.first { it.period.index == 1 }
        assertEquals(LessonStatus.FINISHED, first.status)
        assertEquals(1f, first.progress, 0.0001f)
    }

    @Test
    fun `周次范围会过滤未生效的课`() {
        // 第 1 周：第 3 节的课还没生效
        val table = CourseTable(
            courses = courseTable.courses,
            entries = listOf(CourseEntry("c1", 1, 3, weekRange = WeekRange(5, 20)))
        )
        assertEquals(0, table.entriesOfDay(1, week = 1).size)
        assertEquals(1, table.entriesOfDay(1, week = 6).size)
        // 不传周次时不过滤，便于“整学期视图”
        assertEquals(1, table.entriesOfDay(1).size)
    }

    @Test
    fun `教室覆盖优先于课程默认教室`() {
        val table = CourseTable(
            courses = listOf(Course("c1", "实训", roomId = "默认教室")),
            entries = listOf(CourseEntry("c1", 1, 1, roomIdOverride = "实训楼机房"))
        )
        assertEquals("实训楼机房", table.effectiveRoomId(table.entries.first()))
    }

    @Test
    fun `没有覆盖时使用课程默认教室`() {
        val table = CourseTable(
            courses = listOf(Course("c1", "实训", roomId = "默认教室")),
            entries = listOf(CourseEntry("c1", 1, 1))
        )
        assertEquals("默认教室", table.effectiveRoomId(table.entries.first()))
    }

    @Test
    fun `体育课这类没有教室的课程不会导致闪退`() {
        val lessons = TodaySchedule.build(monday, courseTable, timeTable, LocalTime.of(13, 50))
        val pe = lessons.first { it.course.name == "体育" }
        assertEquals("", pe.roomId)
    }
}
