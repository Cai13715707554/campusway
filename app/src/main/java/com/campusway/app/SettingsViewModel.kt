package com.campusway.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.campusway.BuildInfo
import com.campusway.core.data.CampusRepository
import com.campusway.core.data.SettingsStore
import com.campusway.core.map.MapCacheStore
import com.campusway.core.model.AppSettings
import com.campusway.core.model.CourseTable
import com.campusway.core.model.FontScale
import com.campusway.core.model.MapCacheInfo
import com.campusway.core.model.NavProviders
import com.campusway.core.model.SchoolCalendar
import com.campusway.core.model.ThemeMode
import com.campusway.core.model.TimeTable
import com.campusway.core.model.TimetableAxis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页状态（含关于信息与数据统计）。
 */
data class SettingsUiState(
    val campusName: String = "",
    val buildingCount: Int = 0,
    val roomCount: Int = 0,
    val courseCount: Int = 0,
    val periodCount: Int = 0,
    val termStartDate: String = "",
    val totalWeeks: Int = 20,
    val cacheInfo: MapCacheInfo? = null,
    val message: String? = null,
    val dataProblems: List<String> = emptyList()
)

/**
 * 设置页 ViewModel。
 *
 * 设置项分四组，对应需求“配置以及软件的关于介绍什么的都在这里”：
 *  1. 外观（主题、字体大小、动效）；
 *  2. 课表（显示方式、上课提醒）；
 *  3. 导航（服务商、密钥、离线地图缓存、学校坐标标定）；
 *  4. 数据与关于（恢复示例数据、版本信息）。
 */
