package dev.jdtech.jellyfin.core.scraper

import org.jsoup.Jsoup

/**
 * JavBus 抓取器，移植自 JavSP 的 `javsp/web/javbus.py`。
 *
 * 两个站点特性必须保留：
 * 1. 站点按 Accept-Language 决定返回哪种语言，缺这个头会被重定向到年龄验证页；
 * 2. 检测到爬虫时会先回 302，但重定向前的响应里就已经带着影片数据，要优先用那一份。
 */
class JavBusScraper(private val http: ScraperHttp, private val baseUrl: String) {

    suspend fun parse(dvdId: String): ScrapedMovie {
        val url = "$baseUrl/$dvdId"
        val first = http.get(url, HEADERS)
        val page =
            when {
                first.isRedirect && first.body.isNotBlank() -> JavBusPage(first.body, url)
                first.isRedirect -> http.getFollowingRedirects(url, HEADERS).toPage()
                else -> first.toPage()
            }

        if (page.url.contains("/doc/driver-verify")) {
            throw ScraperException.SiteBlocked("JavBus: 命中年龄验证页，需要先在浏览器中通过验证")
        }
        val html = Jsoup.parse(page.body, page.url)
        // 引入登录验证后状态码不可靠，只能靠页面标题判断是否 404 / 年龄验证
        val pageTitle = html.selectFirst("head > title")?.text().orEmpty()
        when {
            pageTitle.startsWith("404 Page Not Found!") ->
                throw ScraperException.MovieNotFound("JavBus: 未找到影片 '$dvdId'")
            pageTitle.startsWith("Age Verification") ->
                throw ScraperException.SiteBlocked("JavBus: 命中年龄验证页，需要先在浏览器中通过验证")
        }

        val container =
            html.selectFirst("div.container")
                ?: throw ScraperException.WebsiteError("JavBus: 页面结构变化，找不到详情容器")
        val title =
            container.selectFirst("h3")?.text()?.trim().orEmpty().takeIf { it.isNotBlank() }
                ?: throw ScraperException.WebsiteError("JavBus: 页面没有标题")
        val info =
            container.selectFirst("div.col-md-3.info")
                ?: throw ScraperException.WebsiteError("JavBus: 页面结构变化，找不到信息面板")

        return ScrapedMovie(
            site = ScraperSite.JAVBUS,
            // 站点标题里带着番号，原样保留：番号是识别影片的关键，剥掉后还得从文件名里猜回来
            title = title,
            originalTitle = null,
            plot = null,
            genres = container.select("span.genre label a").texts(),
            producer = info.valueAfterLabel("製作商:", "制作商:"),
            // 站点用 0000-00-00 表示未知日期，此时不填
            publishDate =
                info.valueAfterLabel("發行日期:", "发行日期:")?.takeIf { it != "0000-00-00" },
            score = null,
            url = url,
            // 图片取法与 JavSP 一致：封面是左侧大图，剧照取瀑布流里的第一张，原图地址挂在 href 上。
            // 数字版影片上两者都是横图（封面实测 800×535、剧照 800×450），谁当海报由 ImageScraper 按比例决定
            coverUrl = html.selectFirst("a.bigImage img")?.imageUrl(),
            previewUrl = html.selectFirst("div#sample-waterfall > a")?.imageLink(),
        )
    }

    private fun ScraperResponse.toPage(): JavBusPage =
        requireSuccess("JavBus").let { JavBusPage(it.body, it.url) }

    private data class JavBusPage(val body: String, val url: String)

    private companion object {
        val HEADERS =
            mapOf("Accept-Language" to "zh-CN,zh;q=0.9,zh-TW;q=0.8,en;q=0.7,ja;q=0.6")
    }
}
