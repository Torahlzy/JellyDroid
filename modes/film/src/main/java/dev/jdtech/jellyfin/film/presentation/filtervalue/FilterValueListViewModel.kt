package dev.jdtech.jellyfin.film.presentation.filtervalue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.MetadataFacet
import dev.jdtech.jellyfin.repository.JellyfinRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 标签 / 分级 / 年份这类纯文本可选值的浏览列表。 */
@HiltViewModel
class FilterValueListViewModel @Inject constructor(private val repository: JellyfinRepository) :
    ViewModel() {
    private val _state = MutableStateFlow(FilterValueListState())
    val state = _state.asStateFlow()

    fun loadValues(facet: MetadataFacet) {
        viewModelScope.launch {
            _state.emit(_state.value.copy(facet = facet, isLoading = true, error = null))

            try {
                // 三组值来自同一次请求，服务端只提供这一个「可用筛选值」入口
                val filterValues = repository.getFilterValues()
                val values =
                    when (facet) {
                        MetadataFacet.TAG -> filterValues.tags
                        MetadataFacet.OFFICIAL_RATING -> filterValues.officialRatings
                        // 年份在界面上一律按文本展示，后续筛选时再转回数值
                        MetadataFacet.YEAR -> filterValues.years.map(Int::toString)
                        else -> emptyList()
                    }

                AppLog.d("筛选值列表加载完成：%s，共 %d 项", facet, values.size)
                // 用 copy 保留用户已经输入的搜索词，别让「重试」把输入框清空
                _state.emit(
                    _state.value.copy(
                        facet = facet,
                        values = values,
                        isLoading = false,
                        error = null,
                    )
                )
            } catch (e: Exception) {
                AppLog.e(e, "筛选值列表加载失败：%s", facet)
                _state.emit(_state.value.copy(isLoading = false, error = e))
            }
        }
    }

    fun onAction(action: FilterValueListAction) {
        when (action) {
            is FilterValueListAction.OnSearchQueryChange ->
                viewModelScope.launch {
                    _state.emit(_state.value.copy(searchQuery = action.query))
                }
            is FilterValueListAction.Retry -> _state.value.facet?.let { loadValues(it) }
            // 导航由界面处理，ViewModel 只负责状态
            is FilterValueListAction.OnValueClick, is FilterValueListAction.OnBackClick -> Unit
        }
    }
}
