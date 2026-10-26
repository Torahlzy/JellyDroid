package dev.jdtech.jellyfin.core.comment

import dev.jdtech.jellyfin.core.scraper.JavDbScraper
import dev.jdtech.jellyfin.core.scraper.ScraperException
import dev.jdtech.jellyfin.core.scraper.ScraperHttp
import dev.jdtech.jellyfin.core.scraper.ScraperSite
import dev.jdtech.jellyfin.core.scraper.ScraperUrlProvider
import dev.jdtech.jellyfin.logging.AppLog
import javax.inject.Inject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * 从 JavDB 取短评。
 *
 * 影片详情页（`/v/{影片 id}`）本身不渲染短评，短评由「短評」标签异步加载：
 * `GET /v/{影片 id}/reviews/lastest` 返回一段 HTML 片段（无需登录、无需接口签名），条目结构为
 *
 * ```
 * dl.review-items
 *   dt.review-item#review-item-<id>          ← 一条短评
 *     div.review-title                        ← 操作按钮 + 用户名（裸文本） + 星级 + 日期
 *     div.content > p                         ← 发言正文
 *   dt.review-item.more                      ← 「更多短評可成為VIP 或至官方App中查看」
 * ```
 *
 * 注意站点对未登录 / 非 VIP 的网页端**只给最新 3 条**（`?page=` 会被忽略，其余条目要 VIP
 * 或官方 App 才可见），因此这里拿到多少展示多少；[MovieComment] 的说明里不隐含「全量」语义。
 */
class JavDbCommentSource
@Inject
constructor(
    private val http: ScraperHttp,
    private val scraperUrlProvider: ScraperUrlProvider,
) : MovieCommentSource {

    override suspend fun load(keyword: String, useProxy: Boolean): List<MovieComment> {
        val baseUrl =
            scraperUrlProvider.baseUrl(ScraperSite.JAVDB)
                ?: throw ScraperException.WebsiteError("JavDB: 没有配置站点网址")
        // 短评页挂在影片详情页地址下，番号 → 地址的搜索逻辑复用元数据抓取那一套
        val movieUrl = JavDbScraper(http, baseUrl, useProxy).resolveMovieUrl(keyword)
        val reviewsUrl = movieUrl.trimEnd('/') + REVIEWS_PATH

        val response = http.getFollowingRedirects(reviewsUrl, HEADERS, useProxy)
        if (response.code !in 200..299) {
            throw ScraperException.WebsiteError("JavDB: 短评页返回 ${response.code}")
        }
        if (response.url.contains("/login")) {
            throw ScraperException.SitePermissionDenied("JavDB: 需要登录才能查看短评")
        }

        val comments = parse(Jsoup.parse(normalizeBody(response.body), reviewsUrl))
        AppLog.d("JavDB 短评 '%s'：%d 条", keyword, comments.size)
        return comments
    }

    /**
     * 站点对同一个地址有两种响应：带 `X-Requested-With` 的 XHR 请求会把片段包成
     * `(function(){ … innerHTML = "<article …>" … })()`，正文里的引号与斜杠都做了转义。
     * 请求头一旦被中间层加上（部分代理会补），解析就会失败，因此这里先把这个壳拆掉。
     */
    private fun normalizeBody(body: String): String =
        if (body.contains(REVIEW_ITEMS_CLASS) && body.contains(JS_WRAPPER_MARKER)) {
            body
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                // 转义后的换行 / 制表符不解出来的话会变成用户名字符串的一部分（形如 `\n  mo***a`）
                .replace("\\n", " ")
                .replace("\\r", " ")
                .replace("\\t", " ")
        } else {
            body
        }

    private fun parse(document: Document): List<MovieComment> =
        document.select("dl.review-items > dt.review-item").mapNotNull { element ->
            // 末条 `dt.review-item.more` 是「更多短評需 VIP / 官方 App」的提示，不是短评
            if (element.hasClass(MORE_CLASS)) return@mapNotNull null
            val content = element.selectFirst("div.content")?.text()?.trim().orEmpty()
            // 只有星级没有正文的条目对评论区没有意义，直接丢掉
            if (content.isEmpty()) return@mapNotNull null
            MovieComment(
                id = element.id().removePrefix(REVIEW_ELEMENT_ID_PREFIX).ifEmpty { content },
                userName = readUserName(element),
                content = content,
                date = element.selectFirst("span.time")?.text()?.trim()?.takeIf { it.isNotEmpty() },
                likes = element.selectFirst("span.likes-count")?.text()?.trim()?.toIntOrNull(),
            )
        }

    /**
     * 用户名没有独立的标签，是 `div.review-title` 里紧跟操作按钮之后的一个裸文本节点
     * （形如 `mo***a&nbsp;`），因此取第一个非空文本节点，并清掉不换行空格。
     */
    private fun readUserName(element: Element): String =
        element
            .selectFirst("div.review-title")
            ?.childNodes()
            ?.filterIsInstance<TextNode>()
            ?.map { it.text().replace('\u00a0', ' ').trim() }
            ?.firstOrNull { it.isNotEmpty() }
            .orEmpty()

    private companion object {
        const val REVIEWS_PATH = "/reviews/lastest"
        const val REVIEW_ELEMENT_ID_PREFIX = "review-item-"
        const val REVIEW_ITEMS_CLASS = "review-items"
        const val MORE_CLASS = "more"

        /** XHR 形态的特征：片段被包进 `… innerHTML = "<article …>" …` 的 JS 里。 */
        const val JS_WRAPPER_MARKER = "innerHTML"

        /** 站点按 Accept-Language 返回对应语言，缺省可能落到别的语言页面导致解析错位。 */
        val HEADERS =
            mapOf(
                "Accept-Language" to "zh-CN,zh;q=0.9,zh-TW;q=0.8,en-US;q=0.7,en;q=0.6,ja;q=0.5"
            )
    }
}
