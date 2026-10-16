package com.campusway.core.model

/**
 * 编辑时间表/课程表后的结果。
 *
 * 两者可能一起变化：增删节次、或把节次改成时间点，都会影响课程表里
 * `CourseEntry.periodIndex` 引用的序号，所以一起返回。
 */
data class TimetableEditResult(
    val timeTable: TimeTable,
    val courseTable: CourseTable
)

/**
 * 时间表编辑页里的一行：要么是上课节次，要么是时间点。
 *
 * - [key]：一次编辑会话内的稳定标识，供列表复用；
 * - [originalIndex]：这一行原本是哪个节次序号（新建的、或本来就是时间点的为 null）；
 *   保存时用它把课程表里引用的旧序号重映射到新序号，避免课程挂错节次；
 * - [timeText]：一格时间文本，如 `08:00 - 08:45` 或 `06:40`，与首页展示一致。
 */
data class TimeItem(
    val key: String,
    val originalIndex: Int?,
    val isPeriod: Boolean,
    val name: String = "",
    val timeText: String = "",
    val section: String = ""
)

/**
 * 时间表与课程表的人工编辑变换。
 *
 * 全部是纯函数：输入旧值、返回新值，不碰磁盘、不依赖 Android，可直接单测。
 */
object TimetableEditing {

    /** 默认命名「第 N 节」（允许空格）；保存时这类名字随序号重排，自定义名称不动。 */
    private val DEFAULT_PERIOD_NAME = Regex("^第\\s*\\d+\\s*节$")

    /** 时间区间里的分隔符：`-`、`~`、`—`、`～` 等。 */
    private val RANGE_SEPARATOR = Regex("\\s*[-~～—–－_]\\s*")

    /** 把时间文本拆成 (开始, 结束)；只有一个时刻时结束为空串。 */
    fun splitTime(text: String): Pair<String, String> {
        val parts = text.trim().split(RANGE_SEPARATOR).filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "" to ""
            parts.size == 1 -> parts[0] to ""
            else -> parts[0] to parts[1]
        }
    }

    /** 一行展示用的起始分钟数，用于排序；解析不出来排最后。 */
    private fun startMinutes(item: TimeItem): Int {
        val start = item.timeText.trim().split(RANGE_SEPARATOR).firstOrNull().orEmpty()
        return TimeOfDay.parseToMinutes(start).takeIf { it >= 0 } ?: Int.MAX_VALUE
    }

    /**
     * 把时间表拆成编辑用的行：节次与时间点合并后按午别分组（午别按首次出现顺序），
     * 组内按开始时间排序——与课表页「课程时间表」的排布一致。
     */
    fun itemsOf(table: TimeTable): List<TimeItem> {
        val items = mutableListOf<TimeItem>()
        table.periods.forEach { period ->
            items += TimeItem(
                key = "p-${period.index}",
                originalIndex = period.index,
                isPeriod = true,
                name = period.name,
                timeText = "${period.start} - ${period.end}",
                section = period.section.ifBlank { "其他" }
            )
        }
        table.timePoints.forEachIndexed { position, point ->
            items += TimeItem(
                key = "t-$position",
                originalIndex = null,
                isPeriod = false,
                name = point.name,
                timeText = if (point.end.isBlank()) point.start else "${point.start} - ${point.end}",
                section = point.section.ifBlank { "其他" }
            )
        }

        val groups = LinkedHashMap<String, MutableList<TimeItem>>()
        items.sortedBy { startMinutes(it) }.forEach { item ->
            groups.getOrPut(item.section) { mutableListOf() } += item
        }
        return groups.values.flatten()
    }

    /**
     * 保存编辑结果：按当前行顺序给节次重新编号 1..K，
     * 把课程表里引用的节次序号重映射过去；被删掉、或被改成时间点的节次，
     * 它上面的排课一并丢弃。
     */
    fun applyItems(original: TimeTable, courseTable: CourseTable, items: List<TimeItem>): TimetableEditResult {
        val periodItems = items.filter { it.isPeriod }
        val pointItems = items.filterNot { it.isPeriod }

        val remap = HashMap<Int, Int>()
        val periods = periodItems.mapIndexed { i, item ->
            val index = i + 1
            item.originalIndex?.let { remap[it] = index }
            val (start, end) = splitTime(item.timeText)
            Period(
                index = index,
                name = if (DEFAULT_PERIOD_NAME.matches(item.name)) "第 $index 节" else item.name,
                start = start,
                end = end,
                section = item.section
            )
        }

        val entries = courseTable.entries.mapNotNull { entry ->
            remap[entry.periodIndex]?.let { entry.copy(periodIndex = it) }
        }

        val timePoints = pointItems.map { item ->
            val (start, end) = splitTime(item.timeText)
            TimePoint(name = item.name, start = start, end = end, section = item.section)
        }

        return TimetableEditResult(
            timeTable = original.copy(periods = periods, timePoints = timePoints),
            courseTable = courseTable.copy(entries = entries)
        )
    }
}

/**
 * 课程表（课程信息 + 排课）的人工编辑变换。
 */
object CourseEditing {

    /** 新增或更新一门课程（按 id 判断）。 */
    fun upsertCourse(table: CourseTable, course: Course): CourseTable {
        val exists = table.courses.any { it.id == course.id }
        val courses = if (exists) {
            table.courses.map { if (it.id == course.id) course else it }
        } else {
            table.courses + course
        }
        return table.copy(courses = courses)
    }

    /** 删除一门课程，并连带删除它的全部排课。 */
    fun removeCourse(table: CourseTable, courseId: String): CourseTable =
        table.copy(
            courses = table.courses.filterNot { it.id == courseId },
            entries = table.entries.filterNot { it.courseId == courseId }
        )

    /** 设置某天某节上哪门课；[courseId] 为 null 表示清空这一格。 */
    fun setEntry(table: CourseTable, dayOfWeek: Int, periodIndex: Int, courseId: String?): CourseTable {
        val kept = table.entries.filterNot { it.dayOfWeek == dayOfWeek && it.periodIndex == periodIndex }
        return if (courseId == null) {
            table.copy(entries = kept)
        } else {
            table.copy(entries = kept + CourseEntry(courseId = courseId, dayOfWeek = dayOfWeek, periodIndex = periodIndex))
        }
    }

    /** 生成一个不与现有课程冲突的新课程 id。 */
    fun newCourseId(existing: Collection<Course>): String {
        val used = existing.map { it.id }.toSet()
        var n = existing.size + 1
        var id = "course-$n"
        while (id in used) {
            n++
            id = "course-$n"
        }
        return id
    }

    /**
     * 按名称/简称在校园里匹配房间；匹配不到返回空串。
     *
     * 与导入时 `课程名@地点` 的匹配规则保持一致。
     */
    fun matchRoomId(campus: Campus?, location: String): String {
        val text = location.trim()
        if (campus == null || text.isEmpty()) return ""
        campus.buildings.forEach { building ->
            building.floors.forEach { floor ->
                floor.rooms.forEach { room ->
                    if (room.name == text || room.shortName == text) return room.id
                }
            }
        }
        return ""
    }
}
