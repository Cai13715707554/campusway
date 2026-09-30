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
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.campusway.app.TimetableViewData
import com.campusway.app.TimetablePresenter
import com.campusway.core.model.Campus
import com.campusway.core.model.LessonStatus
import com.campusway.core.model.Period
import com.campusway.core.model.TimetableAxis
import com.campusway.core.model.TodayLesson
import com.campusway.ui.components.EmptyState
import com.campusway.ui.components.HarmonyCard
import com.campusway.ui.components.HarmonyChip
import com.campusway.ui.components.HarmonyDivider
import com.campusway.ui.components.HarmonyProgressBar
import com.campusway.ui.components.HarmonyTopBar
import com.campusway.ui.components.SectionHeader
import com.campusway.ui.components.StatusDot
import com.campusway.ui.theme.CampusColors
import com.campusway.ui.theme.CampusDimens
import com.campusway.ui.theme.CampusMotion
import com.campusway.ui.theme.LocalIsDarkTheme

/**
 * 页面 1：课表。
 *
 * 需求要点：
 *  - 默认显示今日课程与时间，高亮“现在的时间段”以及“即将到来的时间段”；
 *  - 也可以把**课程表**与**时间表**分开显示，课程表按整周展示。
 */
@Composable
fun TimetableScreen(
    data: TimetableViewData?,
    campus: Campus,
    onToggleMode: (Boolean) -> Unit,
    onShiftWeek: (Int) -> Unit,
    onBackToThisWeek: () -> Unit,
    onNavigateToRoom: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (data == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("正在读取课表…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        HarmonyTopBar(
            title = "校途",
            subtitle = "第 ${data.weekNumber} / ${data.totalWeeks} 教学周"
        )

        // 今日 / 整周 切换
        ModeSwitcher(
            todayMode = data.dateTitle,
            weekMode = data.weekTitle,
            isToday = true,
            onSelectToday = { onToggleMode(true) },
            onSelectWeek = { onToggleMode(false) }
        )

        if (data.todayLessons.isNotEmpty() || data.currentLesson != null) {
            TodaySummary(data)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = CampusDimens.PagePadding,
                end = CampusDimens.PagePadding,
                top = 4.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(CampusDimens.ItemSpacing)
        ) {
            if (data.todayModeActive) {
                // ---------------- 今日视图 ----------------
                if (data.todayLessons.isEmpty()) {
                    item {
                        EmptyState(
                            text = data.emptyHint.ifBlank { "今天没有排课" },
                            hint = "可在设置中导入课程表与时间表",
                            icon = Icons.Outlined.EventBusy
                        )
                    }
                } else {
                    item {
                        SectionHeader(
                            title = "今日课程",
                            subtitle = "${data.dateTitle} · 共 ${data.todayLessons.size} 节"
                        )
                    }
                    items(
                        data.todayLessons,
                        key = { "${it.entry.courseId}-${it.entry.periodIndex}" }
                    ) { lesson ->
                        LessonCard(
                            lesson = lesson,
                            data = data,
                            campus = campus,
                            onNavigate = { roomId -> onNavigateToRoom(roomId) }
                        )
                    }
                }
                // 今日视图同样把“课程时间表”独立展示出来
                item {
                    TimeTableCard(data)
                }
            } else {
                // ---------------- 整周视图 ----------------
                item {
                    WeekTableCard(
                        data = data,
                        campus = campus,
                        onShiftWeek = onShiftWeek,
                        onBackToThisWeek = onBackToThisWeek
                    )
                }
                item {
                    TimeTableCard(data)
                }
            }
        }
    }
}

/** 顶部的“今日 / 整周”分段控件。 */
@Composable
private fun ModeSwitcher(
    todayMode: String,
    weekMode: String,
    isToday: Boolean,
    onSelectToday: () -> Unit,
    onSelectWeek: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CampusDimens.PagePadding),
        shape = RoundedCornerShape(CampusDimens.ChipRadius),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            SegmentedItem("今日", isToday, Modifier.weight(1f), onSelectToday)
            SegmentedItem("整周课表", !isToday, Modifier.weight(1f), onSelectWeek)
        }
    }
}

