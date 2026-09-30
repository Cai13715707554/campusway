package com.campusway

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campusway.app.AppContainer
import com.campusway.app.CampusWayApplication
import com.campusway.app.NavigationViewModel
import com.campusway.app.SettingsViewModel
import com.campusway.app.TimetableViewModel
import com.campusway.ui.AppShell
import com.campusway.ui.OnboardingProfile
import com.campusway.ui.theme.CampusWayTheme

/**
 * 唯一 Activity。
 *
 * 界面完全由 Compose 承载；主题与字体大小都取自设置流，
 * 因此在设置页改动会立即生效，无需重启。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as CampusWayApplication).container

        setContent {
            val settings by container.settingsStore.settings.collectAsStateWithLifecycle()

            CampusWayTheme(
                themeMode = settings.themeMode,
                fontScale = settings.fontScale
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(container = container)
                }
            }
        }
    }
}

/**
 * 应用根节点：决定进“新用户引导”还是主界面，并创建三个一级页面的 ViewModel。
 *
 * 项目刻意没有引入 Navigation-Compose：
 * 一级页面只有三个，二级状态（例如进入某教学楼）由导航页自己管理，
 * 用轻量状态机更可控，也少一个外部依赖。
 */
@androidx.compose.runtime.Composable
private fun AppRoot(container: AppContainer) {
    val context = LocalContext.current

    val settings by container.settingsStore.settings.collectAsStateWithLifecycle()

    val timetableViewModel: TimetableViewModel = viewModel(
        factory = TimetableViewModel.factory(container.campusRepository, container.settingsStore)
    )
    val navigationViewModel: NavigationViewModel = viewModel(
        factory = NavigationViewModel.factory(
            repository = container.campusRepository,
            settingsStore = container.settingsStore,
            cacheStore = container.mapCache,
            context = context,
            providerFactory = { container.mapProvider() }
        )
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            settingsStore = container.settingsStore,
            repository = container.campusRepository,
            cacheStore = container.mapCache
        )
    )

    // 引导结束待写入的profile；用状态承载，写入交给 LaunchedEffect（挂起操作不能在回调里直接做）
    var pendingProfile by remember { mutableStateOf<OnboardingProfile?>(null) }
    var replayOnboarding by remember { mutableStateOf(false) }

    LaunchedEffect(pendingProfile) {
        val profile = pendingProfile ?: return@LaunchedEffect
        container.settingsStore.update { current ->
            current.copy(
                userName = profile.userName,
                schoolName = profile.schoolName,
                themeMode = profile.themeMode,
                fontScale = profile.fontScale,
                timetableAxis = profile.axis,
                navProvider = profile.provider,
                onboardingCompleted = true
            )
        }
        pendingProfile = null
    }

    LaunchedEffect(replayOnboarding) {
        if (!replayOnboarding) return@LaunchedEffect
        container.settingsStore.update { it.copy(onboardingCompleted = false) }
        replayOnboarding = false
    }

    AppShell(
        settings = settings,
        timetableViewModel = timetableViewModel,
        navigationViewModel = navigationViewModel,
        settingsViewModel = settingsViewModel,
        onFinishOnboarding = { profile -> pendingProfile = profile },
        onReplayOnboarding = { replayOnboarding = true }
    )
}
