package dev.jdtech.jellyfin.repository

import androidx.paging.PagingData
import dev.jdtech.jellyfin.models.FilterValues
import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidItemImage
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidNamedItem
import dev.jdtech.jellyfin.models.FindroidPerson
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.ItemMetadataEdit
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.UserConfiguration

interface JellyfinRepository {
    suspend fun getPublicSystemInfo(): PublicSystemInfo

    suspend fun getUserViews(): List<BaseItemDto>

    suspend fun getEpisode(itemId: UUID): FindroidEpisode

    suspend fun getMovie(itemId: UUID): FindroidMovie

    suspend fun getShow(itemId: UUID): FindroidShow

    suspend fun getSeason(itemId: UUID): FindroidSeason

    suspend fun getLibraries(): List<FindroidCollection>

    suspend fun getItem(itemId: UUID): FindroidItem?

    /**
     * 按条件列出条目。
     *
     * [genreIds] 之后的一组筛选参数与服务端 `/Items` 的同名过滤条件一一对应，未给出时不参与过滤，
     * 因此媒体库浏览与按元数据维度筛选共用这一个方法。
     */
    suspend fun getItems(
        parentId: UUID? = null,
        includeTypes: List<BaseItemKind>? = null,
        recursive: Boolean = false,
        sortBy: SortBy = SortBy.defaultValue,
        sortOrder: SortOrder = SortOrder.ASCENDING,
        startIndex: Int? = null,
        limit: Int? = null,
        genreIds: List<UUID>? = null,
        studioIds: List<UUID>? = null,
        tags: List<String>? = null,
        officialRatings: List<String>? = null,
        years: List<Int>? = null,
        personIds: List<UUID>? = null,
    ): List<FindroidItem>

    /** [getItems] 的分页版本，筛选参数含义相同。 */
    suspend fun getItemsPaging(
        parentId: UUID? = null,
        includeTypes: List<BaseItemKind>? = null,
        recursive: Boolean = false,
        sortBy: SortBy = SortBy.defaultValue,
        sortOrder: SortOrder = SortOrder.ASCENDING,
        genreIds: List<UUID>? = null,
        studioIds: List<UUID>? = null,
        tags: List<String>? = null,
        officialRatings: List<String>? = null,
        years: List<Int>? = null,
        personIds: List<UUID>? = null,
    ): Flow<PagingData<FindroidItem>>

    suspend fun getPerson(personId: UUID): FindroidPerson

    suspend fun getPersonItems(
        personIds: List<UUID>,
        includeTypes: List<BaseItemKind>? = null,
        recursive: Boolean = true,
    ): List<FindroidItem>

    /** 列出全库所有类别（nfo 的 genre）。 */
    suspend fun getGenres(): List<FindroidNamedItem>

    /** 列出全库所有制片公司（nfo 的 studio）。 */
    suspend fun getStudios(): List<FindroidNamedItem>

    /**
     * 按人物类型列出人物。
     *
     * 服务端的 `/Persons` 既不支持偏移分页也不支持排序，只能按 [limit] 截断并用 [searchTerm] 收敛，
     * 因此「取更多」与搜索由调用方负责。
     */
    suspend fun getPersons(
        personTypes: List<String>,
        limit: Int,
        searchTerm: String? = null,
    ): List<FindroidPerson>

    /** 一次性取回服务端提供的可用筛选值（标签、分级、年份）。 */
    suspend fun getFilterValues(): FilterValues

    suspend fun getFavoriteItems(): List<FindroidItem>

    suspend fun getSearchItems(query: String): List<FindroidItem>

    suspend fun getSuggestions(): List<FindroidItem>

    suspend fun getResumeItems(): List<FindroidItem>

    suspend fun getLatestMedia(parentId: UUID): List<FindroidItem>

    suspend fun getSeasons(seriesId: UUID, offline: Boolean = false): List<FindroidSeason>

    suspend fun getNextUp(seriesId: UUID? = null): List<FindroidEpisode>

    suspend fun getEpisodes(
        seriesId: UUID,
        seasonId: UUID,
        fields: List<ItemFields>? = null,
        startItemId: UUID? = null,
        limit: Int? = null,
        offline: Boolean = false,
    ): List<FindroidEpisode>

    suspend fun getMediaSources(itemId: UUID, includePath: Boolean = false): List<FindroidSource>

    suspend fun getStreamUrl(itemId: UUID, mediaSourceId: String): String

    suspend fun getSegments(itemId: UUID): List<FindroidSegment>

    suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray?

    suspend fun postCapabilities()

    suspend fun postPlaybackStart(itemId: UUID)

    suspend fun postPlaybackStop(itemId: UUID, positionTicks: Long, playedPercentage: Int)

    suspend fun postPlaybackProgress(itemId: UUID, positionTicks: Long, isPaused: Boolean)

    suspend fun markAsFavorite(itemId: UUID)

    suspend fun unmarkAsFavorite(itemId: UUID)

    suspend fun markAsPlayed(itemId: UUID)

    suspend fun markAsUnplayed(itemId: UUID)

    /** 列出条目在服务器上的全部图片（封面、背景等）。 */
    suspend fun getItemImages(itemId: UUID): List<FindroidItemImage>

    /** 删除条目在服务器上指定的图片，只删 [images] 中列出的那些。 */
    suspend fun deleteItemImages(itemId: UUID, images: List<FindroidItemImage>)

    /**
     * 把 [imageBytes]（JPEG 字节）上传为条目 [imageType] 类型的图片，如把播放截图设为横屏封面。
     *
     * 该类型已有图片时服务端会直接覆盖，是否需要先让用户确认由调用方负责。
     */
    suspend fun setItemImage(itemId: UUID, imageType: ImageType, imageBytes: ByteArray)

    /** 读取条目在服务器上可编辑的 nfo 元数据，用于编辑弹窗回填。 */
    suspend fun getItemMetadata(itemId: UUID): ItemMetadataEdit

    /**
     * 用 [metadata] 覆盖条目在服务器上的 nfo 元数据。
     *
     * 未涉及的部分（演职员、外部刮削 ID、锁定状态等）由实现方原样保留。
     */
    suspend fun updateItemMetadata(itemId: UUID, metadata: ItemMetadataEdit)

    /** 删除服务器上的条目本身，服务端会连媒体文件与关联的图片、nfo 一起删掉。 */
    suspend fun deleteItem(itemId: UUID)

    fun getBaseUrl(): String

    suspend fun updateDeviceName(name: String)

    suspend fun getUserConfiguration(): UserConfiguration?

    suspend fun getDownloads(): List<FindroidItem>

    fun getUserId(): UUID
}
