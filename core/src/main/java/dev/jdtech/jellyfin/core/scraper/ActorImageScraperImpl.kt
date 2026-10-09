package dev.jdtech.jellyfin.core.scraper

import android.graphics.BitmapFactory
import dev.jdtech.jellyfin.logging.AppLog
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * [ActorImageScraper] 的默认实现：各站点同时搜索演员头像。
 *
 * 支持按名字搜女优的站点是 JavBus 与 JavDB——Jav321 的 `searchstar` 接口已失效
 * （实测只回「AVが見つかりませんでした」），也没有女优列表页，无法参与。
 * JavBus 的演员名是日文原名，与服务器上 nfo 里的名字匹配度最高；JavDB 的搜索结果
 * 常是中文译名，匹配不上时该站自行失败，不强求。
 *
 * 头像一律由本应用自己下载，再交给界面确认后上传：JavBus 的图片会校验 Referer，
 * 媒体服务器直连该地址只会拿到 403，因此不能把图源地址直接丢给服务器。
 * 名字按精确匹配，匹配不上宁可失败也不用别人的头像。
 */
@Singleton
class ActorImageScraperImpl @Inject constructor(private val http: ScraperHttp) : ActorImageScraper {

    /** 串行化 [onResult] 回调：各站点并发抓取，但回调逐个送达，调用方追加状态时无需考虑并发。 */
    private val resultMutex = Mutex()

    override val sites: List<ScraperSite>
        get() = actorImageSites().map { (site, _) -> site }

    override suspend fun scrapeActorImage(
        name: String,
        onResult: suspend (SiteActorImageScrapeResult) -> Unit,
    ): List<SiteActorImageScrapeResult> {
        val key = name.trim()
        if (key.isEmpty()) {
            return actorImageSites().map { (site, _) ->
                SiteActorImageScrapeResult.Failure(site, "演员名字为空").also { onResult(it) }
            }
        }
        val sites = actorImageSites()
        if (sites.isEmpty()) {
            AppLog.w("JavBus / JavDB 都没有配置地址，无法抓取演员头像")
            return emptyList()
        }
        return coroutineScope {
            sites
                .map { (site, baseUrl) ->
                    async {
                        val result = scrapeOnSite(site, key, baseUrl)
                        resultMutex.withLock { onResult(result) }
                        result
                    }
                }
                .awaitAll()
        }
    }

    /**
     * 可用来抓演员头像的站点，按优先级排列；没在本地配置里填地址的会被跳过。
     *
     * Jav321 不在此列：它的女优搜索接口已失效（见类 KDoc），而且历史上给的女优头像
     * 不区分真假（女优实际没有头像时也会给一个像模像样的地址），无法校验。
     */
    private fun actorImageSites(): List<Pair<ScraperSite, String>> =
        listOf(ScraperSite.JAVBUS, ScraperSite.JAVDB).mapNotNull { site ->
            site.baseUrl?.let { baseUrl -> site to baseUrl }
        }

    /** 在单个站点上按名字搜演员并下载头像；搜不到与下载失败都转成该站点的失败结果。 */
    private suspend fun scrapeOnSite(
        site: ScraperSite,
        name: String,
        baseUrl: String,
    ): SiteActorImageScrapeResult =
        when (val search = searchActorPic(site, name, baseUrl)) {
            is ActorSearchResult.Found -> download(site, search.picUrl)
            ActorSearchResult.NotFound ->
                SiteActorImageScrapeResult.Failure(site, "站点上找不到该演员")
            ActorSearchResult.Failed ->
                SiteActorImageScrapeResult.Failure(site, "搜索演员失败，可重新获取")
        }

    /** 搜索演员的结果：必须区分「确认不存在」与「查询本身失败」，否则一次网络抖动会被误判成「没有这个人」。 */
    private sealed interface ActorSearchResult {
        /** 站点上找到了这个人，[picUrl] 是头像地址。 */
        data class Found(val picUrl: String) : ActorSearchResult

        /** 站点上确认没有这个人（名字精确匹配不上，或匹配上了但拿不到头像地址）。 */
        data object NotFound : ActorSearchResult

        /** 查询本身失败（网络错误、非 200 响应），不能下结论。 */
        data object Failed : ActorSearchResult
    }

    /** 在指定站点上按名字搜演员头像；区分确认不存在与查询失败。 */
    private suspend fun searchActorPic(
        site: ScraperSite,
        name: String,
        baseUrl: String,
    ): ActorSearchResult =
        when (site) {
            ScraperSite.JAVBUS -> searchJavBusPic(name, baseUrl)
            ScraperSite.JAVDB -> searchJavDbPic(name, baseUrl)
            // Jav321 不支持按名字搜女优（见 [actorImageSites]），不会走到这里，补全分支而已
            ScraperSite.JAV321 -> ActorSearchResult.NotFound
        }

