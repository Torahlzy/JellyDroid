package dev.jdtech.jellyfin.film.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.film.presentation.collection.CollectionAction
import dev.jdtech.jellyfin.film.presentation.collection.CollectionState
import dev.jdtech.jellyfin.film.presentation.collection.buildCollectionSections
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.CoverDisplayMode
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class FavoritesViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(CollectionState())
    val state = _state.asStateFlow()

    // 排序与封面模式与媒体库列表共用同一套偏好；首次进入时读取，之后以 State 为准，
    // 避免每次刷新都覆盖用户刚做的选择
    private var isPreferencesLoaded = false

    // 同一时刻只保留最后一次取数：切换排序时取消在途请求，
    // 否则旧排序的结果可能后返回并覆盖新结果
    private var loadJob: Job? = null

    fun loadItems() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadPreferencesIfNeeded()
            fetchItems()
        }
    }

    fun onAction(action: CollectionAction) {
        when (action) {
            is CollectionAction.ChangeSorting -> changeSorting(action.sortBy, action.sortOrder)
            is CollectionAction.ChangeCoverMode -> changeCoverMode(action.coverMode)
            // 导航由界面处理，ViewModel 只负责状态
            else -> Unit
        }
    }

    private fun changeSorting(sortBy: SortBy, sortOrder: SortOrder) {
        val previousSortBy = _state.value.sortBy
        val previousSortOrder = _state.value.sortOrder
        if (sortBy == previousSortBy && sortOrder == previousSortOrder) return

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // 首次加载若正好被取消，这里补读偏好，避免封面模式停留在默认值
            loadPreferencesIfNeeded()
            // 先更新排序并落盘，再取数：本次请求立即用新排序，
            // 媒体库列表下次进入时也读到同一套配置
            _state.emit(_state.value.copy(sortBy = sortBy, sortOrder = sortOrder))
            appPreferences.setValue(appPreferences.sortBy, sortBy.toString())
            appPreferences.setValue(appPreferences.sortOrder, sortOrder.toString())

            if (!fetchItems()) {
                // 取数失败就回滚排序与偏好：否则顶栏显示新排序、列表仍是旧顺序，用户会以为已生效
                appPreferences.setValue(appPreferences.sortBy, previousSortBy.toString())
                appPreferences.setValue(appPreferences.sortOrder, previousSortOrder.toString())
                _state.emit(
                    _state.value.copy(sortBy = previousSortBy, sortOrder = previousSortOrder)
                )
            }
        }
    }

    private fun changeCoverMode(coverMode: CoverDisplayMode) {
        // 封面模式只影响展示，不需要重新取数
        if (coverMode == _state.value.coverMode) return

        viewModelScope.launch {
            _state.emit(_state.value.copy(coverMode = coverMode))
            appPreferences.setValue(appPreferences.libraryCoverMode, coverMode.toString())
        }
    }

    /** 按当前 State 的排序取数并刷新列表；失败时把错误写进 State 并返回 false。 */
    private suspend fun fetchItems(): Boolean {
        _state.emit(_state.value.copy(isLoading = true, error = null))
        AppLog.d("加载收藏条目，sortBy=%s，sortOrder=%s", _state.value.sortBy, _state.value.sortOrder)

        return try {
            val items =
                repository.getFavoriteItems(
                    sortBy = _state.value.sortBy,
                    sortOrder = _state.value.sortOrder,
                )
            val sections = withContext(Dispatchers.Default) { buildCollectionSections(items) }
            _state.emit(_state.value.copy(isLoading = false, sections = sections))
            true
        } catch (e: CancellationException) {
            // 被后一次取数取消属于正常流程，不当作错误写进 State
            throw e
        } catch (e: Exception) {
            AppLog.e(e, "加载收藏条目失败")
            _state.emit(_state.value.copy(isLoading = false, error = e))
            false
        }
    }

    // 首次进入时沿用媒体库列表的排序与封面模式偏好，之后不再读取，避免覆盖用户在当前页面的选择
    private suspend fun loadPreferencesIfNeeded() {
        if (isPreferencesLoaded) return

        _state.emit(
            _state.value.copy(
                sortBy = SortBy.fromString(appPreferences.getValue(appPreferences.sortBy)),
                sortOrder = SortOrder.fromString(appPreferences.getValue(appPreferences.sortOrder)),
                coverMode =
                    CoverDisplayMode.fromString(
                        appPreferences.getValue(appPreferences.libraryCoverMode)
                    ),
            )
        )
        // 读取成功后才置位：若协程在读偏好期间被取消，下次进入还能补读
        isPreferencesLoaded = true
    }
}
