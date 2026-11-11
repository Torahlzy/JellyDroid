package dev.jdtech.jellyfin.core.presentation.cache

import dev.jdtech.jellyfin.cache.CacheCategory

sealed interface CacheAction {
    data object Refresh : CacheAction
    data class SetQuota(val megabytes: Int) : CacheAction
    data class Clear(val category: CacheCategory) : CacheAction
}
