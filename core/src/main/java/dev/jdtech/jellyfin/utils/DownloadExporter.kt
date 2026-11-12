package dev.jdtech.jellyfin.utils

import kotlinx.coroutines.flow.Flow

/** 复制既有离线视频到公共目录，不改变 Downloader 维护的离线文件与数据库记录。 */
interface DownloadExporter {
    suspend fun getItems(): List<DownloadExportItem>

    suspend fun export(items: List<DownloadExportItem>)

    fun observeState(): Flow<DownloadExportState>

    suspend fun cancel()
}
