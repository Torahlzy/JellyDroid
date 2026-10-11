package dev.jdtech.jellyfin.core.scraper

/**
 * 单个站点的文字抓取结果。
 *
 * 抓取时会把配了地址的站点**同时**尝试一遍（而不是第一个成功就收场），界面据此按站点切换内容：
 * 成功的站点在「编辑 nfo」弹窗里给出一个可点的选项，失败的只把原因留在抓取进度里。
 */
sealed interface SiteMetadataScrapeResult {
    val site: ScraperSite

    /** 该站点抓到了影片信息。 */
    data class Success(
        override val site: ScraperSite,
        val movie: ScrapedMovie,
    ) : SiteMetadataScrapeResult

    /** 该站点没抓到（没找到、被封锁、页面结构变化、网络错误…），[reason] 为可读原因。 */
    data class Failure(override val site: ScraperSite, val reason: String) : SiteMetadataScrapeResult

    companion object {
        /** 站点还没出结果就被用户取消时的失败原因；取消发生在站点请求之外，没有异常可转述。 */
        const val CANCELLED_REASON = "用户已取消"
    }
}
