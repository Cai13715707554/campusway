package com.campusway.app

import android.app.Application
import com.campusway.core.data.DataStoreSettingsStore

/**
 * 应用入口。
 *
 * 只做一件事：把依赖容器挂在 Application 上，供 Activity/ViewModel 取用。
 * 数据加载放在 UI 层触发（见各 ViewModel 的 init），避免启动时做重活拖慢冷启动。
 *
 * 关于设置的读取方式：
 * 这里**不做**主线程阻塞式读取（warmUp 里是 runBlocking，慢设备上有 ANR 风险）。
 * 改为启动一个后台协程持续把 DataStore 的值同步到内存缓存：
 * 首帧先用默认设置渲染，几毫秒后拿到真实设置并自动重组。
 * 默认主题是「跟随系统」，所以绝大多数情况下用户察觉不到这一瞬间。
 */
class CampusWayApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        val settingsStore = container.settingsStore
        if (settingsStore is DataStoreSettingsStore) {
            settingsStore.startObserving()
        }
    }
}
