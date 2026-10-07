package dev.jdtech.jellyfin.film.presentation.filtervalue

sealed interface FilterValueListAction {
    /** 点击某个值，由界面跳转到该值下的筛选结果页。 */
    data class OnValueClick(val value: String) : FilterValueListAction

    data class OnSearchQueryChange(val query: String) : FilterValueListAction

    data object Retry : FilterValueListAction

    data object OnBackClick : FilterValueListAction
}
