package com.campusway.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.campusway.core.model.Campus
import com.campusway.core.model.FloorPlan
import com.campusway.core.model.NormPoint
import com.campusway.core.model.NormRect
import com.campusway.core.model.Room
import com.campusway.core.model.RoomKind
import com.campusway.ui.theme.CampusColors
import com.campusway.ui.theme.CampusMotion
import com.campusway.ui.theme.LocalIsDarkTheme

/**
 * 校园平面图。
 *
 * 纯矢量绘制，完全不依赖地图 SDK 与网络：
 *  - 点击教学楼进入楼内示意图；
 *  - 可双指缩放、拖动，缩放范围有限制，避免用户迷失；
 *  - 目标建筑/地点会高亮。
 */
@Composable
fun CampusPlanCanvas(
    campus: Campus,
    modifier: Modifier = Modifier,
    highlightedBuildingId: String? = null,
    highlightLandmarkId: String? = null,
    onBuildingClick: (String) -> Unit = {},
    onLandmarkClick: (String) -> Unit = {}
) {
    val colors = planColors()
    val measurer = rememberTextMeasurer()

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // 注意：动画必须在 Canvas 之外计算好再传进去，
    // 因为 Canvas 的绘制 lambda 不是 @Composable 作用域，不能调用 animateColorAsState。
    val highlightedIds = remember(highlightedBuildingId) { setOfNotNull(highlightedBuildingId) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(campus.aspectRatio)
            .pointerInput(campus) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 3.5f)
                    val maxPan = (scale - 1f) * size.width * 0.6f
                    offsetX = (offsetX + pan.x).coerceIn(-maxPan, maxPan)
                    offsetY = (offsetY + pan.y).coerceIn(-maxPan * 0.8f, maxPan * 0.8f)
                }
            }
            .pointerInput(campus) {
                detectTapGestures { position ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    if (w <= 0f || h <= 0f) return@detectTapGestures
                    val pivot = Offset(w / 2f, h / 2f)
                    val local = Offset(
                        x = (position.x - offsetX - pivot.x) / scale + pivot.x,
                        y = (position.y - offsetY - pivot.y) / scale + pivot.y
                    )
                    val nx = local.x / w
                    val ny = local.y / h
                    // 教学楼优先（校园图里更常用）
                    val building = campus.buildings.firstOrNull { it.rect.contains(nx, ny) }
                    if (building != null) {
                        onBuildingClick(building.id)
                    } else {
                        campus.landmarks.firstOrNull { it.rect.contains(nx, ny) }
                            ?.let { onLandmarkClick(it.id) }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(campus.aspectRatio)) {
            val canvasW = size.width
            val canvasH = size.height

            withTransform({
                translate(offsetX, offsetY)
                scale(scale, scale, pivot = Offset(canvasW / 2f, canvasH / 2f))
            }) {
                drawRect(color = colors.ground, size = Size(canvasW, canvasH))

                // 主干道与次干道：示意性装饰，帮助建立空间感
                drawLine(
                    color = colors.road,
                    start = Offset(canvasW * 0.5f, canvasH * 0.02f),
                    end = Offset(canvasW * 0.5f, canvasH * 0.98f),
                    strokeWidth = canvasW * 0.035f
                )
                drawLine(
                    color = colors.road,
                    start = Offset(canvasW * 0.04f, canvasH * 0.52f),
                    end = Offset(canvasW * 0.96f, canvasH * 0.52f),
                    strokeWidth = canvasW * 0.03f
                )

                // 校园地点
                campus.landmarks.forEach { landmark ->
                    val highlighted = landmark.id == highlightLandmarkId
                    drawNormRect(
                        rect = landmark.rect,
                        canvasW = canvasW,
                        canvasH = canvasH,
                        fill = if (highlighted) colors.landmarkHighlight else colors.landmark,
                        border = colors.landmarkBorder,
                        cornerRadius = canvasW * 0.008f,
                        borderWidth = canvasW * 0.002f
                    )
                    drawLabel(
                        measurer = measurer,
                        text = landmark.name,
                        rect = landmark.rect,
                        canvasW = canvasW,
                        canvasH = canvasH,
                        color = colors.labelOnFill,
                        fontSizeSp = 10.5f
                    )
                }

                // 教学楼
                campus.buildings.forEach { building ->
                    val highlighted = building.id in highlightedIds
                    drawNormRect(
                        rect = building.rect,
                        canvasW = canvasW,
                        canvasH = canvasH,
                        fill = if (highlighted) colors.buildingHighlight else colors.building,
                        border = if (highlighted) colors.buildingHighlightBorder else colors.buildingBorder,
                        cornerRadius = canvasW * 0.012f,
                        borderWidth = if (highlighted) canvasW * 0.006f else canvasW * 0.003f
                    )
                    drawLabel(
                        measurer = measurer,
                        text = building.displayName,
                        rect = building.rect,
                        canvasW = canvasW,
                        canvasH = canvasH,
                        color = colors.labelOnFill,
                        fontSizeSp = 12f,
                        bold = true
                    )
                }
            }
        }
    }
}

