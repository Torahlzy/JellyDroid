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

    /**
     * 判断 [keyword] 的短评缓存是否在有效期内，只查磁盘、不产生任何网络请求。
     *
     * 上层用它决定「确定要联网抓取」才需要先做的准备工作（如校验站点登录会话），
     * 缓存新鲜时直接展示，不做多余的网络往返。
     */
    suspend fun hasFreshCache(keyword: String): Boolean
}
