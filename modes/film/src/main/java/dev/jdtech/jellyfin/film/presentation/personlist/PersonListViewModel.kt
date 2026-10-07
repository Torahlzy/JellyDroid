package dev.jdtech.jellyfin.film.presentation.personlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.MetadataFacet
import dev.jdtech.jellyfin.repository.JellyfinRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 人物搜索的输入防抖时长：服务端搜索没有分页，连续输入时只对最后一次发起请求
private const val SEARCH_DEBOUNCE_MILLIS = 300L

/** 演员 / 导演 / 编剧列表。 */
@HiltViewModel
class PersonListViewModel @Inject constructor(private val repository: JellyfinRepository) :
    ViewModel() {
    private val _state = MutableStateFlow(PersonListState())
    val state = _state.asStateFlow()

    /** 当前维度对应的人物类型值，为空表示尚未初始化。 */
    private var personType: String? = null

    /** 已请求过的 limit，决定「加载更多」的量；发起新搜索时重置回一页。 */
    private var loadedLimit = 0

    private var currentJob: Job? = null
    private var searchJob: Job? = null

    fun loadPersons(facet: MetadataFacet) {
        // 每个 facet 是独立路由、各自持有本 ViewModel，故只初始化一次即可。
        // 从人物详情页返回时 Screen 的 LaunchedEffect 会重跑，若再次按空搜索词请求，
        // 会覆盖掉用户当前的搜索结果（输入框还留着关键词，列表却已变回全量）。
        if (personType != null) return

        personType = facet.personType
        requestPersons(limit = PersonListState.PAGE_SIZE, searchTerm = null)
    }

    fun onAction(action: PersonListAction) {
        when (action) {
            is PersonListAction.Search -> {
                viewModelScope.launch { _state.emit(_state.value.copy(searchQuery = action.query)) }

                searchJob?.cancel()
                searchJob =
                    viewModelScope.launch {
                        delay(SEARCH_DEBOUNCE_MILLIS)
                        requestPersons(
                            limit = PersonListState.PAGE_SIZE,
                            searchTerm = action.query.ifBlank { null },
                        )
                    }
            }
            is PersonListAction.OnLoadMore ->
                requestPersons(
                    limit = loadedLimit + PersonListState.PAGE_SIZE,
                    searchTerm = _state.value.searchQuery.ifBlank { null },
                )
            is PersonListAction.Retry ->
                requestPersons(
                    limit = loadedLimit.coerceAtLeast(PersonListState.PAGE_SIZE),
                    searchTerm = _state.value.searchQuery.ifBlank { null },
                )
            // 导航由界面处理，ViewModel 只负责状态
            is PersonListAction.OnPersonClick, is PersonListAction.OnBackClick -> Unit
        }
    }

    /**
     * 请求人物列表。
     *
     * 服务端的人物接口既不支持偏移分页也不支持排序，只能把 limit 放大后整体重取，因此这里每次
     * 整体替换列表而不是追加；页码变大时响应体也会随之变大，界面侧用「加载更多」按钮显式触发。
     */
    private fun requestPersons(limit: Int, searchTerm: String?) {
        val type = personType ?: return

        currentJob?.cancel()
        currentJob =
            viewModelScope.launch {
                _state.emit(_state.value.copy(isLoading = true, error = null))

                try {
                    val persons = repository.getPersons(listOf(type), limit, searchTerm)
                    loadedLimit = limit
                    _state.emit(
                        _state.value.copy(
                            persons = persons,
                            isLoading = false,
                            // 取满一页说明后面可能还有，否则已到末尾
                            canLoadMore = persons.size >= limit,
                        )
                    )
                } catch (e: Exception) {
                    AppLog.e(e, "加载人物列表失败（类型 %s，limit %d）", type, limit)
                    _state.emit(_state.value.copy(isLoading = false, error = e))
                }
            }
    }
}
