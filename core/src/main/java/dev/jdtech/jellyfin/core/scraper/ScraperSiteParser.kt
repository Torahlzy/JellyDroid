package dev.jdtech.jellyfin.core.scraper

/**
 * 站点解析的共用入口。
 *
 * [MetadataScraperImpl]（只取文字）与 [ImageScraperImpl]（只取图片）走的是同一批站点、同一个页面解析，
 * 差别只在拿到 [ScrapedMovie] 之后做什么，因此站点顺序、解析分派与失败原因的写法集中放在这里。
 */

/** 在本地配置里填了地址、可以尝试抓取的站点，按 [ScraperSite] 的声明顺序排列。 */
internal val configuredScraperSites: List<Pair<ScraperSite, String>>
    get() = ScraperSite.entries.mapNotNull { site -> site.baseUrl?.let { url -> site to url } }

/** 用 [site] 对应的抓取器解析 [keyword]，返回页面上的影片信息（含图片地址）。 */
internal suspend fun ScraperSite.parseMovie(
    keyword: String,
    baseUrl: String,
    http: ScraperHttp,
): ScrapedMovie =
    when (this) {
        ScraperSite.JAVDB -> JavDbScraper(http, baseUrl).parse(keyword)
        ScraperSite.JAVBUS -> JavBusScraper(http, baseUrl).parse(keyword)
        ScraperSite.JAV321 -> Jav321Scraper(http, baseUrl).parse(keyword)
    }

/** 失败原因优先用异常自带的说明；没有说明时至少给个能看懂的类型名，而不是光秃秃一个类名。 */
internal fun Throwable.readableReason(): String =
    message?.takeIf { it.isNotBlank() } ?: "未知错误（${javaClass.simpleName}）"
