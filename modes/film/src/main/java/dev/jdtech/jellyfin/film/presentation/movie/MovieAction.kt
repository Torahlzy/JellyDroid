package dev.jdtech.jellyfin.film.presentation.movie

import dev.jdtech.jellyfin.models.FindroidItemImage
import java.util.UUID

sealed interface MovieAction {
    data class Play(val startFromBeginning: Boolean = false) : MovieAction

    data class PlayTrailer(val trailer: String) : MovieAction

    data object MarkAsPlayed : MovieAction

    data object UnmarkAsPlayed : MovieAction

    data object MarkAsFavorite : MovieAction

    data object UnmarkAsFavorite : MovieAction

    data object OnBackClick : MovieAction

    data object OnHomeClick : MovieAction

    data class NavigateToPerson(val personId: UUID) : MovieAction

    /** 删除服务器上该条目的指定封面图片。 */
    data class DeleteItemImages(val images: List<FindroidItemImage>) : MovieAction

    /** 重置服务器上该条目的 nfo（元数据）。 */
    data object ResetItemMetadata : MovieAction

    /** 删除服务器上的条目本身，含视频文件与关联的封面、nfo。 */
    data object DeleteItemWithFiles : MovieAction
}
