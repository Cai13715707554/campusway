package com.campusway.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.campusway.app.SettingsUiState
import com.campusway.core.importer.ImportedTimetable
import com.campusway.core.model.AppSettings
import com.campusway.core.model.Campus
import com.campusway.core.model.FontScale
import com.campusway.core.model.NavProviders
import com.campusway.core.model.ThemeMode
import com.campusway.core.model.TimetableAxis
import com.campusway.ui.components.HarmonyCard
import com.campusway.ui.components.HarmonyChip
import com.campusway.ui.components.HarmonyDivider
import com.campusway.ui.components.HarmonyTopBar
import com.campusway.ui.components.InfoRow
import com.campusway.ui.components.SectionHeader
import com.campusway.ui.theme.CampusColors
import com.campusway.ui.theme.CampusDimens

/**
 * 页面 3：设置。
 *
 * 需求：“配置以及软件的关于介绍什么的都在这里”，
 * 并且“新用户引导里配置的基础数据和个性化选择，可在设置里重新更改”。
 * 因此这里必须能改：
 *  外观（主题/字体）、课表（显示方式/提醒/学期周次）、
 *  导航（服务商与 Key、离线地图缓存、学校坐标）、数据与关于。
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    state: SettingsUiState,
    aboutInfo: List<Pair<String, String>>,
    /** 当前校园数据：导入功课表时用于把 `课程名@地点` 关联到真实房间。 */
    campus: Campus,
    onThemeMode: (ThemeMode) -> Unit,
    onFontScale: (FontScale) -> Unit,
    onAnimations: (Boolean) -> Unit,
    onAxis: (TimetableAxis) -> Unit,
    onShowReminder: (Boolean) -> Unit,
    onReminderLead: (Int) -> Unit,
    onTermStart: (String) -> Unit,
    onTotalWeeks: (Int) -> Unit,
    onProvider: (String) -> Unit,
    onAmapKey: (String) -> Unit,
    onTencentKey: (String) -> Unit,
    onCacheWhenOnline: (Boolean) -> Unit,
    onCacheRadius: (Int) -> Unit,
    onUseLocation: (Boolean) -> Unit,
    onSchoolLocation: (Double, Double) -> Unit,
    onUserName: (String) -> Unit,
    onSchoolName: (String) -> Unit,
    onClearCache: () -> Unit,
    onValidate: () -> Unit,
    /** 清空用户自己导入的数据（不影响"测试数据"开关）。 */
    onClearImportedData: () -> Unit,
    /** 导入一份解析好的功课表。 */
    onImportTimetable: (ImportedTimetable) -> Unit,
    /** 开关内置测试数据。 */
    onSetUseTestData: (Boolean) -> Unit,
    /** 校验调试口令。 */
    onCheckDebugPassword: (String) -> Boolean,
    onResetSettings: () -> Unit,
    onConsumeMessage: () -> Unit,
    onReplayOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingKey by remember { mutableStateOf<String?>(null) }
    var editingCoordinate by remember { mutableStateOf(false) }
    var editingTerm by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf<String?>(null) }
    /** 导入功课表的弹层。 */
    var showImport by remember { mutableStateOf(false) }
    /** 调试入口：先要口令，通过后再显示调试面板。 */
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showDebugPanel by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        HarmonyTopBar(title = "设置", subtitle = "配置与关于")

        state.message?.let { message ->
            MessageRow(text = message, onDismiss = onConsumeMessage)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = CampusDimens.PagePadding,
                end = CampusDimens.PagePadding,
                top = 2.dp,
                bottom = 30.dp
            ),
            verticalArrangement = Arrangement.spacedBy(CampusDimens.ItemSpacing)
        ) {
            // ---------------------------------------------------- 功课表
            // 放在最顶部：导入功课表是主要操作，藏在页面底部会让人以为"没这个功能"
            item { GroupTitle("功课表", Icons.Outlined.TableChart) }
            item {
                HarmonyCard {
                    InfoRow(
                        label = "导入功课表",
                        value = "xlsx / csv / 粘贴",
                        valueColor = MaterialTheme.colorScheme.primary,
                        onClick = { showImport = true }
                    )
                    InfoRow(
                        label = "当前课程",
                        value = "${state.courseCount} 门 · ${state.periodCount} 个节次"
                    )
                    HarmonyDivider()
                    Text(
                        text = "支持 .xlsx（格式示例见工程 docs/功课表.xlsx）、.csv/.txt，" +
                            "也可以从 Excel 框选复制后粘贴。第一行需要是「星期一…星期日」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HarmonyChip(
                            text = "重看新用户引导",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                            onClick = onReplayOnboarding
                        )
                    }
                }
            }

            // ---------------------------------------------------- 外观
            item { GroupTitle("外观", Icons.Outlined.Palette) }
            item {
                HarmonyCard {
                    Text(
                        text = "主题",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    SegmentedRow(
                        options = ThemeMode.entries.map { it.displayName },
                        selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                        onSelect = { onThemeMode(ThemeMode.entries[it]) }
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "字体大小",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    SegmentedRow(
                        options = FontScale.entries.map { it.displayName },
                        selectedIndex = FontScale.entries.indexOf(settings.fontScale),
                        onSelect = { onFontScale(FontScale.entries[it]) }
                    )
                    Spacer(Modifier.height(6.dp))
                    HarmonyDivider()
                    ToggleRow(
                        label = "界面动效",
                        description = "关闭可提升低端机流畅度",
                        checked = settings.animationsEnabled,
                        onCheckedChange = onAnimations
                    )
                }
            }

            // ---------------------------------------------------- 课表
            item { GroupTitle("课表", Icons.Outlined.School) }
            item {
                HarmonyCard {
                    Text(
                        text = "课程表显示方式",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    SegmentedRow(
                        options = TimetableAxis.entries.map { it.displayName },
                        selectedIndex = TimetableAxis.entries.indexOf(settings.timetableAxis),
                        onSelect = { onAxis(TimetableAxis.entries[it]) }
                    )
                    Spacer(Modifier.height(6.dp))
                    HarmonyDivider()
                    ToggleRow(
                        label = "上课提醒",
                        description = "在课表页高亮并提示即将开始的课程",
                        checked = settings.showUpcomingReminder,
                        onCheckedChange = onShowReminder
                    )
                    if (settings.showUpcomingReminder) {
                        StepperRow(
                            label = "提前提醒",
                            valueText = "${settings.reminderLeadMinutes} 分钟",
                            onDecrease = { onReminderLead(settings.reminderLeadMinutes - 5) },
                            onIncrease = { onReminderLead(settings.reminderLeadMinutes + 5) }
                        )
                    }
                    HarmonyDivider()
                    InfoRow(
                        label = "学期开始（第 1 教学周周一）",
                        value = state.termStartDate.ifBlank { "未设置" },
                        onClick = { editingTerm = true }
                    )
                    StepperRow(
                        label = "学期总周数",
                        valueText = "${state.totalWeeks} 周",
                        onDecrease = { onTotalWeeks(state.totalWeeks - 1) },
                        onIncrease = { onTotalWeeks(state.totalWeeks + 1) }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "当前已导入 ${state.courseCount} 门课程、${state.periodCount} 个节次。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---------------------------------------------------- 导航
            item { GroupTitle("导航", Icons.Outlined.Storage) }
            item {
                HarmonyCard {
                    Text(
                        text = "楼外导航服务商",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    // 只用 selectable：界面上不提供「离线估算」，
                    // 它是没网时的自动降级行为，不是一种可供选择的模式。
                    SegmentedRow(
                        options = NavProviders.selectable.map { NavProviders.displayName(it) },
                        selectedIndex = NavProviders.selectable
                            .indexOf(settings.navProvider).coerceAtLeast(0),
                        onSelect = { onProvider(NavProviders.selectable[it]) }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = settings.offlineCapabilityText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    HarmonyDivider()

                    InfoRow(
                        label = "高德地图 Key",
                        value = maskKey(settings.amapKey),
                        valueColor = if (settings.amapKey.isBlank()) CampusColors.Warning
                        else MaterialTheme.colorScheme.onBackground,
                        onClick = { editingKey = NavProviders.AMAP }
                    )
                    InfoRow(
                        label = "腾讯地图 Key",
                        value = maskKey(settings.tencentKey),
                        valueColor = if (settings.tencentKey.isBlank()) CampusColors.Warning
                        else MaterialTheme.colorScheme.onBackground,
                        onClick = { editingKey = NavProviders.TENCENT }
                    )
                    HarmonyDivider()
                    InfoRow(
                        label = "学校中心坐标",
                        value = if (settings.schoolHasGeo) {
                            "%.5f, %.5f".format(settings.schoolLatitude, settings.schoolLongitude)
                        } else "未标定",
                        valueColor = if (settings.schoolHasGeo) MaterialTheme.colorScheme.onBackground
                        else CampusColors.Warning,
                        onClick = { editingCoordinate = true }
                    )
                    ToggleRow(
                        label = "联网时缓存周边地图",
                        description = "缓存后完全离线也能查看学校周边",
                        checked = settings.cacheMapWhenOnline,
                        onCheckedChange = onCacheWhenOnline
                    )
                    if (settings.cacheMapWhenOnline) {
                        StepperRow(
                            label = "缓存半径",
                            valueText = "${settings.mapCacheRadiusMeters} 米",
                            onDecrease = { onCacheRadius(settings.mapCacheRadiusMeters - 500) },
                            onIncrease = { onCacheRadius(settings.mapCacheRadiusMeters + 500) }
                        )
                    }
                    ToggleRow(
                        label = "用定位作为楼外起点",
                        description = "关闭时从校门/校园中心出发",
                        checked = settings.useLocationAsOrigin,
                        onCheckedChange = onUseLocation
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HarmonyChip(
                            text = "清除离线地图缓存",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onClearCache
                        )
                    }
                    val info = state.cacheInfo
                    if (info?.hasCache == true) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "已缓存 ${info.tileCount} 块（${info.sizeText}）· ${info.areaName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---------------------------------------------------- 个人信息
            item { GroupTitle("个人信息", Icons.Outlined.School) }
            item {
                HarmonyCard {
                    EditableField(
                        label = "你的称呼",
                        value = settings.userName,
                        placeholder = "用于课表页问候",
                        onValueChange = onUserName
                    )
                    Spacer(Modifier.height(10.dp))
                    EditableField(
                        label = "学校名称",
                        value = settings.schoolName.ifBlank { state.campusName },
                        placeholder = "例如：某某职业学校",
                        onValueChange = onSchoolName
                    )
                }
            }

            // ---------------------------------------------------- 数据
            item { GroupTitle("数据", Icons.Outlined.Storage) }
            item {
                HarmonyCard {
                    InfoRow(
                        label = "教学楼",
                        value = "${state.buildingCount} 栋"
                    )
                    InfoRow(
                        label = "房间/教室",
                        value = "${state.roomCount} 个"
                    )
                    InfoRow(
                        label = "导入课程",
                        value = "${state.courseCount} 门"
                    )
                    InfoRow(
                        label = "当前数据来源",
                        value = if (settings.useTestData) "内置测试数据" else "你自己导入的数据",
                        valueColor = if (settings.useTestData) CampusColors.Warning
                        else MaterialTheme.colorScheme.onBackground
                    )
                    HarmonyDivider()
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HarmonyChip(
                            text = "校验数据",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                            onClick = onValidate
                        )
                        HarmonyChip(
                            text = "清空已导入数据",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { confirmReset = "data" }
                        )
                    }
                    if (state.dataProblems.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        state.dataProblems.forEach { problem ->
                            Text(
                                text = "· $problem",
                                style = MaterialTheme.typography.bodySmall,
                                color = CampusColors.Error
                            )
                        }
                    } else if (state.message == "数据校验通过") {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "数据校验通过，课表引用的教室都能在校园图里找到。",
                            style = MaterialTheme.typography.bodySmall,
                            color = CampusColors.Success
                        )
                    }
                }
            }

            // ---------------------------------------------------- 关于
            item { GroupTitle("关于", Icons.Outlined.Info) }
            item {
                HarmonyCard {
                    aboutInfo.forEach { (label, value) ->
                        InfoRow(label = label, value = value)
                    }
                    Spacer(Modifier.height(6.dp))
                    HarmonyDivider()
                    // 调试入口：需要口令（1101），防止误触。
                    // 刻意做成一眼能认出来的整行按钮，而不是和普通信息行一样，
                    // 否则很容易被当成只读信息略过。
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(CampusDimens.ChipRadius))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showPasswordDialog = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "调试入口",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "点击后输入口令，可开关测试数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (settings.useTestData) "测试数据：开" else "测试数据：关",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (settings.useTestData) CampusColors.Warning
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "校途把课表、校园平面图和教学楼内导览放在一起，" +
                            "不做室内实时定位，也不需要一直联网——" +
                            "课表、平面示意图和楼内路线全部保存在本机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HarmonyChip(
                            text = "重看新用户引导",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                            onClick = onReplayOnboarding
                        )
                        HarmonyChip(
                            text = "恢复默认设置",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { confirmReset = "settings" }
                        )
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------- 弹窗

    editingKey?.let { provider ->
        KeyDialog(
            providerId = provider,
            initialValue = if (provider == NavProviders.AMAP) settings.amapKey else settings.tencentKey,
            onDismiss = { editingKey = null },
            onConfirm = { value ->
                if (provider == NavProviders.AMAP) onAmapKey(value) else onTencentKey(value)
                editingKey = null
            }
        )
    }

    if (editingCoordinate) {
        CoordinateDialog(
            initialLat = settings.schoolLatitude,
            initialLon = settings.schoolLongitude,
            onDismiss = { editingCoordinate = false },
            onConfirm = { lat, lon ->
                onSchoolLocation(lat, lon)
                editingCoordinate = false
            }
        )
    }

    if (editingTerm) {
        TermDialog(
            initial = state.termStartDate,
            onDismiss = { editingTerm = false },
            onConfirm = { date ->
                onTermStart(date)
                editingTerm = false
            }
        )
    }

    confirmReset?.let { kind ->
        AlertDialog(
            onDismissRequest = { confirmReset = null },
            title = { Text(if (kind == "data") "清空已导入的数据？" else "恢复默认设置？") },
            text = {
                Text(
                    if (kind == "data") {
                        "将删除你导入的功课表与校园数据。清空后课表页会提示重新导入；" +
                            "如果开着「测试数据」，界面仍会显示示例内容。此操作不可撤销。"
                    } else {
                        "主题、导航等个性化设置将回到初始状态。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (kind == "data") onClearImportedData() else onResetSettings()
                    confirmReset = null
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = null }) { Text("取消") }
            }
        )
    }

    // 导入功课表：整页弹层，给预览留足空间
    if (showImport) {
        Dialog(onDismissRequest = { showImport = false }) {
            Surface(
                shape = RoundedCornerShape(CampusDimens.SheetRadius),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CampusDimens.PagePadding)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "导入功课表",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { showImport = false }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        TimetableImportCard(
                            campus = campus,
                            showTitle = false,
                            onImported = { imported ->
                                onImportTimetable(imported)
                                showImport = false
                            }
                        )
                    }
                }
            }
        }
    }

    // 调试入口：口令
    if (showPasswordDialog) {
        DebugPasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { input ->
                if (onCheckDebugPassword(input)) {
                    showPasswordDialog = false
                    showDebugPanel = true
                    true
                } else {
                    false
                }
            }
        )
    }

    // 调试面板
    if (showDebugPanel) {
        DebugPanelDialog(
            useTestData = settings.useTestData,
            state = state,
            onToggleTestData = onSetUseTestData,
            onClearImportedData = onClearImportedData,
            onDismiss = { showDebugPanel = false }
        )
    }
}

/**
 * 调试口令输入。
 *
 * 口令只是防误触，不当作安全边界；界面上会写明这一点。
 */
@Composable
private fun DebugPasswordDialog(
    onDismiss: () -> Unit,
    /** 返回 true 表示口令正确。 */
    onConfirm: (String) -> Boolean
) {
    var text by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调试入口") },
        text = {
            Column {
                Text(
                    text = "输入口令后可以开关内置测试数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        wrong = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("口令") },
                    singleLine = true,
                    isError = wrong,
                    shape = RoundedCornerShape(CampusDimens.ChipRadius)
                )
                if (wrong) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "口令不正确",
                        style = MaterialTheme.typography.bodySmall,
                        color = CampusColors.Error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { wrong = !onConfirm(text) }) { Text("进入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 调试面板：主要就是测试数据开关。 */
@Composable
private fun DebugPanelDialog(
    useTestData: Boolean,
    state: SettingsUiState,
    onToggleTestData: (Boolean) -> Unit,
    onClearImportedData: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调试面板") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "测试数据",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "打开后忽略你导入的数据，直接显示内置示例学校与示例课表",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useTestData,
                        onCheckedChange = onToggleTestData,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                Spacer(Modifier.height(10.dp))
                HarmonyDivider()
                Spacer(Modifier.height(10.dp))
                InfoRow(label = "当前来源", value = if (useTestData) "内置测试数据" else "用户导入的数据")
                InfoRow(label = "课程", value = "${state.courseCount} 门")
                InfoRow(label = "节次", value = "${state.periodCount} 节")
                InfoRow(label = "教学楼", value = "${state.buildingCount} 栋")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "注意：测试数据只是开发/演示用的开关，不涉及你的隐私数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onClearImportedData() }) { Text("清空导入数据") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

// ---------------------------------------------------------------- 复用小组件

@Composable
private fun GroupTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        SectionHeader(title = text)
    }
}

@Composable
private fun SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CampusDimens.ChipRadius),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(CampusDimens.ChipRadius - 2.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.surface
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .clickable { onSelect(index) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        StepperButton("−", onDecrease)
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        StepperButton("+", onIncrease)
    }
}

@Composable
private fun StepperButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EditableField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onValueChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = RoundedCornerShape(CampusDimens.ChipRadius)
        )
    }
}

@Composable
private fun MessageRow(text: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CampusDimens.PagePadding, vertical = 6.dp),
        shape = RoundedCornerShape(CampusDimens.ChipRadius),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(17.dp)
                    .clickable { onDismiss() }
            )
        }
    }
}