@Composable
private fun SegmentedItem(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CampusDimens.ChipRadius - 2.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
            )
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** 顶部“正在上 / 下一节”总览。 */
@Composable
private fun TodaySummary(data: TimetableViewData) {
    val current = data.currentLesson
    val next = data.nextLesson
    if (current == null && next == null) return

    val primary = MaterialTheme.colorScheme.primary

    HarmonyCard(
        modifier = Modifier.padding(
            horizontal = CampusDimens.PagePadding,
            vertical = 10.dp
        ),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        if (current != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(color = CampusColors.Success, filled = true)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "正在上",
                    style = MaterialTheme.typography.labelLarge,
                    color = primary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${current.period.start} - ${current.period.end}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = current.course.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            HarmonyProgressBar(progress = current.progress, color = primary)
            Spacer(Modifier.height(5.dp))
            Text(
                text = "还有 ${current.minutesFromNow} 分钟下课",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (current != null && next != null) {
            Spacer(Modifier.height(10.dp))
            HarmonyDivider()
            Spacer(Modifier.height(10.dp))
        }
        if (next != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(color = CampusColors.Warning, filled = next.status == LessonStatus.UPCOMING)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (next.status == LessonStatus.UPCOMING) "即将开始" else "下一节",
                    style = MaterialTheme.typography.labelLarge,
                    color = CampusColors.Warning
                )
                Spacer(Modifier.weight(1f))
                // 在提醒窗口内时，把“还有多久”直接写出来
                data.upcomingHint?.let { hint ->
                    HarmonyChip(
                        text = hint,
                        containerColor = CampusColors.Warning.copy(alpha = 0.16f),
                        contentColor = CampusColors.Warning
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = "${next.period.start} 开课",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${next.course.name} · ${next.roomId.ifBlank { "地点待定" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 单节课卡片：状态、进度、地点、前往导航入口。 */
@Composable
private fun LessonCard(
    lesson: TodayLesson,
    data: TimetableViewData,
    campus: Campus,
    onNavigate: (String) -> Unit
) {
    val dark = LocalIsDarkTheme.current
    val accent = CampusColors.courseColor(
        data.courses[lesson.course.id]?.colorIndex ?: 0,
        dark
    )
    val highlighted = lesson.status == LessonStatus.ONGOING || lesson.status == LessonStatus.UPCOMING
    val location = TimetablePresenter.locate(campus, lesson.roomId)

    HarmonyCard(
        modifier = Modifier,
        color = if (highlighted) accent.copy(alpha = if (dark) 0.18f else 0.10f)
        else MaterialTheme.colorScheme.surface
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // 左侧色条
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = lesson.course.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusChip(lesson)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append("第 ${lesson.period.index} 节 · ")
                        append("${lesson.period.start}-${lesson.period.end}")
                        if (lesson.course.teacher.isNotBlank()) append(" · ${lesson.course.teacher}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (location != null) {
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Place,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${location.building.displayName} ${location.floor.name} ${location.room.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (lesson.status == LessonStatus.ONGOING) {
                    Spacer(Modifier.height(8.dp))
                    HarmonyProgressBar(progress = lesson.progress, color = accent)
                }
            }
        }

        if (location != null && (lesson.status == LessonStatus.ONGOING || lesson.status == LessonStatus.UPCOMING)) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(CampusDimens.ChipRadius))
                    .background(accent.copy(alpha = 0.14f))
                    .clickable { onNavigate(lesson.roomId) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.NearMe,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "带我去这间教室",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
            }
        }
    }
}

/** 状态标签：进行中 / 即将开始 / 已结束 / 稍后。 */
@Composable
private fun StatusChip(lesson: TodayLesson) {
    when (lesson.status) {
        LessonStatus.ONGOING -> HarmonyChip(
            text = "进行中",
            containerColor = CampusColors.Success.copy(alpha = 0.16f),
            contentColor = CampusColors.Success
        )
        LessonStatus.UPCOMING -> HarmonyChip(
            text = "即将开始",
            containerColor = CampusColors.Warning.copy(alpha = 0.16f),
            contentColor = CampusColors.Warning
        )
        LessonStatus.FINISHED -> HarmonyChip(
            text = "已结束",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LessonStatus.LATER -> HarmonyChip(
            text = "稍后",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 课程时间表。
 *
 * 需求要求时间表与课程表**分开显示**，这里就是独立的一张“课程时间表”。
 */
@Composable
private fun TimeTableCard(data: TimetableViewData) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        SectionHeader(
            title = "课程时间表",
            subtitle = "${data.timeTable.name} · 共 ${data.timeTable.periods.size} 节"
        )
        Spacer(Modifier.height(8.dp))
        HarmonyCard {
            data.periodSections.forEachIndexed { sectionIndex, (section, periods) ->
                if (sectionIndex > 0) {
                    Spacer(Modifier.height(10.dp))
                    HarmonyDivider()
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    text = section,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                periods.forEach { period ->
                    PeriodRow(period = period, axis = data.axis)
                }
            }
            if (data.timeTable.periods.isEmpty()) {
                EmptyState(text = "还没有时间表数据", hint = "可在设置中导入")
            }
        }
    }
}

@Composable
private fun PeriodRow(period: Period, axis: TimetableAxis) {
    val now = java.time.LocalTime.now()
    val isNow = period.isValid &&
        now.hour * 60 + now.minute in period.startMinuteOfDay until period.endMinuteOfDay

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CampusDimens.ChipRadius))
            .background(
                if (isNow) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                else Color.Transparent
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (axis == TimetableAxis.PERIOD) "第 ${period.index} 节" else period.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isNow) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(74.dp)
        )
        Text(
            text = "${period.start} - ${period.end}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        if (isNow) {
            HarmonyChip(text = "当前时段")
        } else {
            Text(
                text = "${period.durationMinutes} 分钟",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 整周课程表。
 *
 * 与时间表分开：这里是“课程表”，左侧是节次，横向是一周七天。
 */
@Composable
private fun WeekTableCard(
    data: TimetableViewData,
    campus: Campus,
    onShiftWeek: (Int) -> Unit,
    onBackToThisWeek: () -> Unit
) {
    val periods = data.timeTable.periods
    val dark = LocalIsDarkTheme.current

    Column(modifier = Modifier.padding(top = 8.dp)) {
        SectionHeader(
            title = "本周课程表",
            subtitle = data.weekTitle,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeekNavButton("‹") { onShiftWeek(-1) }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(CampusDimens.ChipRadius))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onBackToThisWeek() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "本周",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    WeekNavButton("›") { onShiftWeek(1) }
                }
            }
        )
        Spacer(Modifier.height(8.dp))

        HarmonyCard(contentPadding = PaddingValues(8.dp)) {
            // 表头：节次列 + 周一~周日
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.width(38.dp))
                TimetablePresenter.weekdayLabels.forEachIndexed { index, label ->
                    val day = index + 1
                    // 只有“本周”并且是今天才高亮表头
                    val isToday = data.weekOffset == 0 &&
                        java.time.LocalDate.now().dayOfWeek.value == day
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            HarmonyDivider()
            Spacer(Modifier.height(4.dp))

            periods.forEach { period ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.width(38.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${period.index}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = period.start,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    (1..7).forEach { day ->
                        val lessons = data.weekLessons[day].orEmpty()
                            .filter { it.period.index == period.index }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .padding(horizontal = 1.5.dp)
                        ) {
                            if (lessons.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                )
                            } else {
                                val lesson = lessons.first()
                                val accent = CampusColors.courseColor(
                                    data.courses[lesson.course.id]?.colorIndex ?: 0,
                                    dark
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(accent.copy(alpha = if (dark) 0.32f else 0.18f))
                                        .padding(2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = lesson.course.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (dark) Color.White else accent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val room = TimetablePresenter.locate(campus, lesson.roomId)
                                    if (room != null) {
                                        Text(
                                            text = room.room.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun WeekNavButton(text: String, onClick: () -> Unit) {
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
