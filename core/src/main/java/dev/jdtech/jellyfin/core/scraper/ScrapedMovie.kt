package dev.jdtech.jellyfin.core.scraper

import dev.jdtech.jellyfin.models.ItemMetadataEdit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * 从站点抓到的影片信息，字段取自 JavSP 的 `MovieInfo`。
 *
 * 文字字段只保留「编辑 nfo」表单能承载的那些（系列 / 导演 / 演员暂不解析，表单里没有对应输入项）；
 * 图片只解析封面（竖图）与首张剧照（横图）两个地址，供「编辑封面」抓取使用——这里只给地址，
 * 下载与否由 [ImageScraper] 决定。
 */
data class ScrapedMovie(
    val site: ScraperSite,
    /** 影片标题，与站点页面上展示的一致（含番号）。 */
    val title: String?,
    val originalTitle: String?,
    /** 剧情简介。 */
    val plot: String?,
    val genres: List<String>,
    /** 制作商，写入 nfo 的 studio。 */
    val producer: String?,
    /** 发布日期，形如 `2024-05-01`。 */
    val publishDate: String?,
    /** 评分，10 分制。 */
    val score: Float?,
    /** 影片页面地址，仅用于排查问题，也用作下载图片时的 Referer。 */
    val url: String?,
    /**
     * 站点上的「封面」图地址，页面上没有时为 null。
     *
     * 只是位置信息，**不保证是竖版海报**：这几个站点在数字版影片上给的是横版封面（实测 800×535）。
     * 最终当作海报还是背景，由 [ImageScraper] 按图片实际比例决定。
     */
    val coverUrl: String?,
    /**
     * 站点图集里的第一张图地址，页面上没有时为 null。
     *
     * 同样只是位置信息：多数是横版剧照，但也会混进竖版的封面裁切（JavDB 实测第一张就是 147×200 的竖图）。
     */
    val previewUrl: String?,
)

/**
 * 映射成「编辑 nfo」弹窗的表单数据。
 *
 * 抓取结果只用来回填表单，保存仍走用户确认后的既有编辑流程，不会直接写入服务器。
 *
 * @param fallbackTitle 标题兜底值（通常是番号）：少数站点可能整页都没有标题，而表单里标题必填，
 *   为空会卡住确认按钮，因此按「片名 → 原名 → 兜底值」取第一个非空值。
 */
fun ScrapedMovie.toItemMetadataEdit(fallbackTitle: String = ""): ItemMetadataEdit {
    val resolvedName =
        title?.takeIf { it.isNotBlank() }
            ?: originalTitle?.takeIf { it.isNotBlank() }
            ?: fallbackTitle
    return ItemMetadataEdit(
        name = resolvedName,
        originalTitle = originalTitle.orEmpty(),
        overview = plot.orEmpty(),
        genres = genres,
        studios = listOfNotNull(producer?.takeIf { it.isNotBlank() }),
        productionYear = publishDate?.take(4)?.toIntOrNull(),
        premiereDate = publishDate.toPremiereDate(),
        communityRating = score,
    )
}

private fun String?.toPremiereDate(): LocalDateTime? {
    val text = this?.trim().orEmpty()
    if (text.isEmpty()) return null
    return try {
        LocalDate.parse(text).atStartOfDay()
    } catch (_: DateTimeParseException) {
        // 站点偶尔给出非 yyyy-MM-dd 的日期，此时宁可不填也不写入错误值
        null
    }
}
