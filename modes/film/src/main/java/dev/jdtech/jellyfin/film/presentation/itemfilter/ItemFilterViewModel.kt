package dev.jdtech.jellyfin.film.presentation.itemfilter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.CoverDisplayMode
import dev.jdtech.jellyfin.models.MetadataFacet
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

/** 按元数据维度筛选出的条目列表，所有维度共用这一个 ViewModel。 */
@HiltViewModel
class ItemFilterViewModel
@Inject
constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(ItemFilterState())
    val state = _state.asStateFlow()

    private lateinit var facet: MetadataFacet
    private lateinit var key: String

    // 排序与封面模式只以 State 为准；该标记仅用于判断"是否已从偏好读取过"，
    // 避免每次重新取数都覆盖用户在当前页面刚做的选择
    private var isPreferencesLoaded = false

    fun loadItems(facet: MetadataFacet, key: String) {
        this.facet = facet
        this.key = key

        viewModelScope.launch {
            _state.emit(_state.value.copy(facet = facet, error = null))

            loadPreferencesIfNeeded()

            val args = facet.toFilterArgs(key)

            // 日志用于排查"筛选结果为空"，同时记录实际生效的排序
            AppLog.d(
                "按元数据筛选条目：%s = %s，sortBy=%s，sortOrder=%s",
                facet,
                key,
                _state.value.sortBy,
                _state.value.sortOrder,
            )

            try {
                val items =
                    jellyfinRepository
                        .getItemsPaging(
                            includeTypes = FILTER_ITEM_TYPES,
                            recursive = true,
                            sortBy = _state.value.sortBy,
                            sortOrder = _state.value.sortOrder,
                            genreIds = args.genreIds,
                            studioIds = args.studioIds,
                            tags = args.tags,
                            officialRatings = args.officialRatings,
                            years = args.years,
                            personIds = args.personIds,
                        )
                        .cachedIn(viewModelScope)
                _state.emit(_state.value.copy(items = items))
            } catch (e: Exception) {
                AppLog.e(e, "按元数据筛选条目失败：%s = %s", facet, key)
                _state.emit(_state.value.copy(error = e))
            }
        }
    }

    fun onAction(action: ItemFilterAction) {
        when (action) {
            is ItemFilterAction.ChangeSorting -> {
                val current = _state.value
                if (action.sortBy != current.sortBy || action.sortOrder != current.sortOrder) {
                    viewModelScope.launch {
                        // 先更新排序，再重新取数，保证新请求读到的是新排序
                        _state.emit(
                            _state.value.copy(
                                sortBy = action.sortBy,
                                sortOrder = action.sortOrder,
                            )
                        )
                        loadItems(facet, key)
                    }
                }
            }
            is ItemFilterAction.ChangeCoverMode -> {
                // 封面模式只影响展示，不需要重新取数
                if (action.coverMode != _state.value.coverMode) {
                    viewModelScope.launch {
                        _state.emit(_state.value.copy(coverMode = action.coverMode))
                    }
                }
            }
            // 导航由界面处理，ViewModel 只负责状态
            is ItemFilterAction.OnItemClick, is ItemFilterAction.OnBackClick -> Unit
        }
    }

    // 首次进入时沿用媒体库页的排序与封面模式偏好，之后不再读取，避免覆盖用户在当前页面的选择
    private suspend fun loadPreferencesIfNeeded() {
        if (isPreferencesLoaded) return
        isPreferencesLoaded = true

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
    }

    /**
     * 维度与服务端 `/Items` 过滤参数的对应关系。
     *
     * 类别、制片公司、人物用实体 id 过滤（名称可能重复或变更）；标签、分级、年份服务端只有名称 / 数值，
     * 只能按值过滤。
     */
    private fun MetadataFacet.toFilterArgs(key: String): FilterArgs =
        when (this) {
            MetadataFacet.GENRE -> FilterArgs(genreIds = listOf(UUID.fromString(key)))
            MetadataFacet.STUDIO -> FilterArgs(studioIds = listOf(UUID.fromString(key)))
            MetadataFacet.ACTOR,
            MetadataFacet.DIRECTOR,
            MetadataFacet.WRITER -> FilterArgs(personIds = listOf(UUID.fromString(key)))
            MetadataFacet.TAG -> FilterArgs(tags = listOf(key))
            MetadataFacet.OFFICIAL_RATING -> FilterArgs(officialRatings = listOf(key))
            MetadataFacet.YEAR ->
                key.toIntOrNull()?.let { FilterArgs(years = listOf(it)) } ?: FilterArgs()
        }

    /** 一次筛选请求承载的过滤条件，未使用的字段为 null。 */
    private data class FilterArgs(
        val genreIds: List<UUID>? = null,
        val studioIds: List<UUID>? = null,
        val tags: List<String>? = null,
        val officialRatings: List<String>? = null,
        val years: List<Int>? = null,
        val personIds: List<UUID>? = null,
    )

    private companion object {
        /** 元数据维度只用于浏览影片与剧集，单集不参与筛选。 */
        val FILTER_ITEM_TYPES = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
    }
}
