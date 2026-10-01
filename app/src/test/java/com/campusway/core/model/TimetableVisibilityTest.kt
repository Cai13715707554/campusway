package com.campusway.core.model

import com.campusway.core.importer.PeriodTableParser
import com.campusway.core.importer.likelyClassPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 时间表"保留/隐藏哪些项目"的测试。
 *
 * 这是本功能的核心不变量：功课表里的课程是按**节次序号**挂上去的，
 * 所以保留一部分项目后必须重新编号成 1..K，否则课程会挂到空档上。
 */
class TimetableVisibilityTest {

    private val table = TimeTable(
        name = "示例作息",
        periods = listOf(
            Period(1, "起床", "06:40", "07:00", "上午"),
            Period(2, "早餐", "07:00", "07:10", "上午"),
            Period(3, "第一节", "08:00", "08:45", "上午"),
            Period(4, "第二节", "08:55", "09:40", "上午"),
            Period(5, "第五节", "14:00", "14:45", "下午"),
            Period(6, "晚修第一节", "19:40", "20:25", "晚上")
        )
    )

    @Test
    fun `保留部分项目后重新编号为 1 到 K`() {
        val kept = table.keepingKeys(
            setOf("上午|第一节", "上午|第二节", "下午|第五节", "晚上|晚修第一节")
        )
        assertEquals(4, kept.periods.size)
        assertEquals(listOf(1, 2, 3, 4), kept.periods.map { it.index })
        // 名称与午别要保持原样，只是序号变了
        assertEquals(listOf("第一节", "第二节", "第五节", "晚修第一节"), kept.periods.map { it.name })
        assertEquals(listOf("上午", "上午", "下午", "晚上"), kept.periods.map { it.section })
    }

    @Test
    fun `不勾选下午时下午的项目全部消失`() {
        val all = table.periods.map { it.visibilityKey }.toSet()
        val hideAfternoon = all.filterNot { it.startsWith("下午|") }.toSet()
        val kept = table.keepingKeys(hideAfternoon)
        assertTrue(kept.periods.none { it.section == "下午" })
        assertEquals(5, kept.periods.size)
        assertEquals(listOf(1, 2, 3, 4, 5), kept.periods.map { it.index })
    }

    @Test
    fun `全都不勾选时得到一张没有节次的时间表`() {
        val kept = table.keepingKeys(emptySet())
        // 应用"需要用到时间表的地方"据此按没有时间表处理
        assertTrue(kept.hasNoPeriods)
        assertEquals(0, kept.periods.size)
    }

    @Test
    fun `cleared 也会得到没有节次的时间表`() {
        assertTrue(table.cleared().hasNoPeriods)
    }

    @Test
    fun `正常时间表不算没有节次`() {
        assertFalse(table.hasNoPeriods)
    }

    @Test
    fun `按午别分组保持顺序`() {
        val groups = table.sectionsWithPeriods()
        assertEquals(listOf("上午", "下午", "晚上"), groups.map { it.first })
        assertEquals(4, groups.first().second.size)
    }

    @Test
    fun `visibilityKey 由午别与项目名组成`() {
        assertEquals("上午|第一节", table.period(3)!!.visibilityKey)
    }

    @Test
    fun `只有像节次的项目才会被预勾选`() {
        assertTrue(likelyClassPeriod("第一节"))
        assertTrue(likelyClassPeriod("晚修第二节"))
        assertTrue(likelyClassPeriod("早读"))
        assertTrue(likelyClassPeriod("自习"))
        assertTrue(likelyClassPeriod("第10节"))
        assertFalse(likelyClassPeriod("起床"))
        assertFalse(likelyClassPeriod("早餐"))
        assertFalse(likelyClassPeriod("大课间"))
        assertFalse(likelyClassPeriod("眼保健操"))
        assertFalse(likelyClassPeriod("熄灯"))
        assertFalse(likelyClassPeriod(""))
    }
}

/**
 * 预勾选规则的端到端检查：拿示例时间表的真实内容跑一遍，
 * 确认默认勾选出来的正好是「第一节…第八节 + 晚修」，课程能对上号。
 */
class PeriodTableDefaultSelectionTest {

    private val grid = listOf(
        listOf("午别", "项目", "时间"),
        listOf("上午", "起床", "0.27777777777777779"),
        listOf("上午", "早餐", "0.29166666666666669"),
        listOf("上午", "第一节", "8:00——8:45"),
        listOf("上午", "大课间", "8:45——9:05"),
        listOf("上午", "第二节", "9:05——9:50"),
        listOf("下午", "第五节", "14:20——15:05"),
        listOf("晚上", "晚修第一节", "19:40——20:25"),
        listOf("晚上", "熄灯", "0.92361111111111116")
    )

    @Test
    fun `默认勾选节次类项目而不是生活作息`() {
        val imported = PeriodTableParser.parse(grid)
        val selected = imported.defaultSelection()
        val names = imported.timeTable.periods
            .filter { it.visibilityKey in selected }
            .map { it.name }
        assertEquals(listOf("第一节", "第二节", "第五节", "晚修第一节"), names)
    }

    @Test
    fun `默认勾选后重新编号可直接挂课程`() {
        val imported = PeriodTableParser.parse(grid)
        val kept = imported.timeTable.keepingKeys(imported.defaultSelection())
        assertEquals(listOf(1, 2, 3, 4), kept.periods.map { it.index })
        assertEquals("第一节", kept.period(1)!!.name)
        assertEquals("08:00", kept.period(1)!!.start)
    }

    @Test
    fun `没有节次类项目时退回有起止时间的项目`() {
        val noClassNames = listOf(
            listOf("午别", "项目", "时间"),
            listOf("上午", "活动甲", "8:00——8:45"),
            listOf("上午", "活动乙", "0.5")
        )
        val imported = PeriodTableParser.parse(noClassNames)
        val selected = imported.defaultSelection()
        // 只有"活动甲"写了起止时间
        assertEquals(setOf("上午|活动甲"), selected)
    }

    @Test
    fun `既没有节次名也没有区间时全选`() {
        val allPoints = listOf(
            listOf("午别", "项目", "时间"),
            listOf("上午", "甲", "0.3"),
            listOf("上午", "乙", "0.4")
        )
        val imported = PeriodTableParser.parse(allPoints)
        assertEquals(2, imported.defaultSelection().size)
    }
}
