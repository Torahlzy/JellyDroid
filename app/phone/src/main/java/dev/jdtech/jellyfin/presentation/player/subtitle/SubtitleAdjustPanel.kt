package dev.jdtech.jellyfin.presentation.player.subtitle

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.player.local.R as PlayerR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val StepButtonSize = 48.dp
private val OffsetInputWidth = 128.dp
private const val PressedScale = 0.96f

// 当前 M3 1.4 的 MotionScheme 尚未公开；与播放器菜单沿用同一组空间 / 效果弹簧。
private val SpatialSpec =
    spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
private val EffectsSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy)

/** 字幕设置悬浮卡：外观实时预览，进度偏移实时保存，位置由覆盖层按字幕底边定位。 */
@Composable
internal fun SubtitleAdjustPanel(
    state: SubtitleAdjustManager.SubtitleAdjustState,
    onScaleStep: (Boolean) -> Unit,
    onTextOpacityChanged: (Int) -> Unit,
    onOffsetStep: (Long) -> Unit,
    onOffsetInput: (Double) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit,
    isKeyboardVisible: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = MaterialTheme.spacings.extraSmall,
        shadowElevation = MaterialTheme.spacings.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = MaterialTheme.spacings.medium,
                    vertical = MaterialTheme.spacings.extraSmall,
                ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
        ) {
            // 横屏键盘占高较大，输入期间把空间留给输入框与底部操作。
            if (!isKeyboardVisible) {
                SubtitleAdjustHeader(onReset = onReset)
                SubtitleAdjustSections(
                    selectedSection = selectedSection,
                    onSelectSection = { selectedSection = it },
                )
            }
            // 仅内容区滚动，低矮横屏下仍能随时关闭或应用。
            Crossfade(
                targetState = selectedSection,
                modifier = Modifier.weight(1f, fill = false),
                animationSpec = EffectsSpec,
                label = "subtitle-adjust-section",
            ) { section ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(
                        modifier =
                            Modifier.verticalScroll(rememberScrollState())
                                .padding(MaterialTheme.spacings.small),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                    ) {
                        if (section == 0) {
                            SubtitleAppearanceSettings(state, onScaleStep, onTextOpacityChanged)
                        } else {
                            SubtitleTimingSettings(state, onOffsetStep, onOffsetInput)
                        }
                    }
                }
            }
            SubtitleAdjustActions(onClose = onClose, onApply = onApply)
        }
    }
}

@Composable
private fun SubtitleAdjustHeader(onReset: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_closed_caption),
                contentDescription = null,
                modifier = Modifier.padding(MaterialTheme.spacings.small),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = MaterialTheme.spacings.small)) {
            Text(
                text = stringResource(PlayerR.string.player_menu_subtitle_adjust),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(PlayerR.string.subtitle_adjust_live_preview),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onReset,
            interactionSource = interactionSource,
            modifier = Modifier.subtitlePressFeedback(interactionSource),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacings.small),
        ) {
            Text(text = stringResource(PlayerR.string.subtitle_adjust_reset))
        }
    }
}

@Composable
private fun SubtitleAdjustSections(
    selectedSection: Int,
    onSelectSection: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels =
        listOf(PlayerR.string.subtitle_adjust_appearance, PlayerR.string.subtitle_adjust_offset)
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            val interactionSource = remember { MutableInteractionSource() }
            SegmentedButton(
                selected = selectedSection == index,
                onClick = { onSelectSection(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                modifier = Modifier.weight(1f).subtitlePressFeedback(interactionSource),
                interactionSource = interactionSource,
                icon = {},
            ) {
                Text(text = stringResource(label))
            }
        }
    }
}

