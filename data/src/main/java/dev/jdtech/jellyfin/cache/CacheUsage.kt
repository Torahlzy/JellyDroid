package dev.jdtech.jellyfin.cache

/** 下载内容与缓存分开统计；被使用的缓存计入配额但暂不回收。 */
data class CacheUsage(
    val quotaBytes: Long,
    val downloadedBytes: Long,
    val categories: Map<CacheCategory, Long>,
    val protectedBytes: Long,
) {
    val usedBytes: Long get() = categories.values.sum()
}
