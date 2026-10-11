package dev.jdtech.jellyfin.core.scraper

import dev.jdtech.jellyfin.logging.AppLog
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [MetadataScraper] 的默认实现：各站点同时抓取同一个关键词。
 *
 * 单个站点失败（没找到、被封锁、页面结构变化、网络错误）只影响它自己，别的站点照常返回结果，
 * 用户在弹窗里挑一个可用的站点即可；没在本地配置里填地址的站点直接跳过，不算失败。
 * 只有「配置过的站点全部失败」或「一个站点都没配置」才返回空数组。
 */
class MetadataScraperImpl @Inject constructor(private val http: ScraperHttp) : MetadataScraper {

    /** 串行化 [onResult] 回调：各站点并发抓取，但回调逐个送达，调用方追加状态时无需考虑并发。 */
    private val resultMutex = Mutex()

    override val sites: List<ScraperSite>
        get() = configuredScraperSites.map { (site, _) -> site }

    override suspend fun scrape(
        keyword: String,
        onResult: suspend (SiteMetadataScrapeResult) -> Unit,
    ): List<SiteMetadataScrapeResult> {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            AppLog.w("抓取关键词为空，直接放弃")
            return emptyList()
        }

        val sites = configuredScraperSites
        if (sites.isEmpty()) {
            AppLog.w("没有任何站点配置了地址，无法抓取")
            return emptyList()
        }

        return coroutineScope {
            sites
                .map { (site, baseUrl) ->
                    async {
                        val result = scrapeOnSite(site, trimmed, baseUrl)
                        resultMutex.withLock { onResult(result) }
                        result
                    }
                }
                .awaitAll()
        }
    }

    /** 在单个站点上抓取；任何失败都转成该站点的失败结果，不往外抛，免得连坐其它站点。 */
    private suspend fun scrapeOnSite(
        site: ScraperSite,
        keyword: String,
        baseUrl: String,
    ): SiteMetadataScrapeResult =
        try {
            val movie = site.parseMovie(keyword, baseUrl, http)
            AppLog.i("从 %s 抓取 '%s' 成功", site.displayName, keyword)
            SiteMetadataScrapeResult.Success(site, movie)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(e, "%s 抓取 '%s' 失败", site.displayName, keyword)
            SiteMetadataScrapeResult.Failure(site, e.readableReason())
        }
}
