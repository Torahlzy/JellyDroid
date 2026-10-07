package dev.jdtech.jellyfin.core.scraper

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Jav321 抓取器，移植自 JavSP 的 `javsp/web/jav321.py`。
 *
 * 通过 POST `/search` 提交番号，返回的搜索页里已经含有影片详情，无需二次请求。
 * 该站点的字段名用 `<b>` 标注、取值直接跟在标签后面（如 `<b>品番</b>: ABC-123`）。
 */
class Jav321Scraper(private val http: ScraperHttp, private val baseUrl: String) {

    suspend fun parse(dvdId: String): ScrapedMovie {
        val response =
            http.post("$baseUrl/search", mapOf("sn" to dvdId)).requireSuccess("Jav321")
        val html = Jsoup.parse(response.body, baseUrl)

        // 搜索结果下拉框里挂着影片页链接，链接末尾就是影片 ID；仍是 search 说明没搜到
        val pageUrl =
            html.selectFirst("ul.dropdown-menu li a")?.attr("href").orEmpty().takeIf {
                it.isNotBlank()
            } ?: throw ScraperException.MovieNotFound("Jav321: 未找到影片 '$dvdId'")
        if (pageUrl.substringAfterLast('/') == "search") {
            throw ScraperException.MovieNotFound("Jav321: 未找到影片 '$dvdId'")
        }

        val info =
            html.selectFirst("div.col-md-9")
                ?: throw ScraperException.WebsiteError("Jav321: 页面结构变化，找不到信息面板")
        val title =
            html.selectFirst("div.panel-heading h3")?.text()?.trim().orEmpty().takeIf {
                it.isNotBlank()
            } ?: throw ScraperException.WebsiteError("Jav321: 页面没有标题")

        return ScrapedMovie(
            site = ScraperSite.JAV321,
            // 该站点的标题本身就把番号写在开头，JavSP 也原样保留
            title = title,
            originalTitle = null,
            plot = readPlot(info),
            genres = info.select("a[href*='/genre/']").texts(),
            producer = info.selectFirst("a[href*='/company/']")?.text()?.trim()?.takeIf {
                it.isNotEmpty()
            },
            publishDate = info.bTagValue("配信開始日"),
            score = readScore(info),
            url = pageUrl,
        )
    }

    /** 简介是 `panel-body` 里那一格 div 的直接文本，取 ownText 以免把子元素的文本也带上。 */
    private fun readPlot(info: Element): String? =
        info.selectFirst("div.panel-body > div.row > div.col-md-12")?.ownText()?.trim()?.takeIf {
            it.isNotEmpty()
        }

    /** 评分只有星级图片（如 `/img/35.gif` 表示 3.5 星），图片序号除以 5 即 10 分制评分。 */
    private fun readScore(info: Element): Float? {
        val image =
            info.select("b").firstOrNull { it.text().trim() == "平均評価" }?.nextElementSibling()
                ?: return null
        val source = image.attr("data-original")
        if (source.length < 7) return null
        return source.substring(5, 7).toIntOrNull()?.div(5f)
    }
}
