package com.campusway.core.model

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 时间解析与格式化工具。
 *
 * 课表里的时间统一用 `HH:mm` 文本存储（便于导入导出与人工编辑），
 * 计算时再转成分钟数或 [LocalTime]。
 */
object TimeOfDay {

    private val PATTERNS = listOf("H:mm", "HH:mm", "H:mm:ss", "HH:mm:ss")

    /** 解析 `HH:mm` 为当天的分钟数，失败返回 -1。 */
    fun parseToMinutes(text: String): Int {
        val t = parse(text) ?: return -1
        return t.hour * 60 + t.minute
    }

    /** 解析 `HH:mm`（兼容 `H:mm`、含秒）为 [LocalTime]，失败返回 null。 */
    fun parse(text: String): LocalTime? {
        val raw = text.trim()
        if (raw.isEmpty()) return null
        PATTERNS.forEach { pattern ->
            runCatching {
                return LocalTime.parse(raw, DateTimeFormatter.ofPattern(pattern))
            }
        }
        return null
    }

    /** 分钟数格式化为 `HH:mm`。 */
    fun format(minuteOfDay: Int): String {
        val safe = ((minuteOfDay % 1440) + 1440) % 1440
        return "%02d:%02d".format(safe / 60, safe % 60)
    }

    /** 计算与“现在”的间隔并生成人类可读文案，例如“12 分钟后”。 */
    fun describeDelta(from: LocalTime, now: LocalTime): String {
        val minutes = ChronoUnit.MINUTES.between(now, from)
        return when {
            minutes < 0 -> "已结束"
            minutes == 0L -> "现在"
            minutes < 60 -> "$minutes 分钟后"
            else -> "${minutes / 60} 小时 ${minutes % 60} 分钟后"
        }
    }
}

/**
 * 校历：把自然日期换算成“第几教学周”，用于按周过滤课程表。
 */
@Serializable
data class SchoolCalendar(
    /** 第 1 教学周的周一，格式 yyyy-MM-dd。 */
    val termStartDate: String = "",
    /** 学期总周数。 */
    val totalWeeks: Int = 20
) {
    /** 学期开始日期，解析失败返回 null。 */
    val startDate: LocalDate? get() = parseDate(termStartDate)

    /**
     * 计算某天是第几教学周（从 1 开始）。
     * 早于学期开始返回 1，超出总周数返回 [totalWeeks]。
     */
    fun weekOf(date: LocalDate): Int {
        val start = startDate ?: return 1
        val baseMonday = start.with(DayOfWeek.MONDAY)
        val days = ChronoUnit.DAYS.between(baseMonday, date.with(DayOfWeek.MONDAY))
        if (days < 0) return 1
        val week = (days / 7).toInt() + 1
        return week.coerceIn(1, totalWeeks)
    }

    fun weekOf(dateTime: LocalDateTime): Int = weekOf(dateTime.toLocalDate())

    /** 某教学周的周一日期。 */
    fun mondayOfWeek(week: Int): LocalDate? {
        val start = startDate ?: return null
        return start.with(DayOfWeek.MONDAY).plusWeeks((week - 1).toLong())
    }

    companion object {
        fun parseDate(text: String): LocalDate? =
            runCatching { LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()

        /** 默认校历：以“今天所在周的周一”为第 1 周，保证新用户一打开就有合理结果。 */
        fun defaultFor(date: LocalDate = LocalDate.now()): SchoolCalendar = SchoolCalendar(
            termStartDate = date.with(DayOfWeek.MONDAY).format(DateTimeFormatter.ISO_LOCAL_DATE),
            totalWeeks = 20
        )
    }
}

/**
 * 一节课在时间轴上的状态，用于课表页高亮。
 */
enum class LessonStatus {
    /** 已结束。 */
    FINISHED,

    /** 正在进行。 */
    ONGOING,

    /** 即将开始（默认 30 分钟内）。 */
    UPCOMING,

    /** 今天稍后。 */
    LATER
}

/**
 * 课表页“今日课程”列表里的一行。
 */
data class TodayLesson(
    val entry: CourseEntry,
    val course: Course,
    val period: Period,
    val roomId: String,
    val status: LessonStatus,
    /** 距离开始或结束的分钟数，配合状态展示倒计时。 */
    val minutesFromNow: Long,
    val progress: Float
)

/**
 * 今日课表的计算逻辑：不属于 UI，放在领域层便于单元测试。
 */
object TodaySchedule {

    /** 判定“即将开始”的窗口（分钟）。 */
    const val UPCOMING_WINDOW_MINUTES = 30L

    /**
     * 计算某天的课程列表与当前状态。
     *
     * @param date 目标日期（用于计算“今天的星期几”）
     * @param table 课程表
     * @param timeTable 课程时间表
     * @param now 当前时间，便于测试注入
     */
    fun build(
        date: LocalDate,
        table: CourseTable,
        timeTable: TimeTable,
        now: LocalTime = LocalTime.now()
    ): List<TodayLesson> {
        val dayOfWeek = date.dayOfWeek.value
        val nowMinutes = now.hour * 60 + now.minute

        return table.entriesOfDay(dayOfWeek)
            .mapNotNull { entry ->
                val course = table.course(entry.courseId) ?: return@mapNotNull null
                val period = timeTable.period(entry.periodIndex) ?: return@mapNotNull null
                if (!period.isValid) return@mapNotNull null

                val status = when {
                    nowMinutes >= period.endMinuteOfDay -> LessonStatus.FINISHED
                    nowMinutes >= period.startMinuteOfDay -> LessonStatus.ONGOING
                    period.startMinuteOfDay - nowMinutes <= UPCOMING_WINDOW_MINUTES -> LessonStatus.UPCOMING
                    else -> LessonStatus.LATER
                }

                val minutesFromNow = when (status) {
                    LessonStatus.ONGOING -> (period.endMinuteOfDay - nowMinutes).toLong()
                    LessonStatus.FINISHED -> (nowMinutes - period.endMinuteOfDay).toLong()
                    else -> (period.startMinuteOfDay - nowMinutes).toLong()
                }

                val progress = when (status) {
                    LessonStatus.ONGOING -> {
                        val span = period.durationMinutes
                        if (span <= 0) 0f
                        else ((nowMinutes - period.startMinuteOfDay).toFloat() / span).coerceIn(0f, 1f)
                    }
                    LessonStatus.FINISHED -> 1f
                    else -> 0f
                }

                TodayLesson(
                    entry = entry,
                    course = course,
                    period = period,
                    roomId = table.effectiveRoomId(entry),
                    status = status,
                    minutesFromNow = minutesFromNow,
                    progress = progress
                )
            }
            .sortedBy { it.period.startMinuteOfDay }
    }

    /** 找出“当前正在上”的课。 */
    fun current(lessons: List<TodayLesson>): TodayLesson? =
        lessons.firstOrNull { it.status == LessonStatus.ONGOING }

    /** 找出“下一节要上”的课。 */
    fun next(lessons: List<TodayLesson>): TodayLesson? =
        lessons.firstOrNull { it.status == LessonStatus.UPCOMING }
            ?: lessons.firstOrNull { it.status == LessonStatus.LATER }
}
