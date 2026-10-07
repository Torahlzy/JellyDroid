package dev.jdtech.jellyfin.film.presentation.personlist

import dev.jdtech.jellyfin.models.FindroidPerson

sealed interface PersonListAction {
    data class OnPersonClick(val person: FindroidPerson) : PersonListAction

    /** 搜索词变化，由 ViewModel 做防抖后请求服务端。 */
    data class Search(val query: String) : PersonListAction

    data object OnLoadMore : PersonListAction

    data object Retry : PersonListAction

    data object OnBackClick : PersonListAction
}
