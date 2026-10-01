package com.campusway.core.importer

import com.campusway.core.model.COURSE_COLOR_COUNT
import com.campusway.core.model.Campus
import com.campusway.core.model.Course
import com.campusway.core.model.CourseEntry
import com.campusway.core.model.CourseTable
import com.campusway.core.model.Period
import com.campusway.core.model.TimeTable

/**
 * 表格导入的结果。
 */
data class ImportedTimetable(
    val timeTable: TimeTable,
    val courseTable: CourseTable,
    /** 面向用户的提示：识别到的问题、被忽略的内容等。 */
    val warnings: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = courseTable.entries.isEmpty()

    val courseCount: Int get() = courseTable.courses.size

    val periodCount: Int get() = timeTable.periods.size

    /** 识别到的星期数量。 */
    val dayCount: Int get() = courseTable.entries.map { it.dayOfWeek }.distinct().size

    /** 一句话摘要，用于导入预览。 */
    val summary: String
        get() = "识别到 $dayCount 天、$periodCount 个节次、$courseCount 门课程"
}

/**
 * 把"表格式的功课表"解析成课程表。
 *
 * 支持的表格形态（两种都常见）：
 *
 * **A. 表头第一格就是星期**（docs/功课表.xlsx 就是这种）
 * ```
 *        星期一  星期二  星期三  星期四  星期五
 * 早读
 * 上午
 * 数学    英语    视频    数学    动漫
 * ...
 * 下午
 * 视频    动漫    社团    语文    数学
 * ```
 *
 * **B. 第一列是节次/时间，后面才是星期**
 * ```
 * 节次        星期一  星期二
 * 第1节       数学    英语
 * 08:55-09:40 语文    哲学
 * ```
 *
 * 规则：
 *  - 表头行 = 第一个能认出 ≥2 个星期名的行；认出星期名的列即为该天的列。
 *  - 若星期列不从第 0 列开始，则第 0 列是"节次/时间"列。
 *  - **时段行**：整行只有一处文字、且该文字是时段关键词（上午/下午/晚上…）时，
 *    它是时段分隔，不是课程；其后各节都归入该时段。
 *  - 节次名取"节次/时间"列的内容；没有该列时按顺序命名为第 1 节、第 2 节…
 *  - 单元格里可以用 `课程名@地点` 指定上课地点；若地点能在校园数据里找到同名房间，
 *    会自动关联到该房间（用于楼内导航），否则记入备注。
 */
object TimetableGridParser {

    /** 时段关键词。整行只有它时视为时段分隔。 */
    private val SECTION_KEYWORDS = listOf(
        "上午", "下午", "晚上", "中午", "晚间", "早上", "早晨", "傍晚", "凌晨"
    )

    private val WEEKDAY_PATTERNS: List<Pair<Regex, Int>> = listOf(
        Regex("^星期([一二三四五六日天])$") to 0,
        Regex("^周([一二三四五六日天])$") to 0,
        Regex("^礼拜([一二三四五六日天])$") to 0,
        Regex("^(mon|monday|tue|tues|tuesday|wed|wednesday|thu|thur|thurs|thursday|fri|friday|sat|saturday|sun|sunday)\\.?$") to 1
    )

    private val TIME_RANGE = Regex("(\\d{1,2}:\\d{2})\\s*[-~～—–]\\s*(\\d{1,2}:\\d{2})")

