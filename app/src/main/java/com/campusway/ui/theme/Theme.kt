package com.campusway.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.campusway.core.model.FontScale
import com.campusway.core.model.ThemeMode

/**
 * 品牌字体。
 *
 * 需求要求“鸿蒙字体”。HarmonyOS Sans 有版权约束、也不允许随意打进 APK，
 * 因此这里采用**字体回退栈**策略：
 *  - 若系统已安装 HarmonyOS Sans（鸿蒙/部分国产 ROM 会内置），
 *    Android 的字体回退会优先命中它；
 *  - 否则回退到系统默认中文字体（思源黑体/苹方等），
 *    字形风格与鸿蒙字体同属无衬线现代黑体，观感一致。
 *
 * 如果学校希望强制使用指定字体，把 ttf 放进 `res/font/` 后
 * 在这里改成 `FontFamily(Font(R.font.harmony_sans))` 即可，
 * 无需改动其它任何代码。
 */
val CampusFontFamily: FontFamily = FontFamily.Default

/**
 * 全局字号缩放，由设置页的“字体大小”控制。
 * 放在 CompositionLocal 里，避免每个组件都接收参数。
 */
val LocalFontScale = staticCompositionLocalOf { FontScale.NORMAL }

/** 当前是否处于深色主题，供自绘组件（课表格、平面图）取色。 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun CampusWayTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    fontScale: FontScale = FontScale.NORMAL,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val scheme = if (dark) DarkScheme else LightScheme
    val base = MaterialTheme.typography
    val factor = fontScale.value

    /**
     * 按缩放系数重算一套字号。
     *
     * 说明：辅助函数 [scaled] 的字号参数用 Number 而不是 Float，
     * 这样 `36` 与 `12.5f` 两种写法都能直接传（Kotlin 不会把 Int 字面量
     * 隐式转成 Float，写成 Float 会导致一堆类型不匹配的编译错误）。
     */
    val typography = androidx.compose.material3.Typography(
        displaySmall = base.displaySmall.scaled(factor, 36),
        headlineMedium = base.headlineMedium.scaled(factor, 28),
        headlineSmall = base.headlineSmall.scaled(factor, 24),
        titleLarge = base.titleLarge.scaled(factor, 22, FontWeight.SemiBold),
        titleMedium = base.titleMedium.scaled(factor, 17, FontWeight.SemiBold),
        titleSmall = base.titleSmall.scaled(factor, 15, FontWeight.Medium),
        bodyLarge = base.bodyLarge.scaled(factor, 16),
        bodyMedium = base.bodyMedium.scaled(factor, 14),
        bodySmall = base.bodySmall.scaled(factor, 12.5f),
        labelLarge = base.labelLarge.scaled(factor, 14, FontWeight.Medium),
        labelMedium = base.labelMedium.scaled(factor, 12.5f),
        labelSmall = base.labelSmall.scaled(factor, 11)
    )

    CompositionLocalProvider(
        LocalFontScale provides fontScale,
        LocalIsDarkTheme provides dark
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            content = content
        )
    }
}

/** 生成一个按比例缩放、可选加粗的文本样式。 */
private fun TextStyle.scaled(factor: Float, sizeSp: Number, weight: FontWeight? = null): TextStyle =
    copy(
        fontFamily = CampusFontFamily,
        fontSize = (sizeSp.toFloat() * factor).sp,
        fontWeight = weight ?: fontWeight
    )

private val LightScheme = lightColorScheme(
    primary = CampusColors.Blue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = CampusColors.BlueContainerLight,
    onPrimaryContainer = CampusColors.Blue,
    secondary = CampusColors.BlueLight,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = CampusColors.LightBackground,
    onBackground = CampusColors.LightOnBackground,
    surface = CampusColors.LightSurface,
    onSurface = CampusColors.LightOnBackground,
    surfaceVariant = CampusColors.LightSurfaceVariant,
    onSurfaceVariant = CampusColors.LightOnSurfaceVariant,
    outline = CampusColors.LightOutline,
    outlineVariant = CampusColors.BlueOutlineLight,
    error = CampusColors.Error,
    onError = androidx.compose.ui.graphics.Color.White,
    tertiary = CampusColors.Success,
    onTertiary = androidx.compose.ui.graphics.Color.White
)

private val DarkScheme = darkColorScheme(
    primary = CampusColors.BlueDark,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = CampusColors.BlueContainerDark,
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFD6E3FF),
    secondary = CampusColors.BlueLight,
    onSecondary = androidx.compose.ui.graphics.Color(0xFF0B1B33),
    background = CampusColors.DarkBackground,
    onBackground = CampusColors.DarkOnBackground,
    surface = CampusColors.DarkSurface,
    onSurface = CampusColors.DarkOnBackground,
    surfaceVariant = CampusColors.DarkSurfaceVariant,
    onSurfaceVariant = CampusColors.DarkOnSurfaceVariant,
    outline = CampusColors.DarkOutline,
    outlineVariant = CampusColors.BlueOutlineDark,
    error = CampusColors.Error,
    onError = androidx.compose.ui.graphics.Color.White,
    tertiary = CampusColors.Success,
    onTertiary = androidx.compose.ui.graphics.Color.White
)
