package com.campusway.app

import android.content.Context
import com.campusway.core.data.AssetCampusRepository
import com.campusway.core.data.CampusRepository
import com.campusway.core.data.DataStoreSettingsStore
import com.campusway.core.data.SettingsStore
import com.campusway.core.map.MapCacheStore
import com.campusway.core.map.MapProvider
import com.campusway.core.map.MapProviderFactory
import com.campusway.core.map.OfflineMapProvider

/**
 * 简易依赖容器。
 *
 * 项目规模不大，用一个手写容器代替 Hilt/Koin：
 * 少一个注解处理器、少一堆生成代码，离线构建更快，也更容易读懂。
 * 全部依赖都是懒加载单例，进程内共享。
 */
class AppContainer(private val context: Context) {

    val campusRepository: CampusRepository by lazy { AssetCampusRepository(context) }

    val settingsStore: SettingsStore by lazy { DataStoreSettingsStore(context) }

    val mapCache: MapCacheStore by lazy { MapCacheStore(context) }

    /**
     * 当前地图服务商。
     *
     * 这里是“高德 / 腾讯都做”的落点：设置里选中哪家就用哪家，
     * Key 没填或请求失败时，各实现内部会回退到离线估算，
     * 所以永远不会出现“导航页打不开”的情况。
     */
    fun mapProvider(): MapProvider {
        val settings = settingsStore.settings.value
        return MapProviderFactory.create(
            providerId = settings.navProvider,
            context = context,
            cache = mapCache,
            amapKey = { settingsStore.settings.value.amapKey },
            tencentKey = { settingsStore.settings.value.tencentKey }
        )
    }

    /** 兜底服务商，离线场景直接使用。 */
    fun offlineProvider(): MapProvider = OfflineMapProvider()
}
