package dev.jdtech.jellyfin.core.presentation.cache

import dev.jdtech.jellyfin.cache.CacheCategory
import dev.jdtech.jellyfin.cache.CacheUsage

/** 空间扫描和清理共用状态，失败时保留上次有效结果。 */
data class CacheState(
    val usage: CacheUsage? = null,
    val isLoading: Boolean = false,
    val clearing: CacheCategory? = null,
    val error: Exception? = null,
)
