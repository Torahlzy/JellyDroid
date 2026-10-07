package dev.jdtech.jellyfin.core.scraper

/**
 * 单个站点的封面抓取结果。
 *
 * 抓取时会把配了地址的站点**全部**尝试一遍（而不是第一个成功就收场），界面按站点逐行展示：
 * 失败的给出原因，成功的给出可上传的图片。
 */
sealed interface SiteImageScrapeResult {
    val site: ScraperSite

    /** 该站点抓到了至少一张图片。 */
    data class Success(
        override val site: ScraperSite,
        val images: List<ScrapedImage>,
    ) : SiteImageScrapeResult

    /** 该站点没抓到（没找到、被封锁、页面没有图片、网络错误…），[reason] 为可读原因。 */
    data class Failure(override val site: ScraperSite, val reason: String) : SiteImageScrapeResult
}
