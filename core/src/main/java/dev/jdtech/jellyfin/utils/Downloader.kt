package dev.jdtech.jellyfin.utils

import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.UiText
import kotlinx.coroutines.flow.Flow

interface Downloader {
    /**
     * 创建下载任务并入队。
     *
     * @return 任务 id 与错误提示；任务 id 为 null 表示入队失败，此时第二个值说明原因。
     */
    suspend fun downloadItem(
        item: FindroidItem,
        sourceId: String,
        storageIndex: Int = 0,
    ): Pair<String?, UiText?>

    suspend fun cancelDownload(item: FindroidItem, taskId: String)

    suspend fun deleteItem(item: FindroidItem, source: FindroidSource)

    /** 持续观察任务进度，任务不存在时发出 [DownloadStatus.UNKNOWN]。 */
    fun observeProgress(taskId: String?): Flow<DownloadProgress>
}
