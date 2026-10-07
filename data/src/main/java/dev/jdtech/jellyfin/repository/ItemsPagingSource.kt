package dev.jdtech.jellyfin.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * 条目偏移分页。
 *
 * 除媒体库浏览外，也支撑按元数据维度（类别、标签、制片公司、分级、年份、人物）筛选结果的加载，
 * 因此除父级与类型外还携带一组服务端筛选条件，构造时未给出的条件不参与过滤。
 */
class ItemsPagingSource(
    private val jellyfinRepository: JellyfinRepository,
    private val parentId: UUID?,
    private val includeTypes: List<BaseItemKind>?,
    private val recursive: Boolean,
    private val sortBy: SortBy,
    private val sortOrder: SortOrder,
    private val genreIds: List<UUID>? = null,
    private val studioIds: List<UUID>? = null,
    private val tags: List<String>? = null,
    private val officialRatings: List<String>? = null,
    private val years: List<Int>? = null,
    private val personIds: List<UUID>? = null,
) : PagingSource<Int, FindroidItem>() {
    /**
     * 已输出过的项目 id。服务端基于偏移量的分页并不保证稳定：当排序字段存在大量相同值时（例如
     * DatePlayed，未播放的项目取值相同），同一项目可能被多个分页重复返回。重复输出会让惰性网格抛
     * 出 "Key was already used" 异常，因此这里过滤掉重复项。每次刷新都会创建新的实例，集合随之重置。
     */
    private val emittedItemIds = mutableSetOf<UUID>()

    /**
     * 随机排序在本次会话内的固定顺序。
     *
     * 随机排序不能沿用偏移量分页：服务端每次请求都会重新洗牌，同一项目会在多页重复出现，另有项目
     * 永远取不到。因此首次加载时一次性取回一批并打乱，之后各页只对这份快照切片；重建实例（刷新或
     * 切换排序）时重新取一批。
     */
    private var randomSnapshot: List<FindroidItem>? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FindroidItem> {
        val position = params.key ?: 0

        return try {
            if (sortBy == SortBy.RANDOM) {
                loadRandomPage(position, params.loadSize)
            } else {
                loadServerPage(position, params.loadSize)
            }
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    /** 普通排序：按偏移量直接向服务端分页请求。 */
    private suspend fun loadServerPage(
        position: Int,
        loadSize: Int,
    ): LoadResult<Int, FindroidItem> {
        AppLog.d("Retrieving position: %d", position)

        val items = loadItems(sortBy = sortBy, sortOrder = sortOrder, position, loadSize)

        return LoadResult.Page(
            data = items.filter { emittedItemIds.add(it.id) },
            prevKey = if (position == 0) null else position - loadSize,
            nextKey = if (items.isEmpty()) null else position + loadSize,
        )
    }

    /** 随机排序：只对会话快照切片，顺序在本次浏览内稳定，不会重复或遗漏。 */
    private suspend fun loadRandomPage(
        position: Int,
        loadSize: Int,
    ): LoadResult<Int, FindroidItem> {
        val snapshot = randomSnapshot ?: loadRandomSnapshot().also { randomSnapshot = it }

        return LoadResult.Page(
            // 快照内部本身不重复；这里仍走 emittedItemIds 兜底，防止向上回退加载重复输出同一项
            data = snapshot.drop(position).take(loadSize).filter { emittedItemIds.add(it.id) },
            prevKey = if (position == 0) null else position - loadSize,
            nextKey = if (position + loadSize >= snapshot.size) null else position + loadSize,
        )
    }

    /**
     * 取回一批随机条目作为会话快照。
     *
     * 用服务端随机排序一次取回，而不是按稳定顺序拉全量：媒体库可能极大，拉全量既慢又占内存，
     * 随机排序下用户也只需要一批打乱的条目。数量受 [RANDOM_SNAPSHOT_SIZE] 限制，媒体库小于该值时
     * 即覆盖全部条目。
     */
    private suspend fun loadRandomSnapshot(): List<FindroidItem> {
        val items =
            loadItems(sortBy = SortBy.RANDOM, sortOrder = SortOrder.ASCENDING, 0, RANDOM_SNAPSHOT_SIZE)

        AppLog.d("随机排序快照 %d 项（媒体库 %s）", items.size, parentId)

        return items.shuffled()
    }

    /** 把构造参数与分页参数一起转给仓库，随机快照与普通分页共用同一套筛选条件。 */
    private suspend fun loadItems(
        sortBy: SortBy,
        sortOrder: SortOrder,
        position: Int,
        loadSize: Int,
    ): List<FindroidItem> =
        jellyfinRepository.getItems(
            parentId = parentId,
            includeTypes = includeTypes,
            recursive = recursive,
            sortBy = sortBy,
            sortOrder = sortOrder,
            startIndex = position,
            limit = loadSize,
            genreIds = genreIds,
            studioIds = studioIds,
            tags = tags,
            officialRatings = officialRatings,
            years = years,
            personIds = personIds,
        )

    override fun getRefreshKey(state: PagingState<Int, FindroidItem>): Int {
        return 0
    }

    private companion object {
        /**
         * 随机排序一次取回的条目上限。
         *
         * 取回后在本地分页，因此设上限可避免超大媒体库一次性拉取过多数据；媒体库条目数超过该值时，
         * 本次会话的随机排序只覆盖其中的一部分，刷新可换一批。
         */
        const val RANDOM_SNAPSHOT_SIZE = 500
    }
}
