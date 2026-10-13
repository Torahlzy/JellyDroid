package dev.jdtech.jellyfin.core.scraper

import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ScraperUrlProvider] 的默认实现：用户在「更多」菜单里按站点 key 填写的自定义网址优先，
 * 没填才回退到打包配置（BuildConfig，仅 debug 包带入真实值，release / staging 恒为空）。
 */
@Singleton
class ScraperUrlProviderImpl @Inject constructor(
    private val appPreferences: AppPreferences,
) : ScraperUrlProvider {
    override fun baseUrl(site: ScraperSite): String? {
        // 与 ScraperSite.baseUrl 相同的归一化：去空白与结尾斜杠，空串视为未配置
        val fromPreferences =
            appPreferences.getValue(appPreferences.scrapeSiteUrl(site.key)).trim().trimEnd('/')
        return fromPreferences.takeIf { it.isNotEmpty() } ?: site.baseUrl
    }
}
