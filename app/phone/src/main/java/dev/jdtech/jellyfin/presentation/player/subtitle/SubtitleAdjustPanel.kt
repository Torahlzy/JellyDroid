package dev.jdtech.jellyfin.presentation.player.subtitle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.player.local.R as PlayerR
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 字幕调节面板：依附在当前字幕区域（左侧、底边贴着字幕文字上方）的紧凑悬浮卡，
 * 拖动字幕时跟着字幕走，保证调整的同时能看到字幕效果。
 *
 * 内容：字号、字体透明度、字幕进度偏移，以及重置 / 关闭 / 应用。
 * 所有改动经回调进入 [SubtitleAdjustManager] 实时预览；「应用」才持久化，
 * 「关闭」= 取消，「重置」恢复全部默认值（含进度偏移）。
 */
@Composable
internal fun SubtitleAdjustPanel(
    state: SubtitleAdjustManager.SubtitleAdjustState,
    subtitleTextHeightPx: Int,
    onScaleStep: (Boolean) -> Unit,
    onTextOpacityChanged: (Int) -> Unit,
    onOffsetStep: (Long) -> Unit,
    onOffsetInput: (Double) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var cardHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // 卡片底边贴字幕文字上方（字幕底边线 + 一行字幕估高，再留间隙），
    // 贴不下（字幕被拖得太靠上）时贴屏幕顶
    val offsetY =
        with(density) {
            val lineY = (state.viewHeightPx - state.bottomOffsetPx).toFloat()
            val gap = 12.dp.toPx()
            val cardBottomY = lineY - subtitleTextHeightPx - gap
            (cardBottomY - cardHeightPx).coerceAtLeast(8.dp.toPx()).toDp()
        }

    Surface(
        modifier =
            modifier.padding(start = MaterialTheme.spacings.medium)
                .offset(y = offsetY)
                .widthIn(max = 320.dp)
                .onSizeChanged { cardHeightPx = it.height },
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ) {
        Column(
            modifier =
                Modifier.verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = MaterialTheme.spacings.small,
                        vertical = MaterialTheme.spacings.extraSmall,
                    ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
        ) {
            // 字号
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(PlayerR.string.subtitle_adjust_font_size),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.widthIn(min = 56.dp),
                )
                FilledTonalIconButton(
                    onClick = { onScaleStep(false) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Text(
                        text = stringResource(PlayerR.string.subtitle_adjust_size_minus),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    text = stringResource(
                        PlayerR.string.subtitle_adjust_scale_percent,
                        state.scalePercent,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 48.dp),
                )
                FilledTonalIconButton(
                    onClick = { onScaleStep(true) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Text(
                        text = stringResource(PlayerR.string.subtitle_adjust_size_plus),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            // 字体透明度
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(PlayerR.string.subtitle_adjust_text_opacity),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.widthIn(min = 56.dp),
                )
                androidx.compose.material3.Slider(
                    value = state.textOpacityPercent / 100f,
                    onValueChange = { onTextOpacityChanged((it * 100).roundToInt()) },
                    modifier = Modifier.weight(1f).height(32.dp),
                )
                Text(
                    text = stringResource(
                        PlayerR.string.subtitle_adjust_opacity_percent,
                        state.textOpacityPercent,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 40.dp),
                )
            }

            // 字幕进度偏移：档位按钮 + 小输入框（实时保存，不走「应用」）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(PlayerR.string.subtitle_adjust_offset),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.widthIn(min = 56.dp),
                )
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(OFFSET_STEP_LABELS) { step ->
                        TextButton(
                            onClick = { onOffsetStep(step.second) },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(text = step.first, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                OffsetInputField(
                    state = state,
                    onOffsetInput = onOffsetInput,
                )
            }

            // 重置 / 关闭 / 应用
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        text = stringResource(PlayerR.string.subtitle_adjust_reset),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        text = stringResource(PlayerR.string.subtitle_adjust_close),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = onApply,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(
                        text = stringResource(PlayerR.string.subtitle_adjust_apply),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

/**
 * 偏移小输入框：能显示 4 位数字即可，实时回显当前偏移；外部档位调整会同步回填。
 */
@Composable
private fun OffsetInputField(
    state: SubtitleAdjustManager.SubtitleAdjustState,
    onOffsetInput: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf(formatSeconds(state.offsetSeconds)) }
    LaunchedEffect(state.offsetSeconds) {
        val parsed = text.replace(',', '.').toDoubleOrNull()
        // 输入框内容与当前偏移不一致时（档位按钮 / 切换条目导致的回填），以状态为准刷新
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
        modifier = modifier.width(88.dp),
        singleLine = true,
        suffix = { Text(text = "s", style = MaterialTheme.typography.labelMedium) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.bodySmall,
    )
}

/** 偏移档位按钮（顺序为 -20s → -0.3s → +0.3s → +20s）。 */
private val OFFSET_STEP_LABELS: List<Pair<String, Long>> =
    buildList {
        for (stepMs in SubtitleAdjustManager.OFFSET_STEPS_MS.reversed()) {
            add("-${formatOffsetLabel(stepMs)}" to -stepMs)
        }
        for (stepMs in SubtitleAdjustManager.OFFSET_STEPS_MS) {
            add("+${formatOffsetLabel(stepMs)}" to stepMs)
        }
    }

/** 偏移（ms）→ 按钮标签（"20s" / "0.3s"）。 */
private fun formatOffsetLabel(stepMs: Long): String =
    if (stepMs % 1000L == 0L) {
        "${stepMs / 1000L}s"
    } else {
        String.format(Locale.US, "%.1fs", stepMs / 1000.0)
    }

/** 偏移（秒）→ 输入框文本（不带单位后缀），最多保留 2 位小数。 */
private fun formatSeconds(seconds: Double): String {
    val rounded = (seconds * 100).roundToLong() / 100.0
    return if (rounded == floor(rounded)) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", rounded).trimEnd('0')
    }
}
