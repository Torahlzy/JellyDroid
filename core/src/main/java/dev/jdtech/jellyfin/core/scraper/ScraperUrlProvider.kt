package dev.jdtech.jellyfin.core.scraper

/** 提供各抓取站点实际使用的地址，文字、封面、头像抓取共用。 */
interface ScraperUrlProvider {
    /** [site] 的抓取地址；未配置时为 null，抓取时直接跳过该站点。 */
    fun baseUrl(site: ScraperSite): String?
}
