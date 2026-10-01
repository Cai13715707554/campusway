package com.campusway.core.model

import kotlinx.serialization.Serializable

/**
 * 课程配色数量。
 *
 * 放在领域层是因为"给课程分配色号"是导入逻辑的一部分（见 TimetableGridParser），
 * 而色板本身在 ui/theme 里。两处必须一致，这里作为唯一来源。
 */
const val COURSE_COLOR_COUNT = 8

/**
 * 一天的课程时间划分（节次/时段）。
 *
 * 校途的课表由「课程表」和「课程时间表」两部分组成，二者分开存储、分开展示：
 * 课程表描述“周几第几节上什么课”，时间表描述“第几节从几点到几点”。
 */
@Serializable
data class Period(
    /** 节次序号，从 1 开始，全局唯一。 */
    val index: Int,
    /** 展示名称，例如“第1节”“早自习”。 */
    val name: String,
    /** 开始时间，格式 HH:mm。 */
    val start: String,
    /** 结束时间，格式 HH:mm。 */
    val end: String,
    /** 分组名称，例如“上午”“下午”“晚上”，用于在时间表里分区展示。 */
    val section: String = ""
) {
    /** 开始时间的分钟数（0..1439），无法解析时返回 -1。 */
    val startMinuteOfDay: Int get() = TimeOfDay.parseToMinutes(start)

    /** 结束时间的分钟数（0..1439），无法解析时返回 -1。 */
    val endMinuteOfDay: Int get() = TimeOfDay.parseToMinutes(end)

    /** 时间配置是否有效。 */
    val isValid: Boolean get() = startMinuteOfDay >= 0 && endMinuteOfDay > startMinuteOfDay

    /** 时长（分钟），无效时返回 0。 */
    val durationMinutes: Int
        get() = if (isValid) endMinuteOfDay - startMinuteOfDay else 0

    /**
     * 在"时间表里哪些项目要显示"的选择中，用这个键标识一项：`午别|项目名`。
     *
     * 用键而不是索引，是因为重新导入时间表后索引会变、而午别+项目名基本不变，
     * 用户上次的选择还能继续生效。同一午别内同名项目会一起显示/隐藏。
     */
    val visibilityKey: String get() = "$section|$name"
}

/**
 * 作息里的"时间点"。
 *
 * 起床、早餐、教师上办公、大课间、熄灯这类项目只是时间点（或生活作息），
 * **不是上课节次**：它们只在"课程时间表"里展示，
 * 不参与课程表（不占节次序号、不生成课表行）。
 *
 * 判断规则见 `likelyClassPeriod`：名称不是「第N节」这种格式的，都归到这里。
 */
@Serializable
data class TimePoint(
    val name: String,
    val start: String,
    /** 可选结束时间，没有就只显示一个时刻。 */
    val end: String = "",
    val section: String = ""
) {
    /** 展示用的时间文本，例如 `06:40` 或 `07:40 - 07:55`。 */
    val timeText: String get() = if (end.isBlank()) start else "$start - $end"

    /** 与 [Period.visibilityKey] 同一套键，用于"显示哪些项目"的选择。 */
    val visibilityKey: String get() = "$section|$name"

    val startMinuteOfDay: Int get() = TimeOfDay.parseToMinutes(start)
}

/**
 * 时间表里的一行：可能是上课节次，也可能只是一个时间点。
 *
 * 界面只需要按这个统一结构渲染，"是不是节次"由 [isClassPeriod] 决定
 * （非节次的行会显示得淡一些，并且不写"第 N 节"）。
 */
data class TimeTableRow(
    val section: String,
    val label: String,
    val timeText: String,
    val isClassPeriod: Boolean,
    /** 节次序号；时间点为 null。 */
    val periodIndex: Int?,
    /** 排序用：当天的分钟数，解析不出来时排在最后。 */
    val order: Int,
    /** 与 [Period.visibilityKey] / [TimePoint.visibilityKey] 一致，用于显示选择。 */
    val visibilityKey: String
)

/**
 * 课程时间表：全校统一的一份节次划分。
 *
 * 分为两部分：
 *  - [periods]：**上课节次**，序号从 1 连续编号，课程表按它排课；
 *  - [timePoints]：起床/早餐/大课间/熄灯这类时间点，只在时间表里展示。
 *
 * 这样导入学校的作息表时，"一天 23 项"不会把课程表撑成 23 行。
 */
