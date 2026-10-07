package dev.jdtech.jellyfin.film.presentation.nameditem

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

/** 类别 / 制片公司这类「带图片的命名实体」的浏览列表。 */
@HiltViewModel
class NamedItemListViewModel @Inject constructor(private val repository: JellyfinRepository) :
    ViewModel() {
    private val _state = MutableStateFlow(NamedItemListState())
    val state = _state.asStateFlow()

    fun loadItems(facet: MetadataFacet) {
        viewModelScope.launch {
            _state.emit(_state.value.copy(facet = facet, isLoading = true, error = null))

            try {
                val items =
                    when (facet) {
                        MetadataFacet.GENRE -> repository.getGenres()
                        MetadataFacet.STUDIO -> repository.getStudios()
                        // 其余维度由别的浏览页承载，走到这里说明调用方传错了维度
                        else -> emptyList()
                    }

                AppLog.d("命名实体列表加载完成：%s，共 %d 项", facet, items.size)
                // 用 copy 保留用户已经输入的搜索词，别让「重试」把输入框清空
                _state.emit(
                    _state.value.copy(
                        facet = facet,
                        items = items,
                        isLoading = false,
                        error = null,
                    )
                )
            } catch (e: Exception) {
                AppLog.e(e, "命名实体列表加载失败：%s", facet)
                _state.emit(_state.value.copy(isLoading = false, error = e))
            }
        }
    }

    fun onAction(action: NamedItemListAction) {
        when (action) {
            is NamedItemListAction.OnSearchQueryChange ->
                viewModelScope.launch {
                    _state.emit(_state.value.copy(searchQuery = action.query))
                }
            is NamedItemListAction.Retry -> _state.value.facet?.let { loadItems(it) }
            // 导航由界面处理，ViewModel 只负责状态
            is NamedItemListAction.OnItemClick, is NamedItemListAction.OnBackClick -> Unit
        }
    }
}
