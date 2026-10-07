package dev.jdtech.jellyfin.core.scraper

/** 抓取过程中的进度，供界面展示「当前在抓哪个站、哪个站成功」。 */
sealed interface ScrapeProgress {
    /** 开始从 [site] 抓取。 */
    data class Started(val site: ScraperSite) : ScrapeProgress

    /** 已从 [site] 抓到信息。 */
    data class Succeeded(val site: ScraperSite) : ScrapeProgress

    /** [site] 抓取失败，[reason] 为失败原因；失败后会自动尝试下一个站点。 */
    data class Failed(val site: ScraperSite, val reason: String) : ScrapeProgress

    /** 所有站点都没在本地配置里填地址，压根没发起过请求，与 [Failed] 区分开。 */
    data object NotConfigured : ScrapeProgress
}
