package com.campusway.app

import com.campusway.core.data.SampleData
import com.campusway.core.model.AppSettings
import com.campusway.core.model.Course
import com.campusway.core.model.CourseEntry
import com.campusway.core.model.CourseTable
import com.campusway.core.model.LessonStatus
import com.campusway.core.model.Period
import com.campusway.core.model.SchoolCalendar
import com.campusway.core.model.TimeTable
import com.campusway.core.model.TimetableAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 课表页展示逻辑测试。
 *
 * [TimetablePresenter] 是纯函数，不依赖 Android 与协程，
 * 因此“今日/整周切换”“即将上课提示”“空状态文案”都能在 JVM 上直接断言。
 */
class TimetablePresenterTest {

    private val monday = LocalDate.of(2026, 9, 7)

    private val timeTable = TimeTable(
        periods = listOf(
            Period(1, "第1节", "08:00", "08:45", "上午"),
            Period(2, "第2节", "09:00", "09:45", "上午"),
            Period(3, "第3节", "10:00", "10:45", "上午"),
            Period(4, "第4节", "14:00", "14:45", "下午")
        )
    )

    private val table = CourseTable(
        courses = listOf(
            Course("c1", "计算机网络", "王老师", "b1-f3-r302", 0),
            Course("c2", "数学", "李老师", "b1-f1-r101", 1)
        ),
        entries = listOf(
            CourseEntry("c1", 1, 1),
            CourseEntry("c2", 1, 2),
            CourseEntry("c1", 2, 1),
            CourseEntry("c2", 3, 3)
        )
    )

    private val calendar = SchoolCalendar(termStartDate = "2026-09-07", totalWeeks = 20)

    private fun state(
        now: LocalTime,
        todayMode: Boolean = true,
        weekOffset: Int = 0
    ) = TimetableViewState(date = monday, now = now, todayMode = todayMode, weekOffset = weekOffset)

    @Test
    fun `今日视图只包含当天的课`() {
        val data = TimetablePresenter.build(state(LocalTime.of(7, 0)), table, timeTable, calendar, AppSettings.Default)
        assertEquals(2, data.todayLessons.size)
        assertTrue(data.todayLessons.all { it.entry.dayOfWeek == 1 })
    }

    @Test
    fun `当前课与下一节课正确取出`() {
        val data = TimetablePresenter.build(state(LocalTime.of(8, 5)), table, timeTable, calendar, AppSettings.Default)
        assertEquals("计算机网络", data.currentLesson?.course?.name)
        assertEquals("数学", data.nextLesson?.course?.name)
    }

    @Test
    fun `整周视图包含一周七天的数据`() {
        val data = TimetablePresenter.build(
            state(LocalTime.of(9, 0), todayMode = false),
            table, timeTable, calendar, AppSettings.Default
        )
        assertEquals(7, data.weekLessons.size)
        // 周一 2 节、周二 1 节、周三 1 节，其余为空
        assertEquals(2, data.weekLessons[1]?.size)
        assertEquals(1, data.weekLessons[2]?.size)
        assertEquals(1, data.weekLessons[3]?.size)
        assertEquals(0, data.weekLessons[7]?.size)
    }

    @Test
    fun `整周视图不受当前时刻影响`() {
        val early = TimetablePresenter.build(state(LocalTime.of(0, 1)), table, timeTable, calendar, AppSettings.Default)
        val late = TimetablePresenter.build(state(LocalTime.of(23, 59)), table, timeTable, calendar, AppSettings.Default)
        assertEquals(
            early.weekLessons[1]?.map { it.entry.periodIndex },
            late.weekLessons[1]?.map { it.entry.periodIndex }
        )
    }

    @Test
    fun `即将上课时给出提示文案`() {
        // 8:50 时，9:00 的第 2 节在默认 15 分钟提醒窗口内
        val settings = AppSettings.Default.copy(showUpcomingReminder = true, reminderLeadMinutes = 15)
        val data = TimetablePresenter.build(state(LocalTime.of(8, 50)), table, timeTable, calendar, settings)
        assertNotNull(data.upcomingHint)
        assertTrue(data.upcomingHint!!.contains("10 分钟"))
    }

    @Test
    fun `超出提醒窗口不显示提示`() {
        val settings = AppSettings.Default.copy(showUpcomingReminder = true, reminderLeadMinutes = 5)
        val data = TimetablePresenter.build(state(LocalTime.of(8, 50)), table, timeTable, calendar, settings)
        assertNull(data.upcomingHint)
    }

    @Test
    fun `关闭提醒后即使临近上课也不提示`() {
        val settings = AppSettings.Default.copy(showUpcomingReminder = false)
        val data = TimetablePresenter.build(state(LocalTime.of(8, 58)), table, timeTable, calendar, settings)
        assertNull(data.upcomingHint)
    }

