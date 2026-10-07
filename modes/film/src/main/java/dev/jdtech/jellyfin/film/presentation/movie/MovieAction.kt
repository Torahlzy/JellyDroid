package dev.jdtech.jellyfin.film.presentation.movie

import dev.jdtech.jellyfin.models.FindroidItemImage
import dev.jdtech.jellyfin.models.ItemMetadataEdit
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

    /** 用 [metadata] 覆盖服务器上该条目的 nfo（元数据）。 */
    data class UpdateItemMetadata(val metadata: ItemMetadataEdit) : MovieAction

    /** 以 [keyword]（通常是番号）为关键词到各站点抓取 nfo 元数据。 */
    data class ScrapeMetadata(val keyword: String) : MovieAction

    /** 用户确认了抓取失败提示，清掉本次抓取的进度记录。 */
    data object DismissScrapeFailure : MovieAction

    /** 用户中途取消了抓取。 */
    data object CancelScrape : MovieAction

    /** 保存抓取使用的本地代理地址，[address] 为空表示直连。 */
    data class UpdateScrapeProxy(val address: String) : MovieAction

    /** 删除服务器上的条目本身，含视频文件与关联的封面、nfo。 */
    data object DeleteItemWithFiles : MovieAction
}
