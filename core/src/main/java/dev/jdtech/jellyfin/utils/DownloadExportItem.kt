package dev.jdtech.jellyfin.utils

import java.util.UUID

/** 已完成下载、可复制到公共下载目录的视频来源。 */
data class DownloadExportItem(
    val itemId: UUID,
    val sourceId: String,
    val title: String,
    val fileName: String,
    val size: Long,
)
