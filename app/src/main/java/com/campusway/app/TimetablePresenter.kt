package com.campusway.app

import com.campusway.core.model.AppSettings
import com.campusway.core.model.Campus
import com.campusway.core.model.Course
import com.campusway.core.model.CourseTable
import com.campusway.core.model.LessonStatus
import com.campusway.core.model.Period
import com.campusway.core.model.RoomLocation
import com.campusway.core.model.SchoolCalendar
import com.campusway.core.model.TimeTable
import com.campusway.core.model.TodayLesson
import com.campusway.core.model.TodaySchedule
import com.campusway.core.model.TimetableAxis
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 课表页的可变状态。
 */
data class TimetableViewState(
    val date: LocalDate = LocalDate.now(),
    val now: LocalTime = LocalTime.now(),
    /** true = 今日视图，false = 整周视图。 */
    val todayMode: Boolean = true,
    /** 整周视图里距离本教学周的偏移，0 表示本周。 */
    val weekOffset: Int = 0
)

/**
 * 课表页的展示数据。
 *
 * 全部由 [TimetablePresenter.build] 纯函数算出：
 * 不依赖 Android、不依赖协程，可以直接单元测试，
 * 这样“当前时段高亮”“即将到来”这些易错逻辑有测试兜底。
 */
data class TimetableViewData(
    val date: LocalDate,
    val weekNumber: Int,
    val totalWeeks: Int,
    val todayLessons: List<TodayLesson>,
    val currentLesson: TodayLesson?,
    val nextLesson: TodayLesson?,
    /** 整周视图：星期几(1..7) -> 该天课程，按节次升序。 */
    val weekLessons: Map<Int, List<TodayLesson>>,
    val timeTable: TimeTable,
    /** 时间表分组（上午/下午/晚上）。 */
    val periodSections: List<Pair<String, List<Period>>>,
    val courses: Map<String, Course>,
    val axis: TimetableAxis,
    val showUpcomingReminder: Boolean,
    val reminderLeadMinutes: Int,
    val emptyHint: String,
    /** 当前是否处于今日视图。 */
    val todayModeActive: Boolean,
    /** 整周视图的周偏移，0 表示本周。 */
    val weekOffset: Int,
    /**
     * 是否"没有时间表"。
     *
     * 用户在导入时间表时把项目全都不勾选（彩蛋确认后）就是这个状态：
     * 课表页不再显示课程与时间表，原本放时间表的位置改显示占位图。
     */
    val noVisibleTimetable: Boolean
) {
    /** 今日视图标题，例如“10月1日 周三”。 */
    val dateTitle: String = date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)) +
        " " + weekdayName(date)

    /** 整周视图标题，例如“第 3 周 · 9月29日 - 10月5日”。 */
    val weekTitle: String = run {
        val monday = date.with(java.time.DayOfWeek.MONDAY).plusWeeks(weekOffset.toLong())
        val sunday = monday.plusDays(6)
        val fmt = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
        "第 $weekNumber 周 · ${monday.format(fmt)} - ${sunday.format(fmt)}"
    }

    /** 今天的课程是否全部结束。 */
    val allFinished: Boolean =
        todayLessons.isNotEmpty() && todayLessons.all { it.status == LessonStatus.FINISHED }

    /**
     * “即将上课”提示文案：在提前提醒窗口内才有值。
     * 需求要求高亮“即将到来的时间段”，这里把它变成一句能直接看懂的话。
     */
    val upcomingHint: String?
        get() {
            if (!showUpcomingReminder) return null
            val next = nextLesson ?: return null
            if (next.status == LessonStatus.ONGOING) return null
            val minutes = next.minutesFromNow
            if (minutes <= 0 || minutes > reminderLeadMinutes) return null
            return "$minutes 分钟后上课"
        }

    companion object {
        fun weekdayName(date: LocalDate): String = when (date.dayOfWeek.value) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            else -> "周日"
        }
    }
}

/**
 * 课表页展示逻辑。
 */
object TimetablePresenter {

    /** 一周七天的显示名。 */
    val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    fun build(
        state: TimetableViewState,
        table: CourseTable,
        timeTable: TimeTable,
        calendar: SchoolCalendar,
        settings: AppSettings
    ): TimetableViewData {
        val weekNumber = calendar.weekOf(state.date)
        val effectiveWeek = (weekNumber + state.weekOffset).coerceIn(1, calendar.totalWeeks)

        val todayLessons = TodaySchedule.build(
            date = state.date,
            table = table,
            timeTable = timeTable,
            now = state.now
        )

        // 整周视图：固定取“当前选择周的周一”所在周
        val monday = state.date.with(java.time.DayOfWeek.MONDAY)
        val weekLessons = (1..7).associateWith { day ->
            TodaySchedule.build(
                date = monday.plusDays((day - 1).toLong()),
                table = table,
                timeTable = timeTable,
                // 整周视图不关心“现在”，统一按当天 00:00 计算状态，避免出现跨天错乱
                now = LocalTime.MIDNIGHT
            )
        }

        return TimetableViewData(
            date = state.date,
            weekNumber = effectiveWeek,
            totalWeeks = calendar.totalWeeks,
            todayLessons = todayLessons,
            currentLesson = TodaySchedule.current(todayLessons),
            nextLesson = TodaySchedule.next(todayLessons),
            weekLessons = weekLessons,
            timeTable = timeTable,
            periodSections = timeTable.groupedBySection(),
            courses = table.courses.associateBy { it.id },
            axis = settings.timetableAxis,
            showUpcomingReminder = settings.showUpcomingReminder,
            reminderLeadMinutes = settings.reminderLeadMinutes,
            emptyHint = emptyHintFor(todayLessons),
            todayModeActive = state.todayMode,
            weekOffset = state.weekOffset,
            noVisibleTimetable = timeTable.hasNoPeriods
        )
    }

    private fun emptyHintFor(lessons: List<TodayLesson>): String = when {
        lessons.isEmpty() -> "今天没有排课，好好休息"
        lessons.all { it.status == LessonStatus.FINISHED } -> "今天的课都上完啦"
        else -> ""
    }

    /** 距离某节课开始还有多久（用于“即将开始”提示）。 */
    fun minutesUntil(lesson: TodayLesson, now: LocalTime): Long =
        ChronoUnit.MINUTES.between(now, LocalTime.of(lesson.period.startMinuteOfDay / 60, lesson.period.startMinuteOfDay % 60))

    /** 目标教室的定位信息，找不到返回 null（课表页展示“未标定”）。 */
    fun locate(campus: Campus, roomId: String): RoomLocation? =
        if (roomId.isBlank()) null else campus.locateRoom(roomId)
}
