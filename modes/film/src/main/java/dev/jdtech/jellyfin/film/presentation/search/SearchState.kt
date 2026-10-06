package dev.jdtech.jellyfin.film.presentation.search

import dev.jdtech.jellyfin.models.FindroidItem

/**
 * 搜索页状态。
 *
 * [loading] 只表示「用户改动了输入、正在请求新结果」，复用已有结果时不会置为 true，
 * 避免页面重建时搜索框右侧的进度指示器无谓闪动。
 */
data class SearchState(val items: List<FindroidItem> = emptyList(), val loading: Boolean = false)
