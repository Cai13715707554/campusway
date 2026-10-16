package com.campusway.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.campusway.app.TimetablePresenter
import com.campusway.core.model.COURSE_COLOR_COUNT
import com.campusway.core.model.Campus
import com.campusway.core.model.Course
import com.campusway.core.model.CourseEditing
import com.campusway.core.model.CourseTable
import com.campusway.core.model.Period
import com.campusway.core.model.TimePoint
import com.campusway.core.model.TimeTable
import com.campusway.core.model.TimetableEditing
import com.campusway.ui.components.EmptyState
import com.campusway.ui.components.HarmonyCard
import com.campusway.ui.components.HarmonyChip
import com.campusway.ui.components.HarmonyDivider
import com.campusway.ui.components.HarmonyTopBar
import com.campusway.ui.components.SectionHeader
import com.campusway.ui.theme.CampusColors
import com.campusway.ui.theme.CampusDimens
import com.campusway.ui.theme.LocalIsDarkTheme

/**
 * 全屏编辑弹层的统一外壳：顶部栏（返回 + 完成）+ 可滚动的列表内容。
 *
 * 编辑期间只改本地草稿，点「完成」才回调保存；「返回」丢弃改动。
 */
@Composable
private fun EditorScaffold(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                HarmonyTopBar(
                    title = title,
                    subtitle = subtitle,
                    onBack = onDismiss,
                    actions = {
                        HarmonyChip(
                            text = "完成",
                            icon = Icons.Filled.Check,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            onClick = onSave
                        )
                    }
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = CampusDimens.PagePadding,
                        end = CampusDimens.PagePadding,
                        top = 2.dp,
                        bottom = 30.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(CampusDimens.ItemSpacing),
                    content = content
                )
            }
        }
    }
}

/**
 * 课程表编辑器：改课程信息（名称/老师/地点/颜色）与排课（哪天第几节上哪门课）。
 */
