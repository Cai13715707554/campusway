package com.campusway.core.data

import com.campusway.core.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * 设置的读写接口。
 *
 * 抽象成接口有两个原因：
 *  1. 单元测试用 [InMemorySettingsStore]，不需要 Android 的 DataStore；
 *  2. 将来若要换成 Room 或云端同步，只改实现不动上层。
 */
interface SettingsStore {
    val settings: StateFlow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)

    suspend fun reset()
}

/**
 * 纯内存实现，供单元测试与预览使用。
 */
class InMemorySettingsStore(
    initial: AppSettings = AppSettings.Default,
    private val json: Json = JsonConfig.compact
) : SettingsStore {

    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }

    override suspend fun reset() {
        state.value = AppSettings.Default
    }

    /** 序列化往返，用于验证设置可被完整持久化。 */
    fun roundTrip(): AppSettings =
        json.decodeFromString(AppSettings.serializer(), json.encodeToString(AppSettings.serializer(), state.value))
}
