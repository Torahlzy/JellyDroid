package dev.jdtech.jellyfin.presentation.utils

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp

data class SafePadding(val start: Dp, val top: Dp, val end: Dp, val bottom: Dp)

@Composable
fun rememberSafePadding(): SafePadding {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val safePaddingStart =
        with(density) { WindowInsets.safeDrawing.getLeft(this, layoutDirection).toDp() }

    val safePaddingTop = with(density) { WindowInsets.safeDrawing.getTop(this).toDp() }
    val safePaddingEnd =
        with(density) { WindowInsets.safeDrawing.getRight(this, layoutDirection).toDp() }
    val safePaddingBottom = with(density) { WindowInsets.safeDrawing.getBottom(this).toDp() }

    return SafePadding(
        start = safePaddingStart,
        top = safePaddingTop,
        end = safePaddingEnd,
        bottom = safePaddingBottom,
    )
}
