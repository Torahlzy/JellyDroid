package dev.jdtech.jellyfin.core.comment

/**
 * 短评数据源：按番号（或片名）到站点取短评列表。
 *
 * 每个站点一份实现，失败时抛 `ScraperException`；缓存与偏好判断由
 * [MovieCommentRepository] 负责，数据源本身不关心缓存。
 */
interface MovieCommentSource {
    /** 抓取 [keyword] 对应影片的短评；[useProxy] 决定是否走抓取代理。 */
    suspend fun load(keyword: String, useProxy: Boolean): List<MovieComment>
}
