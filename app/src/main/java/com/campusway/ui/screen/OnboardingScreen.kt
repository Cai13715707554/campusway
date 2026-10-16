package com.campusway.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campusway.core.importer.ImportedTimetable
import com.campusway.core.map.Connectivity
import com.campusway.core.map.KeyCheckResult
import com.campusway.core.map.MapKeyValidator
import com.campusway.core.model.Campus
import com.campusway.core.model.CourseTable
import com.campusway.core.model.FontScale
import com.campusway.core.model.NavProviders
import com.campusway.core.model.ThemeMode
import com.campusway.core.model.TimeTable
import com.campusway.core.model.TimetableAxis
import com.campusway.ui.components.HarmonyCard
import com.campusway.ui.components.HarmonyChip
import com.campusway.ui.components.HarmonyDivider
import com.campusway.ui.theme.CampusColors
import com.campusway.ui.theme.CampusDimens
import com.campusway.ui.theme.CampusMotion
import kotlinx.coroutines.launch

/**
 * 新用户引导结束时一次性提交的选择。
 *
 * 主题与字体大小不在其中：它们会在用户点选时**立即写入设置**，
 * 这样引导页里改字号、换主题能实时看到效果。
 */
data class OnboardingDraft(
    val userName: String = "",
    val schoolName: String = "",
    val axis: TimetableAxis = TimetableAxis.PERIOD,
    val provider: String = NavProviders.AMAP,
    val amapKey: String = "",
    val tencentKey: String = ""
)

/**
 * 页面 4：新用户引导。
 *
 * 规则（按需求确定）：
 *  - 主题与字号点选即生效，因为它们是"看得见"的设置；
 *  - **必须填写至少一个导航 Key 才能进入应用**；
 *  - **不要求联网**：没网也能填 Key 并进入，楼外导航会在联网后自动启用；
 *  - 界面上不提供「离线估算」选项，它只是没网时的自动降级行为。
 */
