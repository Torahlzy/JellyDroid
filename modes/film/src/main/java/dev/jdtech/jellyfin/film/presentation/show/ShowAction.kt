package dev.jdtech.jellyfin.film.presentation.show

import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidItemPerson

sealed interface ShowAction {
    data class Play(val startFromBeginning: Boolean = false) : ShowAction

    data class PlayTrailer(val trailer: String) : ShowAction

    data object MarkAsPlayed : ShowAction

    data object UnmarkAsPlayed : ShowAction

    data object MarkAsFavorite : ShowAction

    data object UnmarkAsFavorite : ShowAction

    data object OnBackClick : ShowAction

    data object OnHomeClick : ShowAction

    data class NavigateToItem(val item: FindroidItem) : ShowAction

    /** 点击演员，由界面直接跳转到按该人物筛选的作品列表（不再经过人物详情页）。 */
    data class NavigateToPerson(val person: FindroidItemPerson) : ShowAction
}
