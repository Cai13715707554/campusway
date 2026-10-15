package com.campusway.core.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 表格读取层测试。
 *
 * xlsx 用代码现场构造，不依赖外部文件，这样测试能自证：
 * 共享字符串、稀疏单元格（C5 这种跳列）、合并单元格展开都要正确。
 */
class SheetReadersTest {

    // ---------------------------------------------------------- 文本表格

    @Test
    fun `解析从 Excel 复制出来的制表符文本`() {
        val text = "星期一\t星期二\t星期三\n数学\t英语\t视频\n语文\t哲学\t英语"
        val grid = SheetReaders.readDelimited(text)
        assertEquals(3, grid.size)
        assertEquals(listOf("星期一", "星期二", "星期三"), grid[0])
        assertEquals(listOf("语文", "哲学", "英语"), grid[2])
    }

    @Test
    fun `兼容中文全角逗号与半角逗号`() {
        assertEquals(listOf("星期一", "星期二"), SheetReaders.readDelimited("星期一，星期二")[0])
        assertEquals(listOf("星期一", "星期二"), SheetReaders.readDelimited("星期一,星期二")[0])
    }

    @Test
    fun `CSV 里带引号的逗号不会被切开`() {
        val grid = SheetReaders.readDelimited("""星期一,星期二
            "数学,上册",英语""".trimIndent())
        assertEquals(listOf("数学,上册", "英语"), grid[1])
    }

    @Test
    fun `末尾空行会被裁掉`() {
        val grid = SheetReaders.readDelimited("星期一\t星期二\n数学\t英语\n\n\n")
        assertEquals(2, grid.size)
    }

    @Test
    fun `单列文本也能解析`() {
        val grid = SheetReaders.readDelimited("早读\n上午\n数学")
        assertEquals(3, grid.size)
        assertEquals(listOf("数学"), grid[2])
    }

    // ---------------------------------------------------------- xlsx

    @Test
    fun `解析 xlsx 的共享字符串与稀疏单元格`() {
        val bytes = buildXlsx(
            sharedStrings = listOf("星期一", "星期二", "数学", "英语"),
            sheetXml = """
                <worksheet><sheetData>
                  <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
                  <row r="2"><c r="A2" t="s"><v>2</v></c><c r="C2" t="s"><v>3</v></c></row>
                </sheetData></worksheet>
            """.trimIndent()
        )
        val grid = SheetReaders.readXlsx(bytes.inputStream())
        assertEquals(listOf("星期一", "星期二"), grid[0])
        // C2 是跳列，B2 必须补成空串
        assertEquals(listOf("数学", "", "英语"), grid[1])
    }

    @Test
    fun `解析 xlsx 的内联字符串`() {
        val bytes = buildXlsx(
            sharedStrings = emptyList(),
            sheetXml = """
                <worksheet><sheetData>
                  <row r="1"><c r="A1" t="inlineStr"><is><t>星期一</t></is></c></row>
                </sheetData></worksheet>
            """.trimIndent()
        )
        assertEquals(listOf("星期一"), SheetReaders.readXlsx(bytes.inputStream())[0])
    }

    @Test
    fun `合并单元格的值会铺满整个区域`() {
        // 对应 docs/功课表.xlsx 里 A2:E2 的「早读」：
        // 展开后整周都算早读，而不是只有星期一
        val bytes = buildXlsx(
            sharedStrings = listOf("星期一", "星期二", "星期三", "早读"),
            sheetXml = """
                <worksheet><sheetData>
                  <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>
                  <row r="2"><c r="A2" t="s"><v>3</v></c><c r="B2"/><c r="C2"/></row>
                </sheetData><mergeCells count="1"><mergeCell ref="A2:C2"/></mergeCells></worksheet>
            """.trimIndent()
        )
        val grid = SheetReaders.readXlsx(bytes.inputStream())
        assertEquals(listOf("早读", "早读", "早读"), grid[1])
    }

    @Test
    fun `数字单元格原样读出`() {
        val bytes = buildXlsx(
            sharedStrings = listOf("星期一", "星期二"),
            sheetXml = """
                <worksheet><sheetData>
                  <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
                  <row r="2"><c r="A2"><v>101</v></c><c r="B2"><v>202</v></c></row>
                </sheetData></worksheet>
            """.trimIndent()
        )
        assertEquals(listOf("101", "202"), SheetReaders.readXlsx(bytes.inputStream())[1])
    }

    @Test
    fun `xlsx 读取结果可以直接喂给解析器`() {
        val bytes = buildXlsx(
            sharedStrings = listOf("星期一", "星期二", "数学", "英语", "语文", "哲学"),
            sheetXml = """
                <worksheet><sheetData>
                  <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
                  <row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2" t="s"><v>3</v></c></row>
                  <row r="3"><c r="A3" t="s"><v>4</v></c><c r="B3" t="s"><v>5</v></c></row>
                </sheetData></worksheet>
            """.trimIndent()
        )
        val grid = SheetReaders.read("功课表.xlsx", bytes)
        val imported = TimetableGridParser.parse(grid)
        assertEquals(4, imported.courseTable.entries.size)
        assertEquals(2, imported.periodCount)
        assertTrue(imported.warnings.isEmpty() || imported.warnings.none { it.contains("没找到表头") })
    }

    @Test
    fun `按文件名选择解析方式`() {
        val csv = "星期一,星期二\n数学,英语".toByteArray()
        val grid = SheetReaders.read("table.csv", csv)
        assertEquals(listOf("星期一", "星期二"), grid[0])
    }

    @Test
    fun `坏文件不会崩`() {
        val broken = "PK\u0003\u0004 这不是真的 zip".toByteArray()
        val grid = runCatching { SheetReaders.read("bad.xlsx", broken) }.getOrElse { emptyList() }
        assertTrue(grid.isEmpty())
    }

    // ---------------------------------------------------------- 构造测试用 xlsx

    private fun buildXlsx(sharedStrings: List<String>, sheetXml: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            val sst = buildString {
                append("""<?xml version="1.0" encoding="UTF-8"?>""")
                append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
                sharedStrings.forEach { append("<si><t>").append(it).append("</t></si>") }
                append("</sst>")
            }
            zip.write(sst.toByteArray())

            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write("""<?xml version="1.0" encoding="UTF-8"?>$sheetXml""".toByteArray())
        }
        return out.toByteArray()
    }
}
