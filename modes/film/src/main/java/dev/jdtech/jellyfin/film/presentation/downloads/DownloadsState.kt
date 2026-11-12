package dev.jdtech.jellyfin.film.presentation.downloads

import dev.jdtech.jellyfin.models.CollectionSection
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.utils.DownloadExportItem
import dev.jdtech.jellyfin.utils.DownloadExportState

data class DownloadsState(
    val sections: List<CollectionSection> = emptyList(),
    val isLoading: Boolean = false,
    val error: Exception? = null,
    val showExportDialog: Boolean = false,
    val exportItems: List<DownloadExportItem> = emptyList(),
    val selectedSourceIds: Set<String> = emptySet(),
    val isLoadingExportItems: Boolean = false,
    val isStartingExport: Boolean = false,
    val exportState: DownloadExportState = DownloadExportState(),
    val exportError: UiText? = null,
) {
    val isExportBusy: Boolean
        get() = isStartingExport || exportState.isRunning

    val selectedItems: List<DownloadExportItem>
        get() = exportItems.filter { it.sourceId in selectedSourceIds }

    val selectedSize: Long
        get() = selectedItems.sumOf { it.size }
}
