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
 * 两条数据来源，由"测试数据"开关决定（见 [AppSettings.useTestData]，默认关闭）：
 *
 *  - **关闭（默认）**：只用用户自己的数据。顺序为
 *    `filesDir/data` 下用户导入/修改后的 JSON → 没有就返回空数据。
 *    空数据是"还没导入"的正常状态，界面会给出导入引导，而不是偷偷塞示例学校进去。
 *  - **打开**：忽略用户数据，加载随包 `assets` 里的示例数据（没有则用代码里的 [SampleData]），
 *    方便开发、演示和排查问题。
 *
 * 写盘采用"临时文件 + 原子替换"，避免写入过程中进程被杀导致 JSON 只剩半截。
 */
class AssetCampusRepository(
    context: Context,
    /** 是否使用内置测试数据；由设置提供，默认关闭。 */
    private val testDataEnabled: () -> Boolean = { false },
    private val json: Json = JsonConfig.instance
) : CampusRepository {

    private val appContext = context.applicationContext
    private val dataDir: File get() = File(appContext.filesDir, "data").apply { if (!exists()) mkdirs() }

    private val campusState = MutableStateFlow(Campus())
    private val courseState = MutableStateFlow(CourseTable())
    private val timeState = MutableStateFlow(TimeTable.sample())
    private val calendarState = MutableStateFlow(SchoolCalendar.defaultFor())

    override val campus: StateFlow<Campus> = campusState.asStateFlow()
    override val courseTable: StateFlow<CourseTable> = courseState.asStateFlow()
    override val timeTable: StateFlow<TimeTable> = timeState.asStateFlow()
    override val calendar: StateFlow<SchoolCalendar> = calendarState.asStateFlow()

    override suspend fun load() = withContext(Dispatchers.IO) {
        if (testDataEnabled()) {
            loadTestData()
            return@withContext
        }
        campusState.value = readJson(FILE_CAMPUS, Campus.serializer(), Campus(), allowAssets = false)
        courseState.value = readJson(FILE_COURSE, CourseTable.serializer(), CourseTable(), allowAssets = false)
        timeState.value = readJson(FILE_TIME, TimeTable.serializer(), TimeTable.sample(), allowAssets = false)
        calendarState.value = readJson(
            FILE_CALENDAR,
            CalendarEnvelope.serializer(),
            CalendarEnvelope(SchoolCalendar.defaultFor()),
            allowAssets = false
        ).calendar
    }

    /** 测试数据：优先随包 assets，缺失时用代码内置的示例。 */
    private fun loadTestData() {
        campusState.value = readJson(FILE_CAMPUS, Campus.serializer(), SampleData.campus, allowAssets = true)
        courseState.value = readJson(FILE_COURSE, CourseTable.serializer(), SampleData.courseTable, allowAssets = true)
        timeState.value = readJson(FILE_TIME, TimeTable.serializer(), SampleData.timeTable, allowAssets = true)
        calendarState.value = readJson(
            FILE_CALENDAR,
            CalendarEnvelope.serializer(),
            CalendarEnvelope(SampleData.calendarFor()),
            allowAssets = true
        ).calendar
    }

    /** 导入功课表：课程表与作息一起落盘。 */
    override suspend fun importTimetable(timeTable: TimeTable, courseTable: CourseTable) {
        saveTimeTable(timeTable)
        saveCourseTable(courseTable)
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

    /** 清空用户数据（不会动测试数据开关）。 */
    override suspend fun resetToSample() = withContext(Dispatchers.IO) {
        listOf(FILE_CAMPUS, FILE_COURSE, FILE_TIME, FILE_CALENDAR).forEach { name ->
            runCatching { File(dataDir, name).delete() }
        }
        load()
    }

    /**
     * 读取顺序：用户数据目录 → （可选）assets → [fallback]。
     *
     * [allowAssets] 只在"使用测试数据"时为 true：
     * 否则随包的示例学校会悄悄盖过"用户还没导入"的空状态。
     */
    private fun <T> readJson(
        name: String,
        serializer: KSerializer<T>,
        fallback: T,
        allowAssets: Boolean
    ): T {
        val file = File(dataDir, name)
        if (file.exists()) {
            runCatching { json.decodeFromString(serializer, file.readText()) }
                .onSuccess { return it }
        }
        if (!allowAssets) return fallback
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
 * 将来要支持"一个文件存多个学期"时不会破坏已有文件格式。
 */
@kotlinx.serialization.Serializable
data class CalendarEnvelope(val calendar: SchoolCalendar = SchoolCalendar())
