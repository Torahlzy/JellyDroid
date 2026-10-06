package dev.jdtech.jellyfin.film.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.repository.JellyfinRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(private val repository: JellyfinRepository) :
    ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()

    var currentJob: Job? = null

    // 最近一次成功完成的查询。文字没变说明结果已是最新，直接复用，不再请求：
    // 页面重建（返回媒体页、切换底部栏）时会重新触发一次搜索，重复请求只会让进度指示器白闪一下
    private var lastSearchedQuery: String? = null

    private fun search(query: String) {
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            try {
                if (query.isBlank()) {
                    // 必须一并失效缓存：结果已被清空，若保留缓存，用户重新输入同一关键词会命中
                    // 下面的复用分支，拿到的是空列表
                    lastSearchedQuery = null
                    _state.emit(SearchState(items = emptyList(), loading = false))
                    return@launch
                }

                // 复用已有结果；同时复位可能被取消的上一次请求残留的 loading
                if (query == lastSearchedQuery) {
                    _state.emit(_state.value.copy(loading = false))
                    return@launch
                }

                _state.emit(_state.value.copy(loading = true))
                val items = repository.getSearchItems(query)

                // 仅在成功后记录，失败时不记录，以便页面重建后还能重试
                lastSearchedQuery = query
                _state.emit(SearchState(items = items, loading = false))
            } catch (_: CancellationException) {} catch (e: Exception) {
                AppLog.e(e, "搜索 %s 失败", query)
                _state.emit(_state.value.copy(loading = false))
            }
        }
    }

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.Search -> {
                search(query = action.query)
            }
            else -> Unit
        }
    }
}
