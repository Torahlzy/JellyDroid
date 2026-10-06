package dev.jdtech.jellyfin.film.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.CoverDisplayMode
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind

@HiltViewModel
class LibraryViewModel
@Inject
constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()

    lateinit var parentId: UUID
    lateinit var libraryType: CollectionType

    // 排序与封面模式只以 State 为准；该标记仅用于判断"是否已从偏好读取过"，
    // 避免每次刷新都覆盖用户在当前页面刚做的选择
    private var isPreferencesLoaded = false

    fun setup(parentId: UUID, libraryType: CollectionType) {
        this.parentId = parentId
        this.libraryType = libraryType
    }

    fun loadItems() {
        val itemType =
            when (libraryType) {
                CollectionType.Movies -> listOf(BaseItemKind.MOVIE)
                CollectionType.TvShows -> listOf(BaseItemKind.SERIES)
                CollectionType.BoxSets -> listOf(BaseItemKind.BOX_SET)
                CollectionType.Mixed,
                CollectionType.Folders ->
                    listOf(BaseItemKind.FOLDER, BaseItemKind.MOVIE, BaseItemKind.SERIES)
                else -> null
            }

        val recursive = itemType == null || !itemType.contains(BaseItemKind.FOLDER)

        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoading = true, error = null))

            loadPreferencesIfNeeded()

            val sortBy = _state.value.sortBy
            val sortOrder = _state.value.sortOrder

            // 页面进入与排序变更都走这里，日志用于排查"列表为空"或"排序未生效"
            AppLog.d(
                "加载媒体库 %s（类型 %s），sortBy=%s，sortOrder=%s",
                parentId,
                libraryType,
                sortBy,
                sortOrder,
            )

            try {
                val items =
                    jellyfinRepository
                        .getItemsPaging(
                            parentId = parentId,
                            includeTypes = itemType,
                            recursive = recursive,
                            sortBy =
                                if (
                                    libraryType == CollectionType.TvShows &&
                                        sortBy == SortBy.DATE_PLAYED
                                )
                                    SortBy.SERIES_DATE_PLAYED
                                else sortBy, // Jellyfin uses a different enum for sorting series by
                            // data played
                            sortOrder = sortOrder,
                        )
                        .cachedIn(viewModelScope)
                _state.emit(_state.value.copy(items = items))
                AppLog.d("媒体库 %s 分页数据流已就绪", parentId)
            } catch (e: Exception) {
                AppLog.e(e, "加载媒体库 %s 失败", parentId)
                _state.emit(_state.value.copy(error = e))
            }
        }
    }

    // 首次进入时从偏好读取排序与封面模式，之后不再读取，避免覆盖用户在当前页面的选择
    private suspend fun loadPreferencesIfNeeded() {
        if (isPreferencesLoaded) return
        isPreferencesLoaded = true

        val sortBy = SortBy.fromString(appPreferences.getValue(appPreferences.sortBy))
        val sortOrder = SortOrder.fromString(appPreferences.getValue(appPreferences.sortOrder))
        val coverMode =
            CoverDisplayMode.fromString(appPreferences.getValue(appPreferences.libraryCoverMode))
        _state.emit(_state.value.copy(sortBy = sortBy, sortOrder = sortOrder, coverMode = coverMode))
    }

    fun onAction(action: LibraryAction) {
        when (action) {
            is LibraryAction.ChangeSorting -> {
                val current = _state.value
                if (action.sortBy != current.sortBy || action.sortOrder != current.sortOrder) {
                    AppLog.d("媒体库 %s 切换排序：%s / %s", parentId, action.sortBy, action.sortOrder)
                    viewModelScope.launch {
                        // 先更新排序并落盘，再重新拉取，保证 loadItems 读到的是新排序
                        _state.emit(
                            _state.value.copy(
                                sortBy = action.sortBy,
                                sortOrder = action.sortOrder,
                            )
                        )
                        appPreferences.setValue(appPreferences.sortBy, action.sortBy.toString())
                        appPreferences.setValue(
                            appPreferences.sortOrder,
                            action.sortOrder.toString(),
                        )
                        loadItems()
                    }
                }
            }
            is LibraryAction.ChangeCoverMode -> {
                // 封面模式只影响展示，不需要重新拉取列表
                if (action.coverMode != _state.value.coverMode) {
                    AppLog.d("媒体库 %s 切换封面模式：%s", parentId, action.coverMode)
                    viewModelScope.launch {
                        _state.emit(_state.value.copy(coverMode = action.coverMode))
                        appPreferences.setValue(
                            appPreferences.libraryCoverMode,
                            action.coverMode.toString(),
                        )
                    }
                }
            }
            else -> Unit
        }
    }
}
