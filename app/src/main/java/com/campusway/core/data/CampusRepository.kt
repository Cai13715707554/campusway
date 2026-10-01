package com.campusway.core.data

import com.campusway.core.model.Campus
import com.campusway.core.model.CourseTable
import com.campusway.core.model.SchoolCalendar
import com.campusway.core.model.TimeTable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * 校园数据仓储：课表、时间表、校历、校园平面图。
 *
 * 这些数据全部本地存储，读取不依赖网络，满足“主要功能可离线运行”。
 * 抽成接口是为了让纯 JVM 单元测试直接用内存实现，不需要 Android 环境。
 */
interface CampusRepository {

    val campus: StateFlow<Campus>
    val courseTable: StateFlow<CourseTable>
    val timeTable: StateFlow<TimeTable>
    val calendar: StateFlow<SchoolCalendar>

    suspend fun load()

    suspend fun saveCampus(value: Campus)

    suspend fun saveCourseTable(value: CourseTable)

    suspend fun saveTimeTable(value: TimeTable)

    suspend fun saveCalendar(value: SchoolCalendar)

    /**
     * 导入一份功课表：课程表与作息一起写入。
     *
     * 单独开一个方法而不是让调用方连着调两次 save，是为了保证两者总是同时更新——
     * 只写课程表不写作息，会出现"课程引用了不存在的节次"。
     */
    suspend fun importTimetable(timeTable: TimeTable, courseTable: CourseTable)

    /** 恢复为内置示例数据。 */
    suspend fun resetToSample()
}

/**
 * 内存实现：单元测试与 Compose 预览使用。
 */
class InMemoryCampusRepository(
    initialCampus: Campus = SampleData.campus,
    initialCourseTable: CourseTable = SampleData.courseTable,
    initialTimeTable: TimeTable = SampleData.timeTable,
    initialCalendar: SchoolCalendar = SampleData.calendarFor(),
    private val json: Json = JsonConfig.instance
) : CampusRepository {

    private val campusState = MutableStateFlow(initialCampus)
    private val courseState = MutableStateFlow(initialCourseTable)
    private val timeState = MutableStateFlow(initialTimeTable)
    private val calendarState = MutableStateFlow(initialCalendar)

    override val campus: StateFlow<Campus> = campusState.asStateFlow()
    override val courseTable: StateFlow<CourseTable> = courseState.asStateFlow()
    override val timeTable: StateFlow<TimeTable> = timeState.asStateFlow()
    override val calendar: StateFlow<SchoolCalendar> = calendarState.asStateFlow()

    override suspend fun load() = Unit

    override suspend fun saveCampus(value: Campus) {
        campusState.value = value
    }

    override suspend fun saveCourseTable(value: CourseTable) {
        courseState.value = value
    }

    override suspend fun saveTimeTable(value: TimeTable) {
        timeState.value = value
    }

    override suspend fun saveCalendar(value: SchoolCalendar) {
        calendarState.value = value
    }

    override suspend fun importTimetable(timeTable: TimeTable, courseTable: CourseTable) {
        timeState.value = timeTable
        courseState.value = courseTable
    }

    override suspend fun resetToSample() {
        campusState.value = SampleData.campus
        courseState.value = SampleData.courseTable
        timeState.value = SampleData.timeTable
        calendarState.value = SampleData.calendarFor()
    }

    /** 各数据模型的 JSON 往返，用于验证导入导出格式稳定。 */
    fun campusRoundTrip(): Campus =
        json.decodeFromString(Campus.serializer(), json.encodeToString(Campus.serializer(), campusState.value))

    fun courseTableRoundTrip(): CourseTable =
        json.decodeFromString(
            CourseTable.serializer(),
            json.encodeToString(CourseTable.serializer(), courseState.value)
        )
}
