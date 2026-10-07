package dev.jdtech.jellyfin.film.presentation.filtervalue

import dev.jdtech.jellyfin.models.MetadataFacet

/**
 * 文本值列表状态（标签 / 分级 / 年份）。
 *
 * 这些可选值服务端只能一次性取回，因此 [searchQuery] 只影响展示，不触发请求。
 */
data class FilterValueListState(
    val facet: MetadataFacet? = null,
    val values: List<String> = emptyList(),
    val searchQuery: String = "",
    // 初值为 true：列表页一进来就取数，首帧该显示转圈而不是「暂无内容」
    val isLoading: Boolean = true,
    val error: Exception? = null,
) {
    /** 过滤后用于展示的值，过滤不区分大小写。 */
    val visibleValues: List<String>
        get() =
            if (searchQuery.isBlank()) {
                values
            } else {
                values.filter { it.contains(searchQuery, ignoreCase = true) }
            }
}
