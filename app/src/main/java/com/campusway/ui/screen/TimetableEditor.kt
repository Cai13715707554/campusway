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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.campusway.core.model.TimeItem
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
 * 时间表编辑器：改节次的名称/时间，右侧开关在「节次 / 时间点」之间切换。
 *
 * 布局与首页「课程时间表」一致：按午别分组、组内按时间排序。
 * 增删/切类型都会同步调整课程表引用的节次序号，因此 [onSave] 同时回传两张表。
 */
@Composable
fun TimeTableEditorDialog(
    timeTable: TimeTable,
    courseTable: CourseTable,
    onSave: (TimeTable, CourseTable) -> Unit,
    onDismiss: () -> Unit
) {
    var items by remember { mutableStateOf(TimetableEditing.itemsOf(timeTable)) }
    var newKeySeq by remember { mutableIntStateOf(0) }
    var toggleConfirm by remember { mutableStateOf<TimeItem?>(null) }
    var menuItem by remember { mutableStateOf<TimeItem?>(null) }
    var deleteConfirm by remember { mutableStateOf<TimeItem?>(null) }
    var sectionPicker by remember { mutableStateOf<TimeItem?>(null) }

    fun update(key: String, transform: (TimeItem) -> TimeItem) {
        items = items.map { if (it.key == key) transform(it) else it }
    }

    fun courseCountOf(item: TimeItem): Int =
        item.originalIndex?.let { idx -> courseTable.entries.count { it.periodIndex == idx } } ?: 0

    // 编辑结果预览：用于展示校验问题，也直接就是保存内容
    val preview = TimetableEditing.applyItems(timeTable, courseTable, items)
    val problems = preview.timeTable.validate()

    EditorScaffold(
        title = "编辑时间表",
        subtitle = "改名称与时间，右侧切换节次/时间点",
        onDismiss = onDismiss,
        onSave = { onSave(preview.timeTable, preview.courseTable) }
    ) {
        item {
            HarmonyCard {
                Text(
                    text = "每行三列：名称、时间、类型。时间可写「08:00 - 08:45」，也可以只写「06:40」；" +
                        "右侧开关在「节次」和「时间点」之间切换（节次会进课程表，时间点只在时间表显示）。" +
                        "点行尾的「⋮」可删除或改午别。",
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

        val groups = LinkedHashMap<String, MutableList<TimeItem>>()
        items.forEach { item -> groups.getOrPut(item.section) { mutableListOf() } += item }

        if (groups.isEmpty()) {
            item {
                HarmonyCard {
                    EmptyState(text = "还没有节次或时间点", hint = "点下面的「添加一项」开始")
                    Spacer(Modifier.height(12.dp))
                    HarmonyChip(
                        text = "添加一项",
                        icon = Icons.Filled.Add,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                        onClick = {
                            items = items + TimeItem(key = "n-${newKeySeq++}", originalIndex = null, isPeriod = false, section = "上午")
                        }
                    )
                }
            }
        }

        groups.forEach { (section, groupItems) ->
            item(key = "section-$section") {
                SectionHeader(title = section, subtitle = "${groupItems.size} 项")
            }
            item(key = "rows-$section") {
                HarmonyCard {
                    groupItems.forEachIndexed { index, item ->
                        if (index > 0) HarmonyDivider()
                        TimeItemRow(
                            item = item,
                            onNameChange = { value -> update(item.key) { it.copy(name = value) } },
                            onTimeChange = { value -> update(item.key) { it.copy(timeText = value) } },
                            onToggleType = { wantPeriod ->
                                if (!wantPeriod && item.isPeriod && courseCountOf(item) > 0) {
                                    toggleConfirm = item
                                } else {
                                    update(item.key) { it.copy(isPeriod = wantPeriod) }
                                }
                            },
                            onMenu = { menuItem = item }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    HarmonyChip(
                        text = "添加一项",
                        icon = Icons.Filled.Add,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                        onClick = {
                            items = items + TimeItem(
                                key = "n-${newKeySeq++}",
                                originalIndex = null,
                                isPeriod = false,
                                section = section
                            )
                        }
                    )
                }
            }
        }
    }

    // 节次 -> 时间点 前确认（该节上有课）
    toggleConfirm?.let { item ->
        val affected = courseCountOf(item)
        AlertDialog(
            onDismissRequest = { toggleConfirm = null },
            title = { Text("改成时间点？") },
            text = { Text("「${item.name.ifBlank { "这一节" }}」上有 $affected 节课，改成时间点后会一并清除。") },
            confirmButton = {
                TextButton(onClick = {
                    update(item.key) { it.copy(isPeriod = false) }
                    toggleConfirm = null
                }) { Text("改成时间点") }
            },
            dismissButton = { TextButton(onClick = { toggleConfirm = null }) { Text("取消") } }
        )
    }

    // 行菜单：改午别 / 删除
    menuItem?.let { item ->
        AlertDialog(
            onDismissRequest = { menuItem = null },
            title = { Text(item.name.ifBlank { if (item.isPeriod) "节次" else "时间点" }) },
            text = {
                Column {
                    MenuOption("改午别") {
                        sectionPicker = item
                        menuItem = null
                    }
                    MenuOption("删除") {
                        deleteConfirm = item
                        menuItem = null
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuItem = null }) { Text("取消") }
            }
        )
    }

    deleteConfirm?.let { item ->
        val affected = courseCountOf(item)
        AlertDialog(
            onDismissRequest = { deleteConfirm = null },
            title = { Text("删除这一项？") },
            text = {
                Text(
                    if (affected > 0) {
                        "「${item.name.ifBlank { "这一节" }}」上有 $affected 节课，删除后会一并移除。"
                    } else {
                        "删除后，其余节次序号会自动前移。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    items = items.filterNot { it.key == item.key }
                    deleteConfirm = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm = null }) { Text("取消") } }
        )
    }

    sectionPicker?.let { item ->
        var newSection by remember { mutableStateOf("") }
        val sections = items.map { it.section }.distinct()
        AlertDialog(
            onDismissRequest = { sectionPicker = null },
            title = { Text("改午别") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    sections.forEach { section ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    update(item.key) { it.copy(section = section) }
                                    sectionPicker = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = section,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            if (section == item.section) {
                                Text(
                                    text = "当前",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        HarmonyDivider()
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newSection,
                        onValueChange = { newSection = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("新午别") },
                        singleLine = true,
                        shape = RoundedCornerShape(CampusDimens.ChipRadius)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newSection.isNotBlank(),
                    onClick = {
                        update(item.key) { it.copy(section = newSection.trim()) }
                        sectionPicker = null
                    }
                ) { Text("用新午别") }
            },
            dismissButton = { TextButton(onClick = { sectionPicker = null }) { Text("取消") } }
        )
    }
}

/** 时间表编辑里的一行：名称 / 时间 / 节次-时间点开关 / 更多。 */
@Composable
private fun TimeItemRow(
    item: TimeItem,
    onNameChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onToggleType: (Boolean) -> Unit,
    onMenu: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = item.name,
            onValueChange = onNameChange,
            modifier = Modifier.weight(1.2f),
            placeholder = { Text("名称") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            shape = RoundedCornerShape(CampusDimens.ChipRadius)
        )
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(
            value = item.timeText,
            onValueChange = onTimeChange,
            modifier = Modifier.weight(1.15f),
            placeholder = { Text("08:00 - 08:45") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            shape = RoundedCornerShape(CampusDimens.ChipRadius)
        )
        Spacer(Modifier.width(4.dp))
        Column(
            modifier = Modifier.width(56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (item.isPeriod) "节次" else "时间点",
                style = MaterialTheme.typography.labelSmall,
                color = if (item.isPeriod) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Switch(checked = item.isPeriod, onCheckedChange = onToggleType)
        }
        Icon(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = "更多",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .clickable { onMenu() }
        )
    }
}

/** 行菜单里的一个选项。 */
@Composable
private fun MenuOption(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp)
    )
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
