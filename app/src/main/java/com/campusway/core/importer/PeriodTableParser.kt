package com.campusway.core.importer

import com.campusway.core.model.Period
import com.campusway.core.model.TimeOfDay
import com.campusway.core.model.TimePoint
import com.campusway.core.model.TimeTable
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 时间表（作息表）的解析结果。
 */
data class ImportedPeriodTable(
    val timeTable: TimeTable,
    /** 识别到的午别，按出现顺序。 */
    val sections: List<String>,
    val warnings: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = timeTable.isEmpty

    /** 上课节次数量。 */
    val periodCount: Int get() = timeTable.periods.size

    /** 时间点数量（起床、大课间、熄灯这类，只在时间表里显示）。 */
    val timePointCount: Int get() = timeTable.timePoints.size

    /** 所有项目的数量。 */
    val totalCount: Int get() = periodCount + timePointCount

    val summary: String
        get() = "识别到 ${sections.size} 个午别（${sections.joinToString("、")}）、" +
            "$periodCount 个上课节次、$timePointCount 个时间点"

    /**
     * 默认勾选哪些项目：**全部**。
     *
     * 因为时间点已经不影响课程表了，全勾上最直观（用户看到的作息表最完整），
     * 想精简就整组取消某个午别。
     */
    fun defaultSelection(): Set<String> = timeTable.allVisibilityKeys()
}

/**
 * 项目名是否像"上课的节次"。
 *
 * 例如 第一节 / 晚修第二节 / 早读 / 自习 都算；
 * 起床 / 早餐 / 大课间 / 眼保健操 / 熄灯 这类生活作息不算。
 * 这条判断只用于**预勾选**，用户随时可以改，所以宁可宽松也不要漏掉真正的课。
 */
fun likelyClassPeriod(name: String): Boolean {
    val n = name.trim()
    if (n.isEmpty()) return false
    if (Regex("第\\s*[一二三四五六七八九十\\d]+\\s*节").containsMatchIn(n)) return true
    return listOf("早读", "晨读", "自习", "晚修", "实训", "上课").any { n.contains(it) }
}

/**
 * 把"表格式的时间表"解析成 [TimeTable]。
 *
 * 对应 `docs/时间表.xlsx` 的形态：
 * ```
 * 午别    项目        时间
 * 上午    起床        0.277777…     ← Excel 时间序列值，需要换算
 *         晨练        7:40——7:55    ← 文本区间
 *         第一节       8:00——8:45
 * 下午    午休        12:30——13:50
 * 晚上    晚餐        0.75
 * ```
 *
 * 处理要点：
 *  - **午别纵向合并**：xlsx 读取时已把合并区域铺开；但粘贴/CSV 里没有合并，
 *    所以这里再做一次向下填充，否则只有每组第一行有午别。
 *  - **两种时间写法**：`8:00——8:45` 这类文本区间，以及 Excel 的时间序列值
 *    （0..1 的小数，表示一天中的比例）。后者不换算的话时间会全错。
 *  - **只有时点的项目**（起床、早餐、午餐、晚餐、熄灯…）没有结束时间，
 *    用下一项的开始时间收尾，最后一项按 +10 分钟处理，得到连续的时间轴。
 *  - 午别有哪几种完全由表格决定，不写死「上午/下午/晚上」。
 */
object PeriodTableParser {

    /** 文本区间：`8:00——8:45`、`8:00-8:45`、`8：00～8：45` 等写法都吃。 */
    private val RANGE_REGEX = Regex(
        "(\\d{1,2})\\s*[:：]\\s*(\\d{2})\\s*[-~～—–－_]{1,3}\\s*(\\d{1,2})\\s*[:：]\\s*(\\d{2})"
    )

    /** 单个时点：`7:40`、`07:40`。 */
    private val POINT_REGEX = Regex("^(\\d{1,2})\\s*[:：]\\s*(\\d{2})$")

    /** Excel 时间序列值：0 到 1 之间的小数（也可能带日期整数部分）。 */
    private val SERIAL_REGEX = Regex("^\\d+(\\.\\d+)?$")

    private val SECTION_HEADERS = listOf("午别", "时段", "半天", "上下午")
    private val NAME_HEADERS = listOf("项目", "节次", "名称", "内容")
    private val TIME_HEADERS = listOf("时间", "时刻")

