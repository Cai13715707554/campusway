package com.campusway.core.importer

import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * 把各种来源的表格读成"二维文本网格"，交给 [TimetableGridParser] 解析。
 *
 * 为什么自己解析 .xlsx 而不引第三方库：
 *  - xlsx 本质是一个 zip + 几个 XML，需要的信息只有两张表（共享字符串 + 第一张工作表），
 *    自己解析约一百多行，比引入 POI/其他库小得多，也不影响离线构建；
 *  - 只用 JDK/Android 都自带的 `ZipInputStream` 与 DOM，无新增依赖。
 *
 * 支持三种输入：
 *  1. `.xlsx`（Excel 2007+）——[readXlsx]
 *  2. `.csv` / `.txt` / 直接粘贴的表格文本（以 Tab 分隔为主）——[readDelimited]
 *  3. 其他（当作纯文本按行按 Tab 切分兜底）
 */
object SheetReaders {

    /** 按文件扩展名/内容选择解析方式。 */
    fun read(fileName: String, bytes: ByteArray): List<List<String>> {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".xlsx") || looksLikeZip(bytes) -> readXlsx(bytes.inputStream())
            else -> readDelimited(bytes.toString(Charsets.UTF_8))
        }
    }

    /** xlsx 的魔数：PK\x03\x04，用于用户选了 .xls 改名等情况时兜底判断。 */
    private fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    // ------------------------------------------------------------ 文本表格

    /**
     * 解析以 Tab / 逗号 / 分号分隔的文本表格。
     *
     * 从 Excel 里复制一列范围再粘贴，拿到的就是 Tab 分隔文本，这是最省事的导入方式。
     * 同时兼容中文全角逗号与常见 CSV。
     */
    fun readDelimited(text: String): List<List<String>> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val rows = lines.map { line -> splitLine(line) }
        // 去掉尾部整行为空的行
        val trimmed = rows.dropLastWhile { row -> row.all { it.isBlank() } }
        return trimmed
    }

    private fun splitLine(line: String): List<String> {
        val delimiter = when {
            line.contains('\t') -> '\t'
            line.contains('，') -> '，'
            line.contains(',') -> ','
            line.contains(';') -> ';'
            else -> null
        } ?: return listOf(line.trim())

        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    // CSV 双引号转义
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    cells += sb.toString().trim()
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        cells += sb.toString().trim()
        return cells
    }

    // ------------------------------------------------------------ xlsx

    /**
     * 读取 xlsx 的第一张工作表。
     *
     * 处理要点：
     *  - 共享字符串表 `xl/sharedStrings.xml`（`t="s"` 的单元格存的是索引）；
     *  - 内联字符串 `t="inlineStr"`；
     *  - 合并单元格：把左上角的值**填满整个合并区域**，这样"整行合并的活动"
     *    （例如示例里 A2:E2 的「早读」）会被正确理解为一周每天都有；
     *  - 稀疏单元格：按 `r="C5"` 里的列号定位，缺的列补空串。
     */
    fun readXlsx(input: InputStream): List<List<String>> {
        var sharedStrings: List<String> = emptyList()
        var sheetXml: ByteArray? = null

        // 第一遍：取出需要的两个 entry（zip 只能顺序读，所以要缓存到内存）
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "xl/sharedStrings.xml" -> sharedStrings = parseSharedStrings(zip.readBytes())
                    "xl/worksheets/sheet1.xml" -> sheetXml = zip.readBytes()
                    else -> {
                        // 有些文件第一张表不叫 sheet1，取第一个 worksheets/*.xml 兜底
                        if (sheetXml == null && entry.name.startsWith("xl/worksheets/") &&
                            entry.name.endsWith(".xml")
                        ) {
                            sheetXml = zip.readBytes()
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val xml = sheetXml ?: return emptyList()
        return parseSheet(xml, sharedStrings)
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val doc = documentOf(xml) ?: return emptyList()
        val result = mutableListOf<String>()
        val items = doc.getElementsByTagName("si")
        for (i in 0 until items.length) {
            val si = items.item(i) as? Element ?: continue
            // 一个 si 里可能有多个 <t>（富文本分段），拼起来
            val texts = si.getElementsByTagName("t")
            val sb = StringBuilder()
            for (j in 0 until texts.length) {
                sb.append(texts.item(j).textContent.orEmpty())
            }
            result += sb.toString()
        }
        return result
    }

    private fun parseSheet(xml: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val doc = documentOf(xml) ?: return emptyList()
        val rows = doc.getElementsByTagName("row")

        // 先收集合并区域
        val mergeRanges = mutableListOf<MergeRange>()
        val mergeNodes = doc.getElementsByTagName("mergeCell")
        for (i in 0 until mergeNodes.length) {
            val ref = (mergeNodes.item(i) as? Element)?.getAttribute("ref").orEmpty()
            parseRange(ref)?.let { mergeRanges += it }
        }

        val grid = mutableListOf<MutableList<String>>()
        var maxCol = 0

        for (r in 0 until rows.length) {
            val rowEl = rows.item(r) as? Element ?: continue
            val rowIndex = rowEl.getAttribute("r").toIntOrNull() ?: (r + 1)
            // 用 add 而不是 +=：`grid += mutableListOf()` 会被解析成 plus（返回新 List）
            // 而不是 plusAssign，结果是"给 val 重新赋值"的编译错误。
            while (grid.size < rowIndex) grid.add(mutableListOf())

            val cells = grid[rowIndex - 1]
            val cellNodes = rowEl.getElementsByTagName("c")
            for (c in 0 until cellNodes.length) {
                val cell = cellNodes.item(c) as? Element ?: continue
                val ref = cell.getAttribute("r")
                val col = columnIndex(ref)
                while (cells.size <= col) cells += ""
                cells[col] = cellText(cell, sharedStrings)
            }
            maxCol = maxOf(maxCol, cells.size)
        }

        // 补齐成矩形
        grid.forEach { row -> while (row.size < maxCol) row += "" }

        // 展开合并单元格：把左上角的值铺到整个区域
        mergeRanges.forEach { range ->
            // 注意这里用 .orEmpty() 而不是 ?.orEmpty()：
            // orEmpty 本身声明在可空接收者上，再套一层 ?. 会把结果重新变成 String?。
            val value = grid.getOrNull(range.top - 1)?.getOrNull(range.left).orEmpty()
            if (value.isBlank()) return@forEach
            for (rowIdx in range.top..range.bottom) {
                val row = grid.getOrNull(rowIdx - 1) ?: continue
                for (colIdx in range.left..range.right) {
                    if (colIdx < row.size && row[colIdx].isBlank()) row[colIdx] = value
                }
            }
        }

        // 先裁掉行尾整行为空的行，再把每行末尾的空单元格去掉；
        // 行中间因跳列产生的空串要保留
        return grid.dropLastWhile { row -> row.all { it.isBlank() } }
            .map { row -> row.dropLastWhile { it.isBlank() }.toList() }
    }

    private fun cellText(cell: Element, sharedStrings: List<String>): String {
        val type = cell.getAttribute("t")
        if (type == "inlineStr") {
            val texts = cell.getElementsByTagName("t")
            val sb = StringBuilder()
            for (i in 0 until texts.length) sb.append(texts.item(i).textContent.orEmpty())
            return sb.toString().trim()
        }
        val v = cell.getElementsByTagName("v").item(0)?.textContent.orEmpty()
        if (v.isBlank()) return ""
        return when (type) {
            "s" -> v.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty().trim()
            else -> v.trim()
        }
    }

    /** "C5" -> 2（0 基列号）。 */
    private fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else break
        }
        return (n - 1).coerceAtLeast(0)
    }

    private data class MergeRange(val top: Int, val left: Int, val bottom: Int, val right: Int)

    /** "A2:E2" -> MergeRange(2,0,2,4)。 */
    private fun parseRange(ref: String): MergeRange? {
        val parts = ref.split(':')
        if (parts.size != 2) return null
        val (startCol, startRow) = splitRef(parts[0]) ?: return null
        val (endCol, endRow) = splitRef(parts[1]) ?: return null
        return MergeRange(
            top = minOf(startRow, endRow),
            left = minOf(startCol, endCol),
            bottom = maxOf(startRow, endRow),
            right = maxOf(startCol, endCol)
        )
    }

    private fun splitRef(ref: String): Pair<Int, Int>? {
        val col = columnIndex(ref)
        val row = ref.filter { it.isDigit() }.toIntOrNull() ?: return null
        return col to row
    }

    private fun documentOf(xml: ByteArray): org.w3c.dom.Document? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // 关掉外部实体，避免解析不可信文件时被 XXE 利用。
            // 单独包一层 runCatching：部分平台不认识这个特性名会抛异常，
            // 但它失败不该导致整个表格解析不出来。
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        factory.newDocumentBuilder().parse(xml.inputStream())
    }.getOrNull()
}
