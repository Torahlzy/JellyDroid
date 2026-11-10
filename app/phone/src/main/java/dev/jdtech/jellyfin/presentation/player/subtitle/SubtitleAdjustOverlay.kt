package dev.jdtech.jellyfin.presentation.player.subtitle

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.player.local.R as PlayerR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import kotlin.math.roundToInt

private val PanelMaxWidth = 360.dp
private val DragHandleHeight = 48.dp
private const val DraggedScale = 1.03f

/** 字幕调节覆盖层：卡片跟随字幕定位，仅面板区域拦截触摸，其余区域透传给播放器手势。 */
@Composable
fun SubtitleAdjustOverlay(
    manager: SubtitleAdjustManager,
    modifier: Modifier = Modifier,
) {
    val state by manager.state.collectAsStateWithLifecycle()
    if (!state.isAdjustMode) {
        return
    }
    // 播放画面上的控件统一使用深色，避免系统浅色主题下出现刺眼的亮色面板。
    FindroidTheme(darkTheme = true) {
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
        val edgePadding = MaterialTheme.spacings.small
        val topPadding = insets.calculateTopPadding() + edgePadding
        val bottomPadding = insets.calculateBottomPadding() + edgePadding
        val startPadding = insets.calculateStartPadding(layoutDirection) + MaterialTheme.spacings.medium
        val endPadding = insets.calculateEndPadding(layoutDirection) + MaterialTheme.spacings.medium
        var contentHeightPx by remember { mutableIntStateOf(0) }
        var isHandleDragging by remember { mutableStateOf(false) }
        val bottomLineY = with(density) { (state.viewHeightPx - state.bottomOffsetPx).toDp() }

        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val panelWidth = (maxWidth - startPadding - endPadding).coerceIn(0.dp, PanelMaxWidth)
            val handleSpace = if (isKeyboardVisible) 0.dp else DragHandleHeight + edgePadding
            val panelMaxHeight =
                (maxHeight - topPadding - bottomPadding - handleSpace).coerceAtLeast(0.dp)
            val panelOffsetY =
                with(density) {
                    val gap = edgePadding.toPx()
                    val subtitleTop =
                        state.viewHeightPx - state.bottomOffsetPx - manager.estimateSubtitleTextHeightPx()
                    val maxTop = (maxHeight - bottomPadding).toPx() - contentHeightPx
                    (subtitleTop - gap - contentHeightPx)
                        .coerceIn(topPadding.toPx(), maxOf(topPadding.toPx(), maxTop))
                        .toDp()
                }
            if (isHandleDragging && !isKeyboardVisible && state.viewHeightPx > 0) {
                HorizontalDivider(
                    modifier = Modifier.offset(y = bottomLineY).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier =
                    Modifier.padding(start = startPadding)
                        .offset(y = panelOffsetY)
                        .onSizeChanged { contentHeightPx = it.height }
                        // 只在弹窗（含拖动手柄）内吞掉触摸，弹窗外仍由播放器处理快进等手势
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                currentEvent.changes.forEach { it.consume() }
                            }
                        },
                verticalArrangement = Arrangement.spacedBy(edgePadding),
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
                    isKeyboardVisible = isKeyboardVisible,
                    modifier = Modifier.width(panelWidth).heightIn(max = panelMaxHeight),
                )
                if (!isKeyboardVisible) {
                    SubtitleDragHandle(
                        state = state,
                        onDrag = manager::onBottomOffsetDragged,
                        onDraggingChange = { isHandleDragging = it },
                        modifier = Modifier.align(Alignment.End).widthIn(max = panelWidth),
                    )
                }
            }
        }
    }
}

/** 带文字提示的拖动胶囊，拖动时点亮并显示字幕位置参考线。 */
@Composable
private fun SubtitleDragHandle(
    state: SubtitleAdjustManager.SubtitleAdjustState,
    onDrag: (Int) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.viewHeightPx <= 0) {
        return
    }
    val currentState by rememberUpdatedState(state)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDraggingChange by rememberUpdatedState(onDraggingChange)
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()
    DisposableEffect(Unit) {
        onDispose { currentOnDraggingChange(false) }
    }
    val scale by
        animateFloatAsState(
            targetValue = if (isDragging) DraggedScale else 1f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            label = "subtitle-position-scale",
        )
    val color by
        animateColorAsState(
            targetValue =
                if (isDragging) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
            label = "subtitle-position-color",
        )
    val elevation by
        animateDpAsState(
            targetValue =
                if (isDragging) MaterialTheme.spacings.small else MaterialTheme.spacings.extraSmall,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
            label = "subtitle-position-elevation",
        )
    Surface(
        modifier =
            modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = CircleShape,
        color = color,
        tonalElevation = elevation,
        shadowElevation = elevation,
    ) {
        Row(
            modifier =
                Modifier.clip(CircleShape)
                    .indication(interactionSource, ripple())
                    .draggable(
                        state = rememberDraggableState { delta ->
                            currentOnDrag((currentState.bottomOffsetPx - delta).roundToInt())
                        },
                        orientation = Orientation.Vertical,
                        interactionSource = interactionSource,
                        onDragStarted = { currentOnDraggingChange(true) },
                        onDragStopped = { currentOnDraggingChange(false) },
                    )
                    .sizeIn(minHeight = DragHandleHeight)
                    .padding(horizontal = MaterialTheme.spacings.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        ) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_arrow_down_up),
                contentDescription = null,
            )
            Text(
                text = stringResource(PlayerR.string.subtitle_adjust_drag_handle),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
