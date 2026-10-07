package dev.jdtech.jellyfin.core.scraper

import dev.jdtech.jellyfin.logging.AppLog
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * [MetadataScraper] 的默认实现：按 [ScraperSite] 的声明顺序串行尝试。
 *
 * 单个站点失败（没找到、被封锁、页面结构变化、网络错误）都只记一笔然后继续下一个，
 * 因为一个站点抓不到并不代表别的站点也抓不到；没在本地配置里填地址的站点直接跳过，不算失败。
 * 只有「配置过的站点全部失败」或「一个站点都没配置」才返回 null。
 */
class MetadataScraperImpl @Inject constructor(private val http: ScraperHttp) : MetadataScraper {

    override suspend fun scrape(
        keyword: String,
        onProgress: suspend (ScrapeProgress) -> Unit,
    ): ScrapedMovie? {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            AppLog.w("抓取关键词为空，直接放弃")
            return null
        }

        // 没填地址的站点直接跳过，不算「抓取失败」——否则用户会把本机没配置误判成站点故障；
        // 一个都没填时单独上报 NotConfigured，让界面给出「没配置」而不是「都失败了」的结论
        val sites = configuredScraperSites
        if (sites.isEmpty()) {
            AppLog.w("没有任何站点配置了地址，无法抓取")
            onProgress(ScrapeProgress.NotConfigured)
            return null
        }

        for ((site, baseUrl) in sites) {
            onProgress(ScrapeProgress.Started(site))
            try {
                val movie = site.parseMovie(trimmed, baseUrl, http)
                AppLog.i("从 %s 抓取 '%s' 成功", site.displayName, trimmed)
                onProgress(ScrapeProgress.Succeeded(site))
                return movie
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(e, "%s 抓取 '%s' 失败，尝试下一个站点", site.displayName, trimmed)
                onProgress(ScrapeProgress.Failed(site, e.readableReason()))
            }
        }
        return null
    }
}
