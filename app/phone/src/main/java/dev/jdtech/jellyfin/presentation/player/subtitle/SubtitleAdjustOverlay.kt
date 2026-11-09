package dev.jdtech.jellyfin.presentation.player.subtitle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
 * 字幕调节模式的全屏覆盖层：设置卡 + 跟在设置项之后的垂直拖动手柄（右下角外侧）。
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
    val density = LocalDensity.current
    val subtitleTextHeightPx = manager.estimateSubtitleTextHeightPx()
    // 测的是「面板 + 手柄」整列的高度：手柄排在面板下方，只减面板高度会让它压到字幕上
    var contentHeightPx by remember { mutableIntStateOf(0) }
    var isHandleDragging by remember { mutableStateOf(false) }

    // 整列底边贴字幕文字上方（字幕底边线 + 一行字幕估高，再留间隙），
    // 贴不下（字幕被拖得太靠上）时贴屏幕顶
    val panelOffsetY = with(density) {
        val lineY = (state.viewHeightPx - state.bottomOffsetPx).toFloat()
        val gap = 12.dp.toPx()
        val cardBottomY = lineY - subtitleTextHeightPx - gap
        (cardBottomY - contentHeightPx).coerceAtLeast(8.dp.toPx()).toDp()
    }
    // 拖动参考线：标示字幕底边将要落到的位置
    val bottomLineY = with(density) { (state.viewHeightPx - state.bottomOffsetPx).toDp() }

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
        if (isHandleDragging && state.viewHeightPx > 0) {
            HorizontalDivider(
                modifier =
                    Modifier.align(Alignment.TopStart)
                        .offset(y = bottomLineY)
                        .fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(
            modifier =
                Modifier.align(Alignment.TopStart)
                    .padding(start = MaterialTheme.spacings.medium)
                    .offset(y = panelOffsetY)
                    .onSizeChanged { contentHeightPx = it.height },
        ) {
            SubtitleAdjustPanel(
                state = state,
                onScaleStep = manager::onScaleStepClicked,
                onTextOpacityChanged = manager::onTextOpacityChanged,
                onOffsetStep = manager::onOffsetStepClicked,
                onOffsetInput = manager::onOffsetInputChanged,
                onReset = manager::reset,
                onClose = manager::close,
                onApply = manager::apply,
                modifier = Modifier.widthIn(max = 320.dp),
            )
            SubtitleDragHandle(
                state = state,
                onDrag = manager::onBottomOffsetDragged,
                onDraggingChange = { isHandleDragging = it },
                // 手柄跟在其他设置项后面：对齐面板右缘、排在面板下方
                modifier =
                    Modifier.align(Alignment.End)
                        .padding(top = MaterialTheme.spacings.extraSmall),
            )
        }
    }
}

/**
 * 垂直拖动手柄：固定跟在设置面板右下角外侧，不跟随字幕定位；
 * 按住拖动实时改变字幕底边距，位置参考线由覆盖层单独绘制。
 */
@Composable
private fun SubtitleDragHandle(
    state: SubtitleAdjustManager.SubtitleAdjustState,
    onDrag: (Int) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewHeightPx = state.viewHeightPx
    if (viewHeightPx <= 0) {
        return
    }
    // pointerInput 的拖动回调捕获的是启动时的值，用 rememberUpdatedState 保证读到最新的底边距
    val currentState by rememberUpdatedState(state)

    Box(
        modifier =
            modifier.size(width = 44.dp, height = 48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(24.dp),
                )
                .pointerInput(viewHeightPx) {
                    detectDragGestures(
                        onDragStart = { onDraggingChange(true) },
                        onDragEnd = { onDraggingChange(false) },
                        onDragCancel = { onDraggingChange(false) },
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