    /**
     * 解析表格。
     *
     * @param grid 二维文本网格（行 × 列），空串表示空单元格
     * @param defaultTimes 没有自带时间时使用的作息；节次多于它时按 45 分钟递推生成
     * @param campus 可选：用于把 `@地点` 关联到校园里的房间
     */
    fun parse(
        grid: List<List<String>>,
        defaultTimes: TimeTable = TimeTable.sample(),
        campus: Campus? = null
    ): ImportedTimetable {
        val rows = normalise(grid)
        if (rows.isEmpty()) {
            return ImportedTimetable(defaultTimes, CourseTable(), listOf("表格是空的"))
        }

        val warnings = mutableListOf<String>()

        // 1) 找表头行
        val headerIndex = rows.indexOfFirst { countWeekdays(it) >= 2 }
        if (headerIndex < 0) {
            return ImportedTimetable(
                defaultTimes,
                CourseTable(),
                listOf("没找到表头：第一行需要包含「星期一…星期日」或「周一…周日」这样的星期名（至少两个）")
            )
        }
        val header = rows[headerIndex]

        // 2) 列 -> 星期
        val dayColumns = LinkedHashMap<Int, Int>() // 列号 -> 星期(1..7)
        header.forEachIndexed { col, text ->
            weekdayOf(text)?.let { dayColumns[col] = it }
        }
        if (dayColumns.size < 2) {
            return ImportedTimetable(
                defaultTimes,
                CourseTable(),
                listOf("只认出了 ${dayColumns.size} 个星期列，至少需要两个")
            )
        }
        val duplicatedDays = dayColumns.values.groupBy { it }.filterValues { it.size > 1 }.keys
        if (duplicatedDays.isNotEmpty()) {
            warnings += "表头里重复出现了星期${duplicatedDays.joinToString("、")}，只取第一列"
        }
        // 去掉重复的星期列
        val uniqueDayColumns = LinkedHashMap<Int, Int>()
        val seenDays = HashSet<Int>()
        dayColumns.forEach { (col, day) ->
            if (seenDays.add(day)) uniqueDayColumns[col] = day
        }

        // 3) 是否有独立的"节次/时间"列
        val firstDayCol = uniqueDayColumns.keys.min()
        val labelColumn = if (firstDayCol > 0) 0 else null
        if (labelColumn == null) {
            warnings += "表头第一格就是星期，按「每一行一个节次」解析；节次名取自每行第一格"
        }

        // 4) 逐行解析
        val periods = mutableListOf<Period>()
        val courses = LinkedHashMap<String, Course>()
        val entries = mutableListOf<CourseEntry>()
        var currentSection = SECTION_KEYWORDS.first()
        var autoPeriodIndex = 0

        for (rowIndex in (headerIndex + 1) until rows.size) {
            val row = rows[rowIndex]
            val nonEmpty = row.withIndex().filter { it.value.isNotBlank() }
            if (nonEmpty.isEmpty()) continue

            val label = labelColumn?.let { row.getOrNull(it)?.trim().orEmpty() }.orEmpty()
            val dayCells = uniqueDayColumns.mapNotNull { (col, day) ->
                val text = row.getOrNull(col)?.trim().orEmpty()
                if (text.isBlank()) null else day to text
            }

            // 4a) 整行同一个时段词 -> 时段分隔
            val distinctTexts = nonEmpty.map { it.value }.distinct()
            if (distinctTexts.size == 1 && isSectionKeyword(distinctTexts.first())) {
                currentSection = distinctTexts.first()
                continue
            }

            // 4b) 只有"节次/时间"列有内容
            if (dayCells.isEmpty()) {
                if (label.isBlank()) continue
                if (isSectionKeyword(label)) {
                    currentSection = label
                } else {
                    // 当作一个没有排课的节次名（例如 早读、班会 这类整行活动）
                    autoPeriodIndex++
                    periods += buildPeriod(
                        index = autoPeriodIndex,
                        name = label,
                        section = currentSection,
                        defaultTimes = defaultTimes
                    )
                }
                continue
            }

            // 4c) 普通排课行
            autoPeriodIndex++
            // 有"节次/时间"列就用它的内容当节次名（能解析出时间就一并采用），
            // 否则按顺序编号。注意：没有节次列时，第一格本身是星期一的课程，不能拿来当节次名。
            val name = if (labelColumn != null) {
                label.ifBlank { "第 $autoPeriodIndex 节" }
            } else {
                "第 $autoPeriodIndex 节"
            }

            val period = buildPeriod(
                index = autoPeriodIndex,
                name = name,
                section = currentSection,
                defaultTimes = defaultTimes
            )
            periods += period
            if (!period.isValid) {
                warnings += "第 ${period.index} 节「${period.name}」没有可用时间，已按默认作息推算"
            }

            dayCells.forEach { (day, raw) ->
                val (courseName, location) = splitLocation(raw)
                if (courseName.isBlank()) return@forEach
                val courseId = courses.keys.firstOrNull { it == courseName } ?: courseName
                val roomId = resolveRoom(campus, location)
                val existing = courses[courseId]
                if (existing == null) {
                    courses[courseId] = Course(
                        id = courseId,
                        name = courseName,
                        roomId = roomId,
                        colorIndex = courses.size % COURSE_COLOR_COUNT,
                        note = if (location.isBlank() || roomId.isNotBlank()) "" else "地点：$location"
                    )
                } else if (existing.roomId.isBlank() && roomId.isNotBlank()) {
                    courses[courseId] = existing.copy(roomId = roomId)
                }
                entries += CourseEntry(
                    courseId = courseId,
                    dayOfWeek = day,
                    periodIndex = period.index
                )
            }
        }

        if (entries.isEmpty()) {
            warnings += "没有解析到任何课程，请检查表格里是否填了课程名"
        }

        val maxPeriodIndex = periods.maxOfOrNull { it.index } ?: 0
        val finalTimeTable = TimeTable(
            name = defaultTimes.name,
            periods = periods.sortedBy { it.index }.ifEmpty { defaultTimes.periods }
        ).let { table ->
            // 保证每个节次都有合法时间
            table.copy(periods = table.periods.map { it.ensureValid(defaultTimes) })
        }

        if (maxPeriodIndex > defaultTimes.periods.size) {
            warnings += "节次数（$maxPeriodIndex）超过默认作息的 ${defaultTimes.periods.size} 节，" +
                "超出部分的时间是按 45 分钟递推的，请在设置里核对"
        }

        return ImportedTimetable(
            timeTable = finalTimeTable,
            courseTable = CourseTable(courses = courses.values.toList(), entries = entries),
            warnings = warnings
        )
    }

