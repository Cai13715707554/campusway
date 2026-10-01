package com.campusway.core.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 导入的 Android 侧入口：取文件、读字节、调用纯逻辑解析。
 *
 * 解析本身在 [SheetReaders] 与 [TimetableGridParser] 里，都是纯 Kotlin、可单测；
 * 这里只负责 ContentResolver、文件名这类平台相关的事。
 */
object AndroidSheetImport {

    /** 文件选择器可接受的类型：xlsx、csv/txt，外加兜底。 */
    val MIME_TYPES = arrayOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel",
        "text/csv",
        "text/comma-separated-values",
        "text/plain",
        "*/*"
    )

    /** 读取用户选中的文件并解析。 */
    suspend fun readFile(
        context: Context,
        uri: Uri,
        campus: com.campusway.core.model.Campus? = null
    ): ImportedTimetable = withContext(Dispatchers.IO) {
        val name = displayName(context, uri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext ImportedTimetable(
                timeTable = com.campusway.core.model.TimeTable.sample(),
                courseTable = com.campusway.core.model.CourseTable(),
                warnings = listOf("打不开这个文件，请换一个再试")
            )
        val grid = runCatching { SheetReaders.read(name, bytes) }.getOrElse { emptyList() }
        TimetableGridParser.parse(grid, campus = campus)
    }

    /** 解析用户粘贴进来的表格文本（从 Excel 直接复制就是 Tab 分隔）。 */
    fun readPastedText(
        text: String,
        campus: com.campusway.core.model.Campus? = null
    ): ImportedTimetable {
        if (text.isBlank()) {
            return ImportedTimetable(
                timeTable = com.campusway.core.model.TimeTable.sample(),
                courseTable = com.campusway.core.model.CourseTable(),
                warnings = listOf("还没有粘贴内容")
            )
        }
        val grid = SheetReaders.readDelimited(text)
        return TimetableGridParser.parse(grid, campus = campus)
    }

    /** 取显示用的文件名。 */
    fun displayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(index) ?: uri.lastPathSegment.orEmpty()
                }
            }
        }
        return uri.lastPathSegment.orEmpty()
    }
}
