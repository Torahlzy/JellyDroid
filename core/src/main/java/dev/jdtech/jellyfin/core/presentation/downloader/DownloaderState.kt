package dev.jdtech.jellyfin.core.presentation.downloader

import android.app.DownloadManager
import dev.jdtech.jellyfin.models.UiText

data class DownloaderState(
    val status: Int = 0,
    /** 下载进度，null 表示 DownloadManager 尚未返回文件总大小，进度未知（区别于 0%）。 */
    val progress: Float? = null,
    val errorText: UiText? = null,
) {
    val isDownloading: Boolean
        get() =
            status in
                arrayOf(
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_FAILED,
                )
}
