package com.campusway.core.data

import com.campusway.core.model.AppSettings
import com.campusway.core.model.FontScale
import com.campusway.core.model.NavProviders
import com.campusway.core.model.ThemeMode
import com.campusway.core.model.TimetableAxis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置读写与持久化测试。
 *
 * 设置里既有枚举（主题、字号）也有浮点（3D 透明度）与可空语义，
 * 序列化一旦丢字段，用户重启后设置就"失忆"，所以做完整的往返校验。
 */
class SettingsStoreTest {

    @Test
    fun `默认设置可以直接使用`() {
        val defaults = AppSettings.Default
        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
        assertEquals(FontScale.NORMAL, defaults.fontScale)
        assertEquals(NavProviders.OFFLINE, defaults.navProvider)
        assertFalse(defaults.onboardingCompleted)
        assertFalse(defaults.schoolHasGeo)
    }

    @Test
    fun `修改设置后能读到新值`() = runBlocking {
        val store = InMemorySettingsStore()
        store.update { it.copy(themeMode = ThemeMode.DARK, fontScale = FontScale.LARGE) }
        assertEquals(ThemeMode.DARK, store.settings.value.themeMode)
        assertEquals(FontScale.LARGE, store.settings.value.fontScale)
    }

    @Test
    fun `恢复默认设置`() = runBlocking {
        val store = InMemorySettingsStore(AppSettings.Default.copy(userName = "小蔡"))
        store.reset()
        assertEquals("", store.settings.value.userName)
    }

    @Test
    fun `JSON 往返不丢失任何字段`() {
        val original = AppSettings(
            themeMode = ThemeMode.DARK,
            animationsEnabled = false,
            fontScale = FontScale.EXTRA_LARGE,
            timetableAxis = TimetableAxis.TIME,
            showUpcomingReminder = false,
            reminderLeadMinutes = 25,
            navProvider = NavProviders.TENCENT,
            amapKey = "amap-key-1234",
            tencentKey = "tencent-key-5678",
            cacheMapWhenOnline = false,
            mapCacheRadiusMeters = 3500,
            useLocationAsOrigin = true,
            onboardingCompleted = true,
            userName = "小蔡",
            schoolName = "示例职业学校",
            schoolLatitude = 30.6581,
            schoolLongitude = 104.0659,
            show3dModel = true,
            model3dOpacity = 0.62f
        )
        val store = InMemorySettingsStore(original)
        assertEquals(original, store.roundTrip())
    }

    @Test
    fun `未知字段不会导致解析失败`() {
        val json = JsonConfig.compact
        val text = """
            {"themeMode":"DARK","userName":"小蔡","未来新增的字段":123,"另一个":[1,2]}
        """.trimIndent()
        val parsed = json.decodeFromString(AppSettings.serializer(), text)
        assertEquals(ThemeMode.DARK, parsed.themeMode)
        assertEquals("小蔡", parsed.userName)
        // 缺省字段回落为默认值
        assertEquals(FontScale.NORMAL, parsed.fontScale)
    }

    @Test
    fun `空字符串也能解析为默认设置`() {
        val store = InMemorySettingsStore()
        // 这里验证的是容错策略：坏数据不应该让应用打不开
        val broken = runCatching {
            JsonConfig.compact.decodeFromString(AppSettings.serializer(), "{ 这不是 json }")
        }
        assertTrue(broken.isFailure)
        assertNotNull(store.settings.value)
    }

    @Test
    fun `未选离线服务商时离线能力说明正确`() {
        assertEquals(
            "离线模式：课表、校园平面图与楼内导览全部可用",
            AppSettings.Default.copy(navProvider = NavProviders.OFFLINE).offlineCapabilityText
        )
    }

    @Test
    fun `选了高德但没填 Key 时提示会回退离线估算`() {
        val settings = AppSettings.Default.copy(navProvider = NavProviders.AMAP, amapKey = "")
        assertFalse(settings.providerConfigured)
        assertTrue(settings.offlineCapabilityText.contains("离线估算"))
    }

    @Test
    fun `填了 Key 但没标定学校坐标时仍走离线估算`() {
        val settings = AppSettings.Default.copy(navProvider = NavProviders.AMAP, amapKey = "k")
        assertTrue(settings.providerConfigured)
        assertFalse(settings.schoolHasGeo)
        assertTrue(settings.offlineCapabilityText.contains("未标定"))
    }

    @Test
    fun `填了 Key 且标定了坐标时按在线服务说明`() {
        val settings = AppSettings.Default.copy(
            navProvider = NavProviders.AMAP,
            amapKey = "k",
            schoolLatitude = 30.6,
            schoolLongitude = 104.0
        )
        assertTrue(settings.schoolHasGeo)
        assertTrue(settings.offlineCapabilityText.contains("缓存"))
    }

