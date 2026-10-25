package dev.jdtech.jellyfin.film.presentation.collection

import dev.jdtech.jellyfin.models.CollectionSection
import dev.jdtech.jellyfin.models.CoverDisplayMode
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder

/**
 * 分组列表的共享状态。
 *
 * 排序与封面显示模式由「我喜欢」页与合集页可调（与媒体库列表共用同一套偏好），下载页不提供设置入口，保持默认值即可。
 */
data class CollectionState(
    val sections: List<CollectionSection> = emptyList(),
    val sortBy: SortBy = SortBy.defaultValue,
    val sortOrder: SortOrder = SortOrder.defaultValue,
    val coverMode: CoverDisplayMode = CoverDisplayMode.defaultValue,
    val isLoading: Boolean = false,
    val error: Exception? = null,
)
