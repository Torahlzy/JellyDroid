package dev.jdtech.jellyfin.presentation.player.subtitle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.subtitle.subtitleDurationDiffText
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.presentation.theme.spacings

/** 行首序号，最多 5 行（与字幕站返回的候选数一致）。 */
private const val ORDINALS = "①②③④⑤"

/**
 * 每一行**各自**铺 30% 黑蒙层（`0x4D` ≈ 30%）。
 *
 * 不给整个面板铺一整块：横屏下 5 行加起来接近整屏高度，一大片半透明黑会把画面压掉大半，
 * 反而没法同时看画面判断是否对轴。一行一块既够白字可读，画面也只被占去行所在的那几条。
 */
private val RowScrimColor = Color(0x4D000000)

/** 蒙层块的圆角，让每行看起来是独立的一条而不是整片。 */
private val RowCornerRadius = 8.dp

/**
 * 面板从顶部控制条下方开始：顶部一行有片名与锁定 / 画中画 / 菜单按钮，贴顶会直接压住它们。
 *
 * 取值与 `activity_player.xml` 里 `progress_scrubber_layout` 的 `marginTop="64dp"` 一致，
 * 两者都以上下各占一条控制区为前提，改一处要连另一处一起看。
 */
private val TopControlBarHeight = 64.dp

/**
 * 面板左右边距与按钮内边距。
 *
 * 不套 `MaterialTheme.spacings.*`：这是压在视频画面上的辅助区，页面级间距（16dp / 24dp）会把
 * 文本挤窄、把「应用」按钮顶出画面，所以按覆盖层的紧凑尺度单独取值。
 */
private val PanelHorizontalPadding = 12.dp
private val CloseContentPadding = 10.dp

/** 「关闭」按钮自身高度。 */
private val CloseButtonHeight = 26.dp

/**
 * 「关闭」按钮独占的顶部高度 = 按钮 26dp + **14dp 空隙**。
 *
 * 关闭按钮用 Box 固定在右上角、**不参与行流**：它比一行字幕矮得多，混在 Column 里只要间距少算
 * 一点就会压住第一条字幕。空隙不能只留 2~4dp——半透明块的圆角会糊在一起，看起来仍像被盖住。
 * 内容固定从这条之下开始排。
 */
private val CloseRowHeight = 40.dp

/**
 * 面板行距：正文 18sp、行高 24sp，两行文本区固定 48dp；行内上下 padding 3dp、行间 4dp，
 * 单行约 30dp、两行约 54dp，5 行合计约 286dp。
 *
 * 12sp 在横屏播放时明显偏小（播放器场景观看距离比刷列表远），18sp 是「两行文本 + 5 条候选」
 * 这个高度预算下能放到的最大字号：再往上要么 5 行占满整屏，要么每行只剩半句。
 */
private val RowFontSize = 18.sp
private val RowLineHeight = 24.sp
private val RowTextMinHeight = 48.dp
private val RowInnerPadding = 3.dp
private val RowGap = 4.dp

/** 行头（序号 + 时长差）与按钮比正文小一号：它们只是定位用的标签，正文才是要读的内容。 */
private val MetaFontSize = 14.sp
private val MetaLineHeight = 20.sp
private val ActionFontSize = 14.sp
private val CloseFontSize = 13.sp

private val BodyColor = Color.White
private val HeaderColor = Color(0xCCFFFFFF)
private val ActionColor = Color(0xFFFFD54F)

/**
 * 「多字幕同屏对照」面板：最多 5 行，每行「序号 + 时长差 + 当前这句 + 应用」。
 *
 * 行头不带语言名：同一部影片的候选基本都是中文字幕，写着只是白占横向空间；需要认语言时看字号差与文本本身
 * 更快（简体 / 繁体一眼能分）。
 *
 * 硬性约束（见 wiki/subtitle-download.md 第五节）：
 * - 每行最多两行文本，超出省略号，不撑高行高；
 * - 面板内 `fontScale` 锁 1：12sp 跟随系统字体缩放时两行方案会冲到 300dp 以上，
 *   而这是辅助对照区、不承担无障碍阅读职责（正文阅读仍由播放器字幕轨承担）；
 * - 次序是落盘快照的顺序，**不重排**（与一期「不按推荐度重排」同一个理由：避免用户点错）。
 */
