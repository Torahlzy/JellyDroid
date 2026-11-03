package dev.jdtech.jellyfin.core.subtitle

/**
 * 某条候选里挑出来的那一份字幕，连同字节与它「配不配这部片子」的判断。
 *
 * [durationDiffMs] 为字幕最后时间点减视频时长的差（负数表示比视频短），视频时长未知或字幕里
 * 没有时间戳时为 null；[isRecommended] 表示差值落在容差内，界面据此加推荐标记。
 * 用普通类而不是 `data class`：里面装的是字节数组，`equals` 按引用比较没有意义。
 */
class DownloadedSubtitle(
    val candidate: SubtitleCandidate,
    val subtitle: SubtitleDownload,
    val bytes: ByteArray,
    val durationDiffMs: Long?,
    val isRecommended: Boolean,
)