    @Test
    fun `正在上课时不再提示即将上课`() {
        val settings = AppSettings.Default.copy(showUpcomingReminder = true, reminderLeadMinutes = 30)
        val data = TimetablePresenter.build(state(LocalTime.of(8, 5)), table, timeTable, calendar, settings)
        assertEquals(LessonStatus.ONGOING, data.currentLesson?.status)
        // 下一节 9:00 距离 55 分钟，超出 30 分钟窗口
        assertNull(data.upcomingHint)
    }

    @Test
    fun `没有课时给出休息文案`() {
        val emptyTable = CourseTable()
        val data = TimetablePresenter.build(state(LocalTime.of(9, 0)), emptyTable, timeTable, calendar, AppSettings.Default)
        assertTrue(data.todayLessons.isEmpty())
        assertTrue(data.emptyHint.contains("没有排课"))
    }

    @Test
    fun `课程全部结束时给出结束文案`() {
        val data = TimetablePresenter.build(state(LocalTime.of(23, 0)), table, timeTable, calendar, AppSettings.Default)
        assertTrue(data.allFinished)
        assertTrue(data.emptyHint.contains("上完"))
    }

    @Test
    fun `周次随日期推进`() {
        val secondWeek = TimetableViewState(date = monday.plusWeeks(1), now = LocalTime.of(9, 0))
        val data = TimetablePresenter.build(secondWeek, table, timeTable, calendar, AppSettings.Default)
        assertEquals(2, data.weekNumber)
    }

    @Test
    fun `整周翻页会改变周次且不越界`() {
        val forward = TimetablePresenter.build(
            state(LocalTime.of(9, 0), todayMode = false, weekOffset = 3),
            table, timeTable, calendar, AppSettings.Default
        )
        assertEquals(4, forward.weekNumber)

        // 往回翻过头也不应出现第 0 周
        val backward = TimetablePresenter.build(
            state(LocalTime.of(9, 0), todayMode = false, weekOffset = -10),
            table, timeTable, calendar, AppSettings.Default
        )
        assertTrue(backward.weekNumber >= 1)
    }

    @Test
    fun `周标题包含日期范围`() {
        val data = TimetablePresenter.build(
            state(LocalTime.of(9, 0), todayMode = false),
            table, timeTable, calendar, AppSettings.Default
        )
        assertTrue(data.weekTitle.contains("第 1 周"))
        assertTrue(data.weekTitle.contains("9月7日"))
    }

    @Test
    fun `日期标题包含星期`() {
        val data = TimetablePresenter.build(state(LocalTime.of(9, 0)), table, timeTable, calendar, AppSettings.Default)
        assertTrue(data.dateTitle.contains("周一"))
        assertTrue(data.dateTitle.contains("9月7日"))
    }

    @Test
    fun `时间表按上午下午分组`() {
        val data = TimetablePresenter.build(state(LocalTime.of(9, 0)), table, timeTable, calendar, AppSettings.Default)
        assertEquals(listOf("上午", "下午"), data.periodSections.map { it.first })
    }

    @Test
    fun `课程表显示方式跟随设置`() {
        val byTime = TimetablePresenter.build(
            state(LocalTime.of(9, 0)), table, timeTable, calendar,
            AppSettings.Default.copy(timetableAxis = TimetableAxis.TIME)
        )
        assertEquals(TimetableAxis.TIME, byTime.axis)
    }

    @Test
    fun `课程索引包含全部课程`() {
        val data = TimetablePresenter.build(state(LocalTime.of(9, 0)), table, timeTable, calendar, AppSettings.Default)
        assertEquals(2, data.courses.size)
        assertEquals("计算机网络", data.courses["c1"]?.name)
    }

    @Test
    fun `教室定位能对应到具体楼层`() {
        val campus = SampleData.campus
        val location = TimetablePresenter.locate(campus, SampleData.courseTable.entries.first().let {
            SampleData.courseTable.effectiveRoomId(it)
        })
        assertNotNull(location)
        assertTrue(location!!.building.id.isNotBlank())
    }

    @Test
    fun `空教室不会导致定位异常`() {
        assertNull(TimetablePresenter.locate(SampleData.campus, ""))
        assertNull(TimetablePresenter.locate(SampleData.campus, "不存在的教室"))
    }

    @Test
    fun `内置示例课表能算出今日课程`() {
        // 取一个一定排了课的日期（示例数据周一到周五都有课）
        val data = TimetablePresenter.build(
            TimetableViewState(date = LocalDate.of(2026, 9, 7), now = LocalTime.of(7, 0)),
            SampleData.courseTable,
            SampleData.timeTable,
            SampleData.calendarFor(LocalDate.of(2026, 9, 7)),
            AppSettings.Default
        )
        assertTrue(data.todayLessons.isNotEmpty())
        assertFalse(data.allFinished)
    }
}
