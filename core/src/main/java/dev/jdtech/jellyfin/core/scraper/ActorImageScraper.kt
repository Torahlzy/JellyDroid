package dev.jdtech.jellyfin.core.scraper

/**
 * 按演员名抓取头像。
 *
 * 与 [ImageScraper]（按番号抓影片封面）不同，这里不依赖具体影片：
 * 输入一批演员名，逐人给出头像或失败原因。抓到的图片只在内存里，
 * 由调用方决定是否通过 `setItemImage` 上传到服务器的人物条目上。
 */
interface ActorImageScraper {
    suspend fun scrapeActorImages(
        names: List<String>,
        onResult: suspend (ActorImageScrapeResult) -> Unit,
    ): List<ActorImageScrapeResult>
}
