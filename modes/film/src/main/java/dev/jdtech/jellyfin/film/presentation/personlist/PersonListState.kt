package dev.jdtech.jellyfin.film.presentation.personlist

import dev.jdtech.jellyfin.models.FindroidPerson

/**
 * 人物列表状态（演员 / 导演 / 编剧）。
 *
 * 服务端 `/Persons` 没有偏移分页，[canLoadMore] 表示上次请求取满了 [PAGE_SIZE] 条、可能还有更多，
 * 点击「加载更多」时按更大的 limit 重新请求并整体替换列表。
 *
 * [PAGE_SIZE] 同时是首屏数量与步长：[PAGE_SIZE] 给得大可以少点几次「加载更多」，代价是首屏响应体更大
 * （每次「加载更多」都会重取整份列表），需要在两者之间取平衡。
 */
data class PersonListState(
    val persons: List<FindroidPerson> = emptyList(),
    val searchQuery: String = "",
    // 初值为 true：列表页一进来就取数，首帧该显示转圈而不是「暂无人物」
    val isLoading: Boolean = true,
    val canLoadMore: Boolean = false,
    val error: Exception? = null,
) {
    companion object {
        /** 每次请求的人物数量，也是「加载更多」的步长。 */
        const val PAGE_SIZE = 500
    }
}
