package dev.jdtech.jellyfin.core.scraper

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * JavDB 抓取器，移植自 JavSP 的 `javsp/web/javdb.py`。
 *
 * JavDB 用 CloudFlare 挡爬虫，JavSP 靠 cloudscraper + 浏览器 Cookies 绕过；
 * 本应用两样都没有，只能带浏览器 UA 直连，被拦截时按 [ScraperException.SiteBlocked] 上报，
 * 由上层换下一个站点。
 */
class JavDbScraper(private val http: ScraperHttp, private val baseUrl: String) {

    suspend fun parse(dvdId: String): ScrapedMovie {
        val searchUrl = "$baseUrl/search?q=$dvdId"
        val searchHtml = getHtml(searchUrl)

        // 搜索会返回多条结果，只认番号完全一致的那条；命中多条说明无法确定是哪一部，直接放弃。
        // 番号与链接在同一个 a.box 容器里，按容器整体取值，避免两次 select 的下标错位
        val matches =
            searchHtml.select("a.box").filter { box ->
                box.selectFirst("div.video-title > strong")?.text()?.trim()
                    .equals(dvdId, ignoreCase = true)
            }
        if (matches.isEmpty()) {
            throw ScraperException.MovieNotFound("JavDB: 未找到影片 '$dvdId'")
        }
        if (matches.size > 1) {
            throw ScraperException.MovieNotFound("JavDB: '$dvdId' 有多个完全匹配的搜索结果")
        }
        // 解析搜索页时带了 baseUri，absUrl 会把站内相对链接补成绝对地址
        val movieUrl =
            matches.first().absUrl("href").takeIf { it.isNotBlank() }
                ?: throw ScraperException.WebsiteError("JavDB: 搜索结果缺少影片链接")

        val html = getHtml(movieUrl)
        val container =
            html.selectFirst("div.video-detail")
                ?: throw ScraperException.WebsiteError("JavDB: 影片页结构变化，找不到详情容器")
        val info =
            container.selectFirst("nav.movie-panel-info")
                ?: throw ScraperException.WebsiteError("JavDB: 影片页结构变化，找不到信息面板")
        val title =
            container.selectFirst("h2 strong.current-title")?.text()?.trim().orEmpty().takeIf {
                it.isNotBlank()
            } ?: throw ScraperException.WebsiteError("JavDB: 影片页没有标题")
        // 番号在标题同级的另一个 <strong> 里（形如 `<strong>SSIS-001 </strong>`），单独取出来；
        // 不能走 valueAfterLabel：番號那一行紧跟的是空文本节点，取出来会是一个不换行的空格
        val number =
            container.selectFirst("h2 strong:not(.current-title)")?.text()?.trim()?.takeIf {
                it.isNotEmpty()
            }

        return ScrapedMovie(
            site = ScraperSite.JAVDB,
            number = number,
            // 片名里不含番号，剥掉尾部的女优名即可，番号由上层拼在片名前
            title = stripTrailingActors(stripNumberPrefix(title, number), readActressNames(info)),
            originalTitle =
                container.selectFirst("h2 span.origin-title")?.text()?.trim()?.takeIf {
                    it.isNotEmpty()
                },
            plot = null,
            genres = readGenres(info),
            producer = info.valueAfterLabel("片商:", "賣家:"),
            publisher = info.valueAfterLabel("發行:", "发行:"),
            actresses = readActressNames(info),
            directors = listOfNotNull(info.valueAfterLabel("導演:", "导演:")),
            publishDate = info.valueAfterLabel("日期:"),
            score = readScore(html),
            url = movieUrl,
            // 图片取法与 JavSP 一致（它的 xpath 以 `//` 开头，是在整页里找）：
            // 封面是详情页的 video-cover，图集第一张的原图地址挂在 a.tile-item 的 href 上。
            // 注意两者比例和名字是反的：该站点封面实测 800×535（横），图集第一张实测 147×200（竖），
            // 谁当海报、谁当背景由 ImageScraper 按真实比例决定
            coverUrl = html.selectFirst("img.video-cover")?.imageUrl(),
            previewUrl = html.selectFirst("a.tile-item[data-fancybox='gallery']")?.imageLink(),
        )
    }

    /** 类别标签挂在「類別:」所在 div 的 span 里，取值方式和普通字段不同，单独处理。 */
    private fun readGenres(info: Element): List<String> {
        val category = info.findLabel("類別:", "类别:") ?: return emptyList()
        val value = category.parent() ?: return emptyList()
        return value.select("span a").texts()
    }

    /** 女优名供清理标题尾部使用；站点用 `actor-female` 标女优，男优不带这个 class。 */
    private fun readActressNames(info: Element): List<String> = info.select("a.actor-female").texts()

    /** 评分形如 `4.5分, 由 123 人評價`，站点给的是 5 分制，乘 2 统一到 10 分制。 */
    private fun readScore(html: Document): Float? {
        val star = html.selectFirst("span.score-stars") ?: return null
        val match = SCORE_PATTERN.find(star.nextSiblingText().orEmpty()) ?: return null
        return match.groupValues[1].toFloatOrNull()?.times(2)
    }

    private suspend fun getHtml(url: String): Document {
        val response = http.getFollowingRedirects(url, HEADERS)
        val finalUrl = response.url
        return when (response.code) {
            200 -> {
                // 未登录 / 凭据失效会被跳到登录页，VIP 资源会被跳到支付页
                if (finalUrl.contains("/login")) {
                    throw ScraperException.SitePermissionDenied("JavDB: 需要登录才能访问")
                }
                if (finalUrl.substringAfterLast('/').startsWith("pay")) {
                    throw ScraperException.SitePermissionDenied("JavDB: 该资源仅 VIP 可见")
                }
                Jsoup.parse(response.body, finalUrl)
            }
            403, 503 ->
                throw ScraperException.SiteBlocked(
                    "JavDB: ${response.code} 禁止访问（疑似被 CloudFlare 拦截）"
                )
            else -> throw ScraperException.WebsiteError("JavDB: 非预期状态码 ${response.code}")
        }
    }

    private companion object {
        /** 站点按 Accept-Language 返回对应语言，缺省时可能落到其他语言页面导致解析错位。 */
        val HEADERS =
            mapOf(
                "Accept-Language" to "zh-CN,zh;q=0.9,zh-TW;q=0.8,en-US;q=0.7,en;q=0.6,ja;q=0.5"
            )

        val SCORE_PATTERN = Regex("([\\d.]+)分")
    }
}
