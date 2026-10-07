package dev.jdtech.jellyfin.core.scraper

/**
 * 按站点顺序抓取影片元数据。
 *
 * 只抓文字信息，不下载封面等图片；界面层负责把抓到的内容填进「编辑 nfo」表单，
 * 是否写入服务器由用户确认后再走既有的编辑流程。
 */
interface MetadataScraper {
    /**
     * 用 [keyword]（通常是番号）逐个站点尝试抓取。
     *
     * @param onProgress 每进入一个站点、每次有结果时回调一次，供界面展示执行步骤。
     *   声明为挂起是为了让调用方能在回调里直接更新状态流。
     * @return 抓到的影片信息；所有站点都失败时返回 null，各站点的失败原因已通过
     *   [onProgress] 以 [ScrapeProgress.Failed] 上报。
     */
    suspend fun scrape(
        keyword: String,
        onProgress: suspend (ScrapeProgress) -> Unit,
    ): ScrapedMovie?
}
