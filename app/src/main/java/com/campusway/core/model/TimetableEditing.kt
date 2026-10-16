package com.campusway.core.model

/**
 * 编辑时间表/课程表后的结果。
 *
 * 两者可能一起变化：增删一个上课节次时，课程表里 `CourseEntry.periodIndex`
 * 引用的序号也要同步，否则课程会挂到空档上。
 */
data class TimetableEditResult(
    val timeTable: TimeTable,
    val courseTable: CourseTable
)

/**
 * 时间表与课程表的人工编辑变换。
 *
 * 全部是纯函数：输入旧值、返回新值，不碰磁盘、不依赖 Android，可直接单测。
 *
 * 约定：上课节次序号必须 1..K 连续（见 [TimeTable.validate]）。因此增删节次后
 * 会整体重新编号，并把课程表里引用到的节次序号一起重映射。
 */
object TimetableEditing {

    /** 默认命名「第 N 节」（允许空格）。只有这种名字会随序号重排，自定义名称不动。 */
    private val DEFAULT_PERIOD_NAME = Regex("^第\\s*\\d+\\s*节$")

    private fun renumberDefaultNames(periods: List<Period>): List<Period> =
        periods.map { period ->
            if (DEFAULT_PERIOD_NAME.matches(period.name)) period.copy(name = "第 ${period.index} 节") else period
        }

    // ---------------------------------------------------------------- 节次（时间表）

    /** 修改一个节次的名称/起止时间/午别，序号不变。 */
    fun updatePeriod(table: TimeTable, period: Period): TimeTable =
        table.copy(periods = table.periods.map { if (it.index == period.index) period else it })

    /**
     * 在 [afterIndex] 之后插入一个空节次（为 null 表示插到最前面）。
     * 之后（含新节次）的序号整体 +1，课程表引用的节次同步 +1。
     *
     * @return 新数据，以及新节次的序号。
     */
    fun addPeriodAfter(
        table: TimeTable,
        courseTable: CourseTable,
        afterIndex: Int?,
        name: String = "",
        start: String = "",
        end: String = "",
        section: String = ""
    ): Pair<TimetableEditResult, Int> {
        val sorted = table.periods.sortedBy { it.index }
        val insertAt = if (afterIndex == null) {
            0
        } else {
            val pos = sorted.indexOfFirst { it.index == afterIndex }
            if (pos < 0) sorted.size else pos + 1
        }
        val newIndex = insertAt + 1

        val renumbered = sorted.mapIndexed { i, period ->
            val oldPosition = i + 1
            period.copy(index = if (oldPosition >= newIndex) oldPosition + 1 else oldPosition)
        }
        val inserted = Period(
            index = newIndex,
            name = name.ifBlank { "第 $newIndex 节" },
            start = start,
            end = end,
            section = section
        )
        val newPeriods = renumberDefaultNames(
            renumbered.take(insertAt) + inserted + renumbered.drop(insertAt)
        )

        val newEntries = courseTable.entries.map { entry ->
            if (entry.periodIndex >= newIndex) entry.copy(periodIndex = entry.periodIndex + 1) else entry
        }
        val result = TimetableEditResult(
            timeTable = table.copy(periods = newPeriods),
            courseTable = courseTable.copy(entries = newEntries)
        )
        return result to newIndex
    }

    /**
     * 删除一个节次：其后的节次序号整体 -1，课程同步 -1；
     * 该节次上的排课一并移除（课程本身保留）。
     */
    fun removePeriod(table: TimeTable, courseTable: CourseTable, index: Int): TimetableEditResult {
        val newPeriods = renumberDefaultNames(
            table.periods
                .filterNot { it.index == index }
                .sortedBy { it.index }
                .mapIndexed { i, period -> period.copy(index = i + 1) }
        )
        val newEntries = courseTable.entries
            .filterNot { it.periodIndex == index }
            .map { entry -> if (entry.periodIndex > index) entry.copy(periodIndex = entry.periodIndex - 1) else entry }
        return TimetableEditResult(table.copy(periods = newPeriods), courseTable.copy(entries = newEntries))
    }

    // ---------------------------------------------------------------- 时间点（时间表）

    /** 修改第 [position] 个时间点（起床/早餐/大课间…），身份按位置定位。 */
    fun updateTimePointAt(table: TimeTable, position: Int, point: TimePoint): TimeTable =
        table.copy(timePoints = table.timePoints.mapIndexed { i, p -> if (i == position) point else p })

    /** 删除第 [position] 个时间点。 */
    fun removeTimePointAt(table: TimeTable, position: Int): TimeTable =
        table.copy(timePoints = table.timePoints.filterIndexed { i, _ -> i != position })

    /** 追加一个时间点。 */
    fun addTimePoint(table: TimeTable, point: TimePoint = TimePoint(name = "", start = "")): TimeTable =
        table.copy(timePoints = table.timePoints + point)
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
