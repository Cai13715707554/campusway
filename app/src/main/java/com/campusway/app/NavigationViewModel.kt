package com.campusway.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.campusway.core.data.CampusRepository
import com.campusway.core.data.SettingsStore
import com.campusway.core.map.MapCacheStore
import com.campusway.core.map.MapProvider
import com.campusway.core.map.MapProviderFactory
import com.campusway.core.model.Campus
import com.campusway.core.model.CourseTable
import com.campusway.core.model.Geo
import com.campusway.core.model.GuidancePlan
import com.campusway.core.model.MapCacheInfo
import com.campusway.core.model.NavCoord
import com.campusway.core.model.NavProviders
import com.campusway.core.model.NavTarget
import com.campusway.core.model.OutdoorRoute
import com.campusway.core.model.RoutePlanner
import com.campusway.core.model.SearchHit
import com.campusway.core.model.TodaySchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

/**
 * 导航页的界面状态。
 */
data class NavUiState(
    val query: String = "",
    val results: List<SearchHit> = emptyList(),
    val selectedBuildingId: String? = null,
    val selectedFloorId: String? = null,
    /** 高亮的教室（来自搜索、课表或点击）。 */
    val highlightedRoomIds: List<String> = emptyList(),
    /** 楼内路径（当前楼层）。 */
    val routePoints: List<com.campusway.core.model.NormPoint> = emptyList(),
    val guidance: GuidancePlan? = null,
    val loading: Boolean = false,
    val message: String? = null,
    val cacheInfo: MapCacheInfo? = null,
    /** 是否显示“从当前课表教室出发”的快捷入口。 */
    val currentLessonRoomId: String? = null,
    /**
     * 设置里配置的楼外导航服务商。
     *
     * 与 [GuidancePlan.providerId] 的区别：后者是"上一次路线实际用了谁"，
     * 失败时会降级成离线估算。页面顶部要显示的是用户配置的那个，
     * 否则刚进页面就会出现「离线估算」，看起来像是选了个离线模式。
     */
    val configuredProvider: String = NavProviders.AMAP
) {
    val inBuilding: Boolean get() = selectedBuildingId != null
}

/**
 * 导航页 ViewModel。
 *
 * 覆盖需求里“导航”页的全部能力：
 *  - 校园平面图，点教学楼进入楼内示意图；
 *  - 搜索教学楼/教室，进入后高亮目标教室；
 *  - 楼外导航（高德/腾讯/离线估算）+ 楼内导览，串成一次完整引导；
 *  - 联网时缓存学校周边地图，之后离线也能看。
 */
