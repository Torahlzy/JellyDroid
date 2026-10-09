package dev.jdtech.jellyfin.film.presentation.actorimage

import dev.jdtech.jellyfin.core.scraper.SiteActorImageScrapeResult
import dev.jdtech.jellyfin.core.scraper.ScraperSite
import java.util.UUID

/** 「获取头像」弹窗的状态，电影详情页与人物列表页共用。 */
data class ActorImageScrapeState(
    /** 弹窗当前演员的人物条目 Id，null 表示弹窗已关闭；上传头像时写到该人物上。 */
    val personId: UUID? = null,
    /** 弹窗当前演员的名字，抓取时作为站点搜索关键词。 */
    val personName: String = "",
    /** 服务器上该人物是否已有头像：人物头像只能整体覆盖，界面据此提示用户。 */
    val hasAvatar: Boolean = false,
    /** 可用来抓演员头像的站点（来自本地配置，整个会话内不变），供弹窗先把站点列出来。 */
    val sites: List<ScraperSite> = emptyList(),
    /**
     * 演员头像抓取的逐站点结果，按站点去重覆盖（同一站点只保留最新一条）。
     *
     * 抓到的头像字节只在内存里，关掉弹窗即丢弃。
     */
    val results: List<SiteActorImageScrapeResult> = emptyList(),
    /** 是否正在上传，上传期间禁用各行上传按钮，避免重复点。 */
    val isUploading: Boolean = false,
    /** 抓取使用的本地代理地址，留空表示直连；与 nfo / 封面抓取共用同一份配置。 */
    val proxy: String = "",
)