    fun parse(grid: List<List<String>>, name: String = "导入的作息"): ImportedPeriodTable {
        val rows = grid.map { row -> row.map { it.trim() } }
            .filter { row -> row.any { it.isNotBlank() } }

        if (rows.isEmpty()) {
            // 统一用命名参数：这个类的字段顺序调整过一次，
            // 当时按位置传参把"提示列表"错传给了另一个 Set 字段，编译不过。
            return ImportedPeriodTable(
                timeTable = TimeTable(name = name),
                sections = emptyList(),
                warnings = listOf("表格是空的")
            )
        }

        val warnings = mutableListOf<String>()

        // 1) 找表头：至少能认出「项目/节次」与「时间」两类列
        val headerIndex = rows.indexOfFirst { row ->
            row.any { text -> NAME_HEADERS.any { text.contains(it) } } &&
                row.any { text -> TIME_HEADERS.any { text.contains(it) } }
        }

        val sectionCol: Int
        val nameCol: Int
        val timeCol: Int

        if (headerIndex < 0) {
            // 没有表头时按最常见的三列布局兜底，但必须能解析出时间才认可
            sectionCol = 0
            nameCol = 1
            timeCol = 2
            warnings += "没找到表头（应包含「午别」「项目」「时间」），已按前三列解析，请核对结果"
        } else {
            val header = rows[headerIndex]
            sectionCol = header.indexOfFirst { text -> SECTION_HEADERS.any { text.contains(it) } }
            nameCol = header.indexOfFirst { text -> NAME_HEADERS.any { text.contains(it) } }
            timeCol = header.indexOfFirst { text -> TIME_HEADERS.any { text.contains(it) } }
        }

        val dataRows = rows.drop(if (headerIndex >= 0) headerIndex + 1 else 0)

        // 2) 读成 (午别, 项目, 起始分钟, 结束分钟?)
        // start 用非空 Int：只有解析出开始时间才会构造 Raw，
        // 声明成 Int? 会让后面每次用到都要判空。
        data class Raw(val section: String, val name: String, val start: Int, val end: Int?)

        val raws = mutableListOf<Raw>()
        var lastSection = ""

        dataRows.forEach { row ->
            val sectionText = row.getOrNull(sectionCol)?.trim().orEmpty()
            val rawName = row.getOrNull(nameCol)?.trim().orEmpty()
            val timeText = row.getOrNull(timeCol)?.trim().orEmpty()

            // 午别向下填充：合并单元格铺开过就会有值，粘贴/CSV 则只有第一行有
            val section = when {
                sectionText.isNotBlank() -> sectionText.also { lastSection = it }
                else -> lastSection
            }
            if (rawName.isBlank()) return@forEach

            val (startMinute, endMinute) = parseTime(timeText)
            if (startMinute == null) {
                warnings += "「$rawName」的时间无法识别（$timeText），已跳过"
                return@forEach
            }
            raws += Raw(
                section = section.ifBlank { "未分组" },
                name = rawName,
                start = startMinute,
                end = endMinute
            )
        }

        if (raws.isEmpty()) {
            return ImportedPeriodTable(
                timeTable = TimeTable(name = name),
                sections = emptyList(),
                warnings = warnings + "没有解析到任何项目，请检查「项目」「时间」两列"
            )
        }

        // 3) 分成"上课节次"与"时间点"两类。
        //    只有名称是「第 N 节」这种格式的才算上课节次（课程挂在它上面）；
        //    起床/早餐/大课间/熄灯这类只作为时间点，仅在时间表里显示，不影响课程表。
        val classRaws = raws.filter { likelyClassPeriod(it.name) }
        val pointRaws = raws.filterNot { likelyClassPeriod(it.name) }

        // 上课节次需要起止时间：表格里没写结束时间时，用下一项的起始时间收尾
        //（注意用完整序列找"下一项"，这样 教师上办公 07:10 → 晨练 07:40 也能算对）。
        val periods = classRaws.mapIndexed { i, raw ->
            val rawIndex = raws.indexOf(raw)
            val explicitEnd = raw.end
            val fromNext = raws.getOrNull(rawIndex + 1)?.start?.takeIf { it > raw.start }
            val end = explicitEnd ?: fromNext ?: (raw.start + DEFAULT_TAIL_MINUTES)
            Period(
                index = i + 1,
                name = raw.name,
                start = TimeOfDay.format(raw.start),
                end = TimeOfDay.format(if (end > raw.start) end else raw.start + 5),
                section = raw.section
            )
        }

        // 时间点只展示时刻：表格里写了区间的就显示区间，否则只显示一个时间
        val timePoints = pointRaws.map { raw ->
            TimePoint(
                name = raw.name,
                start = TimeOfDay.format(raw.start),
                end = raw.end?.takeIf { it > raw.start }?.let { TimeOfDay.format(it) }.orEmpty(),
                section = raw.section
            )
        }

        val timeTable = TimeTable(name = name, periods = periods, timePoints = timePoints)
        warnings += timeTable.validate()

        return ImportedPeriodTable(
            timeTable = timeTable,
            sections = raws.map { it.section }.distinct(),
            warnings = warnings.distinct()
        )
    }

    /** 最后一项没有结束时间时，往后补的分钟数。 */
    private const val DEFAULT_TAIL_MINUTES = 10

    /**
     * 解析时间单元格，返回 (起始分钟, 结束分钟?)。识别不出返回 (null, null)。
     */
    fun parseTime(text: String): Pair<Int?, Int?> {
        val t = text.trim()
        if (t.isEmpty()) return null to null

        RANGE_REGEX.find(t)?.let { m ->
            val start = toMinutes(m.groupValues[1], m.groupValues[2])
            val end = toMinutes(m.groupValues[3], m.groupValues[4])
            if (start != null && end != null) return start to end
        }

        POINT_REGEX.find(t)?.let { m ->
            val start = toMinutes(m.groupValues[1], m.groupValues[2])
            if (start != null) return start to null
        }

        // Excel 时间序列值：0.277777… 表示一天中的比例
        if (SERIAL_REGEX.matches(t)) {
            val value = t.toDoubleOrNull()
            if (value != null) {
                val fraction = value - floor(value)
                if (fraction in 0.0..1.0) {
                    return (fraction * 24 * 60).roundToInt().coerceIn(0, 24 * 60 - 1) to null
                }
            }
        }

        return null to null
    }

    private fun toMinutes(hourText: String, minuteText: String): Int? {
        val hour = hourText.toIntOrNull() ?: return null
        val minute = minuteText.toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }
}
