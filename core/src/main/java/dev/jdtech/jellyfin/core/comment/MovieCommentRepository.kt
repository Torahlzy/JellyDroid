package dev.jdtech.jellyfin.core.comment

/**
 * 短评的读取入口：优先读磁盘缓存，未命中或已过期才联网抓取，抓完写回缓存。
 *
 * 缓存有效期与总大小上限都来自设置页（网络 → 外网），因此不在这里暴露参数。
 */
interface MovieCommentRepository {
    /**
     * 取 [keyword]（通常是番号）对应影片的短评。
     *
     * 失败时抛 `ScraperException` 或网络异常，由上层转成界面上的错误提示。
     */
    suspend fun getComments(keyword: String): List<MovieComment>
}
