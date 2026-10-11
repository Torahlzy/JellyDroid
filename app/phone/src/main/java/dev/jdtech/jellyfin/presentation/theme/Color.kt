package dev.jdtech.jellyfin.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.jdtech.jellyfin.core.presentation.theme.ColorDark
import dev.jdtech.jellyfin.core.presentation.theme.ColorLight

val lightScheme =
    lightColorScheme(
        primary = ColorLight.primaryLight,
        onPrimary = ColorLight.onPrimaryLight,
        primaryContainer = ColorLight.primaryContainerLight,
        onPrimaryContainer = ColorLight.onPrimaryContainerLight,
        secondary = ColorLight.secondaryLight,
        onSecondary = ColorLight.onSecondaryLight,
        secondaryContainer = ColorLight.secondaryContainerLight,
        onSecondaryContainer = ColorLight.onSecondaryContainerLight,
        tertiary = ColorLight.tertiaryLight,
        onTertiary = ColorLight.onTertiaryLight,
        tertiaryContainer = ColorLight.tertiaryContainerLight,
        onTertiaryContainer = ColorLight.onTertiaryContainerLight,
        error = ColorLight.errorLight,
        onError = ColorLight.onErrorLight,
        errorContainer = ColorLight.errorContainerLight,
        onErrorContainer = ColorLight.onErrorContainerLight,
        background = ColorLight.backgroundLight,
        onBackground = ColorLight.onBackgroundLight,
        surface = ColorLight.surfaceLight,
        onSurface = ColorLight.onSurfaceLight,
        surfaceVariant = ColorLight.surfaceVariantLight,
        onSurfaceVariant = ColorLight.onSurfaceVariantLight,
        outline = ColorLight.outlineLight,
        outlineVariant = ColorLight.outlineVariantLight,
        scrim = ColorLight.scrimLight,
        inverseSurface = ColorLight.inverseSurfaceLight,
        inverseOnSurface = ColorLight.inverseOnSurfaceLight,
        inversePrimary = ColorLight.inversePrimaryLight,
        surfaceDim = ColorLight.surfaceDimLight,
        surfaceBright = ColorLight.surfaceBrightLight,
        surfaceContainerLowest = ColorLight.surfaceContainerLowestLight,
        surfaceContainerLow = ColorLight.surfaceContainerLowLight,
        surfaceContainer = ColorLight.surfaceContainerLight,
        surfaceContainerHigh = ColorLight.surfaceContainerHighLight,
        surfaceContainerHighest = ColorLight.surfaceContainerHighestLight,
    )

val darkScheme =
    darkColorScheme(
        primary = ColorDark.primaryDark,
        onPrimary = ColorDark.onPrimaryDark,
        primaryContainer = ColorDark.primaryContainerDark,
        onPrimaryContainer = ColorDark.onPrimaryContainerDark,
        secondary = ColorDark.secondaryDark,
        onSecondary = ColorDark.onSecondaryDark,
        secondaryContainer = ColorDark.secondaryContainerDark,
        onSecondaryContainer = ColorDark.onSecondaryContainerDark,
        tertiary = ColorDark.tertiaryDark,
        onTertiary = ColorDark.onTertiaryDark,
        tertiaryContainer = ColorDark.tertiaryContainerDark,
        onTertiaryContainer = ColorDark.onTertiaryContainerDark,
        error = ColorDark.errorDark,
        onError = ColorDark.onErrorDark,
        errorContainer = ColorDark.errorContainerDark,
        onErrorContainer = ColorDark.onErrorContainerDark,
        background = ColorDark.backgroundDark,
        onBackground = ColorDark.onBackgroundDark,
        surface = ColorDark.surfaceDark,
        onSurface = ColorDark.onSurfaceDark,
        surfaceVariant = ColorDark.surfaceVariantDark,
        onSurfaceVariant = ColorDark.onSurfaceVariantDark,
        outline = ColorDark.outlineDark,
        outlineVariant = ColorDark.outlineVariantDark,
        scrim = ColorDark.scrimDark,
        inverseSurface = ColorDark.inverseSurfaceDark,
        inverseOnSurface = ColorDark.inverseOnSurfaceDark,
        inversePrimary = ColorDark.inversePrimaryDark,
        surfaceDim = ColorDark.surfaceDimDark,
        surfaceBright = ColorDark.surfaceBrightDark,
        surfaceContainerLowest = ColorDark.surfaceContainerLowestDark,
        surfaceContainerLow = ColorDark.surfaceContainerLowDark,
        surfaceContainer = ColorDark.surfaceContainerDark,
        surfaceContainerHigh = ColorDark.surfaceContainerHighDark,
        surfaceContainerHighest = ColorDark.surfaceContainerHighestDark,
    )

// Material3 的 ColorScheme 没有警告色槽位，这里补一个语义色（取值随深浅色切换）；
// dynamicColor 开着时（Android 12+）scheme 取自系统壁纸，warning 仍用固定值——
// 它表达「要提醒但不是错误」，与主题主色无关，固定不变反而更稳定
val LocalWarningColor = compositionLocalOf { ColorLight.warningLight }

val MaterialTheme.warningColor: Color
    @Composable get() = LocalWarningColor.current
