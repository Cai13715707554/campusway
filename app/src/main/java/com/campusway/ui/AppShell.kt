package com.campusway.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
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
import com.campusway.core.model.AppSettings
import com.campusway.core.model.FontScale
import com.campusway.core.model.ThemeMode
import com.campusway.core.model.TimetableAxis
import com.campusway.ui.components.BottomTab
import com.campusway.ui.components.HarmonyBottomBar
import com.campusway.ui.screen.NavigationScreen
import com.campusway.ui.screen.OnboardingScreen
import com.campusway.ui.screen.SettingsScreen
import com.campusway.ui.screen.TimetableScreen
import com.campusway.ui.theme.CampusMotion

/**
 * 新用户引导收集到的个性化选择。
 */
data class OnboardingProfile(
    val userName: String = "",
    val schoolName: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: FontScale = FontScale.NORMAL,
    val axis: TimetableAxis = TimetableAxis.PERIOD,
    val provider: String = com.campusway.core.model.NavProviders.OFFLINE
)

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
    onFinishOnboarding: (OnboardingProfile) -> Unit,
    onReplayOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ---------------- 新用户引导 ----------------
    if (!settings.onboardingCompleted) {
        Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
            OnboardingScreen(
                onFinished = { userName, schoolName, themeMode, fontScale, axis, provider ->
                    onFinishOnboarding(
                        OnboardingProfile(
                            userName = userName,
                            schoolName = schoolName,
                            themeMode = themeMode,
                            fontScale = fontScale,
                            axis = axis,
                            provider = provider
                        )
                    )
                }
            )
        }
        return
    }

    // ---------------- 主界面 ----------------
    var tab by remember { mutableStateOf(BottomTab.TIMETABLE) }

    val timetableData by timetableViewModel.uiState.collectAsStateWithLifecycle()
    val navState by navigationViewModel.uiState.collectAsStateWithLifecycle()
    val campus by navigationViewModel.campus.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

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
                        onResetSample = settingsViewModel::resetToSampleData,
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
