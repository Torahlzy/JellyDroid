package dev.jdtech.jellyfin.core.scraper

/** 按演员名在单个站点抓头像的逐站点结果：[Success] 与 [Failure] 各对应界面上的一行。 */
sealed interface SiteActorImageScrapeResult {
    val site: ScraperSite

    /** 该站点抓到了头像字节，等待用户确认后上传。 */
    data class Success(override val site: ScraperSite, val bytes: ByteArray) :
        SiteActorImageScrapeResult

    /** 该站点抓不到头像，[reason] 说明原因，界面直接展示。 */
    data class Failure(override val site: ScraperSite, val reason: String) :
        SiteActorImageScrapeResult
}
