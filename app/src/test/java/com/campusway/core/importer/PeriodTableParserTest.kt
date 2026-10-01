package com.campusway.core.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 时间表解析测试。
 *
 * 网格就是 `docs/时间表.xlsx` 的真实内容（午别纵向合并、时间混合了
 * "文本区间"与"Excel 时间序列值"两种写法），所以这份测试同时锁住了格式约定。
 *
 * 最关键的一条不变量：**只有名称是「第 N 节」这种格式的才算上课节次**，
 * 起床/早餐/大课间/熄灯这类只作为时间点，不能影响课程表。
 */
class PeriodTableParserTest {

    /** xlsx 读取时合并区域已铺开，所以每一行都带午别。 */
    private val exampleGrid = listOf(
        listOf("午别", "项目", "时间"),
        listOf("上午", "起床", "0.27777777777777779"),
        listOf("上午", "早餐", "0.29166666666666669"),
        listOf("上午", "教师上办公", "0.2986111111111111"),
        listOf("上午", "晨练", "7:40——7:55"),
        listOf("上午", "第一节", "8:00——8:45"),
        listOf("上午", "大课间", "8:45——9:05"),
        listOf("上午", "第二节", "9:05——9:50"),
        listOf("上午", "第三节", "10:00——10:45"),
        listOf("上午", "眼保健操", "10:45——10:50"),
        listOf("上午", "第四节", "11:00——11:45"),
        listOf("上午", "午餐", "0.49305555555555558"),
        listOf("下午", "午休", "12:30——13:50"),
        listOf("下午", "教师上办公", "0.58333333333333337"),
        listOf("下午", "第五节", "14:20——15:05"),
        listOf("下午", "眼保健操", "15:05——15:10"),
        listOf("下午", "第六节", "15:20——16:05"),
        listOf("下午", "第七节", "16:15——17:00"),
        listOf("下午", "第八节", "17:10——17:55"),
        listOf("晚上", "晚餐", "0.75"),
        listOf("晚上", "晚上办公自习", "0.8125"),
        listOf("晚上", "晚修第一节", "19:40——20:25"),
        listOf("晚上", "晚修第二节", "20:35——21:20"),
        listOf("晚上", "熄灯", "0.92361111111111116")
    )

    @Test
    fun `解析示例时间表`() {
        val imported = PeriodTableParser.parse(exampleGrid)
        assertEquals(listOf("上午", "下午", "晚上"), imported.sections)
        assertEquals(23, imported.totalCount)
        assertFalse(imported.isEmpty)
    }

    @Test
    fun `只有第N节格式的才算上课节次`() {
        val imported = PeriodTableParser.parse(exampleGrid)
        val periodNames = imported.timeTable.periods.map { it.name }
        // 第一节到第八节、晚修第一二节，以及含"自习"的晚上办公自习
        assertEquals(
            listOf(
                "第一节", "第二节", "第三节", "第四节",
                "第五节", "第六节", "第七节", "第八节",
                "晚上办公自习", "晚修第一节", "晚修第二节"
            ),
            periodNames
        )
        // 起床/早餐/大课间/眼保健操/熄灯这些不能出现在节次里
        listOf("起床", "早餐", "大课间", "眼保健操", "熄灯", "午休", "午餐", "晚餐", "晨练")
            .forEach { name ->
                assertFalse("「$name」不该被当成上课节次", periodNames.contains(name))
            }
    }

    @Test
    fun `生活作息归入时间点`() {
        val imported = PeriodTableParser.parse(exampleGrid)
        val pointNames = imported.timeTable.timePoints.map { it.name }
        assertTrue(pointNames.contains("起床"))
        assertTrue(pointNames.contains("早餐"))
        assertTrue(pointNames.contains("大课间"))
        assertTrue(pointNames.contains("眼保健操"))
        assertTrue(pointNames.contains("熄灯"))
        assertEquals(12, imported.timePointCount)
    }

    @Test
    fun `上课节次从 1 连续编号`() {
        val imported = PeriodTableParser.parse(exampleGrid)
        assertEquals((1..imported.periodCount).toList(), imported.timeTable.periods.map { it.index })
    }

    @Test
    fun `文本区间能正确解析`() {
        val imported = PeriodTableParser.parse(exampleGrid)
        val first = imported.timeTable.periods.first { it.name == "第一节" }
        assertEquals("08:00", first.start)
        assertEquals("08:45", first.end)
        assertEquals("上午", first.section)
    }

    @Test
    fun `Excel 时间序列值能换算成时刻`() {
        // 这是最容易出错的地方：0.2777… 是"一天的比例"，不是 0 点 27 分
        val imported = PeriodTableParser.parse(exampleGrid)
        val wake = imported.timeTable.timePoints.first { it.name == "起床" }
        assertEquals("06:40", wake.start)
        val breakfast = imported.timeTable.timePoints.first { it.name == "早餐" }
        assertEquals("07:00", breakfast.start)
        val dinner = imported.timeTable.timePoints.first { it.name == "晚餐" }
        assertEquals("18:00", dinner.start)
        val lightsOut = imported.timeTable.timePoints.first { it.name == "熄灯" }
        assertEquals("22:10", lightsOut.start)
    }

    @Test
    fun `时间点带区间的显示区间 只有时点的只显示时刻`() {
        val imported = PeriodTableParser.parse(exampleGrid)
        val exercise = imported.timeTable.timePoints.first { it.name == "晨练" }
        assertEquals("07:40 - 07:55", exercise.timeText)
        val wake = imported.timeTable.timePoints.first { it.name == "起床" }
        assertEquals("06:40", wake.timeText)
    }