@Composable
fun OnboardingScreen(
    initialThemeMode: ThemeMode,
    initialFontScale: FontScale,
    /** 当前校园数据：导入功课表时用来把 `课程名@地点` 关联到真实房间。 */
    campus: Campus,
    /** 当前时间表：导入功课时沿用它来定节次。 */
    baseTimeTable: TimeTable,
    /** 当前课程表：导入时间表时用于节数缺口校验。 */
    courseTable: CourseTable,
    /** 用户导入功课表后回调，由上层写入仓储。 */
    onImportTimetable: (ImportedTimetable) -> Unit,
    /** 用户导入时间表后回调（时间表 + 勾选保留的项目键）。 */
    onImportPeriodTable: (TimeTable, List<String>) -> Unit,
    /** 引导页里用编辑器保存课程表。 */
    onSaveCourseTable: (CourseTable) -> Unit,
    /** 引导页里用编辑器保存时间表（含同一操作可能改动的课程表）。 */
    onSaveTimeTable: (TimeTable, CourseTable) -> Unit,
    /** 从设置页「重看引导」进来时，把已保存的 Key 预填好，避免重新粘贴。 */
    initialAmapKey: String = "",
    initialTencentKey: String = "",
    onThemeModeChange: (ThemeMode) -> Unit,
    onFontScaleChange: (FontScale) -> Unit,
    onFinish: (OnboardingDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val validator = remember { MapKeyValidator() }

    var step by remember { mutableIntStateOf(0) }

    var userName by remember { mutableStateOf("") }
    var schoolName by remember { mutableStateOf("") }
    var axis by remember { mutableStateOf(TimetableAxis.PERIOD) }

    // 导航 Key
    var amapKey by remember { mutableStateOf(initialAmapKey) }
    var tencentKey by remember { mutableStateOf(initialTencentKey) }
    var amapResult by remember { mutableStateOf<KeyCheckResult?>(null) }
    var tencentResult by remember { mutableStateOf<KeyCheckResult?>(null) }
    var checking by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf(Connectivity.isOnline(context)) }
    var preferredProvider by remember { mutableStateOf(NavProviders.AMAP) }

    val amapFilled = amapKey.isNotBlank()
    val tencentFilled = tencentKey.isNotBlank()
    /** 准入条件：填了至少一个 Key。注意**不含**联网要求。 */
    val keyFilled = amapFilled || tencentFilled

    /**
     * 最终使用哪个服务商：交给 NavProviders.resolve 决定，
     * 保证保存的服务商一定和填写的 Key 对得上（只填腾讯却存了高德，楼外导航会直接哑掉）。
     */
    val providerToUse = NavProviders.resolve(
        amapKey = amapKey,
        tencentKey = tencentKey,
        preferred = preferredProvider
    )

    // 进入最后一步时刷新一次网络状态（仅用于提示，不影响能否继续）
    LaunchedEffect(step) {
        if (step == TOTAL_STEPS - 1) online = Connectivity.isOnline(context)
    }

    val progress by animateFloatAsState(
        targetValue = (step + 1) / TOTAL_STEPS.toFloat(),
        animationSpec = tween(CampusMotion.Normal),
        label = "onboardingProgress"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = CampusDimens.PagePadding)
    ) {
        Spacer(Modifier.height(28.dp))

        // 顶部品牌区
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(listOf(CampusColors.Blue, CampusColors.BlueLight))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.School,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "校途 CampusWay",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "课程表 · 校园导航 · 楼内导览",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = when (step) {
                TOTAL_STEPS - 1 -> "第 ${step + 1} / $TOTAL_STEPS 步 · 必须填写一个导航 Key"
                2 -> "第 ${step + 1} / $TOTAL_STEPS 步 · 点选项立刻生效，可以直接看效果"
                else -> "第 ${step + 1} / $TOTAL_STEPS 步 · 这些选择之后都能在设置里改"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (step) {
                0 -> StepWelcome()
                1 -> StepProfile(
                    userName = userName,
                    schoolName = schoolName,
                    onUserName = { userName = it },
                    onSchoolName = { schoolName = it }
                )
                2 -> StepAppearance(
                    themeMode = initialThemeMode,
                    fontScale = initialFontScale,
                    // 直接写入设置：主题与字号会立即作用到整个界面
                    onTheme = onThemeModeChange,
                    onFont = onFontScaleChange
                )
                3 -> StepImportTimetable(
                    axis = axis,
                    onAxis = { axis = it },
                    campus = campus,
                    baseTimeTable = baseTimeTable,
                    courseTable = courseTable,
                    onImported = onImportTimetable,
                    onSaveCourseTable = onSaveCourseTable
                )
                4 -> StepImportPeriodTable(
                    onImported = onImportPeriodTable,
                    courseTable = courseTable,
                    baseTimeTable = baseTimeTable,
                    onSaveTimeTable = onSaveTimeTable
                )
                else -> StepNavigationKeys(
                    amapKey = amapKey,
                    tencentKey = tencentKey,
                    onAmapKey = {
                        amapKey = it
                        amapResult = null
                    },
                    onTencentKey = {
                        tencentKey = it
                        tencentResult = null
                    },
                    amapResult = amapResult,
                    tencentResult = tencentResult,
                    checking = checking,
                    online = online,
                    preferredProvider = preferredProvider,
                    onPreferredProvider = { preferredProvider = it },
                    onValidate = {
                        checking = true
                        scope.launch {
                            // 校验是可选的，只是帮用户确认 Key 是否可用
                            if (amapKey.isNotBlank()) {
                                amapResult = validator.check(NavProviders.AMAP, amapKey)
                            }
                            if (tencentKey.isNotBlank()) {
                                tencentResult = validator.check(NavProviders.TENCENT, tencentKey)
                            }
                            checking = false
                        }
                    }
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // 底部按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 0) {
                Text(
                    text = "上一步",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(CampusDimens.ChipRadius))
                        .clickable { step-- }
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (step == TOTAL_STEPS - 1 && !keyFilled) {
                Text(
                    text = "请先填一个 Key",
                    style = MaterialTheme.typography.bodySmall,
                    color = CampusColors.Warning
                )
                Spacer(Modifier.width(10.dp))
            }
            PrimaryButton(
                text = if (step == TOTAL_STEPS - 1) "开始使用" else "下一步",
                enabled = (step != TOTAL_STEPS - 1 || keyFilled) && !checking,
                onClick = {
                    if (step == TOTAL_STEPS - 1) {
                        onFinish(
                            OnboardingDraft(
                                userName = userName.trim(),
                                schoolName = schoolName.trim(),
                                axis = axis,
                                provider = providerToUse,
                                amapKey = amapKey.trim(),
                                tencentKey = tencentKey.trim()
                            )
                        )
                    } else {
                        step++
                    }
                }
            )
        }
    }
}

private const val TOTAL_STEPS = 6

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(CampusDimens.ChipRadius))
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 22.dp, vertical = 11.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StepWelcome() {
    HarmonyCard {
        Text(
            text = "欢迎使用校途",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "校途把你的课表、校园平面图和教学楼内导览放在一起，" +
                "考试周找教室、刚入学找不到实训楼，都不用再问人。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        FeatureLine(Icons.Outlined.Schedule, "课表", "今日课程、当前时段高亮、整周课表与时间表分开展示")
        FeatureLine(Icons.Outlined.Map, "楼外导航", "校园平面图点选教学楼，接高德或腾讯地图")
        FeatureLine(Icons.Outlined.School, "楼内导览", "楼内示意图高亮目标教室，并给出走法")
        FeatureLine(Icons.Outlined.Wifi, "联网一次即可", "配好 Key 后，没网也能查课表与示意图")
    }
}

@Composable
private fun FeatureLine(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StepProfile(
    userName: String,
    schoolName: String,
    onUserName: (String) -> Unit,
    onSchoolName: (String) -> Unit
) {
    HarmonyCard {
        Text(
            text = "先认识一下",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "这两项只影响界面显示，可以留空。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = userName,
            onValueChange = onUserName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("你的称呼") },
            placeholder = { Text("例如：小蔡") },
            singleLine = true,
            shape = RoundedCornerShape(CampusDimens.ChipRadius)
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = schoolName,
            onValueChange = onSchoolName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("学校名称") },
            placeholder = { Text("例如：某某职业学校") },
            singleLine = true,
            shape = RoundedCornerShape(CampusDimens.ChipRadius)
        )
    }
}

