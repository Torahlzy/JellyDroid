package dev.jdtech.jellyfin.core.scraper

import dev.jdtech.jellyfin.models.ItemMetadataEdit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * 从站点抓到的影片信息，字段取自 JavSP 的 `MovieInfo`。
 *
 * 只保留「编辑 nfo」表单能承载的字段：封面、预览图、磁力等一律不解析，
 * 系列 / 导演 / 演员也暂不解析，因为表单里没有对应输入项。
 */
data class ScrapedMovie(
    val site: ScraperSite,
    /** 影片标题（不含番号）。 */
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
    /** 影片页面地址，仅用于排查问题。 */
    val url: String?,
)

/**
 * 映射成「编辑 nfo」弹窗的表单数据。
 *
 * 抓取结果只用来回填表单，保存仍走用户确认后的既有编辑流程，不会直接写入服务器。
 *
 * @param fallbackTitle 标题兜底值（通常是番号）：有的站点（如 JavDB）页面标题里只有番号，
 *   剥掉番号后片名为空，而表单里标题必填，为空会卡住确认按钮，因此按「片名 → 原名 → 兜底值」
 *   取第一个非空值。
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
