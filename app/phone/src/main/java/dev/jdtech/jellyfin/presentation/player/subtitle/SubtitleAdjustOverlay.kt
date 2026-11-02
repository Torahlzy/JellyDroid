package dev.jdtech.jellyfin.presentation.player.subtitle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.player.local.R as PlayerR
import dev.jdtech.jellyfin.presentation.theme.spacings
import kotlin.math.roundToInt

/**
 * 字幕调节模式的全屏覆盖层：左侧依附字幕区域的紧凑设置卡 + 右侧的垂直拖动手柄。
 *
 * 覆盖层根节点消费全部触摸（不响应、不波纹），防止漏到下层的播放器手势
 * （滑动调进度 / 音量 / 亮度）干扰调节；未进入调节模式时不组合任何内容。
 */
@Composable
fun SubtitleAdjustOverlay(
    manager: SubtitleAdjustManager,
    modifier: Modifier = Modifier,
) {
    val state by manager.state.collectAsStateWithLifecycle()
    if (!state.isAdjustMode) {
        return
    }
    Box(
        modifier =
            modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    // 全部触摸在覆盖层终结，避免穿透触发播放器手势
                    awaitFirstDown(requireUnconsumed = false)
                    currentEvent.changes.forEach { it.consume() }
                }
            }
    ) {
        SubtitleDragHandle(
            state = state,
            onDrag = manager::onBottomOffsetDragged,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        SubtitleAdjustPanel(
            state = state,
            subtitleTextHeightPx = manager.estimateSubtitleTextHeightPx(),
            onScaleStep = manager::onScaleStepClicked,
            onTextOpacityChanged = manager::onTextOpacityChanged,
            onOffsetStep = manager::onOffsetStepClicked,
            onOffsetInput = manager::onOffsetInputChanged,
            onReset = manager::reset,
            onClose = manager::close,
            onApply = manager::apply,
            // 面板是依附字幕区域的紧凑悬浮卡，位置由面板内部按字幕底边线计算
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

/**
 * 垂直拖动手柄：贴在屏幕右缘，垂直位置对齐字幕底边；按住拖动实时改变字幕底边距。
 */
@Composable
private fun SubtitleDragHandle(
    state: SubtitleAdjustManager.SubtitleAdjustState,
    onDrag: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewHeightPx = state.viewHeightPx
    if (viewHeightPx <= 0) {
        return
    }
    // pointerInput 的拖动回调捕获的是启动时的值，用 rememberUpdatedState 保证读到最新的底边距
    val currentState by rememberUpdatedState(state)
    val handleCenterY = with(LocalDensity.current) { (viewHeightPx - state.bottomOffsetPx).toDp() }
    val handleHeight = 48.dp
    var isDragging by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxHeight()) {
        if (isDragging) {
            // 拖动时的参考线，标示字幕底边将要落到的位置
            HorizontalDivider(
                modifier =
                    Modifier.align(Alignment.TopStart)
                        .offset(y = handleCenterY)
                        .fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box(
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .offset(y = handleCenterY - handleHeight / 2)
                    .padding(end = MaterialTheme.spacings.small)
                    .size(width = 44.dp, height = handleHeight)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(24.dp),
                    )
                    .pointerInput(viewHeightPx) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                // 手柄中心 y（px）= 视口高 - 底边距，拖动量加到 y 上换算新底边距
                                val handleCenterPx = viewHeightPx - currentState.bottomOffsetPx
                                onDrag((viewHeightPx - (handleCenterPx + dragAmount.y)).roundToInt())
                            },
                        )
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_arrow_down_up),
                contentDescription = stringResource(PlayerR.string.subtitle_adjust_drag_handle),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