    /**
     * 在 JavBus 上按名字搜演员，返回她的头像地址。
     *
     * 演员条目挂在 `div#waterfall` 下的 `a.avatar-box`，头像在 `div.photo-frame > img`（相对地址，
     * 如 `/pics/actress/1269_a.jpg`），名字是该 img 的 title。与 JavSP 的 `javbus.py` 一致：
     * 跳过站点给没有头像的演员塞的默认图 `nowprinting.gif`。
     */
    private suspend fun searchJavBusPic(name: String, baseUrl: String): ActorSearchResult {
        val query = URLEncoder.encode(name, "UTF-8")
        val response =
            try {
                http.getFollowingRedirects("$baseUrl/searchstar/$query", JAVBUS_HEADERS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(e, "JavBus 搜索演员失败：%s", name)
                return ActorSearchResult.Failed
            }
        if (response.code != 200) {
            AppLog.w("JavBus 搜索演员返回 %d：%s", response.code, name)
            return ActorSearchResult.Failed
        }
        val document = Jsoup.parse(response.body, response.url)
        // 被年龄验证页拦下时页面里没有演员条目，不能当成「找不到这个人」（与 [JavBusScraper] 的判断一致）
        if (response.url.contains(JAVBUS_VERIFY_PATH) || isAgeVerificationPage(document)) {
            AppLog.w("JavBus 命中年龄验证页，无法搜索演员：%s", name)
            return ActorSearchResult.Failed
        }
        val picUrl =
            document
                .select("a.avatar-box")
                .firstOrNull { box -> box.selectFirst("img")?.attr("title")?.trim() == name }
                ?.selectFirst("img")
                ?.imageUrl()
                // 站点给没有头像的演员返回的是默认图，不能当成真头像
                ?.takeIf { url -> JAVBUS_PLACEHOLDER_PIC_KEYWORD !in url }
                ?: return ActorSearchResult.NotFound
        return ActorSearchResult.Found(picUrl)
    }

    /** 页面标题是不是 JavBus 的年龄验证页（站点的一种拦截形态，状态码仍是 200）。 */
    private fun isAgeVerificationPage(document: Document): Boolean =
        document.selectFirst("head > title")?.text()?.trim().orEmpty()
            .startsWith(JAVBUS_AGE_VERIFY_TITLE)

    /** 在 JavDB 上按名字搜女优，返回她的头像地址；区分确认不存在与查询失败。 */
    private suspend fun searchJavDbPic(name: String, baseUrl: String): ActorSearchResult {
        val query = URLEncoder.encode(name, "UTF-8")
        val response =
            try {
                http.getFollowingRedirects("$baseUrl/search?q=$query&f=actor", JAVDB_HEADERS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(e, "JavDB 搜索演员失败：%s", name)
                return ActorSearchResult.Failed
            }
        if (response.code != 200) {
            AppLog.w("JavDB 搜索演员返回 %d：%s", response.code, name)
            return ActorSearchResult.Failed
        }
        val document = Jsoup.parse(response.body, response.url)
        val box =
            document
                .select("div.actor-box")
                .firstOrNull { box -> box.select("strong").texts().contains(name) }
                ?: return ActorSearchResult.NotFound
        val picUrl = searchResultPicUrl(box)
        return if (picUrl == null) ActorSearchResult.NotFound else ActorSearchResult.Found(picUrl)
    }

    /**
     * 从一条 JavDB 搜索结果里取女优头像地址，取不到时返回 null。
     *
     * 与 JavSP 的 `search_javdb_pic` 一致：优先用缩略图；缩略图缺失、或站点对无头像女优返回的
     * 占位图（灰色人形剪影，地址带 [JAVDB_PLACEHOLDER_PIC_KEYWORD]）都不能用，改用女优页地址
     * 推导 CDN 地址——头像确实不存在时该地址会下载失败，不会误把占位图当成头像。
     */
    private fun searchResultPicUrl(box: Element): String? {
        val thumbnail =
            (box.selectFirst("img.avatar") ?: box.selectFirst("img"))
                ?.imageUrl()
                ?.takeIf { url -> JAVDB_PLACEHOLDER_PIC_KEYWORD !in url }
        return thumbnail
            ?: box.selectFirst("a")?.absUrl("href")?.takeIf { it.startsWith("http") }
                ?.let { actorPageUrl -> derivePicUrl(actorPageUrl) }
    }

    /**
     * 由 JavDB 的女优页面地址推导头像地址，移植自 JavSP 的 `javdb.get_actor_pic_url`：
     * CDN 的文件名就是女优 Id，前两位作为目录（`/actors/mez5R` → `/avatars/me/mez5R.jpg`）。
     */
    private fun derivePicUrl(actorPageUrl: String): String? {
        val actorId = actorPageUrl.trimEnd('/').substringAfterLast('/')
        if (actorId.isEmpty()) return null
        return "https://c0.jdbstatic.com/avatars/${actorId.take(2).lowercase()}/$actorId.jpg"
    }

    private suspend fun download(site: ScraperSite, url: String): SiteActorImageScrapeResult =
        try {
            val bytes = http.getBytes(url, picHeaders(url))
            if (!isValidActorPic(bytes)) {
                SiteActorImageScrapeResult.Failure(site, "下载到的不是有效图片")
            } else {
                SiteActorImageScrapeResult.Success(site, bytes)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(e, "下载演员头像失败：%s", url)
            SiteActorImageScrapeResult.Failure(site, e.readableReason())
        }

    /**
     * 下载头像时需要的额外请求头。
     *
     * JavBus 的演员头像（`/pics/actress/` 下的 jpg）会校验 Referer，缺了它一律 403（补上图片所在站点的首页即可，
     * 不需要登录或先预热会话）；与 JavSP 的 `actress_pic._pic_headers` 一致，按地址特征判断，
     * 不给不需要的站点带多余的头。
     */
    private fun picHeaders(url: String): Map<String, String> {
        if (JAVBUS_PIC_URL_KEYWORD !in url) return emptyMap()
        val uri = runCatching { URI(url) }.getOrNull() ?: return emptyMap()
        val scheme = uri.scheme ?: return emptyMap()
        val host = uri.host ?: return emptyMap()
        return mapOf("Referer" to "$scheme://$host/")
    }

    /**
     * 检查下载到的字节是不是一张可用的头像。
     *
     * 与 JavSP 的 `valid_actor_pic` 一致：能解码成图片，且既不是 DMM 对无头像女优返回的
     * 90×122 占位图，也不是 JavDB 的 200×200 占位图（尺寸与真头像一样合法，只能按内容指纹识别）——
     * 把占位图当成头像传上去，比没有头像更糟。
     */
    private fun isValidActorPic(bytes: ByteArray): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) return false
        if (width == PLACEHOLDER_PIC_WIDTH && height == PLACEHOLDER_PIC_HEIGHT) return false
        // 先比字节数，只有大小对得上才去算 MD5，避免为每张头像都做一次哈希
        if (bytes.size == JAVDB_PLACEHOLDER_PIC_SIZE && md5Hex(bytes) == JAVDB_PLACEHOLDER_PIC_MD5) {
            return false
        }
        return true
    }

    /** 计算字节串的 MD5（十六进制小写），用于识别 JavDB 的占位图。 */
    private fun md5Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        /** JavDB 按 Accept-Language 返回对应语言，演员名才能与影片页上的一致。 */
        val JAVDB_HEADERS =
            mapOf(
                "Accept-Language" to "zh-CN,zh;q=0.9,zh-TW;q=0.8,en-US;q=0.7,en;q=0.6,ja;q=0.5"
            )

        /** JavBus 缺这个头会被重定向到年龄验证页，必须显式指定语言（与 [JavBusScraper] 一致）。 */
        val JAVBUS_HEADERS =
            mapOf("Accept-Language" to "zh-CN,zh;q=0.9,zh-TW;q=0.8,en;q=0.7,ja;q=0.6")

        /** JavBus 演员头像的路径特征：这些图片校验 Referer，下载时要补上站点首页。 */
        const val JAVBUS_PIC_URL_KEYWORD = "/pics/actress/"

        /** JavBus 给没有头像的演员塞的默认图，不能当成头像。 */
        const val JAVBUS_PLACEHOLDER_PIC_KEYWORD = "nowprinting.gif"

        /** JavBus 的年龄验证页地址与标题：命中它说明请求被拦，页面里没有演员数据。 */
        const val JAVBUS_VERIFY_PATH = "/doc/driver-verify"
        const val JAVBUS_AGE_VERIFY_TITLE = "Age Verification"

        const val PLACEHOLDER_PIC_WIDTH = 90
        const val PLACEHOLDER_PIC_HEIGHT = 122

        /** JavDB 对无头像女优返回的占位图（灰色人形剪影）的地址特征。 */
        const val JAVDB_PLACEHOLDER_PIC_KEYWORD = "images/actor_unknow.jpg"

        /** JavDB 占位图的内容指纹（4465 字节、200×200），尺寸校验拦不住，只能按字节数 + MD5 识别。 */
        const val JAVDB_PLACEHOLDER_PIC_SIZE = 4465
        const val JAVDB_PLACEHOLDER_PIC_MD5 = "37bedef116ad2d621251c3109347420a"
    }
}
