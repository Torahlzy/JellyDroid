package dev.jdtech.jellyfin.film.presentation.downloads

import dev.jdtech.jellyfin.models.CollectionSection
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.utils.DownloadExportState

data class DownloadsState(
    val sections: List<CollectionSection> = emptyList(),
    val isLoading: Boolean = false,
    val error: Exception? = null,
    // 非空时显示条目操作菜单
    val itemMenuItem: FindroidItem? = null,
    // 非空时显示保存到下载文件夹的确认弹窗
    val exportConfirmItem: FindroidItem? = null,
    // 非空时显示删除下载的确认弹窗
    val deleteConfirmItem: FindroidItem? = null,
    val exportState: DownloadExportState = DownloadExportState(),
    // 保存 / 删除等操作的失败提示，显示在下载页顶部
    val operationError: UiText? = null,
) {
    val isExportBusy: Boolean
        get() = exportState.isRunning
}