class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val repository: CampusRepository,
    private val cacheStore: MapCacheStore
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsStore.settings

    /** 当前课程表 / 时间表：课表编辑弹层的数据源。 */
    val courseTable: StateFlow<CourseTable> = repository.courseTable
    val timeTable: StateFlow<TimeTable> = repository.timeTable

    private val detail = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = detail.asStateFlow()

    init {
        viewModelScope.launch {
            repository.load()
            refreshDetail()
        }
    }

    private fun refreshDetail() {
        val campus = repository.campus.value
        val table = repository.courseTable.value
        val timeTable = repository.timeTable.value
        val calendar = repository.calendar.value
        val roomCount = campus.buildings.sumOf { building -> building.floors.sumOf { it.rooms.size } }
        detail.value = detail.value.copy(
            campusName = campus.name,
            buildingCount = campus.buildings.size,
            roomCount = roomCount,
            courseCount = table.courses.size,
            periodCount = timeTable.periods.size,
            termStartDate = calendar.termStartDate,
            totalWeeks = calendar.totalWeeks,
            cacheInfo = cacheStore.readInfo()
        )
    }

    // ------------------------------------------------------------ 外观

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun setFontScale(scale: FontScale) = update { it.copy(fontScale = scale) }

    fun setAnimationsEnabled(enabled: Boolean) = update { it.copy(animationsEnabled = enabled) }

    // ------------------------------------------------------------ 课表

    fun setTimetableAxis(axis: TimetableAxis) = update { it.copy(timetableAxis = axis) }

    fun setShowUpcomingReminder(enabled: Boolean) = update { it.copy(showUpcomingReminder = enabled) }

    fun setReminderLeadMinutes(minutes: Int) =
        update { it.copy(reminderLeadMinutes = minutes.coerceIn(5, 60)) }

    fun setTermStartDate(date: String) {
        viewModelScope.launch {
            val calendar = repository.calendar.value
            val parsed = SchoolCalendar.parseDate(date)
            if (parsed == null) {
                detail.value = detail.value.copy(message = "日期格式应为 yyyy-MM-dd")
                return@launch
            }
            repository.saveCalendar(calendar.copy(termStartDate = date))
            refreshDetail()
            detail.value = detail.value.copy(message = "已更新学期开始日期")
        }
    }

    fun setTotalWeeks(weeks: Int) {
        viewModelScope.launch {
            val calendar = repository.calendar.value
            repository.saveCalendar(calendar.copy(totalWeeks = weeks.coerceIn(1, 30)))
            refreshDetail()
        }
    }

    // ------------------------------------------------------------ 导航

    fun setNavProvider(providerId: String) = update { it.copy(navProvider = providerId) }

    fun setAmapKey(key: String) = update { it.copy(amapKey = key.trim()) }

    fun setTencentKey(key: String) = update { it.copy(tencentKey = key.trim()) }

    fun setCacheMapWhenOnline(enabled: Boolean) = update { it.copy(cacheMapWhenOnline = enabled) }

    fun setMapCacheRadius(meters: Int) =
        update { it.copy(mapCacheRadiusMeters = meters.coerceIn(500, 10_000)) }

    fun setUseLocationAsOrigin(enabled: Boolean) = update { it.copy(useLocationAsOrigin = enabled) }

    /** 标定学校中心坐标（用于楼外导航与周边地图缓存）。 */
    fun setSchoolLocation(latitude: Double, longitude: Double) {
        if (!com.campusway.core.model.Geo.isValid(latitude, longitude)) {
            detail.value = detail.value.copy(message = "经纬度不合法")
            return
        }
        viewModelScope.launch {
            update { it.copy(schoolLatitude = latitude, schoolLongitude = longitude) }
            val campus = repository.campus.value
            repository.saveCampus(campus.copy(latitude = latitude, longitude = longitude))
            refreshDetail()
            detail.value = detail.value.copy(message = "已标定学校坐标")
        }
    }

    fun setShow3dModel(enabled: Boolean) = update { it.copy(show3dModel = enabled) }

    fun setModel3dOpacity(opacity: Float) = update { it.copy(model3dOpacity = opacity.coerceIn(0.1f, 0.9f)) }

    // ------------------------------------------------------------ 数据

    /**
     * 导入功课表：课程表与作息一起写入。
     *
     * 导入后立刻刷新页面统计，并按需要重载数据（测试数据开关打开时，
     * 导入的内容会被示例数据盖住，此时给用户一个明确提示）。
     */
    fun importTimetable(imported: com.campusway.core.importer.ImportedTimetable) {
        viewModelScope.launch {
            if (imported.isEmpty) {
                detail.value = detail.value.copy(message = "没有解析出课程，未导入")
                return@launch
            }
            repository.importTimetable(imported.timeTable, imported.courseTable)
            repository.load()
            refreshDetail()
            val warning = if (imported.warnings.isEmpty()) "" else "（${imported.warnings.size} 条提示）"
            detail.value = detail.value.copy(
                message = if (settingsStore.settings.value.useTestData) {
                    "已导入，但当前开着「测试数据」，显示的是示例课表"
                } else {
                    "导入成功：${imported.summary}$warning"
                }
            )
        }
    }

    /**
     * 导入时间表。
     *
     * [selectedKeys] 语义见 [AppSettings.visiblePeriodKeys]：
     * 传进来的是用户勾选保留的项目键；勾选为空表示"没有时间表"（彩蛋确认后的状态）。
     */
    fun importPeriodTable(timeTable: com.campusway.core.model.TimeTable, selectedKeys: List<String>) {
        viewModelScope.launch {
            repository.saveTimeTable(timeTable)
            settingsStore.update { it.copy(visiblePeriodKeys = selectedKeys) }
            repository.load()
            refreshDetail()
            detail.value = detail.value.copy(
                message = if (timeTable.isEmpty) {
                    "已按「没有时间表」处理：课表页会显示占位图"
                } else {
                    "时间表导入成功：${timeTable.periods.size} 个上课节次、" +
                        "${timeTable.timePoints.size} 个时间点；" +
                        "午别 ${timeTable.rowGroups().joinToString("、") { it.first }}"
                }
            )
        }
    }

    // ------------------------------------------------------------ 编辑

    /** 保存人工编辑后的课程表。 */
    fun saveCourseTable(value: CourseTable) {
        viewModelScope.launch {
            repository.saveCourseTable(value)
            refreshDetail()
            detail.value = detail.value.copy(message = "已保存课程表")
        }
    }

    /** 保存人工编辑后的时间表（节次 + 时间点）。 */
    fun saveTimeTable(value: TimeTable) {
        viewModelScope.launch {
            repository.saveTimeTable(value)
            refreshDetail()
            detail.value = detail.value.copy(message = "已保存时间表")
        }
    }

    // ------------------------------------------------------------ 调试

    /**
     * 校验调试入口密码。
     *
     * 判断逻辑在 [DebugAccess] 里（纯函数，可单测）；
     * 这里只做转发，避免把口令判断散落在 UI 里。
     */
    fun checkDebugPassword(input: String): Boolean = DebugAccess.check(input)

    /** 开关"测试数据"。切换后会立即重新加载数据。 */
    fun setUseTestData(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it.copy(useTestData = enabled) }
            repository.load()
            refreshDetail()
            detail.value = detail.value.copy(
                message = if (enabled) "已打开测试数据：显示内置示例学校与示例课表" else "已关闭测试数据：只显示你自己导入的数据"
            )
        }
    }

    /** 清空用户导入的数据（不会改变测试数据开关）。 */
    fun clearImportedData() {
        viewModelScope.launch {
            repository.resetToSample()
            refreshDetail()
            detail.value = detail.value.copy(
                message = if (settingsStore.settings.value.useTestData) {
                    "已清空导入数据；当前开着测试数据，仍显示示例内容"
                } else {
                    "已清空导入数据，课表页会提示重新导入"
                }
            )
        }
    }

    fun setUserName(name: String) = update { it.copy(userName = name.trim()) }

    fun setSchoolName(name: String) = update { it.copy(schoolName = name.trim()) }

    fun clearMapCache() {
        cacheStore.clear()
        detail.value = detail.value.copy(cacheInfo = null, message = "已清除离线地图缓存")
    }

    fun resetToSampleData() {
        viewModelScope.launch {
            repository.resetToSample()
            refreshDetail()
            detail.value = detail.value.copy(message = "已恢复为示例数据")
        }
    }

    /** 校验当前数据，把问题列给用户（导入的课表最容易出问题）。 */
    fun validateData() {
        viewModelScope.launch {
            val problems = mutableListOf<String>()
            problems += repository.timeTable.value.validate()
            val campus = repository.campus.value
            repository.courseTable.value.entries.forEach { entry ->
                val roomId = repository.courseTable.value.effectiveRoomId(entry)
                if (roomId.isNotBlank() && campus.locateRoom(roomId) == null) {
                    problems += "课程「${repository.courseTable.value.course(entry.courseId)?.name ?: entry.courseId}」" +
                        "引用了不存在的教室 $roomId"
                }
            }
            detail.value = detail.value.copy(
                dataProblems = problems,
                message = if (problems.isEmpty()) "数据校验通过" else "发现 ${problems.size} 个问题"
            )
        }
    }

    fun consumeMessage() {
        detail.value = detail.value.copy(message = null)
    }

    fun resetAllSettings() {
        viewModelScope.launch {
            settingsStore.reset()
            detail.value = detail.value.copy(message = "已恢复默认设置")
        }
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    /** 关于页信息，集中在这里方便统一维护。 */
    val aboutInfo: List<Pair<String, String>> = listOf(
        "应用名称" to "校途 CampusWay",
        "版本" to BuildInfo.VERSION_NAME,
        // 直接列出本版新增：在关于页看到这行，就说明装的是最新构建，
        // 不用再怀疑"改了怎么没生效"。
        "本版新增" to "引导页也能编辑课程表/时间表",
        "构建类型" to BuildInfo.BUILD_TYPE,
        "目标平台" to "Android ${BuildInfo.MIN_SDK}+",
        "导航服务商" to "高德地图 / 腾讯地图（没网时自动按离线估算）",
        "离线能力" to "课表、校园平面图、楼内导览"
    )

    companion object {
        fun factory(
            settingsStore: SettingsStore,
            repository: CampusRepository,
            cacheStore: MapCacheStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(settingsStore, repository, cacheStore) as T
        }
    }
}