/**
 * 单层楼内示意图。
 *
 * 需求：进入某教学楼后显示该楼示意图，并**高亮要去的教室**。
 * 同时支持画出楼内路径（[routePoints]）以及点击房间（[onRoomClick]）。
 */
@Composable
fun FloorPlanCanvas(
    floor: FloorPlan,
    modifier: Modifier = Modifier,
    highlightedRoomIds: List<String> = emptyList(),
    routePoints: List<NormPoint> = emptyList(),
    dimOthers: Boolean = false,
    onRoomClick: (Room) -> Unit = {}
) {
    val colors = planColors()
    val measurer = rememberTextMeasurer()
    val highlightSet = remember(highlightedRoomIds) { highlightedRoomIds.toSet() }

    var scale by remember(floor.floorId) { mutableFloatStateOf(1f) }
    var offsetX by remember(floor.floorId) { mutableFloatStateOf(0f) }
    var offsetY by remember(floor.floorId) { mutableFloatStateOf(0f) }

    // 路径呼吸：让“怎么走”更容易被注意到，幅度刻意做得很小
    val pulse by animateFloatAsState(
        targetValue = if (routePoints.isNotEmpty()) 1f else 0.6f,
        animationSpec = tween(CampusMotion.Slow),
        label = "routePulse"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(floor.aspectRatio)
            .pointerInput(floor.floorId) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.8f, 4f)
                    val maxPan = (scale - 0.5f) * size.width * 0.5f
                    offsetX = (offsetX + pan.x).coerceIn(-maxPan, maxPan)
                    offsetY = (offsetY + pan.y).coerceIn(-maxPan * 0.7f, maxPan * 0.7f)
                }
            }
            .pointerInput(floor.floorId) {
                detectTapGestures { position ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    if (w <= 0f || h <= 0f) return@detectTapGestures
                    val pivot = Offset(w / 2f, h / 2f)
                    val local = Offset(
                        x = (position.x - offsetX - pivot.x) / scale + pivot.x,
                        y = (position.y - offsetY - pivot.y) / scale + pivot.y
                    )
                    floor.roomAt(local.x / w, local.y / h)?.let(onRoomClick)
                }
            }
    ) {
        val canvasW = size.width
        val canvasH = size.height

        withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale, pivot = Offset(canvasW / 2f, canvasH / 2f))
        }) {
            drawRect(color = colors.floorGround, size = Size(canvasW, canvasH))

            floor.rooms.forEach { room ->
                val highlighted = room.id in highlightSet
                val isCorridor = room.kind == RoomKind.CORRIDOR
                val alpha = if (dimOthers && !highlighted && !isCorridor) 0.45f else 1f
                val fill = when {
                    highlighted -> colors.roomHighlight
                    isCorridor -> colors.corridor
                    else -> colors.roomForKind(room.kind)
                }
                drawNormRect(
                    rect = room.rect,
                    canvasW = canvasW,
                    canvasH = canvasH,
                    fill = fill.copy(alpha = fill.alpha * alpha),
                    border = if (highlighted) colors.roomHighlightBorder else colors.roomBorder,
                    cornerRadius = canvasW * 0.006f,
                    borderWidth = if (highlighted) canvasW * 0.0045f else canvasW * 0.0018f
                )
                // 空间太小就不写字，避免糊成一团
                val pixel = room.rect.toPixels(canvasW, canvasH)
                if (pixel[2] > canvasW * 0.055f && pixel[3] > canvasH * 0.05f) {
                    drawLabel(
                        measurer = measurer,
                        text = room.displayName,
                        rect = room.rect,
                        canvasW = canvasW,
                        canvasH = canvasH,
                        color = if (highlighted) colors.roomHighlightText else colors.roomText,
                        fontSizeSp = if (highlighted) 12.5f else 10.5f,
                        bold = highlighted
                    )
                }
            }

            // 路径：虚线 + 起点圆点
            if (routePoints.size >= 2) {
                val path = Path().apply {
                    moveTo(routePoints.first().x * canvasW, routePoints.first().y * canvasH)
                    routePoints.drop(1).forEach { p -> lineTo(p.x * canvasW, p.y * canvasH) }
                }
                drawPath(
                    path = path,
                    color = colors.route.copy(alpha = pulse),
                    style = Stroke(
                        width = canvasW * 0.008f,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(canvasW * 0.02f, canvasW * 0.014f), 0f
                        )
                    )
                )
                val start = routePoints.first()
                drawCircle(
                    color = colors.route,
                    radius = canvasW * 0.014f,
                    center = Offset(start.x * canvasW, start.y * canvasH)
                )
            }

            // 出入口标记
            floor.rooms.filter { it.kind == RoomKind.EXIT }.forEach { exit ->
                val pixel = exit.rect.toPixels(canvasW, canvasH)
                drawCircle(
                    color = colors.exit,
                    radius = canvasW * 0.012f,
                    center = Offset(pixel[0] + pixel[2] / 2f, pixel[1] + pixel[3] / 2f)
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 绘制辅助

/** 把归一化矩形画到画布上。 */
private fun DrawScope.drawNormRect(
    rect: NormRect,
    canvasW: Float,
    canvasH: Float,
    fill: Color,
    border: Color,
    cornerRadius: Float,
    borderWidth: Float = 0f
) {
    val left = rect.left * canvasW
    val top = rect.top * canvasH
    val w = rect.width * canvasW
    val h = rect.height * canvasH
    if (w <= 0f || h <= 0f) return

    drawRoundRect(
        color = fill,
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
    )
    if (borderWidth > 0f) {
        drawRoundRect(
            color = border,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = borderWidth)
        )
    }
}

/** 在归一化矩形中央写文字（超宽自动截断）。 */
private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    rect: NormRect,
    canvasW: Float,
    canvasH: Float,
    color: Color,
    fontSizeSp: Float,
    bold: Boolean = false
) {
    if (text.isBlank()) return
    val maxWidth = (rect.width * canvasW * 0.92f).toInt()
    if (maxWidth <= 8) return

    val layout = measurer.measure(
        text = text,
        style = TextStyle(
            color = color,
            fontSize = fontSizeSp.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        ),
        maxLines = 1,
        constraints = Constraints(maxWidth = maxWidth)
    )
    val x = rect.left * canvasW + (rect.width * canvasW - layout.size.width) / 2f
    val y = rect.top * canvasH + (rect.height * canvasH - layout.size.height) / 2f
    drawText(textLayoutResult = layout, topLeft = Offset(x, y))
}

