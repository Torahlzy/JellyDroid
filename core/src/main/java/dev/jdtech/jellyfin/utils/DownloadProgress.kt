package dev.jdtech.jellyfin.utils

/**
 * 下载任务的状态快照。
 *
 * @property status 任务状态
 * @property progress 下载进度（0f~1f），null 表示还没拿到文件总大小、进度未知（区别于 0%）
 * @property speedBytesPerSecond 下载速度（字节/秒），null 表示当前状态测不出速度（未开始、已暂停、已完成）
 * @property reason [DownloadStatus.PAUSED] 时的具体原因
 */
data class DownloadProgress(
    val status: DownloadStatus,
    val progress: Float?,
    val speedBytesPerSecond: Long? = null,
    val reason: DownloadPauseReason = DownloadPauseReason.NONE,
)
