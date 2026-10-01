package com.campusway.core.model

import com.campusway.core.importer.PeriodTableParser
import com.campusway.core.importer.likelyClassPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 时间表"节次 / 时间点"与"保留哪些项目"的测试。
 *
 * 核心不变量：
 *  - 只有上课节次参与课程表，时间点只展示；
 *  - 保留一部分项目后，节次必须重新编号成 1..K，
 *    否则课程（按节次序号挂）会落到空档上。
 */
class TimetableVisibilityTest {

    private val table = TimeTable(
        name = "示例作息",
        periods = listOf(
            Period(1, "第一节", "08:00", "08:45", "上午"),
            Period(2, "第二节", "08:55", "09:40", "上午"),
            Period(3, "第五节", "14:00", "14:45", "下午"),
            Period(4, "晚修第一节", "19:40", "20:25", "晚上")
        ),
        timePoints = listOf(
            TimePoint("起床", "06:40", "", "上午"),
            TimePoint("大课间", "09:40", "10:00", "上午"),
            TimePoint("午休", "12:30", "13:50", "下午"),
            TimePoint("熄灯", "22:10", "", "晚上")
        )
    )

    @Test
    fun `保留部分项目后节次重新编号为 1 到 K`() {
        val kept = table.keepingKeys(
            setOf("上午|第一节", "上午|第二节", "下午|第五节", "晚上|晚修第一节")
        )
        assertEquals(4, kept.periods.size)
        assertEquals(listOf(1, 2, 3, 4), kept.periods.map { it.index })
        assertEquals(listOf("第一节", "第二节", "第五节", "晚修第一节"), kept.periods.map { it.name })
    }

    @Test
    fun `整组取消下午时节次与时间点一起消失`() {
        val hideAfternoon = table.allVisibilityKeys().filterNot { it.startsWith("下午|") }.toSet()
        val kept = table.keepingKeys(hideAfternoon)
        assertTrue(kept.periods.none { it.section == "下午" })
        assertTrue(kept.timePoints.none { it.section == "下午" })
        assertEquals(3, kept.periods.size)
        assertEquals(listOf(1, 2, 3), kept.periods.map { it.index })
    }

    @Test
    fun `只保留时间点时节次为空但时间表不算空`() {
        val onlyPoints = table.timePoints.map { it.visibilityKey }.toSet()
        val kept = table.keepingKeys(onlyPoints)
        assertTrue(kept.periods.isEmpty())
        assertTrue(kept.hasNoPeriods)
        // 还有时间点要展示，所以不算"什么都没有"
        assertFalse(kept.isEmpty)
        assertEquals(4, kept.timePoints.size)
    }

    @Test
    fun `全都不勾选时得到一张什么都没有的时间表`() {
        val kept = table.keepingKeys(emptySet())
        assertTrue(kept.isEmpty)
        assertTrue(kept.periods.isEmpty())
        assertTrue(kept.timePoints.isEmpty())
    }

    @Test
    fun `cleared 也会得到什么都没有的时间表`() {
        assertTrue(table.cleared().isEmpty)
    }

    @Test
    fun `正常时间表不算空`() {
        assertFalse(table.isEmpty)
        assertFalse(table.hasNoPeriods)
    }

    @Test
    fun `展示行把节次与时间点按时间合并排序`() {
        val rows = table.rows()
        // 06:40 起床、08:00 第一节、08:55 第二节、09:40 大课间、12:30 午休、
        // 14:00 第五节、19:40 晚修第一节、22:10 熄灯
        assertEquals(
            listOf("起床", "第一节", "第二节", "大课间", "午休", "第五节", "晚修第一节", "熄灯"),
            rows.map { it.label }
        )
        // 只有上课节次带序号
        assertEquals(1, rows.first { it.label == "第一节" }.periodIndex)
        assertEquals(null, rows.first { it.label == "起床" }.periodIndex)
        assertFalse(rows.first { it.label == "起床" }.isClassPeriod)
        assertTrue(rows.first { it.label == "第一节" }.isClassPeriod)
    }

    @Test
    fun `展示行按午别分组且保持顺序`() {
        val groups = table.rowGroups()
        assertEquals(listOf("上午", "下午", "晚上"), groups.map { it.first })
        // 上午：起床、第一节、第二节、大课间
        assertEquals(4, groups.first().second.size)
    }

    @Test
    fun `时间点的展示文本`() {
        assertEquals("06:40", TimePoint("起床", "06:40").timeText)
        assertEquals("09:40 - 10:00", TimePoint("大课间", "09:40", "10:00").timeText)
    }

    @Test
    fun `visibilityKey 由午别与名称组成`() {
        assertEquals("上午|第一节", table.period(1)!!.visibilityKey)
        assertEquals("上午|起床", table.timePoints.first().visibilityKey)
        assertEquals(8, table.allVisibilityKeys().size)
    }

    @Test
    fun `只有像节次的项目才会被判定为上课节次`() {
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
 * 端到端检查：拿 `docs/时间表.xlsx` 的真实内容跑一遍，
 * 确认导入后课程表只会有 11 个上课节次，而不是 23 项。
 */
class PeriodTableImportEndToEndTest {

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
    fun `导入后课程表只按上课节次排`() {
        val imported = PeriodTableParser.parse(grid)
        val kept = imported.timeTable.keepingKeys(imported.defaultSelection())

        // 上课节次：第一节、第二节、第五节、晚修第一节
        assertEquals(4, kept.periods.size)
        assertEquals(listOf(1, 2, 3, 4), kept.periods.map { it.index })
        assertEquals("08:00", kept.period(1)!!.start)

        // 时间点仍在时间表里，但不占节次序号
        assertEquals(4, kept.timePoints.size)
        assertTrue(kept.timePoints.any { it.name == "起床" })
    }

    @Test
    fun `取消晚上后晚修节次与熄灯都不在`() {
        val imported = PeriodTableParser.parse(grid)
        val selection = imported.defaultSelection().filterNot { it.startsWith("晚上|") }.toSet()
        val kept = imported.timeTable.keepingKeys(selection)
        assertTrue(kept.periods.none { it.section == "晚上" })
        assertTrue(kept.timePoints.none { it.section == "晚上" })
        assertEquals(3, kept.periods.size)
        assertEquals(listOf(1, 2, 3), kept.periods.map { it.index })
    }
}
