package com.campusway.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
}

/**
 * 课程时间表：全校统一的一份节次划分。
 */
@Serializable
data class TimeTable(
    /** 时间表名称，例如“冬季作息”“夏季作息”。 */
    val name: String = "默认作息",
    val periods: List<Period> = emptyList()
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
    @SerialName("weeks") val weekRange: IntRange? = null,
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
