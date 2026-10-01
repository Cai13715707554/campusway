package com.campusway.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 校途配色。
 *
 * 依据需求文档《UI 风格交互要求》：
 *  风格：鸿蒙；配色：鸿蒙蓝白配色；需支持浅色/深色。
 *
 * 主色取鸿蒙标志性的蓝（浅色 #0A59F7 / 深色 #317AF7），
 * 中性色略偏蓝避免发灰；深色底不用纯黑，减少刺眼与拖影。
 */
object CampusColors {

    // ------------------------------------------------------------ 主色

    val Blue = Color(0xFF0A59F7)
    val BlueDark = Color(0xFF317AF7)
    val BlueLight = Color(0xFF4D8DF8)
    val BlueContainerLight = Color(0xFFDCE7FF)
    val BlueContainerDark = Color(0xFF1B3A6B)
    val BlueOutlineLight = Color(0xFFB8CDF7)
    val BlueOutlineDark = Color(0xFF3A4A66)

    // ------------------------------------------------------------ 浅色

    val LightBackground = Color(0xFFF5F6F8)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFEEF1F6)
    val LightOnBackground = Color(0xFF181A1F)
    val LightOnSurfaceVariant = Color(0xFF5C6270)
    val LightOutline = Color(0xFFDFE3EA)

    // ------------------------------------------------------------ 深色

    val DarkBackground = Color(0xFF101114)
    val DarkSurface = Color(0xFF1A1C20)
    val DarkSurfaceVariant = Color(0xFF24262B)
    val DarkOnBackground = Color(0xFFE8EAED)
    val DarkOnSurfaceVariant = Color(0xFFA8AEBB)
    val DarkOutline = Color(0xFF34373D)

    // ------------------------------------------------------------ 语义色

    val Success = Color(0xFF1FA84E)
    val Warning = Color(0xFFE08A00)
    val Error = Color(0xFFD94838)
    val Info = Color(0xFF0A59F7)

    /**
     * 课程主题色板：8 组颜色，保证浅色与深色底上都能看清白字。
     * 同一门课在所有页面颜色保持一致。
     *
     * 数量必须与 core 里的 [com.campusway.core.model.COURSE_COLOR_COUNT] 一致
     * （导入课表时按它取模分配色号）。
     */
    private val CoursePaletteLight = listOf(
        Color(0xFF0A59F7), // 鸿蒙蓝
        Color(0xFF1D9E6F), // 青绿
        Color(0xFFB25E09), // 琥珀
        Color(0xFF8A4FD3), // 紫
        Color(0xFF0E8FA8), // 湖蓝
        Color(0xFFC0392B), // 砖红
        Color(0xFF4B6A88), // 石板蓝
        Color(0xFF7A5C1E)  // 深金
    )

    private val CoursePaletteDark = listOf(
        Color(0xFF4D8DF8),
        Color(0xFF35B98A),
        Color(0xFFCE7B22),
        Color(0xFFA97BE0),
        Color(0xFF2BA8C2),
        Color(0xFFD9584A),
        Color(0xFF6C8AA8),
        Color(0xFF9A7A34)
    )

    fun courseColor(index: Int, dark: Boolean): Color {
        val palette = if (dark) CoursePaletteDark else CoursePaletteLight
        val safe = ((index % palette.size) + palette.size) % palette.size
        return palette[safe]
    }

    val coursePaletteSize: Int get() = CoursePaletteLight.size
}

/**
 * 尺寸规范：鸿蒙风格强调较大的圆角与留白。
 */
object CampusDimens {
    /** 卡片圆角。 */
    val CardRadius = 16.dp
    /** 小卡片/标签圆角。 */
    val ChipRadius = 10.dp
    /** 弹层圆角。 */
    val SheetRadius = 24.dp
    /** 页面左右留白。 */
    val PagePadding = 16.dp
    /** 卡片内边距。 */
    val CardPadding = 14.dp
    /** 列表项间距。 */
    val ItemSpacing = 10.dp
    /** 底部导航栏内容高度。 */
    val BottomBarHeight = 64.dp
}

/**
 * 动效时长：在“性能”和“花里胡哨”之间取平衡——
 * 只用 150~300ms 的位移与淡入，不做长编排。
 */
object CampusMotion {
    const val Fast = 150
    const val Normal = 220
    const val Slow = 300
}
