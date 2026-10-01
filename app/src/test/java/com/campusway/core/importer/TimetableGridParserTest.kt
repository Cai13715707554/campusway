package com.campusway.core.importer

import com.campusway.core.model.Campus
import com.campusway.core.model.FloorPlan
import com.campusway.core.model.Building
import com.campusway.core.model.NormRect
import com.campusway.core.model.Room
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 功课表导入解析测试。
 *
 * 用例里的网格就是 `docs/功课表.xlsx` 的真实内容（含 A2:E2 的合并单元格，
 * 读取时会被展开成"整行都填 早读"），所以这份测试同时锁住了格式约定。
 */
class TimetableGridParserTest {

    /**
     * docs/功课表.xlsx 的内容：
     * 表头 A..E 是星期一到星期五；A 列同时也是星期一；
     * 上午/下午 两行是时段分隔；早读整行合并；班会只有星期一。
     */
    private val exampleGrid = listOf(
        listOf("星期一", "星期二", "星期三", "星期四", "星期五"),
        listOf("早读", "早读", "早读", "早读", "早读"),   // A2:E2 合并后被展开
        listOf("上午"),
        listOf("数学", "英语", "视频", "数学", "动漫"),
        listOf("语文", "哲学", "英语", "哲学", "动漫"),
        listOf("动漫", "安全", "历史", "视频", "音乐"),
        listOf("动漫", "体育", "体育", "视频", "安全"),
        listOf("下午"),
        listOf("视频", "动漫", "社团", "语文", "数学"),
        listOf("视频", "动漫", "社团", "语文", "英语"),
        listOf("班会")
    )

    @Test
    fun `解析示例功课表`() {
        val imported = TimetableGridParser.parse(exampleGrid)

        // 5 天都被识别（班会只在星期一，其余四天由排课行覆盖）
        val days = imported.courseTable.entries.map { it.dayOfWeek }.distinct().sorted()
        assertEquals(listOf(1, 2, 3, 4, 5), days)

        // 8 个节次：早读 + 4 节上午 + 2 节下午 + 班会（上午/下午是分隔行，不占节次）
        assertEquals(8, imported.periodCount)
        assertFalse(imported.isEmpty)
    }

    @Test
    fun `上午与下午被识别为时段而不是课程`() {
        val imported = TimetableGridParser.parse(exampleGrid)
        assertFalse(
            "时段词不应该被当成课程",
            imported.courseTable.courses.any { it.name == "上午" || it.name == "下午" }
        )
        // 时段分组要正确落到节次上
        val sections = imported.timeTable.groupedBySection().map { it.first }
        assertTrue(sections.contains("上午"))
        assertTrue(sections.contains("下午"))
    }

    @Test
    fun `整行合并的早读全周都有`() {
        val imported = TimetableGridParser.parse(exampleGrid)
        val zaodu = imported.courseTable.courses.firstOrNull { it.name == "早读" }
        assertNotNull(zaodu)
        val days = imported.courseTable.entries
            .filter { it.courseId == zaodu!!.id }
            .map { it.dayOfWeek }
            .sorted()
        assertEquals(listOf(1, 2, 3, 4, 5), days)
    }

    @Test
    fun `只在星期一出现的班会只排一天`() {
        val imported = TimetableGridParser.parse(exampleGrid)
        val banhui = imported.courseTable.courses.firstOrNull { it.name == "班会" }
        assertNotNull(banhui)
        val entries = imported.courseTable.entries.filter { it.courseId == banhui!!.id }
        assertEquals(1, entries.size)
        assertEquals(1, entries.first().dayOfWeek)
    }

    @Test
    fun `同一门课会被合并成一门`() {
        val imported = TimetableGridParser.parse(exampleGrid)
        val names = imported.courseTable.courses.map { it.name }
        assertEquals(names.distinct().size, names.size)
        // 数学在示例里出现多次，只能有一门
        assertEquals(1, names.count { it == "数学" })
        // 而且要为它生成多条排课
        val math = imported.courseTable.courses.first { it.name == "数学" }
        assertTrue(imported.courseTable.entries.count { it.courseId == math.id } >= 3)
    }

    @Test
    fun `课程色号按出现顺序分配且不越界`() {
        val imported = TimetableGridParser.parse(exampleGrid)
        imported.courseTable.courses.forEach { course ->
            assertTrue(course.colorIndex >= 0)
            // 色号必须落在色板范围内，否则画出来会取不到颜色
            assertTrue(
                "色号 ${course.colorIndex} 越界",
                course.colorIndex < com.campusway.core.model.COURSE_COLOR_COUNT
            )
        }
    }

    @Test
    fun `没有节次列时按顺序编号节次`() {
        val imported = TimetableGridParser.parse(exampleGrid)
        assertEquals((1..8).toList(), imported.timeTable.periods.map { it.index })
        // 每个节次都要有合法时间，否则课表页没法高亮
        imported.timeTable.periods.forEach { assertTrue(it.isValid) }
    }

    // ---------------------------------------------------------- 带节次列的表

