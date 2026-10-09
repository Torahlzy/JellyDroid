package dev.jdtech.jellyfin.core.scraper

/**
 * 按演员名抓取头像。
 *
 * 与 [ImageScraper]（按番号抓影片封面）不同，这里不依赖具体影片：输入一个演员名，
 * 各站点同时搜索她的头像。抓到的图片只在内存里，由调用方决定是否通过
 * `setItemImage` 上传到服务器的人物条目上。
 */
interface ActorImageScraper {
    /**
     * 在本地配置里填了地址、且支持按名字搜女优的站点（按尝试顺序）。
     *
     * 界面据此先把要抓的站点列出来，再逐个填上结果；没有站点配置地址时为空。
     */
    val sites: List<ScraperSite>

    /**
     * 在所有可用站点上**同时**搜索 [name] 的头像并下载。
     *
     * @param onResult 每个站点出结果时回调一次，供界面逐条追加展示。
     * @return 各站点的结果，顺序与 [sites] 一致；为空数组表示没有任何可用站点。
     */
    suspend fun scrapeActorImage(
        name: String,
        onResult: suspend (SiteActorImageScrapeResult) -> Unit,
    ): List<SiteActorImageScrapeResult>
}
