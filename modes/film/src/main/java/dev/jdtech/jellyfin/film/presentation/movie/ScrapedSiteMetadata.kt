package dev.jdtech.jellyfin.film.presentation.movie

import dev.jdtech.jellyfin.core.scraper.ScraperSite
import dev.jdtech.jellyfin.models.ItemMetadataEdit

/**
 * 某个站点抓到的内容，已经转成编辑表单、并按设置翻译好。
 *
 * 抓取时每个成功的站点都会备一份，「编辑 nfo」弹窗里点站点名即可切换成这一份；
 * 内容只在内存里，写入服务器仍走用户确认后的既有编辑流程。
 */
data class ScrapedSiteMetadata(val site: ScraperSite, val metadata: ItemMetadataEdit)
