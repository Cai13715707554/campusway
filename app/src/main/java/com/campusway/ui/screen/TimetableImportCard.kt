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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.campusway.core.importer.AndroidSheetImport
import com.campusway.core.importer.ImportedTimetable
import com.campusway.core.model.Campus
import com.campusway.core.model.TimeTable
import com.campusway.ui.components.HarmonyCard
import com.campusway.ui.components.HarmonyDivider
import com.campusway.ui.theme.CampusColors
import com.campusway.ui.theme.CampusDimens
import kotlinx.coroutines.launch

/**
 * 导入表格式功课表。
 *
 * 引导页与设置页共用同一块卡片，避免两处各写一遍、行为不一致。
 *
 * 支持两种来源：
 *  1. 选文件：`.xlsx`（自己解析，无第三方依赖）/ `.csv` / `.txt`；
 *  2. 直接粘贴：从 Excel 里框选复制，粘进来就是制表符分隔的表格。
 *
 * 解析完成后先给预览（识别到多少天/节次/课程 + 注意事项），确认无误再写入。
 */
@Composable
fun TimetableImportCard(
    campus: Campus,
    onImported: (ImportedTimetable) -> Unit,
    modifier: Modifier = Modifier,
    /** 当前已有的时间表：导入功课时沿用它来定节次名称与时间，避免把已配好的作息冲掉。 */
    baseTimeTable: TimeTable? = null,
    /** 引导页里空间紧张，标题由外层给，这里就不再重复。 */
    showTitle: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pastedText by remember { mutableStateOf("") }
    var showPaste by remember { mutableStateOf(false) }
    var parsing by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ImportedTimetable?>(null) }
    var applied by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        parsing = true
        applied = false
        scope.launch {
            val name = AndroidSheetImport.displayName(context, uri)
            val imported = AndroidSheetImport.readFile(context, uri, campus, baseTimeTable)
            fileName = name
            result = imported
            parsing = false
        }
    }

    HarmonyCard(modifier = modifier) {
        if (showTitle) {
            Text(
                text = "导入功课表",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = "支持 .xlsx / .csv / .txt，也可以直接从 Excel 框选复制后粘贴。" +
                "表格第一行需要是「星期一…星期日」，第一列可以放节次或时间。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImportAction(
                text = "选择文件",
                icon = Icons.Outlined.UploadFile,
                enabled = !parsing,
                onClick = { filePicker.launch(AndroidSheetImport.MIME_TYPES) }
            )
            ImportAction(
                text = if (showPaste) "收起粘贴框" else "粘贴表格",
                icon = Icons.Outlined.ContentPaste,
                enabled = !parsing,
                onClick = { showPaste = !showPaste }
            )
            if (parsing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp).align(Alignment.CenterVertically),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (showPaste) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = pastedText,
                onValueChange = {
                    pastedText = it
                    applied = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp, max = 200.dp),
                placeholder = {
                    Text("从 Excel 复制表格后粘贴到这里，例如：\n星期一\t星期二\n数学\t英语")
                },
                shape = RoundedCornerShape(CampusDimens.ChipRadius)
            )
            Spacer(Modifier.height(8.dp))
            ImportAction(
                text = "解析粘贴内容",
                icon = Icons.Outlined.ContentPaste,
                enabled = pastedText.isNotBlank(),
                onClick = {
                    fileName = null
                    result = AndroidSheetImport.readPastedText(pastedText, campus, baseTimeTable)
                    applied = false
                }
            )
        }

        result?.let { imported ->
            Spacer(Modifier.height(12.dp))
            HarmonyDivider()
            Spacer(Modifier.height(12.dp))
            ImportPreview(imported = imported, fileName = fileName)

            if (imported.blocking.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                imported.blocking.forEach { problem ->
                    Text(
                        text = "· $problem",
                        style = MaterialTheme.typography.bodySmall,
                        color = CampusColors.Error
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "补足后再回来导入；这里不会自动帮你补节次。",
                    style = MaterialTheme.typography.bodySmall,
                    color = CampusColors.Warning
                )
            } else if (imported.isEmpty) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "没有解析出课程，请检查表头是否是星期名、以及表格里是否填了课程。",
                    style = MaterialTheme.typography.bodySmall,
                    color = CampusColors.Error
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ImportAction(
                        text = if (applied) "已导入" else "导入这些数据",
                        icon = Icons.Filled.Check,
                        enabled = !applied,
                        onClick = {
                            onImported(imported)
                            applied = true
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
}

/** 解析结果预览：数量摘要 + 注意事项 + 前几行原样展示。 */
@Composable
private fun ImportPreview(imported: ImportedTimetable, fileName: String?) {
    if (fileName != null) {
        Text(
            text = "文件：$fileName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
    }
    Text(
        text = imported.summary,
        style = MaterialTheme.typography.bodyMedium,
        color = if (imported.isEmpty) CampusColors.Error else CampusColors.Success
    )

    if (imported.courseTable.courses.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "课程：" + imported.courseTable.courses.take(8).joinToString("、") { it.name } +
                if (imported.courseTable.courses.size > 8) " …" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "节次：" + imported.timeTable.periods.take(4)
                .joinToString("、") { "${it.name}(${it.start})" } +
                if (imported.timeTable.periods.size > 4) " …" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (imported.warnings.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        imported.warnings.forEach { warning ->
            Text(
                text = "· $warning",
                style = MaterialTheme.typography.bodySmall,
                color = CampusColors.Warning
            )
        }
    }
}

@Composable
private fun ImportAction(
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