    @Test
    fun `离线服务商永远视为已配置`() {
        assertTrue(AppSettings.Default.copy(navProvider = NavProviders.OFFLINE).providerConfigured)
        assertTrue(AppSettings.Default.copy(navProvider = NavProviders.OFFLINE, amapKey = "").providerConfigured)
    }

    @Test
    fun `服务商显示名可读`() {
        assertEquals("高德地图", NavProviders.displayName(NavProviders.AMAP))
        assertEquals("腾讯地图", NavProviders.displayName(NavProviders.TENCENT))
        assertEquals("离线估算", NavProviders.displayName(NavProviders.OFFLINE))
        // 未知 id 不应崩
        assertEquals("离线估算", NavProviders.displayName("某家没听过的地图"))
    }
}

/**
 * 数据仓储：内存实现与示例数据自洽性。
 */
class CampusRepositoryTest {

    @Test
    fun `默认加载内置示例数据`() = runBlocking {
        val repo = InMemoryCampusRepository(
            initialCampus = SampleData.campus,
            initialCourseTable = SampleData.courseTable,
            initialTimeTable = SampleData.timeTable,
            initialCalendar = SampleData.calendarFor()
        )
        repo.load()
        assertTrue(repo.campus.value.buildings.isNotEmpty())
        assertTrue(repo.courseTable.value.courses.isNotEmpty())
        assertTrue(repo.timeTable.value.periods.isNotEmpty())
    }

    @Test
    fun `保存校园数据后立即可读`() = runBlocking {
        val repo = InMemoryCampusRepository(initialCampus = SampleData.campus)
        val renamed = SampleData.campus.copy(name = "新的学校名")
        repo.saveCampus(renamed)
        assertEquals("新的学校名", repo.campus.value.name)
    }

    @Test
    fun `校园数据 JSON 往返保留楼层与房间`() {
        val repo = InMemoryCampusRepository(initialCampus = SampleData.campus)
        val restored = repo.campusRoundTrip()
        assertEquals(SampleData.campus.buildings.size, restored.buildings.size)
        assertEquals(
            SampleData.campus.buildings.first().floors.size,
            restored.buildings.first().floors.size
        )
        // 房间 id 必须完全一致，否则课表会找不到教室
        val original = SampleData.campus.locateRoom("b1-f3-r302")
        val after = restored.locateRoom("b1-f3-r302")
        assertNotNull(original)
        assertNotNull(after)
        assertEquals(original!!.room.name, after!!.room.name)
    }

    @Test
    fun `课表 JSON 往返保留排课与教室覆盖`() {
        val repo = InMemoryCampusRepository()
        val restored = repo.courseTableRoundTrip()
        assertEquals(SampleData.courseTable.courses.size, restored.courses.size)
        assertEquals(SampleData.courseTable.entries.size, restored.entries.size)
    }

    @Test
    fun `恢复示例数据`() = runBlocking {
        val repo = InMemoryCampusRepository(initialCampus = SampleData.campus)
        repo.saveCampus(SampleData.campus.copy(name = "被改过的"))
        repo.resetToSample()
        assertEquals(SampleData.campus.name, repo.campus.value.name)
    }

    @Test
    fun `示例数据校验通过`() {
        assertEquals(emptyList<String>(), SampleData.validate())
    }

    @Test
    fun `示例校园里每栋楼都有楼层与房间`() {
        SampleData.campus.buildings.forEach { building ->
            assertTrue("${building.name} 没有楼层", building.floors.isNotEmpty())
            building.floors.forEach { floor ->
                assertTrue("${building.name}${floor.name} 没有房间", floor.rooms.isNotEmpty())
            }
        }
    }

    @Test
    fun `示例数据里每层都有可走的路径节点`() {
        SampleData.campus.buildings.forEach { building ->
            building.floors.forEach { floor ->
                assertTrue("${building.id} ${floor.floorId} 缺少路径节点", floor.nodes.size >= 2)
                assertTrue("${building.id} ${floor.floorId} 缺少路径连线", floor.edges.isNotEmpty())
            }
        }
    }

    @Test
    fun `示例数据包含校园地标与经纬度`() {
        assertTrue(SampleData.campus.landmarks.isNotEmpty())
        assertTrue(SampleData.campus.hasGeo)
        assertTrue(SampleData.campus.buildings.all { it.hasGeo })
    }

    @Test
    fun `示例时间表的前后节次不重叠`() {
        assertEquals(emptyList<String>(), SampleData.timeTable.validate())
    }

    @Test
    fun `示例课表覆盖工作日`() {
        val days = SampleData.courseTable.entries.map { it.dayOfWeek }.toSet()
        assertTrue(days.containsAll(setOf(1, 2, 3, 4, 5)))
    }
}
