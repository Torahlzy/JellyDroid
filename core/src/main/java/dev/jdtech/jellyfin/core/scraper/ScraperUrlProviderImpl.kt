package dev.jdtech.jellyfin.core.scraper

import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.Constants
import dev.jdtech.jellyfin.settings.domain.ScrapeSiteUrlsProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ScraperUrlProvider] 的默认实现：用户在「更多」菜单里按站点 key 填写的自定义网址优先，
 * 没填才回退到打包配置（BuildConfig，仅 debug 包带入真实值，release / staging 恒为空）。
 */
@Singleton
class ScraperUrlProviderImpl @Inject constructor(
    private val appPreferences: AppPreferences,
) : ScraperUrlProvider, ScrapeSiteUrlsProvider {
    override fun baseUrl(site: ScraperSite): String? {
        // 与 ScraperSite.baseUrl 相同的归一化：去空白与结尾斜杠，空串视为未配置
        val fromPreferences =
            appPreferences.getValue(appPreferences.scrapeSiteUrl(site.key)).trim().trimEnd('/')
        return fromPreferences.takeIf { it.isNotEmpty() } ?: site.baseUrl
    }

    /**
     * 「抓取网址」弹窗回填用（设置页与电影详情页共用）：与 [baseUrl] 同一套回退规则，
     * 把打包配置也算进来。站点清单以 [ScraperSite] 的枚举顺序为唯一来源，字幕站附在末尾；
     * 未配置的站点给空串而不是不放入 map，保证弹窗能列出所有站点。
     */
    override fun effectiveUrls(): Map<String, String> = buildMap {
        ScraperSite.entries.forEach { site -> put(site.key, baseUrl(site).orEmpty()) }
        put(Constants.SUBTITLE_SITE_KEY, appPreferences.subtitleSiteBaseUrl())
    }
}
