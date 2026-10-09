package dev.jdtech.jellyfin.film.presentation.actorimage

import dev.jdtech.jellyfin.core.scraper.SiteActorImageScrapeResult
import java.util.UUID

/** 「获取头像」弹窗的动作，电影详情页与人物列表页共用。 */
sealed interface ActorImageScrapeAction {
    /**
     * 打开弹窗并立即在所有可用站点上同时搜索演员（[personId] + [name]）的头像。
     *
     * [hasAvatar] 表示服务器上该人物是否已有头像：人物头像只能整体覆盖，界面据此提示用户。
     */
    data class Scrape(val personId: UUID, val name: String, val hasAvatar: Boolean) :
        ActorImageScrapeAction

    /** 把 [image]（某个站点抓到的头像）上传为对应人物在服务器上的封面。 */
    data class Upload(val image: SiteActorImageScrapeResult.Success) : ActorImageScrapeAction

    /** 保存抓取使用的本地代理地址，[address] 为空表示直连。 */
    data class UpdateProxy(val address: String) : ActorImageScrapeAction

    /** 关闭弹窗：中断进行中的抓取并丢掉已抓到的头像（都只在内存里）。 */
    data object Close : ActorImageScrapeAction
}
