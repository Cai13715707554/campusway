package com.campusway.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 时间表与课程表的一致性检查测试。
 *
 * 锁住「导入时间表前，新时间表每个午别的节次数不能少于课程表已用到的节数」这条规则。
 */
class TimetableConsistencyTest {

    private fun p(index: Int, section: String) = Period(index = index, name = "第 $index 节", start = "08:00", end = "08:45", section = section)

    /** 上午 2 节、下午 1 节。 */
    private val base = TimeTable(periods = listOf(p(1, "上午"), p(2, "上午"), p(3, "下午")))

    /** 课程表用到上午的第 1、2 节和下午的第 3 节。 */
    private val course = CourseTable(
        entries = listOf(
            CourseEntry("c", 1, 1),
            CourseEntry("c", 2, 2),
            CourseEntry("c", 3, 3)
        )
    )

    @Test
    fun `按午别统计时间表的节次`() {
        assertEquals(mapOf("上午" to 2, "下午" to 1), TimetableConsistency.periodsBySection(base))
    }

    @Test
    fun `按午别统计课程表用到的节次`() {
        assertEquals(mapOf("上午" to 2, "下午" to 1), TimetableConsistency.usedPeriodsBySection(course, base))
    }

    @Test
    fun `节次够用时没有缺口`() {
        assertTrue(TimetableConsistency.importProblems(course, base, base).isEmpty())
    }

    @Test
    fun `新时间表某午别变少就报缺口`() {
        val fewer = TimeTable(periods = listOf(p(1, "上午")))

        val problems = TimetableConsistency.importProblems(course, base, fewer)

        assertTrue(problems.any { it.contains("上午") })
        assertTrue(problems.any { it.contains("下午") })
    }

    @Test
    fun `没有课程表时永远没有缺口`() {
        val fewer = TimeTable(periods = listOf(p(1, "上午")))
        assertTrue(TimetableConsistency.importProblems(CourseTable(), base, fewer).isEmpty())
    }
}
