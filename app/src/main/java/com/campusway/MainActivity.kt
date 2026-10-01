package com.campusway

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campusway.app.AppContainer
import com.campusway.app.CampusWayApplication
import com.campusway.app.NavigationViewModel
import com.campusway.app.SettingsViewModel
import com.campusway.app.TimetableViewModel
import com.campusway.core.model.AppSettings
import com.campusway.ui.AppShell
import com.campusway.ui.screen.OnboardingDraft
import com.campusway.ui.theme.CampusWayTheme
import kotlinx.coroutines.launch

/**
 * 唯一 Activity。
 *
 * 界面完全由 Compose 承载；主题与字体大小都取自设置流，
 * 因此在设置页、以及新用户引导页里改动都会立即生效，无需重启。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 顺序很重要：先 super.onCreate，再做边到边设置
        enableEdgeToEdge()

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
    val scope = rememberCoroutineScope()

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

    /** 统一的设置写入入口：所有回调都在协程里落到 DataStore。 */
    val update: ((AppSettings) -> AppSettings) -> Unit = { transform ->
        scope.launch { container.settingsStore.update(transform) }
    }

    AppShell(
        settings = settings,
        timetableViewModel = timetableViewModel,
        navigationViewModel = navigationViewModel,
        settingsViewModel = settingsViewModel,
        // 主题与字号：引导页点选就立即生效（否则要等走完流程才看得到效果）
        onOnboardingThemeMode = { mode -> update { it.copy(themeMode = mode) } },
        onOnboardingFontScale = { scale -> update { it.copy(fontScale = scale) } },
        onFinishOnboarding = { draft: OnboardingDraft ->
            update { current ->
                current.copy(
                    userName = draft.userName,
                    schoolName = draft.schoolName,
                    timetableAxis = draft.axis,
                    navProvider = draft.provider,
                    amapKey = draft.amapKey,
                    tencentKey = draft.tencentKey,
                    onboardingCompleted = true
                )
            }
        },
        onReplayOnboarding = { update { it.copy(onboardingCompleted = false) } }
    )
}
