package com.campusway.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.campusway.ui.theme.CampusDimens
import com.campusway.ui.theme.CampusMotion

/**
 * 底部导航栏（课表 / 导航 / 设置）。
 *
 * 按需求文档，三个一级页面平铺在底部；选中项用主色 + 浅色胶囊底提示，
 * 切换只做 150ms 的颜色过渡，不搞页面级缩放动画，兼顾性能与观感。
 */
enum class BottomTab(
    val label: String,
    val icon: ImageVector,
    val iconSelected: ImageVector
) {
    TIMETABLE("课表", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    MAP("导航", Icons.Outlined.Map, Icons.Filled.Map),
    SETTINGS("设置", Icons.Outlined.Settings, Icons.Filled.Settings)
}

@Composable
fun HarmonyBottomBar(
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column {
            HarmonyDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(CampusDimens.BottomBarHeight)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BottomTab.entries.forEach { tab ->
                    BottomBarItem(
                        tab = tab,
                        selected = tab == selected,
                        onClick = { onSelect(tab) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(CampusMotion.Fast),
        label = "tabTint"
    )
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(CampusMotion.Fast),
        label = "tabBg"
    )
    val interaction = remember { MutableInteractionSource() }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(container)
                .clickable(interactionSource = interaction, indication = null) { onClick() }
                .padding(horizontal = 18.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (selected) tab.iconSelected else tab.icon,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
}
