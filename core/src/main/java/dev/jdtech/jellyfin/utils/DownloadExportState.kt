package dev.jdtech.jellyfin.utils

import dev.jdtech.jellyfin.models.UiText

/** 公共目录保存任务的进度；已完成的文件不随剩余任务取消而删除。 */
data class DownloadExportState(
    val total: Int = 0,
    val saved: Int = 0,
    val failed: Int = 0,
    val cancelled: Int = 0,
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val error: UiText? = null,
)
