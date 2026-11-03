package dev.jdtech.jellyfin.core.presentation.subtitle

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.core.subtitle.SubtitleLanguage
import kotlin.math.abs

/**
 * 时长差的说明文案：「字幕短 27 秒」/「字幕长 3 分 12 秒」。
 *
 * 不写成 ±秒：光看符号分不清是字幕比视频短，还是视频比字幕短。
 * 差值不足 1 秒视为与视频等长；视频时长未知或字幕里没有时间戳时显示「未知」。
 *
 * 下载字幕弹窗的候选列表与播放页的对照面板共用这一份实现（两处必须同一种写法，
 * 否则同一份字幕在两处显示成不同的差异描述，用户会以为数据不一致）。
 */
@Composable
fun subtitleDurationDiffText(diffMs: Long?): String {
    if (diffMs == null) return stringResource(R.string.subtitle_candidate_unknown_duration)
    val absMs = abs(diffMs)
    if (absMs < 1_000) return stringResource(R.string.subtitle_candidate_length_matched)
    val duration = subtitleDurationText(absMs)
    return if (diffMs < 0) {
        stringResource(R.string.subtitle_candidate_shorter, duration)
    } else {
        stringResource(R.string.subtitle_candidate_longer, duration)
    }
}

/** 时长本身的写法：不足 1 分钟只给秒，满 1 分钟给分 + 秒。 */
@Composable
private fun subtitleDurationText(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes == 0L) {
        stringResource(R.string.subtitle_candidate_duration_seconds, seconds)
    } else {
        stringResource(R.string.subtitle_candidate_duration_minutes, minutes, seconds)
    }
}

/** 语言在界面上的名字，供候选列表、完成提示与对照面板共用。 */
@StringRes
fun SubtitleLanguage.labelRes(): Int =
    when (this) {
        SubtitleLanguage.SIMPLIFIED_CHINESE -> R.string.subtitle_language_simplified
        SubtitleLanguage.TRADITIONAL_CHINESE -> R.string.subtitle_language_traditional
        SubtitleLanguage.ENGLISH -> R.string.subtitle_language_english
    }