@Serializable
data class TimeTable(
    /** 时间表名称，例如“冬季作息”“夏季作息”。 */
    val name: String = "默认作息",
    val periods: List<Period> = emptyList(),
    val timePoints: List<TimePoint> = emptyList()
) {
    private val byIndex: Map<Int, Period> by lazy { periods.associateBy { it.index } }

    fun period(index: Int): Period? = byIndex[index]

    fun periodOrNull(index: Int): Period? = byIndex[index]

    /** 按 [Period.section] 分组，保持 periods 的原始顺序。 */
    fun groupedBySection(): List<Pair<String, List<Period>>> {
        val order = LinkedHashMap<String, MutableList<Period>>()
        periods.forEach { p ->
            val key = p.section.ifBlank { "其他" }
            order.getOrPut(key) { mutableListOf() }.add(p)
        }
        return order.map { it.key to it.value.toList() }
    }

    /** 校验：节次序号唯一、时间合法、区间不重叠。返回人类可读的问题列表，空列表代表通过。 */
    fun validate(): List<String> {
        val problems = mutableListOf<String>()
        val duplicated = periods.groupBy { it.index }.filterValues { it.size > 1 }.keys
        if (duplicated.isNotEmpty()) {
            problems += "节次序号重复：${duplicated.sorted().joinToString("、")}"
        }
        periods.forEach { p ->
            if (!p.isValid) problems += "第${p.index}节「${p.name}」时间不合法：${p.start}-${p.end}"
        }
        val sorted = periods.filter { it.isValid }.sortedBy { it.startMinuteOfDay }
        sorted.zipWithNext().forEach { (a, b) ->
            if (b.startMinuteOfDay < a.endMinuteOfDay) {
                problems += "第${a.index}节与第${b.index}节时间重叠"
            }
        }
        return problems
    }

    // ---------------------------------------------------------------- 显示选择

    /**
     * 只保留 [keys] 里的项目。
     *
     * 节次会**重新编号**为 1..K：课程是按"节次序号"挂上去的，
     * 序号必须连续，否则课程会挂到空档上（表现为课表里出现莫名其妙的空行）。
     * 时间点不参与编号，只按选择保留/去掉。
     */
    fun keepingKeys(keys: Collection<String>): TimeTable = copy(
        periods = periods.filter { it.visibilityKey in keys }
            .mapIndexed { i, p -> p.copy(index = i + 1) },
        timePoints = timePoints.filter { it.visibilityKey in keys }
    )

    /** 清空（彩蛋确认后按"没有时间表"处理）。 */
    fun cleared(): TimeTable = copy(periods = emptyList(), timePoints = emptyList())

    /** 节次与时间点都没有。 */
    val isEmpty: Boolean get() = periods.isEmpty() && timePoints.isEmpty()

    /** 没有任何上课节次（课表会是空的，但可能还有时间点要展示）。 */
    val hasNoPeriods: Boolean get() = periods.isEmpty()

    /**
     * 时间表的展示行：节次与时间点合并后按时间排序。
     *
     * [TimeTableRow.isClassPeriod] 用来区分两者：非节次的行在界面上显示得淡一些，
     * 并且不写"第 N 节"。
     */
    fun rows(): List<TimeTableRow> {
        val periodRows = periods.map { period ->
            TimeTableRow(
                section = period.section.ifBlank { "其他" },
                label = period.name,
                timeText = "${period.start} - ${period.end}",
                isClassPeriod = true,
                periodIndex = period.index,
                order = period.startMinuteOfDay.takeIf { it >= 0 } ?: Int.MAX_VALUE,
                visibilityKey = period.visibilityKey
            )
        }
        val pointRows = timePoints.map { point ->
            TimeTableRow(
                section = point.section.ifBlank { "其他" },
                label = point.name,
                timeText = point.timeText,
                isClassPeriod = false,
                periodIndex = null,
                order = point.startMinuteOfDay.takeIf { it >= 0 } ?: Int.MAX_VALUE,
                visibilityKey = point.visibilityKey
            )
        }
        return (periodRows + pointRows).sortedBy { it.order }
    }

    /** 展示行按午别分组（保持首次出现的顺序）。 */
    fun rowGroups(): List<Pair<String, List<TimeTableRow>>> {
        val order = LinkedHashMap<String, MutableList<TimeTableRow>>()
        rows().forEach { row -> order.getOrPut(row.section) { mutableListOf() }.add(row) }
        return order.map { it.key to it.value.toList() }
    }

    /** 节次与时间点的全部键，用于"显示哪些项目"的全选。 */
    fun allVisibilityKeys(): Set<String> =
        (periods.map { it.visibilityKey } + timePoints.map { it.visibilityKey }).toSet()

    /** 按午别分组的节次（不含时间点）。 */
    fun sectionsWithPeriods(): List<Pair<String, List<Period>>> = groupedBySection()

    companion object {
        /** 在没有导入任何时间表时使用的示例作息，便于新用户直接看到效果。 */
        fun sample(): TimeTable = TimeTable(
            name = "默认作息",
            periods = listOf(
                Period(1, "第1节", "08:00", "08:45", "上午"),
                Period(2, "第2节", "08:55", "09:40", "上午"),
                Period(3, "第3节", "10:00", "10:45", "上午"),
                Period(4, "第4节", "10:55", "11:40", "上午"),
                Period(5, "第5节", "14:00", "14:45", "下午"),
                Period(6, "第6节", "14:55", "15:40", "下午"),
                Period(7, "第7节", "16:00", "16:45", "下午"),
                Period(8, "第8节", "19:00", "19:45", "晚上"),
                Period(9, "第9节", "19:55", "20:40", "晚上")
            )
        )
    }
}

