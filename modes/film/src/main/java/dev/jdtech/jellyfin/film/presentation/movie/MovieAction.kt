package dev.jdtech.jellyfin.film.presentation.movie

import dev.jdtech.jellyfin.core.scraper.ActorImageScrapeResult
import dev.jdtech.jellyfin.core.scraper.ScrapedImage
import dev.jdtech.jellyfin.models.FindroidItemImage
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.ItemMetadataEdit
import dev.jdtech.jellyfin.settings.domain.models.TranslateSettings

sealed interface MovieAction {
    data class Play(val startFromBeginning: Boolean = false) : MovieAction

    data class PlayTrailer(val trailer: String) : MovieAction

    data object MarkAsPlayed : MovieAction

    data object UnmarkAsPlayed : MovieAction

    data object MarkAsFavorite : MovieAction

    data object UnmarkAsFavorite : MovieAction

    data object OnBackClick : MovieAction

    data object OnHomeClick : MovieAction

    /** 点击演员，由界面直接跳转到按该人物筛选的作品列表（不再经过人物详情页）。 */
    data class NavigateToPerson(val person: FindroidItemPerson) : MovieAction

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

    /** 以 [keyword] 为关键词逐个站点抓取横图与竖图，抓到的图片先留在内存里等用户逐站上传。 */
    data class ScrapeItemImages(val keyword: String) : MovieAction

    /** 关闭抓取结果弹窗：中断进行中的抓取并丢掉已抓到的图片。 */
    data object CloseImageScrape : MovieAction

    /** 把 [images]（某个站点抓到的横图 / 竖图）上传到服务器。 */
    data class UploadScrapedImages(val images: List<ScrapedImage>) : MovieAction

    /** 按当前影片的演员名逐人抓取头像，结果留在内存里等用户逐人上传。 */
    data object ScrapeActorImages : MovieAction

    /**
     * 把 [images]（抓到的演员头像）上传为对应人物在服务器上的封面。
     *
     * 传整个列表即「全部上传」，传单个即逐行上传；调用方负责过滤掉已有头像的人物。
     */
    data class UploadActorImages(
        val images: List<ActorImageScrapeResult.Success>,
    ) : MovieAction

    /** 关闭演员头像弹窗：中断进行中的抓取并丢掉已抓到的头像（都只在内存里）。 */
    data object CloseActorImageScrape : MovieAction

    /** 保存抓取使用的本地代理地址，[address] 为空表示直连。 */
    data class UpdateScrapeProxy(val address: String) : MovieAction

    /** 保存翻译设置，决定之后的抓取要不要、以及怎么自动翻译。 */
    data class UpdateTranslateSettings(val settings: TranslateSettings) : MovieAction

    /**
     * 保存翻译设置，并把编辑 nfo 弹窗里当前的内容（[metadata]）翻一遍再回填。
     *
     * 设置与内容一起传：设置先落本地、再按同一份设置翻译，避免中间隔着一次状态更新。
     */
    data class TranslateMetadata(
        val metadata: ItemMetadataEdit,
        val settings: TranslateSettings,
    ) : MovieAction

    /** 删除服务器上的条目本身，含视频文件与关联的封面、nfo。 */
    data object DeleteItemWithFiles : MovieAction
}
