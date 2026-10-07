package dev.jdtech.jellyfin.film.presentation.nameditem

import dev.jdtech.jellyfin.models.FindroidNamedItem
import dev.jdtech.jellyfin.models.MetadataFacet

/**
 * 命名实体列表状态（类别 / 制片公司）。
 *
 * 这两种维度数量有限，一次取回全部后由客户端过滤，因此 [searchQuery] 只影响展示，不触发请求。
 */
data class NamedItemListState(
    val facet: MetadataFacet? = null,
    val items: List<FindroidNamedItem> = emptyList(),
    val searchQuery: String = "",
    // 初值为 true：列表页一进来就取数，首帧该显示转圈而不是「暂无内容」
    val isLoading: Boolean = true,
    val error: Exception? = null,
) {
    /** 过滤后用于展示的条目，过滤不区分大小写。 */
    val visibleItems: List<FindroidNamedItem>
        get() =
            if (searchQuery.isBlank()) {
                items
            } else {
                items.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
}
