package dev.jdtech.jellyfin.core.scraper

import android.graphics.BitmapFactory
import dev.jdtech.jellyfin.logging.AppLog
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.ImageType

/**
 * [ImageScraper] 的默认实现：按 [ScraperSite] 的顺序把配了地址的站点全部尝试一遍。
 *
 * 每个站点无论成败都会产出一条结果，界面据此逐行展示「失败原因」或「抓到的图片 + 上传按钮」；
 * 没有任何站点配置地址时返回空数组（界面提示去 local.properties 里填地址）。
 */
class ImageScraperImpl @Inject constructor(private val http: ScraperHttp) : ImageScraper {

    override val sites: List<ScraperSite>
        get() = configuredScraperSites.map { (site, _) -> site }

    override suspend fun scrapeImages(
        keyword: String,
        onResult: suspend (SiteImageScrapeResult) -> Unit,
    ): List<SiteImageScrapeResult> {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            AppLog.w("图片抓取关键词为空，直接放弃")
            return emptyList()
        }

        val sites = configuredScraperSites
        if (sites.isEmpty()) {
            AppLog.w("没有任何站点配置了地址，无法抓取图片")
            return emptyList()
        }

        val results = mutableListOf<SiteImageScrapeResult>()
        for ((site, baseUrl) in sites) {
            val result =
                try {
                    val movie = site.parseMovie(trimmed, baseUrl, http)
                    val images = downloadImages(movie)
                    if (images.isEmpty()) {
                        AppLog.w("%s 抓到了影片，但页面没有可用的封面图", site.displayName)
                        SiteImageScrapeResult.Failure(site, "页面没有可用的封面图")
                    } else {
                        AppLog.i("从 %s 抓取到 %d 张封面图", site.displayName, images.size)
                        SiteImageScrapeResult.Success(site, images)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.w(e, "%s 抓取封面图失败", site.displayName)
                    SiteImageScrapeResult.Failure(site, e.readableReason())
                }
            results += result
            onResult(result)
        }
        return results
    }

    /**
     * 下载站点上的「封面」与「图集第一张」，再按图片真实比例归成竖图 / 横图。
     *
     * 单张下载失败不算整站失败：另一张仍然可能可用。站点普遍做了防盗链，
     * 因此带上影片页地址作 Referer。
     */
    private suspend fun downloadImages(movie: ScrapedMovie): List<ScrapedImage> {
        val headers = movie.url?.let { url -> mapOf("Referer" to url) }.orEmpty()
        // 同一张图可能既被当成封面又被当成图集首图，去重后再下载
        val candidates =
            listOfNotNull(
                movie.coverUrl?.let { url -> ImageType.PRIMARY to url },
                movie.previewUrl
                    ?.takeIf { it != movie.coverUrl }
                    ?.let { url -> ImageType.BACKDROP to url },
            )
        val downloaded =
            candidates.mapNotNull { (declaredType, imageUrl) ->
                try {
                    ScrapedImage(declaredType, http.getBytes(imageUrl, headers))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.w(e, "下载封面图失败：%s", imageUrl)
                    null
                }
            }
        return downloaded.typedByOrientation()
    }

    /**
     * 按图片真实比例决定谁当海报、谁当背景。
     *
     * 站点给出的「封面 / 图集首图」位置并不可靠：数字版影片的封面本身就是横版（实测 JavDB、JavBus
     * 都是 800×535），而图集首图也可能混进竖版的封面裁切（JavDB 实测 147×200）。按位置硬套就会
     * 出现「海报是横图、背景是竖图」的错位，所以这里按比例分：竖的进 [ImageType.PRIMARY]（海报）、
     * 横的进 [ImageType.BACKDROP]（背景）。
     *
     * 两张同一方向时（站点只给了同一种比例的图）只留站点声明的「封面」那张放进对应槽位，
     * 另一张丢掉——硬塞进另一个槽位就又变成「海报是横图」了。
     */
    private fun List<ScrapedImage>.typedByOrientation(): List<ScrapedImage> {
        val portrait = firstOrNull { it.isPortrait() == true }
        val landscape = firstOrNull { it.isPortrait() == false }
        return when {
            portrait != null && landscape != null ->
                listOf(portrait.retyped(ImageType.PRIMARY), landscape.retyped(ImageType.BACKDROP))
            portrait != null -> listOf(portrait.retyped(ImageType.PRIMARY))
            landscape != null -> listOf(landscape.retyped(ImageType.BACKDROP))
            // 尺寸都读不出来（多半不是图片，而是站点返回的错误页）：按站点声明的位置返回，交给界面展示
            else -> this
        }
    }

    /** 图片是不是竖的；读不出尺寸（不是图片）时返回 null。 */
    private fun ScrapedImage.isPortrait(): Boolean? {
        // inJustDecodeBounds 只读图片头里的尺寸，不解码像素
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return options.outHeight > options.outWidth
    }

    /** 换成另一种图片类型；类型没变时原样返回，避免多造一个对象。 */
    private fun ScrapedImage.retyped(imageType: ImageType): ScrapedImage =
        if (this.imageType == imageType) this else ScrapedImage(imageType, bytes)
}
