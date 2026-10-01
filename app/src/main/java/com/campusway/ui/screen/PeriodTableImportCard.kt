package com.campusway.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campusway.core.importer.AndroidSheetImport
import com.campusway.core.importer.ImportedPeriodTable
import com.campusway.core.model.Period
import com.campusway.core.model.TimeTable
import com.campusway.ui.components.HarmonyCard
import com.campusway.ui.components.HarmonyDivider
import com.campusway.ui.theme.CampusColors
import com.campusway.ui.theme.CampusDimens
import kotlinx.coroutines.launch

/**
 * 导入时间表（作息表），并让用户选择显示哪些午别/项目。
 *
 * 需求要点：
 *  - 识别后询问用户要显示哪些**午别里的项目**（有的同学不住校，可以不显示晚上）；
 *  - 可选项完全来自表格里实际出现的午别，不写死「上午/下午/晚上」；
 *  - 允许全都不显示：这时弹「喵确认吗？喵」，确认后按"没有时间表"处理，
 *    课表页原本放时间表的位置改显示占位图。
 */
@Composable
fun PeriodTableImportCard(
    /** 回调：最终生效的时间表（只含勾选的项目、已重新编号），以及勾选的键。 */
    onImported: (TimeTable, List<String>) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pastedText by remember { mutableStateOf("") }
    var showPaste by remember { mutableStateOf(false) }
    var parsing by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ImportedPeriodTable?>(null) }
    /** 勾选保留的项目键（午别|项目名）。勾选的会成为节次，未勾选的不进入时间表。 */
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var applied by remember { mutableStateOf(false) }
    var showEasterEgg by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        parsing = true
        applied = false
        scope.launch {
            val name = AndroidSheetImport.displayName(context, uri)
            val imported = AndroidSheetImport.readPeriodTableFile(context, uri)
            fileName = name
            result = imported
            selectedKeys = imported.defaultSelection()
            parsing = false
        }
    }

    fun resetOn(imported: ImportedPeriodTable, name: String?) {
        fileName = name
        result = imported
        selectedKeys = imported.defaultSelection()
        applied = false
    }

    HarmonyCard(modifier = modifier) {
        if (showTitle) {
            Text(
                text = "导入时间表",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = "作息表需要有「午别 / 项目 / 时间」三列，格式示例见工程 docs/时间表.xlsx。" +
                "时间可以是 8:00——8:45 这样的区间，也可以是 Excel 的时间格式。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodImportAction(
                text = "选择文件",
                icon = Icons.Outlined.UploadFile,
                enabled = !parsing,
                onClick = { filePicker.launch(AndroidSheetImport.MIME_TYPES) }
            )
            PeriodImportAction(
                text = if (showPaste) "收起粘贴框" else "粘贴表格",
                icon = Icons.Outlined.ContentPaste,
                enabled = !parsing,
                onClick = { showPaste = !showPaste }
            )
            if (parsing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.CenterVertically),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (showPaste) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = pastedText,
                onValueChange = { pastedText = it; applied = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp, max = 200.dp),
                placeholder = { Text("从 Excel 复制表格后粘贴到这里，例如：\n午别\t项目\t时间\n上午\t第一节\t8:00——8:45") },
                shape = RoundedCornerShape(CampusDimens.ChipRadius)
            )
            Spacer(Modifier.height(8.dp))
            PeriodImportAction(
                text = "解析粘贴内容",
                icon = Icons.Outlined.ContentPaste,
                enabled = pastedText.isNotBlank(),
                onClick = { resetOn(AndroidSheetImport.readPeriodTableText(pastedText), null) }
            )
        }

        result?.let { imported ->
            Spacer(Modifier.height(12.dp))
            HarmonyDivider()
            Spacer(Modifier.height(12.dp))

            fileName?.let {
                Text(
                    text = "文件：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = imported.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = if (imported.isEmpty) CampusColors.Error else CampusColors.Success
            )
            imported.warnings.forEach { warning ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "· $warning",
                    style = MaterialTheme.typography.bodySmall,
                    color = CampusColors.Warning
                )
            }

            if (imported.isEmpty) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "没有解析出项目，请检查表格是否有「午别 / 项目 / 时间」三列。",
                    style = MaterialTheme.typography.bodySmall,
                    color = CampusColors.Error
                )
            } else {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "选择要显示的项目",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "勾选的项目会成为课表里的节次。不需要的午别整组取消即可（例如不住校可以不显示晚上）；" +
                        "全部取消也可以，那就会当作没有时间表。已按「第几节」这类名称帮你预勾选。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))
                imported.timeTable.sectionsWithPeriods().forEach { (section, periods) ->
                    SectionToggleGroup(
                        section = section,
                        periods = periods,
                        selectedKeys = selectedKeys,
                        onToggleSection = { selectAll ->
                            val keys = periods.map { it.visibilityKey }.toSet()
                            selectedKeys = if (selectAll) selectedKeys + keys else selectedKeys - keys
                        },
                        onTogglePeriod = { period, selected ->
                            selectedKeys = if (selected) selectedKeys + period.visibilityKey
                            else selectedKeys - period.visibilityKey
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                val total = imported.timeTable.periods.size
                val selectedCount = imported.timeTable.periods.count { it.visibilityKey in selectedKeys }
                Text(
                    text = "将保留 $selectedCount / $total 项作为节次",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selectedCount == 0) CampusColors.Warning
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PeriodImportAction(
                        text = if (applied) "已导入" else "导入这些数据",
                        icon = Icons.Filled.Check,
                        enabled = !applied,
                        onClick = {
                            // 全都不勾选 -> 彩蛋确认
                            if (selectedCount == 0) showEasterEgg = true
                            else {
                                onImported(
                                    imported.timeTable.keepingKeys(selectedKeys),
                                    selectedKeys.toList()
                                )
                                applied = true
                            }
                        }
                    )
                    if (applied) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "已写入，可在课表页查看",
                            style = MaterialTheme.typography.bodySmall,
                            color = CampusColors.Success
                        )
                    }
                }
            }
        }
    }

    // 彩蛋：全部不勾选时确认
    if (showEasterEgg) {
        AlertDialog(
            onDismissRequest = { showEasterEgg = false },
            title = { Text("喵确认吗？喵") },
            text = {
                Text(
                    text = "你把时间表里的项目全都不勾选了。确认的话，课表页会当作没有时间表，" +
                        "原本放时间表的位置会显示一张占位图。之后想恢复，重新导入时间表即可。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showEasterEgg = false
                    result?.let { imported ->
                        // 按"没有时间表"处理：写入一张没有节次的时间表
                        onImported(imported.timeTable.cleared(), emptyList())
                        applied = true
                    }
                }) { Text("喵，确认") }
            },
            dismissButton = {
                TextButton(onClick = { showEasterEgg = false }) { Text("再想想") }
            }
        )
    }
}

/** 一个午别分组：标题 + 整组开关 + 组内每一项的勾选。 */
@Composable
private fun SectionToggleGroup(
    section: String,
    periods: List<Period>,
    selectedKeys: Set<String>,
    onToggleSection: (Boolean) -> Unit,
    onTogglePeriod: (Period, Boolean) -> Unit
) {
    val allSelected = periods.all { it.visibilityKey in selectedKeys }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CampusDimens.ChipRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = section,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (allSelected) "整组取消" else "整组勾选",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(CampusDimens.ChipRadius))
                    .clickable { onToggleSection(!allSelected) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Switch(
                checked = allSelected,
                onCheckedChange = onToggleSection,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        periods.forEach { period ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = period.visibilityKey in selectedKeys,
                    onCheckedChange = { checked -> onTogglePeriod(period, checked) }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = period.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${period.start} - ${period.end}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodImportAction(
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
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}
