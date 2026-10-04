package dev.jdtech.jellyfin.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

class ItemsPagingSource(
    private val jellyfinRepository: JellyfinRepository,
    private val parentId: UUID?,
    private val includeTypes: List<BaseItemKind>?,
    private val recursive: Boolean,
    private val sortBy: SortBy,
    private val sortOrder: SortOrder,
) : PagingSource<Int, FindroidItem>() {
    /**
     * 已输出过的项目 id。服务端基于偏移量的分页并不保证稳定：当排序字段存在大量相同值时（例如
     * DatePlayed，未播放的项目取值相同），同一项目可能被多个分页重复返回。重复输出会让惰性网格抛
     * 出 "Key was already used" 异常，因此这里过滤掉重复项。每次刷新都会创建新的实例，集合随之重置。
     */
    private val emittedItemIds = mutableSetOf<UUID>()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FindroidItem> {
        val position = params.key ?: 0

        Timber.d("Retrieving position: $position")

        return try {
            val items =
                jellyfinRepository.getItems(
                    parentId = parentId,
                    includeTypes = includeTypes,
                    recursive = recursive,
                    sortBy = sortBy,
                    sortOrder = sortOrder,
                    startIndex = position,
                    limit = params.loadSize,
                )
            LoadResult.Page(
                data = items.filter { emittedItemIds.add(it.id) },
                prevKey = if (position == 0) null else position - params.loadSize,
                nextKey = if (items.isEmpty()) null else position + params.loadSize,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, FindroidItem>): Int {
        return 0
    }
}
