package com.campusway.core.data

import android.content.Context
import com.campusway.core.model.Campus
import com.campusway.core.model.CourseTable
import com.campusway.core.model.SchoolCalendar
import com.campusway.core.model.TimeTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 校园数据的 Android 实现。
 *
 * 读取顺序（越靠前优先级越高）：
 *   1. `filesDir/data/*.json` —— 用户在设置页导入或修改后的数据；
 *   2. `assets/data/*.json`  —— 随包内置的示例学校数据（由 tools/GenerateAssets 生成）；
 *   3. 代码里的 [SampleData]   —— 最后兜底，保证任何情况下都有可用数据。
 *
 * 全部走本地文件，无网络依赖。写盘采用“临时文件 + 原子替换”，
 * 避免写入过程中进程被杀导致 JSON 只剩半截、下次启动解析失败。
 */
class AssetCampusRepository(
    context: Context,
    private val json: Json = JsonConfig.instance
) : CampusRepository {

    private val appContext = context.applicationContext
    private val dataDir: File get() = File(appContext.filesDir, "data").apply { if (!exists()) mkdirs() }

    private val campusState = MutableStateFlow(SampleData.campus)
    private val courseState = MutableStateFlow(SampleData.courseTable)
    private val timeState = MutableStateFlow(SampleData.timeTable)
    private val calendarState = MutableStateFlow(SampleData.calendarFor())

    override val campus: StateFlow<Campus> = campusState.asStateFlow()
    override val courseTable: StateFlow<CourseTable> = courseState.asStateFlow()
    override val timeTable: StateFlow<TimeTable> = timeState.asStateFlow()
    override val calendar: StateFlow<SchoolCalendar> = calendarState.asStateFlow()

    override suspend fun load() = withContext(Dispatchers.IO) {
        campusState.value = readJson(FILE_CAMPUS, Campus.serializer(), SampleData.campus)
        courseState.value = readJson(FILE_COURSE, CourseTable.serializer(), SampleData.courseTable)
        timeState.value = readJson(FILE_TIME, TimeTable.serializer(), SampleData.timeTable)
        calendarState.value = readJson(
            FILE_CALENDAR,
            CalendarEnvelope.serializer(),
            CalendarEnvelope(SampleData.calendarFor())
        ).calendar
    }

    override suspend fun saveCampus(value: Campus) = withContext(Dispatchers.IO) {
        writeJson(FILE_CAMPUS, Campus.serializer(), value)
        campusState.value = value
    }

    override suspend fun saveCourseTable(value: CourseTable) = withContext(Dispatchers.IO) {
        writeJson(FILE_COURSE, CourseTable.serializer(), value)
        courseState.value = value
    }

    override suspend fun saveTimeTable(value: TimeTable) = withContext(Dispatchers.IO) {
        writeJson(FILE_TIME, TimeTable.serializer(), value)
        timeState.value = value
    }

    override suspend fun saveCalendar(value: SchoolCalendar) = withContext(Dispatchers.IO) {
        writeJson(FILE_CALENDAR, CalendarEnvelope.serializer(), CalendarEnvelope(value))
        calendarState.value = value
    }

    override suspend fun resetToSample() = withContext(Dispatchers.IO) {
        listOf(FILE_CAMPUS, FILE_COURSE, FILE_TIME, FILE_CALENDAR).forEach { name ->
            runCatching { File(dataDir, name).delete() }
        }
        load()
    }

    /** 从用户数据目录或 assets 读取，失败时回退到 [fallback]。 */
    private fun <T> readJson(name: String, serializer: KSerializer<T>, fallback: T): T {
        val file = File(dataDir, name)
        if (file.exists()) {
            runCatching { json.decodeFromString(serializer, file.readText()) }
                .onSuccess { return it }
        }
        return runCatching {
            appContext.assets.open("data/$name").bufferedReader().use { it.readText() }
                .let { json.decodeFromString(serializer, it) }
        }.getOrElse { fallback }
    }

    /** 原子写入：先写 .tmp，成功后重命名覆盖。 */
    private fun <T> writeJson(name: String, serializer: KSerializer<T>, value: T) {
        val target = File(dataDir, name)
        val tmp = File(dataDir, "$name.tmp")
        runCatching {
            tmp.writeText(json.encodeToString(serializer, value))
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }.onFailure {
            runCatching { tmp.delete() }
        }
    }

    private companion object {
        const val FILE_CAMPUS = "campus.json"
        const val FILE_COURSE = "course_table.json"
        const val FILE_TIME = "time_table.json"
        const val FILE_CALENDAR = "calendar.json"
    }
}

/**
 * 校历在文件里套一层壳。
 *
 * 直接序列化 [SchoolCalendar] 也行，但套一层之后，
 * 将来要支持“一个文件存多个学期”时不会破坏已有文件格式。
 */
@kotlinx.serialization.Serializable
data class CalendarEnvelope(val calendar: SchoolCalendar = SchoolCalendar())