/**
 * 导入时间表（作息表）。
 *
 * 识别后让用户勾选要显示哪些午别/项目：
 * 不住校的同学可以整组取消晚上；同类情况都能自己决定。
 * 一个都不勾选也可以——会弹「喵确认吗？喵」，确认后按"没有时间表"处理，
 * 课表页原本放时间表的位置改显示占位图。
 */
@Composable
private fun StepImportPeriodTable(
    onImported: (TimeTable, List<String>) -> Unit,
    courseTable: CourseTable,
    baseTimeTable: TimeTable,
    onSaveTimeTable: (TimeTable, CourseTable) -> Unit
) {
    var showEditor by remember { mutableStateOf(false) }

    PeriodTableImportCard(
        onImported = onImported,
        courseTable = courseTable,
        baseTimeTable = baseTimeTable
    )

    Spacer(Modifier.height(12.dp))

    HarmonyCard {
        Text(
            text = "导入后还能改",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "节次的名称、起止时间、午别，以及增删节次/时间点，都能在编辑里改。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        HarmonyChip(
            text = "编辑时间表",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
            onClick = { showEditor = true }
        )
    }

    if (showEditor) {
        TimeTableEditorDialog(
            timeTable = baseTimeTable,
            courseTable = courseTable,
            onSave = { time, course ->
                onSaveTimeTable(time, course)
                showEditor = false
            },
            onDismiss = { showEditor = false }
        )
    }

    Spacer(Modifier.height(12.dp))

    HarmonyCard {
        Text(
            text = "这一步可以跳过",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "不导入的话会先用一套默认作息（每天 9 节）。" +
                "任何时候都能到 设置 → 功课表 → 导入时间表 重新导入并改选择。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 外观设置。
 *
 * 这里的选择**立即写入设置**，因此下面的预览会实时跟着变；
 * 预览刻意用大号字，档位差距也放大到 20% 以上，避免"改完看不出区别"。
 */
@Composable
private fun StepAppearance(
    themeMode: ThemeMode,
    fontScale: FontScale,
    onTheme: (ThemeMode) -> Unit,
    onFont: (FontScale) -> Unit
) {
    HarmonyCard {
        Text(
            text = "外观",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "点下面的选项会立即生效，可以直接看下面的效果预览。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        OptionGroup(
            label = "主题",
            options = ThemeMode.entries.map { it.displayName },
            selectedIndex = ThemeMode.entries.indexOf(themeMode),
            onSelect = { onTheme(ThemeMode.entries[it]) }
        )
        Spacer(Modifier.height(14.dp))
        OptionGroup(
            label = "字体大小（当前 ${fontScale.optionLabel}）",
            options = FontScale.entries.map { it.displayName },
            selectedIndex = FontScale.entries.indexOf(fontScale),
            onSelect = { onFont(FontScale.entries[it]) }
        )
    }

    Spacer(Modifier.height(12.dp))

    // 效果预览：用大号字，档位差异一眼可见
    HarmonyCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) {
        Text(
            text = "效果预览",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "计算机网络",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "第 3 节 · 10:00-10:45 · 1 号楼 3 层 302 网络实训室",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "当前字号：${fontScale.displayName}（${fontScale.percentText}）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 导入功课表 + 选择课程表显示方式。
 *
 * 为什么把"导入"放在引导里：没有课表，进去之后整个应用是空的。
 * 这里允许先跳过（直接下一步），课表页会提示去设置里导入。
 */
@Composable
private fun StepImportTimetable(
    axis: TimetableAxis,
    onAxis: (TimetableAxis) -> Unit,
    campus: Campus,
    baseTimeTable: TimeTable,
    courseTable: CourseTable,
    onImported: (ImportedTimetable) -> Unit,
    onSaveCourseTable: (CourseTable) -> Unit
) {
    var showEditor by remember { mutableStateOf(false) }

    TimetableImportCard(
        campus = campus,
        baseTimeTable = baseTimeTable,
        onImported = onImported
    )

    Spacer(Modifier.height(12.dp))

    HarmonyCard {
        Text(
            text = "导入后还能改",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "课程、排课、节次与时间都能再改，不用重新导入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        HarmonyChip(
            text = "编辑课程表",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
            onClick = { showEditor = true }
        )
    }

    if (showEditor) {
        CourseTableEditorDialog(
            courseTable = courseTable,
            timeTable = baseTimeTable,
            campus = campus,
            onSave = {
                onSaveCourseTable(it)
                showEditor = false
            },
            onDismiss = { showEditor = false }
        )
    }

    Spacer(Modifier.height(12.dp))

    HarmonyCard {
        Text(
            text = "课表显示",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "课程表与课程时间表分开显示。这里选择课程表左侧那一列显示什么。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        OptionGroup(
            label = "课程表左侧显示",
            options = TimetableAxis.entries.map { it.displayName },
            selectedIndex = TimetableAxis.entries.indexOf(axis),
            onSelect = { onAxis(TimetableAxis.entries[it]) }
        )
        Spacer(Modifier.height(14.dp))
        HarmonyDivider()
        Spacer(Modifier.height(12.dp))
        Text(
            text = "格式示例见工程里的 docs/功课表.xlsx：第一行是星期一到星期五，" +
                "第一列可以放节次或时间，「上午 / 下午」这类整行文字会被识别为时段分隔。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 最后一步：导航 Key。
 *
 * 门槛只是"填了至少一个"，**不要求联网**：
 * 没网时也能填好 Key 进入应用，联网后楼外导航会自动启用。
 * 「校验」按钮是可选的辅助，用来确认 Key 是否真的可用。
 */
@Composable
private fun StepNavigationKeys(
    amapKey: String,
    tencentKey: String,
    onAmapKey: (String) -> Unit,
    onTencentKey: (String) -> Unit,
    amapResult: KeyCheckResult?,
    tencentResult: KeyCheckResult?,
    checking: Boolean,
    online: Boolean,
    preferredProvider: String,
    onPreferredProvider: (String) -> Unit,
    onValidate: () -> Unit
) {
    val amapFilled = amapKey.isNotBlank()
    val tencentFilled = tencentKey.isNotBlank()
    val amapOk = amapResult?.isSuccess == true
    val tencentOk = tencentResult?.isSuccess == true
    val bothFilled = amapFilled && tencentFilled

    HarmonyCard {
        Text(
            text = "导航 Key（必填一个）",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "在开放平台申请「Web 服务」类型的 Key，高德或腾讯任填一个即可进入。" +
                "没网也能先填好，联网后楼外导航会自动启用；没网时按离线方式估算，不影响课表与楼内导览。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(10.dp))
        NetworkNote(online = online)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = amapKey,
            onValueChange = onAmapKey,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("高德地图 Key") },
            placeholder = { Text("粘贴 Web 服务 Key") },
            singleLine = true,
            enabled = !checking,
            shape = RoundedCornerShape(CampusDimens.ChipRadius)
        )
        ResultLine(amapResult, filled = amapFilled)
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = tencentKey,
            onValueChange = onTencentKey,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("腾讯地图 Key") },
            placeholder = { Text("粘贴 WebService Key") },
            singleLine = true,
            enabled = !checking,
            shape = RoundedCornerShape(CampusDimens.ChipRadius)
        )
        ResultLine(tencentResult, filled = tencentFilled)

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(
                text = if (checking) "校验中…" else "联网校验（可选）",
                // 校验需要网络，但没有网也不影响下一步
                enabled = !checking && online && (amapFilled || tencentFilled),
                onClick = onValidate
            )
            if (checking) {
                Spacer(Modifier.width(10.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (bothFilled) {
            Spacer(Modifier.height(14.dp))
            HarmonyDivider()
            Spacer(Modifier.height(12.dp))
            OptionGroup(
                label = "两个都填了，默认用哪个",
                options = NavProviders.selectable.map { NavProviders.displayName(it) },
                selectedIndex = if (preferredProvider == NavProviders.TENCENT) 1 else 0,
                onSelect = { onPreferredProvider(if (it == 1) NavProviders.TENCENT else NavProviders.AMAP) }
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                amapOk || tencentOk -> "Key 校验通过，可以开始使用了。"
                amapFilled || tencentFilled -> "已填写 Key，可以继续。建议在联网时点一次校验确认可用。"
                else -> "请至少填写一个 Key 才能继续。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = when {
                amapOk || tencentOk -> CampusColors.Success
                amapFilled || tencentFilled -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> CampusColors.Warning
            }
        )
    }
}

/** 网络状态提示：仅告知，不拦人。 */
@Composable
private fun NetworkNote(online: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CampusDimens.ChipRadius))
            .background(
                if (online) CampusColors.Success.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (online) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
            contentDescription = null,
            tint = if (online) CampusColors.Success else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (online) {
                "网络已连接：可以顺手点一次「联网校验」确认 Key 可用"
            } else {
                "当前没有网络：不影响填写和进入，联网后楼外导航会自动启用"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (online) CampusColors.Success else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 单个 Key 的校验结果行。 */
@Composable
private fun ResultLine(result: KeyCheckResult?, filled: Boolean) {
    if (result == null) {
        if (filled) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = "尚未校验（可以不校验直接继续）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    val (text, color) = when (result) {
        is KeyCheckResult.Valid -> "校验通过" to CampusColors.Success
        is KeyCheckResult.InvalidKey -> result.message to CampusColors.Error
        is KeyCheckResult.NetworkError -> "网络异常：${result.message}" to CampusColors.Warning
    }
    Spacer(Modifier.height(5.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (result.isSuccess) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

@Composable
private fun OptionGroup(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(CampusDimens.ChipRadius))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onSelect(index) }
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
