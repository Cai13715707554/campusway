package com.campusway.core.importer

import com.campusway.core.model.Period
import com.campusway.core.model.TimeOfDay
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
    /**
     * 表格里本身就写了起止时间的项目键。
     *
     * 「第一节 8:00——8:45」这种是有明确区间的；「起床 06:40」只是一个时点。
     * 用来在没有可识别的"节次名"时兜底预勾选。
     */
    val rangedKeys: Set<String> = emptySet(),
    val warnings: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = timeTable.periods.isEmpty()

    val periodCount: Int get() = timeTable.periods.size

    val summary: String
        get() = "识别到 ${sections.size} 个午别（${sections.joinToString("、")}）、$periodCount 个项目"

    /**
     * 默认勾选哪些项目。
     *
     * 优先选"像上课节次"的（第一节、晚修第二节…），这样课程能直接挂到正确的节次上；
     * 一个都没有时退回"表格里写了起止时间的项目"；再没有就全选。
     */
    fun defaultSelection(): Set<String> {
        val likely = timeTable.periods.filter { likelyClassPeriod(it.name) }.map { it.visibilityKey }.toSet()
        return when {
            likely.isNotEmpty() -> likely
            rangedKeys.isNotEmpty() -> rangedKeys
            else -> timeTable.periods.map { it.visibilityKey }.toSet()
        }
    }
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
            return ImportedPeriodTable(TimeTable(name = name), emptyList(), listOf("表格是空的"))
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
        data class Raw(val section: String, val name: String, val start: Int?, val end: Int?)

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

            val (start, end) = parseTime(timeText)
            if (start == null) {
                warnings += "「$rawName」的时间无法识别（$timeText），已跳过"
                return@forEach
            }
            raws += Raw(section = section.ifBlank { "未分组" }, name = rawName, start = start, end = end)
        }

        if (raws.isEmpty()) {
            return ImportedPeriodTable(
                TimeTable(name = name),
                emptyList(),
                warnings + "没有解析到任何项目，请检查「项目」「时间」两列"
            )
        }

        // 3) 补全结束时间：只有时点的项目用下一项的开始时间收尾，最后一项 +10 分钟
        val periods = raws.mapIndexed { index, raw ->
            val end = raw.end ?: raws.getOrNull(index + 1)?.start?.takeIf { it > raw.start }
                ?: (raw.start + DEFAULT_TAIL_MINUTES)
            Period(
                index = index + 1,
                name = raw.name,
                start = TimeOfDay.format(raw.start),
                end = TimeOfDay.format(if (end > raw.start) end else raw.start + 5),
                section = raw.section
            )
        }

        val timeTable = TimeTable(name = name, periods = periods)
        warnings += timeTable.validate()

        return ImportedPeriodTable(
            timeTable = timeTable,
            sections = periods.map { it.section }.distinct(),
            // 明确写了起止时间的项目（raws 里 end 不为空的那些）
            rangedKeys = raws.filter { it.end != null }.map { "${it.section}|${it.name}" }.toSet(),
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
