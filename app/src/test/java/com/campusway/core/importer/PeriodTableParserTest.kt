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
        assertEquals(23, imported.periodCount)
        assertFalse(imported.isEmpty)
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
        val wake = imported.timeTable.periods.first { it.name == "起床" }
        assertEquals("06:40", wake.start)
        val breakfast = imported.timeTable.periods.first { it.name == "早餐" }
        assertEquals("07:00", breakfast.start)
        val dinner = imported.timeTable.periods.first { it.name == "晚餐" }
        assertEquals("18:00", dinner.start)
        val lightsOut = imported.timeTable.periods.first { it.name == "熄灯" }
        assertEquals("22:10", lightsOut.start)
    }

    @Test
    fun `只有时点的项目用下一项开始时间收尾`() {
        val imported = PeriodTableParser.parse(exampleGrid)
        val wake = imported.timeTable.periods.first { it.name == "起床" }
        // 起床 06:40，下一项早餐 07:00
        assertEquals("06:40", wake.start)
        assertEquals("07:00", wake.end)
    }

    @Test
    fun `最后一项没有下一项时向后补十分钟`() {
        val imported = PeriodTableParser.parse(exampleGrid)
        val last = imported.timeTable.periods.last()
        assertEquals("熄灯", last.name)
        assertEquals("22:10", last.start)
        assertEquals("22:20", last.end)
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
    fun `摘要文案包含午别与项目数`() {
        val imported = PeriodTableParser.parse(exampleGrid)
        assertTrue(imported.summary.contains("上午"))
        assertTrue(imported.summary.contains("23 个项目"))
    }
}