@Composable
fun CourseTableEditorDialog(
    courseTable: CourseTable,
    timeTable: TimeTable,
    campus: Campus,
    onSave: (CourseTable) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(courseTable) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }
    var pendingCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var confirmDeleteCourse by remember { mutableStateOf<Course?>(null) }
    val dark = LocalIsDarkTheme.current

    fun addNewCourse(): Course {
        val course = Course(
            id = CourseEditing.newCourseId(draft.courses),
            name = "新课程",
            colorIndex = draft.courses.size % COURSE_COLOR_COUNT
        )
        draft = CourseEditing.upsertCourse(draft, course)
        return course
    }

    EditorScaffold(
        title = "编辑课程表",
        subtitle = "改课程信息与排课",
        onDismiss = onDismiss,
        onSave = { onSave(draft) }
    ) {
        item {
            HarmonyCard {
                Text(
                    text = "点一个格子可以给那一节排课、换课或清空。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { SectionHeader(title = "课程", subtitle = "${draft.courses.size} 门") }
        item {
            HarmonyCard {
                if (draft.courses.isEmpty()) {
                    EmptyState(text = "还没有课程", hint = "点下面的「新增课程」开始")
                } else {
                    draft.courses.forEachIndexed { index, course ->
                        if (index > 0) HarmonyDivider()
                        CourseRowItem(
                            course = course,
                            dark = dark,
                            onEdit = { editingCourse = course },
                            onDelete = { confirmDeleteCourse = course }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                HarmonyChip(
                    text = "新增课程",
                    icon = Icons.Filled.Add,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    onClick = { editingCourse = addNewCourse() }
                )
            }
        }

        item { SectionHeader(title = "排课", subtitle = "点格子改那节课") }
        item {
            HarmonyCard(contentPadding = PaddingValues(8.dp)) {
                if (timeTable.periods.isEmpty()) {
                    EmptyState(text = "还没有上课节次", hint = "先到「编辑时间表」添加节次")
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.width(42.dp))
                        TimetablePresenter.weekdayLabels.forEach { label ->
                            Text(
                                text = label,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    HarmonyDivider()
                    Spacer(Modifier.height(4.dp))
                    timeTable.periods.sortedBy { it.index }.forEach { period ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.width(42.dp),
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
                                val entry = draft.entries.firstOrNull {
                                    it.dayOfWeek == day && it.periodIndex == period.index
                                }
                                val course = entry?.let { e -> draft.courses.firstOrNull { it.id == e.courseId } }
                                val accent = course?.let { CampusColors.courseColor(it.colorIndex, dark) }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .padding(horizontal = 1.5.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (accent != null) accent.copy(alpha = if (dark) 0.32f else 0.18f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .clickable { pendingCell = day to period.index }
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (course != null && accent != null) {
                                        Text(
                                            text = course.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (dark) Color.White else accent,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingCourse?.let { course ->
        CourseEditDialog(
            course = course,
            campus = campus,
            onDismiss = { editingCourse = null },
            onConfirm = { updated ->
                draft = CourseEditing.upsertCourse(draft, updated)
                editingCourse = null
            }
        )
    }

    confirmDeleteCourse?.let { course ->
        AlertDialog(
            onDismissRequest = { confirmDeleteCourse = null },
            title = { Text("删除课程「${course.name}」？") },
            text = { Text("这门课在整周课表里的排课会一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    draft = CourseEditing.removeCourse(draft, course.id)
                    confirmDeleteCourse = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteCourse = null }) { Text("取消") }
            }
        )
    }

    pendingCell?.let { (day, periodIndex) ->
        val currentId = draft.entries
            .firstOrNull { it.dayOfWeek == day && it.periodIndex == periodIndex }
            ?.courseId
        CoursePickDialog(
            day = day,
            periodIndex = periodIndex,
            courses = draft.courses,
            currentCourseId = currentId,
            dark = dark,
            onPick = { id ->
                draft = CourseEditing.setEntry(draft, day, periodIndex, id)
                pendingCell = null
            },
            onClear = {
                draft = CourseEditing.setEntry(draft, day, periodIndex, null)
                pendingCell = null
            },
            onCreate = {
                val course = addNewCourse()
                draft = CourseEditing.setEntry(draft, day, periodIndex, course.id)
                pendingCell = null
                editingCourse = course
            },
            onDismiss = { pendingCell = null }
        )
    }
}

@Composable
private fun CourseRowItem(
    course: Course,
    dark: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(CampusColors.courseColor(course.colorIndex, dark))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = listOfNotNull(
                course.teacher.takeIf { it.isNotBlank() },
                course.note.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = "删除课程",
            tint = CampusColors.Error,
            modifier = Modifier
                .size(20.dp)
                .clickable { onDelete() }
        )
    }
}

/** 课程信息编辑：名称 / 老师 / 地点 / 颜色。 */
@Composable
private fun CourseEditDialog(
    course: Course,
    campus: Campus,
    onDismiss: () -> Unit,
    onConfirm: (Course) -> Unit
) {
    val dark = LocalIsDarkTheme.current
    var name by remember { mutableStateOf(course.name) }
    var teacher by remember { mutableStateOf(course.teacher) }
    var location by remember {
        mutableStateOf(
            if (course.roomId.isNotBlank()) {
                campus.locateRoom(course.roomId)?.room?.name ?: ""
            } else {
                course.note.removePrefix("地点：")
            }
        )
    }
    var colorIndex by remember { mutableIntStateOf(course.colorIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("课程信息") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("课程名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(CampusDimens.ChipRadius)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("任课老师") },
                    singleLine = true,
                    shape = RoundedCornerShape(CampusDimens.ChipRadius)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("上课地点（可留空）") },
                    placeholder = { Text("例如：302 教室") },
                    singleLine = true,
                    shape = RoundedCornerShape(CampusDimens.ChipRadius)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "颜色",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0 until COURSE_COLOR_COUNT).forEach { i ->
                        val color = CampusColors.courseColor(i, dark)
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (i == colorIndex) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                    } else Modifier
                                )
                                .clickable { colorIndex = i }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val loc = location.trim()
                    val roomId = CourseEditing.matchRoomId(campus, loc)
                    val note = if (loc.isBlank() || roomId.isNotBlank()) "" else "地点：$loc"
                    onConfirm(
                        course.copy(
                            name = name.trim(),
                            teacher = teacher.trim(),
                            roomId = roomId,
                            note = note,
                            colorIndex = colorIndex
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 给某天某节选课：列出现有课程 + 新建 + 清空。 */
@Composable
private fun CoursePickDialog(
    day: Int,
    periodIndex: Int,
    courses: List<Course>,
    currentCourseId: String?,
    dark: Boolean,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("周${TimetablePresenter.weekdayLabels[day - 1]} 第 $periodIndex 节") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (courses.isEmpty()) {
                    Text(
                        text = "还没有课程，先新建一门。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    courses.forEach { course ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(course.id) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(CampusColors.courseColor(course.colorIndex, dark))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = course.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            if (course.id == currentCourseId) {
                                Text(
                                    text = "当前",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        HarmonyDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreate) { Text("新建课程") }
        },
        dismissButton = {
            Row {
                if (currentCourseId != null) {
                    TextButton(onClick = onClear) { Text("清空") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

/**
 * 时间表编辑器：改节次的名称/起止时间/午别，可增删节次；时间点同样可增删改。
 *
 * 增删节次会同步调整课程表里引用的节次序号，因此 [onSave] 同时回传两张表。
 */
@Composable
fun TimeTableEditorDialog(
    timeTable: TimeTable,
    courseTable: CourseTable,
    onSave: (TimeTable, CourseTable) -> Unit,
    onDismiss: () -> Unit
) {
    var draftTime by remember { mutableStateOf(timeTable) }
    var draftCourse by remember { mutableStateOf(courseTable) }
    var confirmDeletePeriod by remember { mutableStateOf<Int?>(null) }

    val problems = draftTime.validate()

    fun addPeriodAfter(index: Int?, section: String) {
        val (result, _) = TimetableEditing.addPeriodAfter(
            table = draftTime,
            courseTable = draftCourse,
            afterIndex = index,
            section = section
        )
        draftTime = result.timeTable
        draftCourse = result.courseTable
    }

    EditorScaffold(
        title = "编辑时间表",
        subtitle = "改节次名称与时间，可增删",
        onDismiss = onDismiss,
        onSave = { onSave(draftTime, draftCourse) }
    ) {
        item {
            HarmonyCard {
                Text(
                    text = "「第 N 节」这类名称会作为上课节次，课程表按它排课；" +
                        "起床、大课间、熄灯等属于时间点，只在时间表里显示。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (problems.isNotEmpty()) {
            item {
                HarmonyCard(color = CampusColors.Warning.copy(alpha = 0.12f)) {
                    problems.forEach { problem ->
                        Text(
                            text = "· $problem",
                            style = MaterialTheme.typography.bodySmall,
                            color = CampusColors.Warning
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "可以先保存，之后再回来修改。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { SectionHeader(title = "上课节次", subtitle = "${draftTime.periods.size} 节") }
        item {
            HarmonyCard {
                val periods = draftTime.periods.sortedBy { it.index }
                if (periods.isEmpty()) {
                    EmptyState(text = "还没有上课节次", hint = "点下面的「新增节次」开始")
                } else {
                    periods.forEachIndexed { index, period ->
                        if (index > 0) HarmonyDivider()
                        PeriodEditRow(
                            period = period,
                            onUpdate = { updated -> draftTime = TimetableEditing.updatePeriod(draftTime, updated) },
                            onDelete = { confirmDeletePeriod = period.index },
                            onAddAfter = { addPeriodAfter(period.index, period.section) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                HarmonyChip(
                    text = "新增节次",
                    icon = Icons.Filled.Add,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    onClick = { addPeriodAfter(draftTime.periods.maxOfOrNull { it.index }, "上午") }
                )
            }
        }

        item {
            SectionHeader(
                title = "时间点",
                subtitle = "起床 / 早餐 / 大课间 / 熄灯…（不影响课程表）"
            )
        }
        item {
            HarmonyCard {
                if (draftTime.timePoints.isEmpty()) {
                    EmptyState(text = "还没有时间点", hint = "不需要可以不加")
                } else {
                    draftTime.timePoints.forEachIndexed { index, point ->
                        if (index > 0) HarmonyDivider()
                        TimePointEditRow(
                            point = point,
                            onUpdate = { updated ->
                                draftTime = TimetableEditing.updateTimePointAt(draftTime, index, updated)
                            },
                            onDelete = { draftTime = TimetableEditing.removeTimePointAt(draftTime, index) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                HarmonyChip(
                    text = "新增时间点",
                    icon = Icons.Filled.Add,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    onClick = { draftTime = TimetableEditing.addTimePoint(draftTime) }
                )
            }
        }
    }

    confirmDeletePeriod?.let { index ->
        val affected = draftCourse.entries.count { it.periodIndex == index }
        AlertDialog(
            onDismissRequest = { confirmDeletePeriod = null },
            title = { Text("删除第 $index 节？") },
            text = {
                Text(
                    if (affected > 0) {
                        "这一节上有 $affected 节课，删除后会一并移除，其余节次序号自动前移。"
                    } else {
                        "删除后其余节次序号会自动前移。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val result = TimetableEditing.removePeriod(draftTime, draftCourse, index)
                    draftTime = result.timeTable
                    draftCourse = result.courseTable
                    confirmDeletePeriod = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletePeriod = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PeriodEditRow(
    period: Period,
    onUpdate: (Period) -> Unit,
    onDelete: () -> Unit,
    onAddAfter: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "第 ${period.index} 节",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(66.dp)
            )
            OutlinedTextField(
                value = period.name,
                onValueChange = { onUpdate(period.copy(name = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("名称") },
                singleLine = true,
                shape = RoundedCornerShape(CampusDimens.ChipRadius)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = period.start,
                onValueChange = { onUpdate(period.copy(start = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("开始") },
                placeholder = { Text("08:00") },
                singleLine = true,
                shape = RoundedCornerShape(CampusDimens.ChipRadius)
            )
            OutlinedTextField(
                value = period.end,
                onValueChange = { onUpdate(period.copy(end = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("结束") },
                placeholder = { Text("08:45") },
                singleLine = true,
                shape = RoundedCornerShape(CampusDimens.ChipRadius)
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = period.section,
            onValueChange = { onUpdate(period.copy(section = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("午别（上午 / 下午 / 晚上…）") },
            singleLine = true,
            shape = RoundedCornerShape(CampusDimens.ChipRadius)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onAddAfter) { Text("在下方插入") }
            TextButton(onClick = onDelete) { Text("删除", color = CampusColors.Error) }
        }
    }
}

@Composable
private fun TimePointEditRow(
    point: TimePoint,
    onUpdate: (TimePoint) -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        OutlinedTextField(
            value = point.name,
            onValueChange = { onUpdate(point.copy(name = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("名称") },
            singleLine = true,
            shape = RoundedCornerShape(CampusDimens.ChipRadius)
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = point.start,
                onValueChange = { onUpdate(point.copy(start = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("时间") },
                placeholder = { Text("06:40") },
                singleLine = true,
                shape = RoundedCornerShape(CampusDimens.ChipRadius)
            )
            OutlinedTextField(
                value = point.end,
                onValueChange = { onUpdate(point.copy(end = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("结束（可留空）") },
                singleLine = true,
                shape = RoundedCornerShape(CampusDimens.ChipRadius)
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = point.section,
            onValueChange = { onUpdate(point.copy(section = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("午别") },
            singleLine = true,
            shape = RoundedCornerShape(CampusDimens.ChipRadius)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDelete) { Text("删除", color = CampusColors.Error) }
        }
    }
}
