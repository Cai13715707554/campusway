package com.campusway.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.campusway.core.model.AppSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/** 应用级 DataStore，整个进程只创建一次。 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "campusway_settings"
)

/**
 * 基于 DataStore 的设置存储。
 *
 * 两条重要设计：
 *
 * 1. **设置整体以一条 JSON 保存**。配置项会随版本增减，用 JSON 天然容忍新旧字段差异，
 *    不必为每个字段单独建 key 并写迁移代码。
 *
 * 2. **内存缓存是界面读取的唯一来源，写入时先更新内存再落盘**。
 *    如果把 `cache.value = next` 放在 `dataStore.edit {}` 内部，
 *    界面能否拿到新值就取决于磁盘写入何时完成——写入慢或卡住时，
 *    引导页里调字号、换主题就会"没有实时生效"。
 */
class DataStoreSettingsStore(
    context: Context,
    private val json: Json = JsonConfig.compact
) : SettingsStore {

    private val dataStore = context.applicationContext.settingsDataStore

    /** 界面读的就是它；写入时先改这里，保证零延迟反馈。 */
    private val cache = MutableStateFlow(AppSettings.Default)
    override val settings: StateFlow<AppSettings> = cache.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observeJob: Job? = null

    /** 首次加载完成信号：写入前必须先等它，否则会在默认值上做修改而丢掉已保存的设置。 */
    private val initialLoad = CompletableDeferred<Unit>()

    /**
     * 启动一次即可：把已保存的设置读进内存缓存。
     *
     * 之后不再持续监听磁盘变化——本进程是唯一的写入方，
     * 持续监听反而会用磁盘旧值覆盖内存里刚写入的新值。
     */
    fun startObserving() {
        if (observeJob != null) return
        observeJob = scope.launch {
            runCatching { dataStore.data.first() }
                .getOrNull()
                ?.let { prefs -> cache.value = decode(prefs[KEY]) }
            initialLoad.complete(Unit)
        }
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        awaitInitialLoad()
        val next = transform(cache.value)
        if (next == cache.value) return

        // 1) 立即反馈到界面
        cache.value = next

        // 2) 再持久化；失败时回读磁盘，避免内存与存储不一致
        persist(next)
    }

    override suspend fun reset() {
        awaitInitialLoad()
        cache.value = AppSettings.Default
        persist(AppSettings.Default)
    }

    /**
     * 等首次加载完成，避免在默认值上做修改而丢掉已保存的设置。
     *
     * 加超时上界：万一存储读取异常或极慢，也不能让用户"点了没反应"——
     * 超过 2 秒就按当前内存值继续，持久化照常进行。
     */
    private suspend fun awaitInitialLoad() {
        if (observeJob == null) return
        withTimeoutOrNull(INITIAL_LOAD_TIMEOUT_MS) { initialLoad.await() }
    }

    private suspend fun persist(value: AppSettings) {
        runCatching {
            dataStore.edit { prefs ->
                prefs[KEY] = json.encodeToString(AppSettings.serializer(), value)
            }
        }.onFailure {
            cache.value = runCatching { dataStore.data.first() }
                .getOrNull()?.let { prefs -> decode(prefs[KEY]) }
                ?: AppSettings.Default
        }
    }

    private fun decode(raw: String?): AppSettings {
        if (raw.isNullOrBlank()) return AppSettings.Default
        return runCatching {
            json.decodeFromString(AppSettings.serializer(), raw)
        }.getOrElse { AppSettings.Default }
    }

    private companion object {
        val KEY = stringPreferencesKey("settings_json")

        /** 首次加载的最长等待时间，超过就按当前内存值继续。 */
        const val INITIAL_LOAD_TIMEOUT_MS = 2_000L
    }
}