@Composable
fun SubtitleCompareOverlay(
    manager: SubtitleCompareManager,
    onApply: (SubtitleCompareManager.CompareRow) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by manager.state.collectAsStateWithLifecycle()
    if (!state.isShowing) return
    // 只在文本真的变了才打：用来区分「状态里就没文本」和「有文本但没画出来」
    LaunchedEffect(state.rows) {
        AppLog.d(
            "对照面板渲染：%s",
            state.rows.joinToString(" | ") { row -> "#${row.order} ${row.text.length}字" },
        )
    }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(horizontal = PanelHorizontalPadding)
                    .padding(top = TopControlBarHeight)
        ) {
            Column(
                // 顶部让出「关闭」那一条，保证它永远压不到第一行字幕
                modifier = Modifier.fillMaxWidth().padding(top = CloseRowHeight),
                verticalArrangement = Arrangement.spacedBy(RowGap),
            ) {
                state.rows.forEach { row ->
                    CompareRowItem(
                        row = row,
                        applyEnabled = !state.isApplying,
                        onApply = { onApply(row) },
                    )
                }
            }
            // 关闭入口固定在右上角、紧贴面板顶部，为第一行留出下面的空隙
            Row(
                modifier = Modifier.align(Alignment.TopEnd).height(CloseButtonHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isApplying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = ActionColor,
                    )
                    Spacer(Modifier.size(6.dp))
                }
                TextButton(
                    onClick = onClose,
                    contentPadding = PaddingValues(horizontal = CloseContentPadding),
                    modifier =
                        Modifier.height(CloseButtonHeight)
                            .clip(RoundedCornerShape(RowCornerRadius))
                            .background(RowScrimColor),
                ) {
                    Text(
                        text = stringResource(CoreR.string.subtitle_compare_close),
                        color = HeaderColor,
                        fontSize = CloseFontSize,
                    )
                }
            }
        }
    }
}

/**
 * 一行对照：行头（序号 + 时长差）**靠左**，中间当前这句**上下左右居中**，右侧固定「应用」。
 *
 * 两个容易踩的点：
 * - 垂直居中不能靠 `Text` 的 `heightIn(min = ...)`：容器被撑高后文字仍画在顶部，看起来偏上。
 *   固定最小高度要加在外层 `Box` 上，再用 `contentAlignment = Center` 把文本放到正中。
 * - 行头与正文是两段独立文本（不是 `AnnotatedString` 混排）：14sp 混在 18sp 里只会基线对齐，
 *   小字看着比正文高一截。
 */
@Composable
private fun CompareRowItem(
    row: SubtitleCompareManager.CompareRow,
    applyEnabled: Boolean,
    onApply: () -> Unit,
) {
    val ordinal = ORDINALS.getOrNull(row.order - 1)?.toString() ?: row.order.toString()
    val duration = subtitleDurationDiffText(row.durationDiffMs)
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(RowCornerRadius))
                .background(RowScrimColor)
                .padding(
                    start = MaterialTheme.spacings.small,
                    end = MaterialTheme.spacings.extraSmall,
                    top = RowInnerPadding,
                    bottom = RowInnerPadding,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 行头靠左，本身也在行高中线
        Text(
            text = "$ordinal $duration",
            color = HeaderColor,
            style = TextStyle(fontSize = MetaFontSize, lineHeight = MetaLineHeight),
            maxLines = 1,
        )
        Spacer(Modifier.width(MaterialTheme.spacings.small))
        // 行高由这里撑住；文本在这个框内上下左右都居中
        Box(
            modifier = Modifier.weight(1f).heightIn(min = RowTextMinHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = row.text,
                modifier = Modifier.fillMaxWidth(),
                color = BodyColor,
                style = TextStyle(fontSize = RowFontSize, lineHeight = RowLineHeight),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            onClick = onApply,
            enabled = applyEnabled,
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacings.small),
            modifier = Modifier.height(28.dp),
        ) {
            Text(
                text = stringResource(CoreR.string.subtitle_compare_apply),
                color = ActionColor,
                fontSize = ActionFontSize,
            )
        }
    }
}
