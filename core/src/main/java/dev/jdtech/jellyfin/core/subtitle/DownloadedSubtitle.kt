package dev.jdtech.jellyfin.core.subtitle

/**
 * [SubtitleDownloader] 挑出来的字幕。
 *
 * [durationDiffMs] 是字幕最晚时间点与视频时长的差；视频时长未知时不比较，为 null。
 * 用普通类而不是 `data class`：里面装的是字节数组，`equals` 按引用比较没有意义。
 */
class DownloadedSubtitle(
    val subtitle: SubtitleDownload,
    val bytes: ByteArray,
    val durationDiffMs: Long?,
)
