package dev.jdtech.jellyfin.film.presentation.downloads

import dev.jdtech.jellyfin.models.FindroidItem

sealed interface DownloadsAction {
    data class OnItemClick(val item: FindroidItem) : DownloadsAction

    /** 长按条目，弹出条目操作菜单（保存到下载文件夹 / 删除下载） */
    data class OnItemMenu(val item: FindroidItem) : DownloadsAction

    data object DismissItemMenu : DownloadsAction

    /** 点击条目右上角下载图标或菜单里的「保存到下载文件夹」，弹出保存确认弹窗 */
    data class OnItemExport(val item: FindroidItem) : DownloadsAction

    data object DismissExportConfirm : DownloadsAction

    data object ConfirmExport : DownloadsAction

    /** 菜单里的「删除下载」，弹出删除确认弹窗 */
    data class OnItemDelete(val item: FindroidItem) : DownloadsAction

    data object DismissDeleteConfirm : DownloadsAction

    data object ConfirmDelete : DownloadsAction

    data object StoragePermissionDenied : DownloadsAction

    data object Retry : DownloadsAction
}
