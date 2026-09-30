package com.campusway.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 课程时间表的行为测试。
 *
 * 时间表是课表的“分母”：节次时间错了，整个课表页的高亮都会错，
 * 所以这里把解析、校验、分组都覆盖到。
 */
class TimeTableTest {

    private val table = TimeTable.sample()

    @Test
    fun `示例时间表可以通过校验`() {
        assertEquals(emptyList<String>(), table.validate())
    }

    @Test
    fun `解析 HH mm 与 H mm 两种写法`() {
        assertEquals(8 * 60, TimeOfDay.parseToMinutes("08:00"))
        assertEquals(8 * 60, TimeOfDay.parseToMinutes("8:00"))
        assertEquals(19 * 60 + 45, TimeOfDay.parseToMinutes("19:45"))
        assertEquals(8 * 60, TimeOfDay.parseToMinutes("08:00:00"))
    }

    @Test
    fun `非法时间返回负一而不是抛异常`() {
        assertEquals(-1, TimeOfDay.parseToMinutes(""))
        assertEquals(-1, TimeOfDay.parseToMinutes("25:00"))
        assertEquals(-1, TimeOfDay.parseToMinutes("八点"))
    }

    @Test
    fun `分钟数格式化回 HH mm`() {
        assertEquals("08:00", TimeOfDay.format(480))
        assertEquals("00:00", TimeOfDay.format(0))
        // 跨天时取模，不出现 24:30 这种非法时间
        assertEquals("00:30", TimeOfDay.format(24 * 60 + 30))
        assertEquals("23:30", TimeOfDay.format(-30))
    }

    @Test
    fun `节次时长计算正确`() {
        val period = table.period(1)
        assertNotNull(period)
        assertEquals(45, period!!.durationMinutes)
        assertTrue(period.isValid)
    }

    @Test
    fun `结束时间早于开始时间时判定为无效`() {
        val bad = Period(1, "错的", "10:00", "09:00", "上午")
        assertFalse(bad.isValid)
        assertTrue(TimeTable(periods = listOf(bad)).validate().isNotEmpty())
    }

    @Test
    fun `重复节次序号会被校验发现`() {
        val duplicated = TimeTable(
            periods = listOf(
                Period(1, "第一节", "08:00", "08:45", "上午"),
                Period(1, "重复的节次", "09:00", "09:45", "上午")
            )
        )
        val problems = duplicated.validate()
        assertTrue(problems.any { it.contains("重复") })
    }

    @Test
    fun `时间重叠会被校验发现`() {
        val overlapping = TimeTable(
            periods = listOf(
                Period(1, "第一节", "08:00", "09:00", "上午"),
                Period(2, "第二节", "08:50", "09:40", "上午")
            )
        )
        assertTrue(overlapping.validate().any { it.contains("重叠") })
    }

    @Test
    fun `按上午下午晚上分组且保持顺序`() {
        val groups = table.groupedBySection()
        assertEquals(listOf("上午", "下午", "晚上"), groups.map { it.first })
        assertEquals(4, groups.first().second.size)
    }

    @Test
    fun `没有分组名时归入其他`() {
        val t = TimeTable(periods = listOf(Period(1, "早读", "07:30", "07:50")))
        assertEquals(listOf("其他"), t.groupedBySection().map { it.first })
    }

    @Test
    fun `查询不存在的节次返回空`() {
        assertEquals(null, table.period(99))
    }
}

/**
 * 校历：把日期换算成教学周。
 */
class SchoolCalendarTest {

    private val monday = LocalDate.of(2026, 9, 7) // 周一
    private val calendar = SchoolCalendar(termStartDate = "2026-09-07", totalWeeks = 20)

    @Test
    fun `学期第一天是第 1 周`() {
        assertEquals(1, calendar.weekOf(monday))
    }

    @Test
    fun `同周周日仍算第 1 周`() {
        assertEquals(1, calendar.weekOf(monday.plusDays(6)))
    }

    @Test
    fun `下一周一是第 2 周`() {
        assertEquals(2, calendar.weekOf(monday.plusDays(7)))
    }

    @Test
    fun `晚于学期开始的日期正常递推`() {
        assertEquals(5, calendar.weekOf(monday.plusWeeks(4)))
    }

    @Test
    fun `超出总周数被裁剪到最后一周年`() {
        assertEquals(20, calendar.weekOf(monday.plusWeeks(40)))
    }

    @Test
    fun `早于学期开始按第 1 周处理`() {
        assertEquals(1, calendar.weekOf(monday.minusWeeks(3)))
    }

    @Test
    fun `非法日期不会崩且回退到第 1 周`() {
        val broken = SchoolCalendar(termStartDate = "不是日期", totalWeeks = 20)
        assertEquals(1, broken.weekOf(monday))
        assertEquals(null, SchoolCalendar.parseDate("2026-13-45"))
    }

    @Test
    fun `可以反查某周的周一`() {
        assertEquals(monday, calendar.mondayOfWeek(1))
        assertEquals(monday.plusWeeks(2), calendar.mondayOfWeek(3))
    }
}
