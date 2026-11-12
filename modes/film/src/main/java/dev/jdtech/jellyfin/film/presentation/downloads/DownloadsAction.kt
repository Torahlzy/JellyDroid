package dev.jdtech.jellyfin.film.presentation.downloads

import dev.jdtech.jellyfin.models.FindroidItem

sealed interface DownloadsAction {
    data class OnItemClick(val item: FindroidItem) : DownloadsAction

    data object OpenExportDialog : DownloadsAction

    data object CloseExportDialog : DownloadsAction

    data class ToggleExportItem(val sourceId: String) : DownloadsAction

    data object ToggleSelectAll : DownloadsAction

    data object ExportSelected : DownloadsAction

    data object CancelExport : DownloadsAction

    data object StoragePermissionDenied : DownloadsAction

    data object Retry : DownloadsAction
}