/**
 * 一门课程的基础信息。
 */
@Serializable
data class Course(
    val id: String,
    val name: String,
    /** 任课教师。 */
    val teacher: String = "",
    /** 默认上课地点（教室）。 */
    val roomId: String = "",
    /** 主题色索引，对应设计系统里的课程色板。 */
    val colorIndex: Int = 0,
    val note: String = ""
)

/**
 * 生效的周次范围（含端点）。
 *
 * 刻意不用 Kotlin 的 `IntRange`：它不是 kotlinx.serialization 支持的类型，
 * 会直接编译失败（Serializer has not been found for type 'IntRange?'）。
 * 这里用一个普通数据类，既能序列化成 `{"start":5,"end":20}`，也保留了 `in` 语法。
 */
@Serializable
data class WeekRange(
    val start: Int,
    val end: Int
) {
    operator fun contains(week: Int): Boolean = week in start..end

    /** 便于展示，例如“第 5-20 周”。 */
    val label: String get() = if (start == end) "第 $start 周" else "第 $start-$end 周"

    companion object {
        /** 由 IntRange 构造，便于调用方沿用 `5..20` 的写法。 */
        fun of(range: IntRange): WeekRange = WeekRange(range.first, range.last)
    }
}

/** 让 `WeekRange(5, 20)` 与 `5..20` 都能直接用的辅助函数。 */
fun weekRangeOf(range: IntRange): WeekRange = WeekRange.of(range)

/**
 * 课程表的一条记录：某周、周几、第几节上哪门课。
 */
@Serializable
data class CourseEntry(
    val courseId: String,
    /** 星期几：1=周一 … 7=周日。 */
    val dayOfWeek: Int,
    /** 节次序号，可跨多节（例如实训课连上 3、4 节）。 */
    val periodIndex: Int,
    /** 生效的周次范围（含端点），null 表示全学期。 */
    val weekRange: WeekRange? = null,
    /** 覆盖默认上课地点，为空时使用 [Course.roomId]。 */
    val roomIdOverride: String = ""
) {
    fun matchesWeek(week: Int): Boolean {
        val range = weekRange ?: return true
        return week in range
    }
}

/**
 * 课程表：周几 × 节次 的排课结果。
 */
@Serializable
data class CourseTable(
    val courses: List<Course> = emptyList(),
    val entries: List<CourseEntry> = emptyList()
) {
    private val courseById: Map<String, Course> by lazy { courses.associateBy { it.id } }

    fun course(courseId: String): Course? = courseById[courseId]

    /** 取某周某天某节的排课，可能有多条（合班/分组）。 */
    fun entriesAt(dayOfWeek: Int, periodIndex: Int, week: Int? = null): List<CourseEntry> =
        entries.filter { it.dayOfWeek == dayOfWeek && it.periodIndex == periodIndex }
            .filter { week == null || it.matchesWeek(week) }

    /** 取某周某天的全部排课，按节次升序。 */
    fun entriesOfDay(dayOfWeek: Int, week: Int? = null): List<CourseEntry> =
        entries.filter { it.dayOfWeek == dayOfWeek }
            .filter { week == null || it.matchesWeek(week) }
            .sortedBy { it.periodIndex }

    /** 某条记录实际生效的教室 id。 */
    fun effectiveRoomId(entry: CourseEntry): String =
        entry.roomIdOverride.ifBlank { courseById[entry.courseId]?.roomId.orEmpty() }

    /** 整张表里出现过的所有教室 id，用于导航页搜索。 */
    fun referencedRoomIds(): Set<String> =
        entries.map { effectiveRoomId(it) }.filter { it.isNotBlank() }.toSet()
}
