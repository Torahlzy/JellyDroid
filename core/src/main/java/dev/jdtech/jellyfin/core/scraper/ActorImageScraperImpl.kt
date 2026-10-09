package dev.jdtech.jellyfin.core.scraper

import android.graphics.BitmapFactory
import dev.jdtech.jellyfin.logging.AppLog
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import org.jsoup.Jsoup

/**
 * [ActorImageScraper] 的默认实现：只走 JavDB 的女优搜索。
 *
 * 选 JavDB 的原因与 JavSP 一致——它的头像存在 `jdbstatic.com` 这个 CDN 上，
 * 可由女优页面地址直接推导（如 `/actors/mez5R` → `c0.jdbstatic.com/avatars/me/mez5R.jpg`），
 * 不像 JavBus 的头像那样被 CloudFlare 拦截（服务器和手机都拉不到）。
 *
 * 搜索结果按名字精确匹配，匹配不上宁可失败也不用别人的头像；结果（含「找不到」的负缓存）
 * 存在内存里，同一批演员反复抓取时不会重复请求站点。
 */
@Singleton
class ActorImageScraperImpl @Inject constructor(private val http: ScraperHttp) : ActorImageScraper {

    /** 名字 → 头像地址；值为空串表示站点上找不到这个人，同样缓存以免反复搜索。 */
    private val cache = HashMap<String, String>()

    override suspend fun scrapeActorImages(
        names: List<String>,
        onResult: suspend (ActorImageScrapeResult) -> Unit,
    ): List<ActorImageScrapeResult> {
        val baseUrl = ScraperSite.JAVDB.baseUrl
        if (baseUrl == null) {
            AppLog.w("JavDB 未配置地址，无法抓取演员头像")
            return names.map { name ->
                ActorImageScrapeResult.Failure(name, "未配置 JavDB 地址").also { onResult(it) }
            }
        }
        return names.map { name ->
            val result = scrapeOne(name, baseUrl)
            onResult(result)
            result
        }
    }

    private suspend fun scrapeOne(name: String, baseUrl: String): ActorImageScrapeResult {
        val key = name.trim()
        if (key.isEmpty()) return ActorImageScrapeResult.Failure(name, "名字为空")
        // 命中缓存的两种情况：空串表示站点上确认没有这个人；否则直接下载头像
        cache[key]?.let { url ->
            return if (url.isEmpty()) {
                ActorImageScrapeResult.Failure(name, "站点上找不到该演员")
            } else {
                download(name, url)
            }
        }
        return when (val search = searchActorPic(key, baseUrl)) {
            is ActorSearchResult.Found -> {
                cache[key] = search.picUrl
                download(name, search.picUrl)
            }
            // 确认站点上没有这个人才做负缓存，查询失败（网络抖动等）下次还要再试
            ActorSearchResult.NotFound -> {
                cache[key] = ""
                ActorImageScrapeResult.Failure(name, "站点上找不到该演员")
            }
            ActorSearchResult.Failed -> ActorImageScrapeResult.Failure(name, "搜索演员失败，可重新抓取")
        }
    }

    /** 搜索演员的结果：必须区分「确认不存在」与「查询本身失败」，否则一次网络抖动会把人错误地负缓存一整个会话。 */
    private sealed interface ActorSearchResult {
        /** 站点上找到了这个人，[picUrl] 是头像地址。 */
        data class Found(val picUrl: String) : ActorSearchResult

        /** 站点上确认没有这个人（名字精确匹配不上，或匹配上了但拿不到头像地址）。 */
        data object NotFound : ActorSearchResult

        /** 查询本身失败（网络错误、非 200 响应），不能下结论。 */
        data object Failed : ActorSearchResult
    }

    /** 在 JavDB 上按名字搜女优，返回她的头像地址；区分确认不存在与查询失败。 */
    private suspend fun searchActorPic(name: String, baseUrl: String): ActorSearchResult {
        val query = URLEncoder.encode(name, "UTF-8")
        val response =
            try {
                http.getFollowingRedirects("$baseUrl/search?q=$query&f=actor", HEADERS)
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
        // 部分女优没有头像缩略图，此时用女优页地址推导 CDN 地址兜底
        val picUrl =
            box.selectFirst("img.avatar")?.imageUrl()
                ?: box.selectFirst("a")?.absUrl("href")?.takeIf { it.startsWith("http") }
                    ?.let { actorPageUrl -> derivePicUrl(actorPageUrl) }
        return if (picUrl == null) ActorSearchResult.NotFound else ActorSearchResult.Found(picUrl)
    }

    /**
     * 由女优页面地址推导头像地址，移植自 JavSP 的 `javdb.get_actor_pic_url`：
     * CDN 的文件名就是女优 Id，前两位作为目录（`/actors/mez5R` → `/avatars/me/mez5R.jpg`）。
     */
    private fun derivePicUrl(actorPageUrl: String): String? {
        val actorId = actorPageUrl.trimEnd('/').substringAfterLast('/')
        if (actorId.isEmpty()) return null
        return "https://c0.jdbstatic.com/avatars/${actorId.take(2).lowercase()}/$actorId.jpg"
    }

    private suspend fun download(name: String, url: String): ActorImageScrapeResult =
        try {
            val bytes = http.getBytes(url)
            if (!isValidActorPic(bytes)) {
                ActorImageScrapeResult.Failure(name, "下载到的不是有效图片")
            } else {
                ActorImageScrapeResult.Success(name, bytes)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(e, "下载演员头像失败：%s", name)
            ActorImageScrapeResult.Failure(name, e.readableReason())
        }

    /**
     * 检查下载到的字节是不是一张可用的头像。
     *
     * 与 JavSP 的 `valid_actor_pic` 一致：能解码成图片，且不是 DMM 对无头像女优返回的
     * 90×122 占位图——把占位图当成头像传上去，比没有头像更糟。
     */
    private fun isValidActorPic(bytes: ByteArray): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) return false
        return width != PLACEHOLDER_PIC_WIDTH || height != PLACEHOLDER_PIC_HEIGHT
    }

    private companion object {
        /** 站点按 Accept-Language 返回对应语言，演员名才能与影片页上的一致。 */
        val HEADERS =
            mapOf(
                "Accept-Language" to "zh-CN,zh;q=0.9,zh-TW;q=0.8,en-US;q=0.7,en;q=0.6,ja;q=0.5"
            )

        const val PLACEHOLDER_PIC_WIDTH = 90
        const val PLACEHOLDER_PIC_HEIGHT = 122
    }
}