/**
 * 平面图配色：浅色/深色两套，集中定义避免多处重复。
 */
data class PlanColors(
    val ground: Color,
    val road: Color,
    val building: Color,
    val buildingBorder: Color,
    val buildingHighlight: Color,
    val buildingHighlightBorder: Color,
    val landmark: Color,
    val landmarkBorder: Color,
    val landmarkHighlight: Color,
    val labelOnFill: Color,
    val floorGround: Color,
    val corridor: Color,
    val roomClassroom: Color,
    val roomLab: Color,
    val roomOffice: Color,
    val roomService: Color,
    val roomOther: Color,
    val roomBorder: Color,
    val roomText: Color,
    val roomHighlight: Color,
    val roomHighlightBorder: Color,
    val roomHighlightText: Color,
    val route: Color,
    val exit: Color
) {
    fun roomForKind(kind: RoomKind): Color = when (kind) {
        RoomKind.CLASSROOM -> roomClassroom
        RoomKind.LAB -> roomLab
        RoomKind.OFFICE -> roomOffice
        RoomKind.RESTROOM, RoomKind.STAIRS, RoomKind.ELEVATOR, RoomKind.EXIT -> roomService
        RoomKind.CORRIDOR -> corridor
        else -> roomOther
    }
}

/** 取当前主题对应的平面图配色。深色模式降低饱和度，避免大面积刺眼。 */
@Composable
fun planColors(): PlanColors {
    val dark = LocalIsDarkTheme.current
    return remember(dark) {
        if (dark) PlanColors(
            ground = Color(0xFF15171B),
            road = Color(0xFF2A2E35),
            building = Color(0xFF2C3A52),
            buildingBorder = Color(0xFF3E527A),
            buildingHighlight = CampusColors.BlueDark,
            buildingHighlightBorder = Color(0xFF8CC1FF),
            landmark = Color(0xFF2A2E35),
            landmarkBorder = Color(0xFF3C424C),
            landmarkHighlight = Color(0xFF3F5B8C),
            labelOnFill = Color(0xFFE8EAED),
            floorGround = Color(0xFF15171B),
            corridor = Color(0xFF23262C),
            roomClassroom = Color(0xFF27324A),
            roomLab = Color(0xFF1F3A38),
            roomOffice = Color(0xFF33302A),
            roomService = Color(0xFF2B2E35),
            roomOther = Color(0xFF2A2D34),
            roomBorder = Color(0xFF3C424C),
            roomText = Color(0xFFB9C0CC),
            roomHighlight = CampusColors.BlueDark,
            roomHighlightBorder = Color(0xFF8CC1FF),
            roomHighlightText = Color.White,
            route = Color(0xFF7DF0B0),
            exit = Color(0xFF7DF0B0)
        ) else PlanColors(
            ground = Color(0xFFEDF1F7),
            road = Color(0xFFDDE3EC),
            building = Color(0xFFC3D4F0),
            buildingBorder = Color(0xFF9BB6E4),
            buildingHighlight = CampusColors.Blue,
            buildingHighlightBorder = Color(0xFF0A59F7),
            landmark = Color(0xFFE4E9F1),
            landmarkBorder = Color(0xFFC6CEDB),
            landmarkHighlight = Color(0xFFB9D2F7),
            labelOnFill = Color(0xFF1B2733),
            floorGround = Color(0xFFF2F4F8),
            corridor = Color(0xFFE6EAF1),
            roomClassroom = Color(0xFFDCE7FB),
            roomLab = Color(0xFFD6F0E6),
            roomOffice = Color(0xFFF1E8D8),
            roomService = Color(0xFFE7E9EE),
            roomOther = Color(0xFFE9EBF0),
            roomBorder = Color(0xFFC2CAD7),
            roomText = Color(0xFF3A4250),
            roomHighlight = CampusColors.Blue,
            roomHighlightBorder = Color(0xFF063FB8),
            roomHighlightText = Color.White,
            route = Color(0xFF1FA84E),
            exit = Color(0xFF1FA84E)
        )
    }
}
