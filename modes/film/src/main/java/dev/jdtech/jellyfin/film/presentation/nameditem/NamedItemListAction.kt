package dev.jdtech.jellyfin.film.presentation.nameditem

import dev.jdtech.jellyfin.models.FindroidNamedItem

sealed interface NamedItemListAction {
    /** 点击某个值，由界面跳转到该值下的筛选结果页。 */
    data class OnItemClick(val item: FindroidNamedItem) : NamedItemListAction

    data class OnSearchQueryChange(val query: String) : NamedItemListAction

    data object Retry : NamedItemListAction

    data object OnBackClick : NamedItemListAction
}
