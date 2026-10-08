package dev.jdtech.jellyfin.film.presentation.episode

import dev.jdtech.jellyfin.models.FindroidItemPerson
import java.util.UUID

sealed interface EpisodeAction {
    data class Play(val startFromBeginning: Boolean = false) : EpisodeAction

    data object MarkAsPlayed : EpisodeAction

    data object UnmarkAsPlayed : EpisodeAction

    data object MarkAsFavorite : EpisodeAction

    data object UnmarkAsFavorite : EpisodeAction

    data object OnBackClick : EpisodeAction

    data object OnHomeClick : EpisodeAction

    /** 点击演员，由界面直接跳转到按该人物筛选的作品列表（不再经过人物详情页）。 */
    data class NavigateToPerson(val person: FindroidItemPerson) : EpisodeAction

    data class NavigateToSeason(val seasonId: UUID) : EpisodeAction
}
