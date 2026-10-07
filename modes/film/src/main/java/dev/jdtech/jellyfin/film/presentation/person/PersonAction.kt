package dev.jdtech.jellyfin.film.presentation.person

import dev.jdtech.jellyfin.models.FindroidItem

sealed interface PersonAction {
    data object NavigateBack : PersonAction

    data object NavigateHome : PersonAction

    data class NavigateToItem(val item: FindroidItem) : PersonAction

    /** 查看该人物的全部作品，由界面跳转到按人物筛选的结果页。 */
    data object NavigateToAllItems : PersonAction
}
