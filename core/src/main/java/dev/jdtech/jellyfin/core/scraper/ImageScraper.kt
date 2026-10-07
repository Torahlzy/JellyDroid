package dev.jdtech.jellyfin.core.scraper

/**
 * 抓取影片封面图片（横图 + 竖图）。
 *
 * 与 [MetadataScraper] 走同一批站点、同一套页面解析，区别只在于这里把抓到的图片先下载到内存、
 * 交给界面预览与逐站上传；中途取消则什么都不留下。
 */
interface ImageScraper {
    /**
     * 在本地配置里填了地址、抓取时会被尝试的站点（按尝试顺序）。
     *
     * 界面据此先把要抓的站点列出来，再逐个填上结果；没有任何站点配置地址时为空。
     */
    val sites: List<ScraperSite>

    /**
     * 用 [keyword]（通常是番号）逐个站点抓取横图与竖图，**所有站点都会尝试**。
     *
     * @param onResult 每个站点出结果时回调一次，供界面逐条追加展示。
     * @return 各站点的结果，顺序与 [ScraperSite] 一致；为空数组表示没有任何站点配置了地址。
     */
    suspend fun scrapeImages(
        keyword: String,
        onResult: suspend (SiteImageScrapeResult) -> Unit,
    ): List<SiteImageScrapeResult>
}
