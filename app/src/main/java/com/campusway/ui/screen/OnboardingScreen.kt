package com.campusway.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campusway.core.model.FontScale
import com.campusway.core.model.NavProviders
import com.campusway.core.model.ThemeMode
import com.campusway.core.model.TimetableAxis
import com.campusway.ui.components.HarmonyCard
import com.campusway.ui.components.HarmonyDivider
import com.campusway.ui.theme.CampusColors
import com.campusway.ui.theme.CampusDimens
import com.campusway.ui.theme.CampusMotion

/**
 * 页面 4：新用户引导。
 *
 * 需求：“先让用户配置基础数据和个性化选择（这些可在设置里重新更改）”。
 * 因此这里只收集**真正必要**的几项，其余全部给默认值：
 *  1. 称呼与学校名称；
 *  2. 外观（主题、字体大小）；
 *  3. 课表显示方式；
 *  4. 楼外导航方案（离线 / 高德 / 腾讯，Key 可留空后补）。
 * 每一步都可以跳过，跳过后仍能正常用示例数据跑起来。
 */
@Composable
fun OnboardingScreen(
    onFinished: (
        userName: String,
        schoolName: String,
        themeMode: ThemeMode,
        fontScale: FontScale,
        axis: TimetableAxis,
        provider: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(0) }
    var userName by remember { mutableStateOf("") }
    var schoolName by remember { mutableStateOf("") }
    var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var fontScale by remember { mutableStateOf(FontScale.NORMAL) }
    var axis by remember { mutableStateOf(TimetableAxis.PERIOD) }
    var provider by remember { mutableStateOf(NavProviders.OFFLINE) }

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
                        Brush.linearGradient(
                            listOf(CampusColors.Blue, CampusColors.BlueLight)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.School,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
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

        // 进度条
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
            text = "第 ${step + 1} / $TOTAL_STEPS 步 · 随时可跳过，之后在设置里都能改",
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
                    themeMode = themeMode,
                    fontScale = fontScale,
                    onTheme = { themeMode = it },
                    onFont = { fontScale = it }
                )
                3 -> StepTimetable(axis = axis, onAxis = { axis = it })
                else -> StepNavigation(provider = provider, onProvider = { provider = it })
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
            Text(
                text = "跳过",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(CampusDimens.ChipRadius))
                    .clickable {
                        onFinished(
                            userName, schoolName, themeMode, fontScale, axis, provider
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 11.dp)
            )
            Spacer(Modifier.width(8.dp))
            PrimaryButton(
                text = if (step == TOTAL_STEPS - 1) "开始使用" else "下一步",
                onClick = {
                    if (step == TOTAL_STEPS - 1) {
                        onFinished(userName, schoolName, themeMode, fontScale, axis, provider)
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
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(CampusDimens.ChipRadius))
            .background(MaterialTheme.colorScheme.primary)
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 11.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
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
        FeatureLine(Icons.Outlined.Map, "楼外导航", "校园平面图点选教学楼，可接高德/腾讯，也可离线估算")
        FeatureLine(Icons.Outlined.School, "楼内导览", "楼内示意图高亮目标教室，并给出走法")
        FeatureLine(Icons.Outlined.CloudOff, "离线可用", "课表与所有示意图都存在本机，没网照样用")
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
            text = "鸿蒙风蓝白配色，浅色与深色都支持。",
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

@Composable
private fun StepNavigation(provider: String, onProvider: (String) -> Unit) {
    HarmonyCard {
        Text(
            text = "楼外导航",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "高德和腾讯都支持，Key 也可以之后在设置里再填。" +
                "不填或没网时，楼外导航自动使用离线直线估算，功能不会中断。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        OptionGroup(
            label = "默认服务商",
            options = NavProviders.all.map { NavProviders.displayName(it) },
            selectedIndex = NavProviders.all.indexOf(provider).coerceAtLeast(0),
            onSelect = { onProvider(NavProviders.all[it]) }
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
