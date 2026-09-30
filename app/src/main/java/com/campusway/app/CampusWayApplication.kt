package com.campusway.app

import android.app.Application

/**
 * 应用入口。
 *
 * 只做一件事：把依赖容器挂在 Application 上，供 Activity/ViewModel 取用。
 * 数据加载放在 UI 层触发（见各 ViewModel 的 init），避免启动时做重活拖慢冷启动。
 */
class CampusWayApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 设置是首帧就要用的数据：先同步预热一次，再挂上持续同步
        val settingsStore = container.settingsStore
        if (settingsStore is com.campusway.core.data.DataStoreSettingsStore) {
            settingsStore.warmUp()
            settingsStore.startObserving()
        }
    }
}
