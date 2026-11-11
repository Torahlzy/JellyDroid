package dev.jdtech.jellyfin.cache

/** 在线图片缓存有独立配额；下载内容只统计不回收，被使用的共享缓存计入配额但暂不回收。 */
data class CacheUsage(
    val quotaBytes: Long,
    val imageQuotaBytes: Long,
    val imageCacheBytes: Long,
    val downloadedBytes: Long,
    val categories: Map<CacheCategory, Long>,
    val protectedBytes: Long,
) {
    val usedBytes: Long get() = categories.values.sum()
}
