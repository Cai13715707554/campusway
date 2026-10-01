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
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
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
import com.campusway.core.map.Connectivity
import com.campusway.core.map.KeyCheckResult
import com.campusway.core.map.MapKeyValidator
import com.campusway.core.model.FontScale
import com.campusway.core.model.NavProviders
import com.campusway.core.model.ThemeMode
import com.campusway.core.model.TimetableAxis
import com.campusway.ui.components.HarmonyCard
import com.campusway.ui.components.HarmonyDivider
import com.campusway.ui.theme.CampusColors
import com.campusway.ui.theme.CampusDimens
import com.campusway.ui.theme.CampusMotion
import kotlinx.coroutines.launch

/**
 * 新用户引导结束时一次性提交的选择。
 *
 * 主题与字体大小不在其中：它们会在用户点选时**立即写入设置**，
 * 这样引导页里改字号、换主题能实时看到效果（否则要等到走完流程才生效）。
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
 * 需求：
 *  - 先让用户配置基础数据与个性化选择，且都能在设置里重新更改；
 *  - **必须填写至少一个导航 Key**；
 *  - **首次使用必须联网**（用于在线校验 Key）。
 *
 * 因此最后一步是硬门槛：没有联网、或两个 Key 都没通过校验，
 * 就无法进入应用。校验通过后日常使用不再依赖网络。
 */
@Composable
fun OnboardingScreen(
    initialThemeMode: ThemeMode,
    initialFontScale: FontScale,
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

    // 文字类选择先留在本地，走完流程再一次性写入，避免每敲一个字都写一次存储
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

    val amapOk = amapResult?.isSuccess == true
    val tencentOk = tencentResult?.isSuccess == true
    val keyReady = amapOk || tencentOk

    /**
     * 最终使用哪个服务商：按校验结果直接算出来，不依赖上面那个异步 effect。
     * 否则「只校验了腾讯、立刻点开始使用」这种时序下可能保存成没填 Key 的高德。
     */
    val providerToUse = when {
        amapOk && tencentOk -> preferredProvider
        amapOk -> NavProviders.AMAP
        tencentOk -> NavProviders.TENCENT
        else -> preferredProvider
    }

    // 进入导航 Key 那一步时，重新确认一次网络状态
    LaunchedEffect(step) {
        if (step == TOTAL_STEPS - 1) online = Connectivity.isOnline(context)
    }

    // 两个 Key 都通过时，默认使用用户选中的那个；只通过一个时自动用它
    LaunchedEffect(amapOk, tencentOk) {
        preferredProvider = when {
            amapOk && tencentOk -> preferredProvider
            amapOk -> NavProviders.AMAP
            tencentOk -> NavProviders.TENCENT
            else -> preferredProvider
        }
    }

    val progress by animateFloatAsState(
        targetValue = (step + 1) / TOTAL_STEPS.toFloat(),
        animationSpec = tween(CampusMotion.Normal),
        label = "onboardingProgress"
    )

    val canGoNext = if (step == TOTAL_STEPS - 1) keyReady else true

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
            text = if (step == TOTAL_STEPS - 1) {
                "第 ${step + 1} / $TOTAL_STEPS 步 · 首次使用需要联网校验导航 Key"
            } else {
                "第 ${step + 1} / $TOTAL_STEPS 步 · 这些选择之后都能在设置里改"
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
                3 -> StepTimetable(axis = axis, onAxis = { axis = it })
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
                    onRecheckNetwork = { online = Connectivity.isOnline(context) },
                    onValidate = {
                        checking = true
                        scope.launch {
                            // 校验填了的那些 Key；两个都填就都校验
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
            PrimaryButton(
                text = if (step == TOTAL_STEPS - 1) "开始使用" else "下一步",
                enabled = canGoNext && !checking,
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

private const val TOTAL_STEPS = 5

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
        FeatureLine(Icons.Outlined.CloudOff, "离线可用", "配置完成后，没网也能查课表和示意图")
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
            text = "鸿蒙风蓝白配色。点下面的选项会立即生效，方便你直接看到效果。",
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
        Spacer(Modifier.height(12.dp))
        OptionGroup(
            label = "字体大小",
            options = FontScale.entries.map { it.displayName },
            selectedIndex = FontScale.entries.indexOf(fontScale),
            onSelect = { onFont(FontScale.entries[it]) }
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "示例：校途把课表、导航和楼内导览放在一起。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun StepTimetable(axis: TimetableAxis, onAxis: (TimetableAxis) -> Unit) {
    HarmonyCard {
        Text(
            text = "课表",
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
            text = "当前内置了一套示例作息与示例课程，导入自己的课表可在设置里完成。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 最后一步：导航 Key。
 *
 * 必须是硬门槛——没有 Key 楼外导航就只能离线估算，
 * 而需求要求首次配置时就把它配好并联网校验通过。
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
    onRecheckNetwork: () -> Unit,
    onValidate: () -> Unit
) {
    val amapOk = amapResult?.isSuccess == true
    val tencentOk = tencentResult?.isSuccess == true
    val anyKey = amapKey.isNotBlank() || tencentKey.isNotBlank()

    HarmonyCard {
        Text(
            text = "导航 Key（必填）",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "楼外导航需要地图服务商支持。请在开放平台申请「Web 服务」类型的 Key，" +
                "高德或腾讯任填一个即可。首次配置需要联网校验，校验通过后日常使用不再依赖网络。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(10.dp))
        NetworkBanner(online = online, onRecheck = onRecheckNetwork)
        Spacer(Modifier.height(10.dp))

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
        ResultLine(amapResult)
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
        ResultLine(tencentResult)

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(
                text = if (checking) "校验中…" else "联网校验 Key",
                enabled = !checking && online && anyKey,
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

        if (amapOk && tencentOk) {
            Spacer(Modifier.height(14.dp))
            HarmonyDivider()
            Spacer(Modifier.height(12.dp))
            OptionGroup(
                label = "默认使用哪个",
                options = listOf(NavProviders.displayName(NavProviders.AMAP), NavProviders.displayName(NavProviders.TENCENT)),
                selectedIndex = if (preferredProvider == NavProviders.TENCENT) 1 else 0,
                onSelect = { onPreferredProvider(if (it == 1) NavProviders.TENCENT else NavProviders.AMAP) }
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                amapOk || tencentOk -> "校验通过，可以开始使用了。"
                !online -> "当前没有可用网络，连接网络后点「重新检测」。"
                else -> "填写 Key 后点「联网校验 Key」，校验通过才能继续。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (amapOk || tencentOk) CampusColors.Success
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NetworkBanner(online: Boolean, onRecheck: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CampusDimens.ChipRadius))
            .background(
                if (online) CampusColors.Success.copy(alpha = 0.12f)
                else CampusColors.Warning.copy(alpha = 0.14f)
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (online) "网络已连接，可以校验 Key" else "首次使用需要联网：当前无可用网络",
            style = MaterialTheme.typography.bodySmall,
            color = if (online) CampusColors.Success else CampusColors.Warning,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "重新检测",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(CampusDimens.ChipRadius))
                .clickable { onRecheck() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** 单个 Key 的校验结果行。 */
@Composable
private fun ResultLine(result: KeyCheckResult?) {
    if (result == null) return
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
