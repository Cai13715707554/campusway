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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.campusway.app.NavUiState
import com.campusway.core.model.Building
import com.campusway.core.model.Campus
import com.campusway.core.model.FloorPlan
import com.campusway.core.model.Geo
import com.campusway.core.model.NavProviders
import com.campusway.core.model.SearchHit
import com.campusway.core.model.subtitle
import com.campusway.core.model.title
import com.campusway.ui.components.CampusPlanCanvas
import com.campusway.ui.components.EmptyState
import com.campusway.ui.components.FloorPlanCanvas
import com.campusway.ui.components.HarmonyCard
import com.campusway.ui.components.HarmonyChip
import com.campusway.ui.components.HarmonyDivider
import com.campusway.ui.components.HarmonyTopBar
import com.campusway.ui.components.SectionHeader
import com.campusway.ui.theme.CampusColors
import com.campusway.ui.theme.CampusDimens

/**
 * 页面 2：导航。
 *
 * 需求要点：
 *  - 显示学校平面图，点击教学楼进入该教学楼的示意图；
 *  - 可直接搜索教学楼或教室，进入后高亮目标教室；
 *  - 楼外导航（高德/腾讯/离线估算）与楼内导览串联成一次完整引导。
 */
@Composable
fun NavigationScreen(
    state: NavUiState,
    campus: Campus,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onOpenBuilding: (String) -> Unit,
    onCloseBuilding: () -> Unit,
    onSelectFloor: (String) -> Unit,
    onRoomClick: (String) -> Unit,
    onOpenHit: (SearchHit) -> Unit,
    onCacheMap: () -> Unit,
    onClearCache: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        val building = state.selectedBuildingId?.let { campus.building(it) }

        HarmonyTopBar(
            title = if (building != null) building.displayName else campus.name,
            subtitle = if (building != null) "楼内示意图 · 点击房间查看路线" else "校园平面图 · 点击建筑进入",
            onBack = if (building != null) onCloseBuilding else null,
            actions = {
                if (building == null) {
                    HarmonyChip(
                        text = NavProviders.displayName(
                            when {
                                state.guidance?.providerId != null -> state.guidance.providerId
                                else -> NavProviders.OFFLINE
                            }
                        ),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        icon = Icons.Outlined.Explore
                    )
                }
            }
        )

        // 搜索框（校园视图才有意义）
        if (building == null) {
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                onClear = onClearSearch
            )
        }

        state.message?.let { message ->
            MessageBar(text = message, onDismiss = onConsumeMessage)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = CampusDimens.PagePadding,
                end = CampusDimens.PagePadding,
                top = 6.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(CampusDimens.ItemSpacing)
        ) {
            // 搜索结果优先展示
            if (state.results.isNotEmpty()) {
                item { SectionHeader(title = "搜索结果", subtitle = "共 ${state.results.size} 条") }
                items(state.results) { hit ->
                    SearchResultRow(hit = hit, onClick = { onOpenHit(hit) })
                }
            }

            if (building == null) {
                // ---------------- 校园视图 ----------------
                item {
                    HarmonyCard(contentPadding = PaddingValues(6.dp)) {
                        CampusPlanCanvas(
                            campus = campus,
                            highlightedBuildingId = null,
                            onBuildingClick = onOpenBuilding
                        )
                    }
                }

                item { SectionHeader(title = "教学楼", subtitle = "点击进入楼内示意图") }
                items(campus.buildings) { b ->
                    BuildingRow(
                        building = b,
                        distanceText = distanceText(campus, b),
                        onClick = { onOpenBuilding(b.id) }
                    )
                }

                if (campus.landmarks.isNotEmpty()) {
                    item { SectionHeader(title = "校园地点") }
                    items(campus.landmarks) { landmark ->
                        HarmonyCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = landmark.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }

                item { MapCacheCard(state = state, onCache = onCacheMap, onClear = onClearCache) }
            } else {
                // ---------------- 楼内视图 ----------------
                item {
                    FloorSelector(
                        building = building,
                        selectedFloorId = state.selectedFloorId,
                        onSelect = onSelectFloor
                    )
                }

                val floor = selectedFloor(building, state.selectedFloorId)
                if (floor != null) {
                    item {
                        HarmonyCard(contentPadding = PaddingValues(6.dp)) {
                            FloorPlanCanvas(
                                floor = floor,
                                highlightedRoomIds = state.highlightedRoomIds,
                                routePoints = state.routePoints,
                                dimOthers = state.highlightedRoomIds.isNotEmpty(),
                                onRoomClick = { room -> onRoomClick(room.id) }
                            )
                        }
                    }
                    item {
                        Text(
                            text = "提示：双指可缩放、拖动查看细节；点击房间即可规划到该教室的路线。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    item { EmptyState(text = "该楼层暂无示意图数据") }
                }

                state.guidance?.let { plan ->
                    item { GuidanceCard(plan = plan) }
                }
            }
        }
    }
}

/** 搜索输入框。 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CampusDimens.PagePadding),
        placeholder = { Text("搜索教学楼 / 教室，例如 302") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "清空",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onClear() }
                )
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(CampusDimens.ChipRadius)
    )
}

/** 一行提示信息（缓存结果、错误等）。 */
@Composable
private fun MessageBar(text: String, onDismiss: () -> Unit) {
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

@Composable
private fun SearchResultRow(hit: SearchHit, onClick: () -> Unit) {
    HarmonyCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hit is SearchHit.RoomHit) Icons.Outlined.Route
                    else Icons.Filled.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hit.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = hit.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BuildingRow(
    building: Building,
    distanceText: String?,
    onClick: () -> Unit
) {
    HarmonyCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = building.displayName.take(2),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = building.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = buildString {
                        append("${building.floors.size} 层")
                        if (distanceText != null) append(" · 约 $distanceText")
                        if (!building.hasGeo) append(" · 未标定坐标")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.Layers,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 楼层切换。 */
@Composable
private fun FloorSelector(
    building: Building,
    selectedFloorId: String?,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        building.floors.sortedBy { it.level }.forEach { floor ->
            val selected = floor.floorId == selectedFloorId
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(CampusDimens.ChipRadius))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(floor.floorId) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = floor.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 楼外 + 楼内引导卡片。 */
@Composable
private fun GuidanceCard(plan: com.campusway.core.model.GuidancePlan) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        SectionHeader(
            title = "前往 ${plan.target.label}",
            subtitle = "预计 ${plan.totalMinutes} 分钟"
        )
        Spacer(Modifier.height(8.dp))
        HarmonyCard {
            // ---- 楼外段 ----
            plan.outdoor?.let { outdoor ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.DirectionsWalk,
                        contentDescription = null,
                        tint = CampusColors.Warning,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "楼外导航",
                        style = MaterialTheme.typography.labelLarge,
                        color = CampusColors.Warning
                    )
                    Spacer(Modifier.weight(1f))
                    HarmonyChip(
                        text = NavProviders.displayName(outdoor.providerId),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = outdoor.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                outdoor.legs.take(4).forEach { leg ->
                    if (leg.instruction.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "· ${leg.instruction}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                HarmonyDivider()
                Spacer(Modifier.height(10.dp))
            }

            // ---- 楼内段 ----
            val indoor = plan.indoor
            if (indoor != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Route,
                        contentDescription = null,
                        tint = CampusColors.Success,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "楼内导览",
                        style = MaterialTheme.typography.labelLarge,
                        color = CampusColors.Success
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "约 ${indoor.totalMinutes} 分钟",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(5.dp))
                indoor.steps.forEachIndexed { index, step ->
                    Text(
                        text = "${index + 1}. ${step.instruction}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            } else {
                Text(
                    text = "该教室暂无楼内路径数据，可参考楼层示意图前往",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 离线地图缓存卡片。 */
@Composable
private fun MapCacheCard(
    state: NavUiState,
    onCache: () -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        SectionHeader(
            title = "离线地图",
            subtitle = "联网时缓存学校周边地图，之后没网也能查看"
        )
        Spacer(Modifier.height(8.dp))
        HarmonyCard {
            val info = state.cacheInfo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (info?.hasCache == true) "已缓存 ${info.tileCount} 块" else "尚未缓存",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.weight(1f))
                if (info?.hasCache == true) {
                    Text(
                        text = info.sizeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (info?.hasCache == true) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "区域：${info.areaName} · 半径 ${info.radiusMeters} 米",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    text = if (state.loading) "缓存中…" else "缓存学校周边",
                    icon = Icons.Outlined.CloudDownload,
                    enabled = !state.loading,
                    onClick = onCache
                )
                if (info?.hasCache == true) {
                    ActionButton(
                        text = "清除",
                        icon = Icons.Filled.Close,
                        enabled = true,
                        onClick = onClear
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "说明：未配置地图 Key 或没有网络时，楼外导航会自动使用离线直线估算，功能不受影响。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(CampusDimens.ChipRadius))
            .background(
                if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------- 小工具

private fun selectedFloor(building: Building, floorId: String?): FloorPlan? =
    floorId?.let { building.floor(it) } ?: building.defaultFloor()

private fun distanceText(campus: Campus, building: Building): String? {
    if (!campus.hasGeo || !building.hasGeo) return null
    val meters = Geo.distanceMeters(
        campus.latitude, campus.longitude, building.latitude, building.longitude
    )
    return Geo.formatDistance(meters)
}