    @Test
    fun `节次只有时点没有区间时用下一项开始时间收尾`() {
        val imported = PeriodTableParser.parse(exampleGrid)
        // 晚上办公自习 19:30 是时点式的，下一项晚修第一节 19:40
        val evening = imported.timeTable.periods.first { it.name == "晚上办公自习" }
        assertEquals("19:30", evening.start)
        assertEquals("19:40", evening.end)
    }

    @Test
    fun `最后一个节次没有下一项时向后补十分钟`() {
        val grid = listOf(
            listOf("午别", "项目", "时间"),
            listOf("上午", "第一节", "8:00-8:45"),
            listOf("上午", "第二节", "0.7")
        )
        val imported = PeriodTableParser.parse(grid)
        val last = imported.timeTable.periods.last()
        assertEquals("第二节", last.name)
        assertEquals("16:48", last.start)
        assertEquals("16:58", last.end)
    }

    @Test
    fun `所有节次时间都合法`() {
        val imported = PeriodTableParser.parse(exampleGrid)
        imported.timeTable.periods.forEach { period ->
            assertTrue("${period.name} 时间不合法：${period.start}-${period.end}", period.isValid)
        }
    }

    @Test
    fun `午别只有第一行有值时向下填充`() {
        // 从 Excel 复制粘贴时没有合并单元格，午别只出现在每段第一行
        val grid = listOf(
            listOf("午别", "项目", "时间"),
            listOf("上午", "第一节", "8:00-8:45"),
            listOf("", "第二节", "8:55-9:40"),
            listOf("下午", "第五节", "14:00-14:45"),
            listOf("", "第六节", "14:55-15:40")
        )
        val imported = PeriodTableParser.parse(grid)
        assertEquals(listOf("上午", "上午", "下午", "下午"), imported.timeTable.periods.map { it.section })
        assertEquals(listOf("上午", "下午"), imported.sections)
    }

    @Test
    fun `没有表头时用前三列兜底并给出提示`() {
        val grid = listOf(
            listOf("上午", "第一节", "8:00-8:45"),
            listOf("下午", "第五节", "14:00-14:45")
        )
        val imported = PeriodTableParser.parse(grid)
        assertEquals(2, imported.periodCount)
        assertTrue(imported.warnings.any { it.contains("表头") })
    }

    @Test
    fun `空表格与缺列给出可读提示`() {
        assertTrue(PeriodTableParser.parse(emptyList()).isEmpty)
        assertTrue(PeriodTableParser.parse(emptyList()).warnings.isNotEmpty())

        val noTime = listOf(listOf("午别", "项目", "时间"), listOf("上午", "第一节", "待定"))
        val imported = PeriodTableParser.parse(noTime)
        assertTrue(imported.isEmpty)
        assertTrue(imported.warnings.any { it.contains("无法识别") })
    }

    @Test
    fun `各种时间写法都能解析`() {
        assertEquals(480 to 525, PeriodTableParser.parseTime("8:00——8:45"))
        assertEquals(480 to 525, PeriodTableParser.parseTime("08:00-08:45"))
        assertEquals(480 to 525, PeriodTableParser.parseTime("8：00～8：45"))
        assertEquals(480 to 495, PeriodTableParser.parseTime("8:00–8:15"))
        assertEquals(460 to null, PeriodTableParser.parseTime("7:40"))
        assertEquals(1305 to null, PeriodTableParser.parseTime("21:45"))
        // 带日期整数部分的序列值取小数部分
        assertEquals(0 to null, PeriodTableParser.parseTime("0"))
        assertEquals(null to null, PeriodTableParser.parseTime(""))
        assertEquals(null to null, PeriodTableParser.parseTime("待定"))
        assertEquals(null to null, PeriodTableParser.parseTime("25:99"))
    }

    @Test
    fun `空项目名不会被当成节次`() {
        val grid = listOf(
            listOf("午别", "项目", "时间"),
            listOf("上午", "", "8:00-8:45"),
            listOf("上午", "第一节", "8:00-8:45")
        )
        assertEquals(1, PeriodTableParser.parse(grid).periodCount)
    }

    @Test
    fun `默认全部勾选`() {
        // 时间点已经不影响课程表了，所以默认全选最直观
        val imported = PeriodTableParser.parse(exampleGrid)
        assertEquals(23, imported.defaultSelection().size)
    }

    @Test
    fun `摘要分别给出节次与时间点数量`() {
        val imported = PeriodTableParser.parse(exampleGrid)
        assertTrue(imported.summary.contains("上午"))
        assertTrue(imported.summary.contains("11 个上课节次"))
        assertTrue(imported.summary.contains("12 个时间点"))
    }

    @Test
    fun `完全没有节次名时全部算时间点`() {
        val grid = listOf(
            listOf("午别", "项目", "时间"),
            listOf("上午", "活动甲", "8:00——8:45"),
            listOf("上午", "活动乙", "0.5")
        )
        val imported = PeriodTableParser.parse(grid)
        assertEquals(0, imported.periodCount)
        assertEquals(2, imported.timePointCount)
        // 没有节次就没有课表行，但时间表里仍能看到这两项
        assertTrue(imported.timeTable.hasNoPeriods)
        assertFalse(imported.timeTable.isEmpty)
    }
}