    // ------------------------------------------------------------ 内部工具

    /** 去掉首尾空行、每格 trim。 */
    private fun normalise(grid: List<List<String>>): List<List<String>> =
        grid.map { row -> row.map { it.trim() } }
            .let { rows ->
                var last = rows.size - 1
                while (last >= 0 && rows[last].all { it.isBlank() }) last--
                if (last < 0) emptyList() else rows.subList(0, last + 1).toList()
            }

    private fun countWeekdays(row: List<String>): Int = row.count { weekdayOf(it) != null }

    /** 识别星期名，返回 1..7。 */
    fun weekdayOf(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty()) return null
        WEEKDAY_PATTERNS.forEach { (regex, kind) ->
            val m = regex.matchEntire(t.lowercase()) ?: return@forEach
            if (kind == 0) {
                return when (m.groupValues[1]) {
                    "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; else -> 7
                }
            }
            return when (m.groupValues[1].take(3)) {
                "mon" -> 1; "tue" -> 2; "wed" -> 3; "thu" -> 4; "fri" -> 5; "sat" -> 6; else -> 7
            }
        }
        // 兼容"星期一(第1-2节)"这类带后缀的写法
        Regex("^(星期|周|礼拜)([一二三四五六日天])").find(t)?.let { m ->
            return when (m.groupValues[2]) {
                "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; else -> 7
            }
        }
        return null
    }

    private fun isSectionKeyword(text: String): Boolean {
        val t = text.trim()
        return SECTION_KEYWORDS.any { t == it || t.startsWith(it) && t.length <= it.length + 2 }
    }

    /** 拆分 `课程名@地点`。 */
    private fun splitLocation(raw: String): Pair<String, String> {
        val at = raw.lastIndexOf('@')
        if (at <= 0 || at == raw.length - 1) return raw.trim() to ""
        return raw.substring(0, at).trim() to raw.substring(at + 1).trim()
    }

    /** 把地点文本关联到校园里的房间。 */
    private fun resolveRoom(campus: Campus?, location: String): String {
        if (campus == null || location.isBlank()) return ""
        campus.buildings.forEach { building ->
            building.floors.forEach { floor ->
                floor.rooms.forEach { room ->
                    if (room.name == location || room.shortName == location) return room.id
                }
            }
        }
        return ""
    }

    /** 生成节次：能从名称里解析出时间就用它，否则套用默认作息的对应节次。 */
    private fun buildPeriod(
        index: Int,
        name: String,
        section: String,
        defaultTimes: TimeTable
    ): Period {
        TIME_RANGE.find(name)?.let { m ->
            return Period(
                index = index,
                name = name,
                start = m.groupValues[1],
                end = m.groupValues[2],
                section = section
            )
        }
        val fallback = defaultTimes.period(index) ?: derivePeriod(index, defaultTimes)
        return Period(
            index = index,
            name = name,
            start = fallback.start,
            end = fallback.end,
            section = section
        )
    }

    /** 默认作息不够用时按 45 分钟一节、课间 10 分钟递推。 */
    private fun derivePeriod(index: Int, defaultTimes: TimeTable): Period {
        val lastEnd = defaultTimes.periods.maxOfOrNull { it.endMinuteOfDay } ?: (8 * 60)
        val start = lastEnd + 10 * (index - defaultTimes.periods.size).coerceAtLeast(1)
        return Period(
            index = index,
            name = "第 $index 节",
            start = com.campusway.core.model.TimeOfDay.format(start),
            end = com.campusway.core.model.TimeOfDay.format(start + 45),
            section = "其他"
        )
    }

    private fun Period.ensureValid(defaultTimes: TimeTable): Period =
        if (isValid) this else {
            val fallback = defaultTimes.period(index) ?: derivePeriod(index, defaultTimes)
            copy(start = fallback.start, end = fallback.end)
        }
}
