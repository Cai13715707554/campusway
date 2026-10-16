package com.campusway.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
// 注意：不要显式 import androidx.compose.foundation.layout.weight。
// weight 是 RowScope/ColumnScope 的成员扩展，显式导入会解析到内部实现而编译失败。
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusway.app.NavigationViewModel
import com.campusway.app.SettingsViewModel
import com.campusway.app.TimetableViewModel
import com.campusway.core.importer.ImportedTimetable
import com.campusway.core.model.AppSettings
import com.campusway.core.model.FontScale
import com.campusway.core.model.ThemeMode
import com.campusway.core.model.TimeTable
import com.campusway.ui.components.BottomTab
import com.campusway.ui.components.HarmonyBottomBar
import com.campusway.ui.screen.NavigationScreen
import com.campusway.ui.screen.OnboardingDraft
import com.campusway.ui.screen.OnboardingScreen
import com.campusway.ui.screen.SettingsScreen
import com.campusway.ui.screen.TimetableScreen
import com.campusway.ui.theme.CampusMotion

/**
 * 应用外壳：底部导航（课表 / 导航 / 设置）+ 新用户引导。
 *
 * 页面切换用 220ms 的 Crossfade：比滑动更省性能，
 * 在低端机上也不会掉帧，符合“性能与效果之间平衡”的要求。
 */
@Composable
fun AppShell(
    settings: AppSettings,
    timetableViewModel: TimetableViewModel,
    navigationViewModel: NavigationViewModel,
    settingsViewModel: SettingsViewModel,
    /** 引导页里切换主题：立即写入设置，界面实时生效。 */
    onOnboardingThemeMode: (ThemeMode) -> Unit,
    /** 引导页里切换字号：立即写入设置，界面实时生效。 */
    onOnboardingFontScale: (FontScale) -> Unit,
    /** 引导页导入功课表：写入仓储。 */
    onImportTimetable: (ImportedTimetable) -> Unit,
    /** 导入时间表：传入最终生效的时间表与勾选保留的项目键。 */
    onImportPeriodTable: (TimeTable, List<String>) -> Unit,
    onFinishOnboarding: (OnboardingDraft) -> Unit,
    onReplayOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 校园数据与当前时间表要在引导分支之前取：
    // 引导页导入功课时需要用校园数据匹配教室、用当前时间表定节次
    val campus by navigationViewModel.campus.collectAsStateWithLifecycle()
    val currentTimeTable by navigationViewModel.timeTable.collectAsStateWithLifecycle()

    // ---------------- 新用户引导 ----------------
    if (!settings.onboardingCompleted) {
        Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
            OnboardingScreen(
                initialThemeMode = settings.themeMode,
                initialFontScale = settings.fontScale,
                campus = campus,
                baseTimeTable = currentTimeTable,
                onImportTimetable = onImportTimetable,
                onImportPeriodTable = onImportPeriodTable,
                initialAmapKey = settings.amapKey,
                initialTencentKey = settings.tencentKey,
                onThemeModeChange = onOnboardingThemeMode,
                onFontScaleChange = onOnboardingFontScale,
                onFinish = onFinishOnboarding
            )
        }
        return
    }

    // ---------------- 主界面 ----------------
    var tab by remember { mutableStateOf(BottomTab.TIMETABLE) }

    val timetableData by timetableViewModel.uiState.collectAsStateWithLifecycle()
    val navState by navigationViewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val settingsCourseTable by settingsViewModel.courseTable.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Box(modifier = Modifier.weight(1f)) {
            Crossfade(
                targetState = tab,
                animationSpec = tween(CampusMotion.Normal),
                label = "tabContent"
            ) { current ->
                when (current) {
                    BottomTab.TIMETABLE -> TimetableScreen(
                        data = timetableData,
                        campus = campus,
                        onToggleMode = timetableViewModel::setTodayMode,
                        onShiftWeek = timetableViewModel::shiftWeek,
                        onBackToThisWeek = timetableViewModel::backToThisWeek,
                        onNavigateToRoom = { roomId ->
                            // 从课表直接跳到导航页对应教室
                            navigationViewModel.openRoomById(roomId)
                            tab = BottomTab.MAP
                        }
                    )

                    BottomTab.MAP -> NavigationScreen(
                        state = navState,
                        campus = campus,
                        onQueryChange = navigationViewModel::onQueryChange,
                        onClearSearch = navigationViewModel::clearSearch,
                        onOpenBuilding = navigationViewModel::openBuilding,
                        onCloseBuilding = navigationViewModel::closeBuilding,
                        onSelectFloor = navigationViewModel::selectFloor,
                        onRoomClick = navigationViewModel::onRoomClick,
                        onOpenHit = navigationViewModel::openHit,
                        onCacheMap = navigationViewModel::cacheMapAroundSchool,
                        onClearCache = navigationViewModel::clearMapCache,
                        onConsumeMessage = navigationViewModel::consumeMessage
                    )

                    BottomTab.SETTINGS -> SettingsScreen(
                        settings = settings,
                        state = settingsState,
                        aboutInfo = settingsViewModel.aboutInfo,
                        campus = campus,
                        baseTimeTable = currentTimeTable,
                        courseTable = settingsCourseTable,
                        onSaveCourseTable = settingsViewModel::saveCourseTable,
                        onSaveTimeTable = settingsViewModel::saveTimeTable,
                        onThemeMode = settingsViewModel::setThemeMode,
                        onFontScale = settingsViewModel::setFontScale,
                        onAnimations = settingsViewModel::setAnimationsEnabled,
                        onAxis = settingsViewModel::setTimetableAxis,
                        onShowReminder = settingsViewModel::setShowUpcomingReminder,
                        onReminderLead = settingsViewModel::setReminderLeadMinutes,
                        onTermStart = settingsViewModel::setTermStartDate,
                        onTotalWeeks = settingsViewModel::setTotalWeeks,
                        onProvider = settingsViewModel::setNavProvider,
                        onAmapKey = settingsViewModel::setAmapKey,
                        onTencentKey = settingsViewModel::setTencentKey,
                        onCacheWhenOnline = settingsViewModel::setCacheMapWhenOnline,
                        onCacheRadius = settingsViewModel::setMapCacheRadius,
                        onUseLocation = settingsViewModel::setUseLocationAsOrigin,
                        onSchoolLocation = settingsViewModel::setSchoolLocation,
                        onUserName = settingsViewModel::setUserName,
                        onSchoolName = settingsViewModel::setSchoolName,
                        onClearCache = settingsViewModel::clearMapCache,
                        onValidate = settingsViewModel::validateData,
                        onClearImportedData = settingsViewModel::clearImportedData,
                        onImportTimetable = settingsViewModel::importTimetable,
                        onImportPeriodTable = settingsViewModel::importPeriodTable,
                        onSetUseTestData = settingsViewModel::setUseTestData,
                        onCheckDebugPassword = settingsViewModel::checkDebugPassword,
                        onResetSettings = settingsViewModel::resetAllSettings,
                        onConsumeMessage = settingsViewModel::consumeMessage,
                        onReplayOnboarding = onReplayOnboarding
                    )
                }
            }
        }

        Box(modifier = Modifier.navigationBarsPadding()) {
            HarmonyBottomBar(
                selected = tab,
                onSelect = { tab = it }
            )
        }
    }
}
