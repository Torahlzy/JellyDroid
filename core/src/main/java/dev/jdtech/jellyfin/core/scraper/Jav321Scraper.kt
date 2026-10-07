package dev.jdtech.jellyfin.core.scraper

import dev.jdtech.jellyfin.logging.AppLog
import kotlinx.coroutines.CancellationException
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Jav321 抓取器，移植自 JavSP 的 `javsp/web/jav321.py`。
 *
 * 通过 POST `/search` 提交番号：命中影片时站点会 301 跳到详情页，因此必须跟着跳转，
 * 否则只能拿到一个没有正文的裸跳转（JavSP 用 `requests` 默认就会跟随）。
 * 该站点的字段名用 `<b>` 标注、取值直接跟在标签后面（如 `<b>品番</b>: ABC-123`）。
 *
 * 站点会按 Cookie 判定访客是否正常（先访问首页才拿到 `is_loyal`），因此搜索前先 [warmUp] 一次首页。
 */
class Jav321Scraper(private val http: ScraperHttp, private val baseUrl: String) {

    suspend fun parse(dvdId: String): ScrapedMovie {
        // 先按浏览器的路子访问一次首页：站点会给正常访客发 Cookie，少了它后续请求更容易被判成爬虫
        warmUp()
        val response =
            http.postFollowingRedirects(
                "$baseUrl/search",
                mapOf("sn" to dvdId),
                // 真实浏览器的搜索表单是从首页提交的，带上 Referer
                headers = mapOf("Referer" to "$baseUrl/"),
            )
        // 同域跳转（命中影片 → /video/{cid}）已经跟完了，剩下的跳转都是跨域：站点按客户端特征
        // 把访客跳走（如手机 UA 会被跳到 xiazai.it.com 这类下载 / 广告域名），跟过去拿不到内容，
        // 直接把原因说出来，别让它变成一个要等读超时、还看不懂的「非预期状态码 302」
        if (response.isRedirect) {
            throw ScraperException.SiteBlocked(
                "Jav321: 请求被跨域重定向到 ${response.location}，未继续跟随（站点按客户端特征把访客跳走）"
            )
        }
        response.requireSuccess("Jav321")
        // 命中影片会被跳到 /video/{cid}；没跳转说明还停留在搜索页，即没找到这部影片
        if (response.url.substringAfterLast('/').startsWith("search")) {
            throw ScraperException.MovieNotFound("Jav321: 未找到影片 '$dvdId'")
        }
        val pageUrl = response.url
        val html = Jsoup.parse(response.body, pageUrl)

        val info =
            html.selectFirst("div.col-md-9")
                ?: throw ScraperException.WebsiteError("Jav321: 页面结构变化，找不到信息面板")
        // h3 形如「片名 女优名<small>番号 女优名</small>」：只能取直接文本（ownText），
        // 用 text() 会把 <small> 里的番号与女优名再拼一遍，标题就变成「片名 女优名 番号 女优名」
        val rawTitle =
            html.selectFirst("div.panel-heading h3")?.ownText()?.trim().orEmpty().takeIf {
                it.isNotBlank()
            } ?: throw ScraperException.WebsiteError("Jav321: 页面没有标题")
        // 番号在「品番」字段里，站点给的是小写，统一成大写，与其它站点及识别出的关键词一致
        val number = info.bTagValue("品番")?.uppercase()

        val gallery =
            html.select("div.col-xs-12.col-md-12 p a img.img-responsive").mapNotNull {
                it.imageUrl()
            }

        return ScrapedMovie(
            site = ScraperSite.JAV321,
            number = number,
            title = stripTrailingActors(rawTitle, info.select("a[href*='/star/']").texts()),
            originalTitle = null,
            plot = readPlot(info),
            genres = info.select("a[href*='/genre/']").texts(),
            producer = info.selectFirst("a[href*='/company/']")?.text()?.trim()?.takeIf {
                it.isNotEmpty()
            },
            publishDate = info.bTagValue("配信開始日"),
            score = readScore(info),
            url = pageUrl,
            // 封面取信息面板左侧的小图（DMM 的 ps.jpg，实测 147×200 竖图），与信息栏同属一个 row，
            // 用 info 定位可避开侧栏的推荐图；旧布局取不到时退回图集第一张
            coverUrl =
                info.parent()?.selectFirst("div.col-md-3 img.img-responsive")?.imageUrl()
                    ?: gallery.firstOrNull(),
            // 剧照取图集第一张：就是这张片子的横版封面（DMM 的 pl.jpg，实测 800×535），正是要的背景图。
            // 它与左侧的竖图是同一张画面的两种裁切，谁进哪个槽位不按位置、由 ImageScraper 按比例决定
            previewUrl = gallery.firstOrNull(),
        )
    }

    /**
     * 访问一次首页，只为让站点把「正常访客」的 Cookie 发下来（jav321 是 `is_loyal`）。
     *
     * Cookie 由 [ScraperHttp] 的 Cookie 存储负责保存，后续搜索请求会自动带上。
     * 这次访问的正文不解析，失败也无所谓：顶多是后面的搜索用不上 Cookie，照旧会给出失败原因。
     */
    private suspend fun warmUp() {
        try {
            http.get("$baseUrl/")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(e, "Jav321: 预热访问首页失败，继续尝试搜索")
        }
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
