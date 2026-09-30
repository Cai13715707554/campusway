package com.campusway.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.campusway.core.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/** 应用级 DataStore，整个进程只创建一次。 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "campusway_settings"
)

/**
 * 基于 DataStore 的设置存储。
 *
 * 设置整体以一条 JSON 字符串保存：档位字段会随版本增减，
 * 用 JSON 能天然容忍新旧字段差异（配合 JsonConfig 的容错配置），
 * 不必为每个字段单独建 Preference key 并处理迁移。
 */
class DataStoreSettingsStore(
    context: Context,
    private val json: Json = JsonConfig.compact
) : SettingsStore {

    private val dataStore = context.applicationContext.settingsDataStore

    /** DataStore 读取是异步的，这里缓存最近一次值，供 UI 同步读取。 */
    private val cache = MutableStateFlow(AppSettings.Default)
    override val settings: StateFlow<AppSettings> = cache.asStateFlow()

    /** DataStore 内的原始流，用于启动时预热缓存。 */
    private val rawFlow: Flow<AppSettings> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> decode(prefs[KEY]) }

    /** 同步用的应用级作用域，与任何界面生命周期无关。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observeJob: Job? = null

    /** 在应用启动时调用一次即可，之后 [settings] 始终是最新值。 */
    fun warmUp(): AppSettings {
        val loaded = runCatching {
            runBlocking { dataStore.data.first() }
        }.getOrNull()?.let { prefs -> decode(prefs[KEY]) } ?: AppSettings.Default
        cache.value = loaded
        return loaded
    }

    /**
     * 开始持续把 DataStore 的值同步到内存缓存。
     *
     * 应用启动时调用一次。这样无论是本进程写入，还是数据被备份还原/被其他来源改动，
     * [settings] 都会跟着更新，界面不会停留在旧值。
     */
    fun startObserving() {
        if (observeJob != null) return
        observeJob = scope.launch {
            rawFlow.collect { latest -> cache.value = latest }
        }
    }

    /** 停止同步（一般不需要，仅在测试里手动收尾时用）。 */
    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY])
            val next = transform(current)
            prefs[KEY] = json.encodeToString(AppSettings.serializer(), next)
            cache.value = next
        }
    }

    override suspend fun reset() {
        dataStore.edit { prefs ->
            prefs[KEY] = json.encodeToString(AppSettings.serializer(), AppSettings.Default)
        }
        cache.value = AppSettings.Default
    }

    private fun decode(raw: String?): AppSettings {
        if (raw.isNullOrBlank()) return AppSettings.Default
        return runCatching {
            json.decodeFromString(AppSettings.serializer(), raw)
        }.getOrElse { AppSettings.Default }
    }

    private companion object {
        val KEY = stringPreferencesKey("settings_json")
    }
}