    @Test
    fun `第一列是节次时用它的内容当节次名并解析时间`() {
        val grid = listOf(
            listOf("节次", "星期一", "星期二"),
            listOf("08:00-08:45", "数学", "英语"),
            listOf("第2节 08:55-09:40", "语文", "哲学")
        )
        val imported = TimetableGridParser.parse(grid)
        assertEquals(2, imported.periodCount)
        assertEquals("08:00", imported.timeTable.periods[0].start)
        assertEquals("08:45", imported.timeTable.periods[0].end)
        // 第二个节次名里带时间，也要解析出来
        assertEquals("08:55", imported.timeTable.periods[1].start)
        assertEquals(4, imported.courseTable.entries.size)
    }

    @Test
    fun `表头出现中文星期与英文星期都能识别`() {
        assertEquals(1, TimetableGridParser.weekdayOf("星期一"))
        assertEquals(7, TimetableGridParser.weekdayOf("星期日"))
        assertEquals(7, TimetableGridParser.weekdayOf("星期天"))
        assertEquals(3, TimetableGridParser.weekdayOf("周三"))
        assertEquals(5, TimetableGridParser.weekdayOf("礼拜五"))
        assertEquals(2, TimetableGridParser.weekdayOf("Tuesday"))
        assertEquals(6, TimetableGridParser.weekdayOf("Sat"))
        assertEquals(null, TimetableGridParser.weekdayOf("节次"))
        assertEquals(null, TimetableGridParser.weekdayOf(""))
    }

    @Test
    fun `带后缀的星期名也能识别`() {
        // 有些学校会写成"星期一(第1-2节)"
        assertEquals(1, TimetableGridParser.weekdayOf("星期一（第1-2节）"))
    }

    // ---------------------------------------------------------- 地点与容错

    @Test
    fun `单元格里的地点能关联到校园房间`() {
        val room = Room(
            id = "b1-f3-r302", name = "302 网络实训室", shortName = "302",
            floorId = "b1-f3", buildingId = "b1", rect = NormRect(0f, 0f, 1f, 1f)
        )
        val campus = Campus(
            name = "测试",
            buildings = listOf(
                Building(
                    id = "b1", name = "1 号楼",
                    floors = listOf(FloorPlan(buildingId = "b1", floorId = "b1-f3", level = 3, name = "3 层", rooms = listOf(room)))
                )
            )
        )
        val grid = listOf(
            listOf("星期一", "星期二"),
            listOf("计算机网络@302", "数学")
        )
        val imported = TimetableGridParser.parse(grid, campus = campus)
        val course = imported.courseTable.courses.first { it.name == "计算机网络" }
        // 地点能对上房间就关联 roomId，供楼内导航直接跳转
        assertEquals("b1-f3-r302", course.roomId)
    }

    @Test
    fun `地点对不上房间时记入备注而不是丢掉`() {
        val grid = listOf(
            listOf("星期一", "星期二"),
            listOf("体育@操场", "数学")
        )
        val imported = TimetableGridParser.parse(grid)
        val course = imported.courseTable.courses.first { it.name == "体育" }
        assertEquals("", course.roomId)
        assertTrue(course.note.contains("操场"))
    }

    @Test
    fun `空表格给出可读提示而不是崩溃`() {
        val imported = TimetableGridParser.parse(emptyList())
        assertTrue(imported.isEmpty)
        assertTrue(imported.warnings.isNotEmpty())
    }

    @Test
    fun `没有表头时给出可读提示`() {
        val grid = listOf(listOf("数学", "英语"), listOf("语文", "哲学"))
        val imported = TimetableGridParser.parse(grid)
        assertTrue(imported.isEmpty)
        assertTrue(imported.warnings.any { it.contains("表头") })
    }

    @Test
    fun `只有一个星期列时拒绝导入并提示`() {
        val grid = listOf(listOf("星期一", "备注"), listOf("数学", "带实训手册"))
        val imported = TimetableGridParser.parse(grid)
        assertTrue(imported.isEmpty)
        assertTrue(imported.warnings.isNotEmpty())
    }

    @Test
    fun `完全空白的格子不会生成课程`() {
        val grid = listOf(
            listOf("星期一", "星期二", "星期三"),
            listOf("数学", "", "英语"),
            listOf("", "", "")
        )
        val imported = TimetableGridParser.parse(grid)
        assertEquals(2, imported.courseTable.entries.size)
        assertEquals(2, imported.courseCount)
    }

    @Test
    fun `节次数超过默认作息时会补出时间并提示`() {
        val rows = mutableListOf(listOf("星期一", "星期二"))
        repeat(12) { rows += listOf("数学", "英语") }
        val imported = TimetableGridParser.parse(rows)
        assertEquals(12, imported.periodCount)
        imported.timeTable.periods.forEach { assertTrue(it.isValid) }
        assertTrue(imported.warnings.any { it.contains("默认作息") })
    }

    @Test
    fun `摘要文案包含关键数量`() {
        val imported = TimetableGridParser.parse(exampleGrid)
        assertTrue(imported.summary.contains("5 天"))
        assertTrue(imported.summary.contains("8 个节次"))
    }
}
