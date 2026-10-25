package dev.jdtech.jellyfin.film.presentation.collection

import dev.jdtech.jellyfin.models.CoverDisplayMode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder

sealed interface CollectionAction {
    data class OnItemClick(val item: FindroidItem) : CollectionAction

    data class OnItemDetails(val item: FindroidItem) : CollectionAction

    data object OnBackClick : CollectionAction

    /** 「我喜欢」页与合集页使用；下载页不展示设置入口。 */
    data class ChangeSorting(val sortBy: SortBy, val sortOrder: SortOrder) : CollectionAction

    /** 「我喜欢」页与合集页使用；封面显示模式只影响展示，不需要重新取数。 */
    data class ChangeCoverMode(val coverMode: CoverDisplayMode) : CollectionAction
}
