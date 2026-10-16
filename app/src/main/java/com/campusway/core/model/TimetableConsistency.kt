package com.campusway.core.model

/**
 * 时间表与课程表的一致性检查。
 *
 * 用途：导入时间表前，确认新时间表每个午别的上课节次数不少于课程表**在该午别已经用到的**节次数。
 * 否则那些排课会挂到不存在的节次上（界面上表现为课程凭空消失），所以缺口时要提示用户先补足，
 * 而不是静默丢课。
 */
object TimetableConsistency {

    private fun sectionOf(period: Period): String = period.section.ifBlank { "其他" }

    /** 每个午别的上课节次数。 */
    fun periodsBySection(table: TimeTable): Map<String, Int> =
        table.periods.groupingBy { sectionOf(it) }.eachCount()

    /**
     * 课程表在每个午别用到的节次数。
     *
     * 节次的午别取自 [timeTable]（课程本身不带午别）；同一节次被多门课占用只算一次。
     */
    fun usedPeriodsBySection(courseTable: CourseTable, timeTable: TimeTable): Map<String, Int> {
        val sectionByIndex = timeTable.periods.associate { it.index to sectionOf(it) }
        val used = HashMap<String, MutableSet<Int>>()
        courseTable.entries.forEach { entry ->
            val section = sectionByIndex[entry.periodIndex] ?: return@forEach
            used.getOrPut(section) { mutableSetOf() } += entry.periodIndex
        }
        return used.mapValues { it.value.size }
    }

    /**
     * 导入时间表前的缺口提示：新时间表每个午别的节次数不足课程表已用到的数量时给出说明。
     * 返回空列表表示没有缺口、可以导入。
     */
    fun importProblems(
        courseTable: CourseTable,
        currentTimeTable: TimeTable,
        newTimeTable: TimeTable
    ): List<String> {
        val used = usedPeriodsBySection(courseTable, currentTimeTable)
        val available = periodsBySection(newTimeTable)
        return used.mapNotNull { (section, need) ->
            val have = available[section] ?: 0
            if (need > have) {
                "「$section」课程表已用到 $need 节，新时间表只有 $have 节；" +
                    "请多勾选一些项目，或先在「编辑时间表」补足"
            } else null
        }
    }
}
