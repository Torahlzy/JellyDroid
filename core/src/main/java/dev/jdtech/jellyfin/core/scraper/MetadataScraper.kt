package dev.jdtech.jellyfin.core.scraper

/**
 * 抓取影片的文字元数据。
 *
 * 把配了地址的站点**同时**抓一遍，各站结果都带回来（而不是第一个成功就收场），
 * 界面层据此让用户在站点之间切换内容；是否写入服务器由用户确认后再走既有的编辑流程。
 * 只抓文字，不下载封面等图片（图片见 [ImageScraper]）。
 */
interface MetadataScraper {
    /**
     * 在本地配置里填了地址、抓取时会被尝试的站点（按 [ScraperSite] 的声明顺序）。
     *
     * 界面据此先把要抓的站点列出来，再逐个填上结果；没有任何站点配置地址时为空。
     */
    val sites: List<ScraperSite>

    /**
     * 用 [keyword]（通常是番号）在所有站点上同时抓取。
     *
     * @param onResult 每个站点出结果时回调一次（各站并发，回调逐个送达），供界面逐条展示。
     * @return 各站点的结果，顺序与 [sites] 一致；为空数组表示没有任何站点配置了地址。
     */
    suspend fun scrape(
        keyword: String,
        onResult: suspend (SiteMetadataScrapeResult) -> Unit,
    ): List<SiteMetadataScrapeResult>
}