// ---------------------------------------------------------------- 弹窗实现

@Composable
private fun KeyDialog(
    providerId: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${NavProviders.displayName(providerId)} Key") },
        text = {
            Column {
                Text(
                    text = "在服务商开放平台申请「Web 服务」类型的 Key 后填入。" +
                        "没有 Key 或没有网络时，楼外导航会自动按离线方式估算，功能不会中断。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("粘贴 Key") },
                    singleLine = true,
                    shape = RoundedCornerShape(CampusDimens.ChipRadius)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun CoordinateDialog(
    initialLat: Double,
    initialLon: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit
) {
    var lat by remember { mutableStateOf(if (initialLat == 0.0) "" else initialLat.toString()) }
    var lon by remember { mutableStateOf(if (initialLon == 0.0) "" else initialLon.toString()) }
    val valid = lat.toDoubleOrNull() != null && lon.toDoubleOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标定学校坐标") },
        text = {
            Column {
                Text(
                    text = "用于楼外导航与周边地图缓存。可从地图应用复制学校门口的经纬度。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("纬度，例如 30.65810") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(CampusDimens.ChipRadius)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lon,
                    onValueChange = { lon = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("经度，例如 104.06590") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(CampusDimens.ChipRadius)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(lat.toDoubleOrNull() ?: 0.0, lon.toDoubleOrNull() ?: 0.0) }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun TermDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("学期开始日期") },
        text = {
            Column {
                Text(
                    text = "填写第 1 教学周的周一日期，格式 yyyy-MM-dd。课表按它推算当前是第几周。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("2026-09-01") },
                    singleLine = true,
                    shape = RoundedCornerShape(CampusDimens.ChipRadius)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** Key 脱敏展示：只显示前 4 位与后 4 位。 */
private fun maskKey(key: String): String {
    if (key.isBlank()) return "未填写"
    if (key.length <= 8) return "已填写"
    return key.take(4) + "…" + key.takeLast(4)
}
