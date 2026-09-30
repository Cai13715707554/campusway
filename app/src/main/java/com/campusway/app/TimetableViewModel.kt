package com.campusway.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.campusway.core.data.CampusRepository
import com.campusway.core.data.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * 课表页 ViewModel。
 *
 * 数据全部来自本地仓储，进入页面即可用；同时启动一个轻量时钟，
 * 每 30 秒刷新一次“现在”，保证当前课程高亮与倒计时是活的。
 * 时钟只在页面存活期间运行（viewModelScope），离开页面自动停止，不浪费电。
 */
class TimetableViewModel(
    private val repository: CampusRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {

    private val viewState = MutableStateFlow(TimetableViewState())

    val uiState: StateFlow<TimetableViewData?> = combine(
        flow = viewState,
        flow2 = repository.courseTable,
        flow3 = repository.timeTable,
        flow4 = repository.calendar,
        flow5 = settingsStore.settings,
        transform = { state, table, timeTable, calendar, settings ->
            TimetablePresenter.build(state, table, timeTable, calendar, settings)
        }
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    init {
        startClock()
    }

    /** 每 30 秒推进一次“现在”。30 秒足够精确到分钟，又不会频繁重组。 */
    private fun startClock() = viewModelScope.launch {
        while (isActive) {
            val now = LocalTime.now()
            viewState.value = viewState.value.copy(
                now = now,
                date = LocalDate.now()
            )
            delay(CLOCK_INTERVAL_MS)
        }
    }

    /** 今日视图 / 整周视图切换。 */
    fun setTodayMode(todayMode: Boolean) {
        viewState.value = viewState.value.copy(todayMode = todayMode)
    }

    /** 整周视图翻页。 */
    fun shiftWeek(delta: Int) {
        viewState.value = viewState.value.copy(
            weekOffset = (viewState.value.weekOffset + delta).coerceIn(-MAX_WEEK_OFFSET, MAX_WEEK_OFFSET)
        )
    }

    fun backToThisWeek() {
        viewState.value = viewState.value.copy(weekOffset = 0)
    }

    companion object {
        private const val CLOCK_INTERVAL_MS = 30_000L
        private const val MAX_WEEK_OFFSET = 10

        /** 简易工厂：项目未引入 DI 框架，用手写工厂更直观。 */
        fun factory(
            repository: CampusRepository,
            settingsStore: SettingsStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TimetableViewModel(repository, settingsStore) as T
        }
    }
}