@Composable
private fun SubtitleAppearanceSettings(
    state: SubtitleAdjustManager.SubtitleAdjustState,
    onScaleStep: (Boolean) -> Unit,
    onTextOpacityChanged: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
    ) {
        Text(
            text = stringResource(PlayerR.string.subtitle_adjust_font_size),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        SubtitleSizeButton(increase = false, onClick = { onScaleStep(false) })
        Text(
            text = stringResource(PlayerR.string.subtitle_adjust_scale_percent, state.scalePercent),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(MaterialTheme.spacings.extraLarge),
        )
        SubtitleSizeButton(increase = true, onClick = { onScaleStep(true) })
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
    ) {
        Column {
            Text(
                text = stringResource(PlayerR.string.subtitle_adjust_text_opacity),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    PlayerR.string.subtitle_adjust_opacity_percent,
                    state.textOpacityPercent,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val opacityLabel = stringResource(PlayerR.string.subtitle_adjust_text_opacity)
        Slider(
            value = state.textOpacityPercent / 100f,
            onValueChange = { onTextOpacityChanged((it * 100).roundToInt()) },
            modifier = Modifier.weight(1f).semantics { contentDescription = opacityLabel },
        )
    }
}

@Composable
private fun SubtitleSizeButton(
    increase: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(StepButtonSize).subtitlePressFeedback(interactionSource),
        interactionSource = interactionSource,
    ) {
        Text(
            text = stringResource(
                if (increase) PlayerR.string.subtitle_adjust_size_plus
                else PlayerR.string.subtitle_adjust_size_minus
            ),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SubtitleTimingSettings(
    state: SubtitleAdjustManager.SubtitleAdjustState,
    onOffsetStep: (Long) -> Unit,
    onOffsetInput: (Double) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
    ) {
        Text(
            text = stringResource(PlayerR.string.subtitle_adjust_timing_saved),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        OffsetInputField(state = state, onOffsetInput = onOffsetInput)
    }
    // 所有档位同时展示，避免横向列表把小幅微调藏到屏幕外。
    OFFSET_STEP_LABELS.chunked(4).forEach { steps ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
        ) {
            steps.forEach { (label, offset) ->
                val interactionSource = remember { MutableInteractionSource() }
                FilledTonalButton(
                    onClick = { onOffsetStep(offset) },
                    modifier = Modifier.weight(1f).subtitlePressFeedback(interactionSource),
                    interactionSource = interactionSource,
                    contentPadding = PaddingValues(horizontal = MaterialTheme.spacings.extraSmall),
                ) {
                    Text(text = label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun SubtitleAdjustActions(
    onClose: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeInteraction = remember { MutableInteractionSource() }
    val applyInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
    ) {
        FilledTonalButton(
            onClick = onClose,
            modifier = Modifier.weight(1f).subtitlePressFeedback(closeInteraction),
            interactionSource = closeInteraction,
        ) {
            Text(text = stringResource(PlayerR.string.subtitle_adjust_close))
        }
        Button(
            onClick = onApply,
            modifier = Modifier.weight(1f).subtitlePressFeedback(applyInteraction),
            interactionSource = applyInteraction,
        ) {
            Text(text = stringResource(PlayerR.string.subtitle_adjust_apply))
        }
    }
}

@Composable
private fun Modifier.subtitlePressFeedback(interactionSource: MutableInteractionSource): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            targetValue = if (isPressed) PressedScale else 1f,
            animationSpec = SpatialSpec,
            label = "subtitle-adjust-press",
        )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** 外部档位调整会同步回填，保留输入负号、小数点等中间状态。 */
@Composable
private fun OffsetInputField(
    state: SubtitleAdjustManager.SubtitleAdjustState,
    onOffsetInput: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf(formatSeconds(state.offsetSeconds)) }
    LaunchedEffect(state.offsetSeconds) {
        val parsed = text.replace(',', '.').toDoubleOrNull()
        if (parsed == null || abs(parsed - state.offsetSeconds) > 1e-9) {
            text = formatSeconds(state.offsetSeconds)
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            input.replace(',', '.').toDoubleOrNull()?.let(onOffsetInput)
        },
        modifier = modifier.width(OffsetInputWidth),
        singleLine = true,
        label = { Text(text = stringResource(PlayerR.string.subtitle_adjust_offset)) },
        suffix = { Text(text = stringResource(PlayerR.string.subtitle_adjust_seconds_unit)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

/** 偏移档位按钮按提前 / 延后分行。 */
private val OFFSET_STEP_LABELS: List<Pair<String, Long>> =
    buildList {
        for (stepMs in SubtitleAdjustManager.OFFSET_STEPS_MS.reversed()) {
            add("-${formatOffsetLabel(stepMs)}" to -stepMs)
        }
        for (stepMs in SubtitleAdjustManager.OFFSET_STEPS_MS) {
            add("+${formatOffsetLabel(stepMs)}" to stepMs)
        }
    }

/** 偏移（ms）转为紧凑的秒数按钮标签。 */
private fun formatOffsetLabel(stepMs: Long): String =
    if (stepMs % 1000L == 0L) {
        "${stepMs / 1000L}s"
    } else {
        String.format(Locale.US, "%.1fs", stepMs / 1000.0)
    }

/** 偏移（秒）转为输入框文本，最多保留两位小数。 */
private fun formatSeconds(seconds: Double): String {
    val rounded = (seconds * 100).roundToLong() / 100.0
    return if (rounded == floor(rounded)) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", rounded).trimEnd('0')
    }
}

@Preview(widthDp = 360, locale = "zh-rCN")
@Preview(widthDp = 320, heightDp = 280, locale = "en")
@Composable
private fun SubtitleAdjustPanelPreview() {
    FindroidTheme(darkTheme = true, dynamicColor = false) {
        SubtitleAdjustPanel(
            state = SubtitleAdjustManager.SubtitleAdjustState(textOpacityPercent = 85),
            onScaleStep = {},
            onTextOpacityChanged = {},
            onOffsetStep = {},
            onOffsetInput = {},
            onReset = {},
            onClose = {},
            onApply = {},
        )
    }
}
