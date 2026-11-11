package dev.jdtech.jellyfin.core.subtitle

import dev.jdtech.jellyfin.core.scraper.ScraperHttp
import dev.jdtech.jellyfin.core.scraper.requireSuccess
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.Constants
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * subtitlecat.com 的字幕搜索与下载。
 *
 * 站点结构（2026-11 实测）：
 * - 搜索结果在 `table.sub-table tbody tr` 里，每行第一个带链接的格子是详情页，链接文本是字幕名；
 * - 详情页每种语言一个 `div.sub-single`，下载入口是 `a#download_<语言代码>`，`href` 就是 `.srt` 直链；
 * - 直链不需要 Referer、也不需要 Cookie，但要能连上站点（走抓取代理，见 [ScraperHttp]）。
 *
 * 站点地址在「抓取网址」弹窗里配置，默认已填官方地址（见 [AppPreferences.subtitleSiteBaseUrl]），
 * 用户可改成镜像域名；详情页与直链都从页面里取绝对地址，因此换了域名也能跟着走，但镜像须与官方站
 * 保持相同的路径结构（搜索页 `/index.php` 与详情页 `/subs/` 是写死的）。
 *
 * 站点会给几十种机翻语言，这里只认 [SubtitleLanguage] 里的三种；同一份候选优先简体，
 * 没有简体才退到繁体、英文。
 */
@Singleton
class SubtitlecatScraper
@Inject
constructor(
    private val http: ScraperHttp,
    private val appPreferences: AppPreferences,
) : SubtitleScraper {

    override val siteName: String = SITE_NAME

    override suspend fun search(keyword: String, limit: Int): List<SubtitleCandidate> = withContext(Dispatchers.Default) {
        val baseUrl = appPreferences.subtitleSiteBaseUrl()
        val searchUrl = "$baseUrl$SEARCH_PATH?search=${encode(keyword)}"
        val response = http.getFollowingRedirects(searchUrl).requireSuccess(siteName)
        val document = Jsoup.parse(response.body, response.url)
        document
            .select(SEARCH_ROW_SELECTOR)
            .asSequence()
            .mapNotNull { row ->
                val anchor = row.selectFirst("td a[href]") ?: return@mapNotNull null
                val pageUrl = anchor.absUrl("href").trim()
                // 只在站内的字幕详情页里找，跳过页面上其它链接（上传入口、语言切换等）
                if (!pageUrl.contains(SUBTITLE_PATH)) return@mapNotNull null
                SubtitleCandidate(title = anchor.text().trim(), pageUrl = pageUrl)
            }
            .take(limit)
            .toList()
    }

    override suspend fun resolve(candidate: SubtitleCandidate): List<SubtitleDownload> = withContext(Dispatchers.Default) {
        // 站点会把不带 www 的详情页 301 到 www（搜索结果里的链接是相对路径，拼出来就不带 www），
        // 抓取用的客户端默认不跟随重定向，这里必须显式跟随，否则每次都只拿到一个 301
        val response = http.getFollowingRedirects(candidate.pageUrl).requireSuccess(siteName)
        val document = Jsoup.parse(response.body, response.url)
        // 页面上有哪些语言是既定事实，全部返回给调用方，由它按语言优先级决定用哪份；
        // 这里不要自己挑，否则「简体优先于繁体」的规则会被站点顺序左右
        SubtitleLanguage.entries.mapNotNull { language ->
            val anchor = document.selectFirst(downloadSelector(language)) ?: return@mapNotNull null
            // 原始语言（日语）那份是 zip 打包，这里只接受能直接上传的 srt / vtt
            val url = anchor.absUrl("href").trim()
            val format = url.substringAfterLast('.', "").lowercase()
            if (format !in SUPPORTED_FORMATS) return@mapNotNull null
            SubtitleDownload(
                title = candidate.title,
                language = language,
                format = format,
                url = url,
            )
        }
    }

    override suspend fun download(subtitle: SubtitleDownload): ByteArray =
        http.getBytes(subtitle.url, what = "字幕", maxBytes = MAX_SUBTITLE_BYTES)

    private fun downloadSelector(language: SubtitleLanguage): String =
        "a#download_${language.subtitlecatKey}"

    private fun encode(keyword: String): String = URLEncoder.encode(keyword, Charsets.UTF_8.name())

    private companion object {
        /** 错误提示用的站点名，与配置 key 分开，避免 key 变更时文案跟着变。 */
        const val SITE_NAME = Constants.SUBTITLE_SITE_DISPLAY_NAME

        const val MAX_SUBTITLE_BYTES = 8L * 1024 * 1024

        const val SEARCH_PATH = "/index.php"

        /** 详情页路径，用来过滤搜索结果里的非字幕链接。 */
        const val SUBTITLE_PATH = "/subs/"

        const val SEARCH_ROW_SELECTOR = "table.sub-table tbody tr"

        val SUPPORTED_FORMATS = setOf("srt", "vtt")
    }
}
