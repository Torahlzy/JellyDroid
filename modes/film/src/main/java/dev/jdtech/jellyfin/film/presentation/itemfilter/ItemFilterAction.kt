package dev.jdtech.jellyfin.film.presentation.itemfilter

import dev.jdtech.jellyfin.models.CoverDisplayMode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder

sealed interface ItemFilterAction {
    data class OnItemClick(val item: FindroidItem) : ItemFilterAction

    data object OnBackClick : ItemFilterAction

    data class ChangeSorting(val sortBy: SortBy, val sortOrder: SortOrder) : ItemFilterAction

    data class ChangeCoverMode(val coverMode: CoverDisplayMode) : ItemFilterAction
}
