package dev.jdtech.jellyfin.presentation.settings.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.presentation.theme.Motion
import dev.jdtech.jellyfin.presentation.theme.spacings

/** 设置行共享的按压反馈；当前 M3 的 MotionScheme 尚未公开，统一复用项目内弹簧。 */
@Composable
fun settingsInteraction(enabled: Boolean): SettingsInteraction {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESSED_SCALE else 1f,
        animationSpec = Motion.spatialSpec(),
        label = "settings-press-scale",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed && enabled) MaterialTheme.spacings.extraSmall else 0.dp,
        animationSpec = Motion.effectsSpec(),
        label = "settings-press-tone",
    )
    return SettingsInteraction(source, Modifier.graphicsLayer { scaleX = scale; scaleY = scale }, elevation)
}

class SettingsInteraction(
    val source: MutableInteractionSource,
    val modifier: Modifier,
    val tonalElevation: Dp,
)

private const val PRESSED_SCALE = 0.98f
