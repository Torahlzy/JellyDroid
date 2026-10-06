package dev.jdtech.jellyfin.core.presentation.downloader

import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.utils.DownloadStatus

data class DownloaderState(
    val status: DownloadStatus = DownloadStatus.UNKNOWN,
    /** 下载进度，null 表示还没拿到文件总大小，进度未知（区别于 0%）。 */
    val progress: Float? = null,
    /** 下载速度（字节/秒），仅在下载中且已测出速度时有值。 */
    val speedBytesPerSecond: Long? = null,
    val errorText: UiText? = null,
) {
    /** 是否处于"未结束"状态；PAUSED 也计入，否则等待重试时界面会失去入口。 */
    val isDownloading: Boolean
        get() = status in UNFINISHED_STATUSES

    private companion object {
        val UNFINISHED_STATUSES =
            arrayOf(
                DownloadStatus.QUEUED,
                DownloadStatus.RUNNING,
                DownloadStatus.PAUSED,
                DownloadStatus.FAILED,
            )
    }
}