class NavigationViewModel(
    private val repository: CampusRepository,
    private val settingsStore: SettingsStore,
    private val cacheStore: MapCacheStore,
    private val providerFactory: (String) -> MapProvider,
    /** 取系统定位用；纯逻辑单元测试时可为 null。 */
    private val appContext: android.content.Context? = null
) : ViewModel() {

    private val state = MutableStateFlow(NavUiState())

    val uiState: StateFlow<NavUiState> = state.asStateFlow()

    /** 当前校园数据，界面直接读取（同时也通过 flow 触发重组）。 */
    val campus: StateFlow<Campus> = repository.campus
    val courseTable: StateFlow<CourseTable> = repository.courseTable

    init {
        // 持续跟随设置里的服务商，用户在设置页改了就立刻反映到页面顶部
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                state.value = state.value.copy(configuredProvider = settings.navProvider)
            }
        }
        viewModelScope.launch {
            repository.load()
            state.value = state.value.copy(cacheInfo = cacheStore.readInfo())
            refreshCurrentLessonRoom()
        }
    }

    /** 找出“今天正在上/下一节”的教室，作为导航快捷入口。 */
    private suspend fun refreshCurrentLessonRoom() {
        val table = repository.courseTable.first()
        val timeTable = repository.timeTable.first()
        val lessons = TodaySchedule.build(LocalDate.now(), table, timeTable, LocalTime.now())
        val roomId = TodaySchedule.current(lessons)?.roomId
            ?: TodaySchedule.next(lessons)?.roomId
        state.value = state.value.copy(currentLessonRoomId = roomId?.takeIf { it.isNotBlank() })
    }

    // ------------------------------------------------------------ 搜索

    fun onQueryChange(query: String) {
        val campus = repository.campus.value
        state.value = state.value.copy(
            query = query,
            results = if (query.isBlank()) emptyList() else campus.search(query)
        )
    }

    fun clearSearch() {
        state.value = state.value.copy(query = "", results = emptyList())
    }

    // ------------------------------------------------------------ 楼内

    fun openBuilding(buildingId: String) {
        val campus = repository.campus.value
        val building = campus.building(buildingId) ?: return
        state.value = state.value.copy(
            selectedBuildingId = buildingId,
            selectedFloorId = building.defaultFloor()?.floorId,
            highlightedRoomIds = emptyList(),
            routePoints = emptyList(),
            guidance = null,
            results = emptyList(),
            query = "",
            message = null
        )
    }

    fun closeBuilding() {
        state.value = state.value.copy(
            selectedBuildingId = null,
            selectedFloorId = null,
            highlightedRoomIds = emptyList(),
            routePoints = emptyList(),
            guidance = null,
            message = null
        )
    }

    fun selectFloor(floorId: String) {
        state.value = state.value.copy(selectedFloorId = floorId)
    }

    /**
     * 从课表页“带我去这间教室”进来的入口。
     *
     * 会自动：进入该教室所在的教学楼 → 切到所在楼层 → 高亮教室 →
     * 一次性算好楼内路径与楼外段，省得用户再搜一遍。
     */
    fun openRoomById(roomId: String) {
        val campus = repository.campus.value
        val location = campus.locateRoom(roomId)
        if (location == null) {
            state.value = state.value.copy(message = "这间教室还没有在校园图里标定")
            return
        }
        state.value = state.value.copy(
            selectedBuildingId = location.building.id,
            selectedFloorId = location.floor.floorId,
            highlightedRoomIds = listOf(location.room.id),
            results = emptyList(),
            query = "",
            message = null
        )
        planGuidance(
            buildingId = location.building.id,
            roomId = location.room.id,
            label = location.room.name,
            fromRoomId = originRoomId()
        )
    }

    /** 点击搜索结果：教室直接高亮并规划路线；教学楼则进入该楼。 */
    fun openHit(hit: SearchHit) {
        when (hit) {
            is SearchHit.BuildingHit -> openBuilding(hit.building.id)
            is SearchHit.RoomHit -> {
                val fromRoom = originRoomId()
                state.value = state.value.copy(
                    selectedBuildingId = hit.building.id,
                    selectedFloorId = hit.floor.floorId,
                    highlightedRoomIds = listOf(hit.room.id),
                    results = emptyList(),
                    query = ""
                )
                planGuidance(hit.building.id, hit.room.id, hit.room.name, fromRoom)
            }
            is SearchHit.LandmarkHit -> {
                // 地标没有楼内部分，只做楼外引导
                state.value = state.value.copy(results = emptyList(), query = "")
                planLandmarkGuidance(hit)
            }
        }
    }

    /** 在楼内点击房间：高亮它，并显示“怎么走”。 */
    fun onRoomClick(roomId: String) {
        val buildingId = state.value.selectedBuildingId ?: return
        val building = repository.campus.value.building(buildingId) ?: return
        val room = building.findRoom(roomId) ?: return
        state.value = state.value.copy(highlightedRoomIds = listOf(roomId))
        planGuidance(buildingId, roomId, room.name, originRoomId())
    }

    /**
     * 起点选择：需求明确不做室内实时定位，所以只做显式选择——
     * 优先用“当前正在上/下一节课的教室”，否则从本楼出入口进入。
     */
    private fun originRoomId(): String? = state.value.currentLessonRoomId

    // ------------------------------------------------------------ 楼外 + 楼内引导

    /**
     * 规划一次完整引导：楼外段 + 楼内段。
     *
     * 楼内路径会同时写进 [NavUiState.routePoints]，供示意图画折线；
     * 楼层也会自动切到目标教室所在层，用户不用自己找。
     */
    private fun planGuidance(buildingId: String, roomId: String?, label: String, fromRoomId: String?) {
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, message = null)
            val campus = repository.campus.value
            val settings = settingsStore.settings.value
            val building = campus.building(buildingId)

            // 楼内段：先算，顺便决定要展示哪一层
            val indoor = if (building != null && !roomId.isNullOrBlank()) {
                RoutePlanner.planIndoor(
                    campus = campus,
                    building = building,
                    toRoomId = roomId,
                    fromRoomId = fromRoomId?.takeIf { room -> building.findRoom(room) != null }
                )
            } else null

            // 楼外段
            val outdoor = computeOutdoor(
                campus = campus,
                target = building?.let { NavCoord(it.latitude, it.longitude) },
                providerId = settings.navProvider
            )

            state.value = state.value.copy(
                loading = false,
                selectedFloorId = indoor?.floorId ?: state.value.selectedFloorId,
                routePoints = indoor?.polylines?.get(indoor.floorId).orEmpty(),
                guidance = GuidancePlan(
                    target = NavTarget(buildingId, roomId, label),
                    outdoor = outdoor,
                    indoor = indoor,
                    providerId = outdoor?.providerId ?: settings.navProvider
                )
            )
        }
    }

    private fun planLandmarkGuidance(hit: SearchHit.LandmarkHit) {
        viewModelScope.launch {
            val settings = settingsStore.settings.value
            val campus = repository.campus.value
            val landmark = hit.landmark
            val outdoor = if (landmark.hasGeo && campus.hasGeo) {
                providerFactory(settings.navProvider).walkRoute(
                    from = NavCoord(campus.latitude, campus.longitude),
                    to = NavCoord(landmark.latitude, landmark.longitude)
                )
            } else null
            state.value = state.value.copy(
                guidance = GuidancePlan(
                    target = NavTarget(buildingId = "", roomId = null, label = landmark.name),
                    outdoor = outdoor,
                    indoor = null,
                    providerId = outdoor?.providerId ?: NavProviders.OFFLINE
                )
            )
        }
    }

    /** 计算楼外段：能定位就用定位，否则从校门/校园中心出发。 */
    private suspend fun computeOutdoor(
        campus: Campus,
        target: NavCoord?,
        providerId: String
    ): OutdoorRoute? {
        if (target == null || !target.isValid) {
            return OutdoorRoute(
                distanceMeters = 0.0,
                minutes = 0,
                legs = listOf(
                    com.campusway.core.model.OutdoorLeg(
                        "该建筑尚未标定经纬度，无法进行楼外导航（可在设置里标定学校坐标）",
                        0.0, 0
                    )
                ),
                providerId = NavProviders.OFFLINE,
                isEstimated = true
            )
        }

        val origin = campusOrigin(campus) ?: return OutdoorRoute(
            distanceMeters = 0.0,
            minutes = 0,
            legs = listOf(
                com.campusway.core.model.OutdoorLeg(
                    "尚未标定学校坐标，仅提供楼内导览",
                    0.0, 0
                )
            ),
            providerId = NavProviders.OFFLINE,
            isEstimated = true
        )

        return withContext(Dispatchers.IO) {
            providerFactory(providerId).walkRoute(origin, target)
        }
    }

    /** 楼外起点：优先用系统定位（若用户开启且可用），否则用学校中心点/校门。 */
    private suspend fun campusOrigin(campus: Campus): NavCoord? {
        val settings = settingsStore.settings.value
        if (settings.useLocationAsOrigin) {
            lastKnownLocation()?.let { return it }
        }
        val gate = campus.landmarks.firstOrNull { it.hasGeo }
        return when {
            gate != null -> NavCoord(gate.latitude, gate.longitude)
            campus.hasGeo -> NavCoord(campus.latitude, campus.longitude)
            else -> null
        }
    }

    /**
     * 取最近一次定位。
     *
     * 这里刻意用系统 API 而不是高德定位 SDK：
     *  1. 不绑定服务商，换高德/腾讯都不影响；
     *  2. 少一个几 MB 的 so 依赖，APK 更小；
     *  3. 精度对“到校门口”这种粒度完全够用。
     */
    private fun lastKnownLocation(): NavCoord? = runCatching {
        val context = appContext ?: return null
        val manager = context.getSystemService(android.content.Context.LOCATION_SERVICE)
            as? android.location.LocationManager ?: return null
        val provider = when {
            manager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ->
                android.location.LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) ->
                android.location.LocationManager.NETWORK_PROVIDER
            else -> return null
        }
        manager.getLastKnownLocation(provider)?.let { NavCoord(it.latitude, it.longitude) }
    }.getOrNull()

    // ------------------------------------------------------------ 地图缓存

    /** 联网时缓存学校周边地图，供之后离线查看。 */
    fun cacheMapAroundSchool() {
        viewModelScope.launch {
            val campus = repository.campus.value
            val settings = settingsStore.settings.value
            if (!campus.hasGeo) {
                state.value = state.value.copy(message = "尚未标定学校坐标，无法缓存周边地图")
                return@launch
            }
            state.value = state.value.copy(loading = true, message = "正在缓存学校周边地图…")
            val provider = providerFactory(settings.navProvider)
            val info = withContext(Dispatchers.IO) {
                provider.cacheAround(
                    center = NavCoord(campus.latitude, campus.longitude),
                    radiusMeters = settings.mapCacheRadiusMeters,
                    areaName = campus.name
                )
            }
            val (tiles, bytes) = cacheStore.stats()
            val merged = info.copy(tileCount = tiles, sizeBytes = bytes)
            cacheStore.writeInfo(merged)
            state.value = state.value.copy(
                loading = false,
                cacheInfo = merged,
                message = if (merged.tileCount > 0) {
                    "已缓存 ${merged.tileCount} 块地图（${merged.sizeText}），离线可用"
                } else {
                    "缓存失败：请检查网络与 ${NavProviders.displayName(settings.navProvider)} Key"
                }
            )
        }
    }

    fun clearMapCache() {
        cacheStore.clear()
        state.value = state.value.copy(cacheInfo = null, message = "已清除离线地图缓存")
    }

    fun consumeMessage() {
        state.value = state.value.copy(message = null)
    }

    /** 距离某个建筑多远（用于楼外距离的即时展示）。 */
    fun distanceTo(buildingId: String): String? {
        val campus = repository.campus.value
        val building = campus.building(buildingId) ?: return null
        if (!building.hasGeo || !campus.hasGeo) return null
        val meters = Geo.distanceMeters(
            campus.latitude, campus.longitude, building.latitude, building.longitude
        )
        return Geo.formatDistance(meters)
    }

    companion object {
        fun factory(
            repository: CampusRepository,
            settingsStore: SettingsStore,
            cacheStore: MapCacheStore,
            context: android.content.Context,
            providerFactory: (String) -> MapProvider
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NavigationViewModel(
                    repository = repository,
                    settingsStore = settingsStore,
                    cacheStore = cacheStore,
                    providerFactory = providerFactory,
                    appContext = context.applicationContext
                ) as T
        }
    }
}
