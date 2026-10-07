package dev.jdtech.jellyfin.film.presentation.itemfilter

import androidx.paging.PagingData
import dev.jdtech.jellyfin.models.CoverDisplayMode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.MetadataFacet
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 按某个元数据值筛选出的条目列表。
 *
 * 列表的加载中 / 出错状态由分页流自己的 [PagingData] 携带（界面用 `loadState` 渲染），因此这里不再
 * 单独放 isLoading；[error] 只用于「取分页流本身」就失败这种兜底情况。
 */
data class ItemFilterState(
    val items: Flow<PagingData<FindroidItem>> = emptyFlow(),
    val facet: MetadataFacet? = null,
    val sortBy: SortBy = SortBy.NAME,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val coverMode: CoverDisplayMode = CoverDisplayMode.defaultValue,
    val error: Exception? = null,
)
