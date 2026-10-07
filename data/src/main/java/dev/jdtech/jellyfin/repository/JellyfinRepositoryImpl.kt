package dev.jdtech.jellyfin.repository

import android.content.Context
import android.util.Base64
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.FilterValues
import dev.jdtech.jellyfin.models.FindroidBoxSet
import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidImages
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
import dev.jdtech.jellyfin.models.toFindroidCollection
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidImages
import dev.jdtech.jellyfin.models.toFindroidItem
import dev.jdtech.jellyfin.models.toFindroidItemImage
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.models.toFindroidNamedItem
import dev.jdtech.jellyfin.models.toFindroidPerson
import dev.jdtech.jellyfin.models.toFindroidSeason
import dev.jdtech.jellyfin.models.toFindroidSegment
import dev.jdtech.jellyfin.models.toFindroidShow
import dev.jdtech.jellyfin.models.toFindroidSource
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.DeviceOptionsDto
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.NameGuidPair
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SortOrder as ItemSortOrder
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.UserConfiguration
import org.jellyfin.sdk.model.toFileInfo

// 上传图片的 Content-Type：服务端用它映射落盘扩展名（image/jpeg → .jpg），必须与实际字节格式一致
private const val JPEG_MEDIA_TYPE = "image/jpeg"

// 查找合集封面时最多试探的子条目数量：合集内排序最前的条目也可能没有图片
private const val BOX_SET_COVER_CANDIDATE_LIMIT = 3

// 类别 / 制片公司一次取回的上限：这两类实体数量有限，一次取回后在客户端过滤，不做分页
private const val NAMED_ITEM_LIMIT = 500

// 同时试探封面的合集数量上限：合集库一页可能有多个缺图合集，避免一次性打出大量并发请求
private const val BOX_SET_COVER_CONCURRENCY = 4

// 合集是否有可用封面：横竖图任一存在即可，与 ItemPoster 的取图逻辑保持一致
private val FindroidImages.hasCover: Boolean
    get() = primary != null || backdrop != null

// 编辑元数据时要读回的字段：写回时漏掉任何一个，服务端都会当成「未提供」而把对应内容清空或重置。
// 演职员不在此列：服务端对 People 是「传了才更新」，写回时显式传 null 即可原样保留，无需读回
private val METADATA_EDIT_FIELDS =
    listOf(
        ItemFields.SETTINGS,
        ItemFields.ORIGINAL_TITLE,
        ItemFields.OVERVIEW,
        ItemFields.GENRES,
        ItemFields.TAGS,
        ItemFields.TAGLINES,
        ItemFields.STUDIOS,
        ItemFields.PRODUCTION_LOCATIONS,
        ItemFields.PROVIDER_IDS,
        ItemFields.CUSTOM_RATING,
        ItemFields.DATE_CREATED,
    )

// 服务端只读取 Studios 的 Name，Id 仅用于跟已有制片厂对上号，新加的随便生成一个即可
private fun toStudioNameGuidPair(name: String, existing: List<NameGuidPair>?): NameGuidPair =
    existing?.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: NameGuidPair(name = name, id = UUID.randomUUID())

class JellyfinRepositoryImpl(
    private val context: Context,
    private val jellyfinApi: JellyfinApi,
    private val database: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
) : JellyfinRepository {
    /**
     * 合集封面缓存：封面取自合集内部条目，会话内基本不变，缓存后翻页、回退都不会重复请求。
     *
     * 键含服务器地址，切换服务器不会取到上一个服务器的图片；值为空 [FindroidImages] 表示已试探过但确实没有可用图片。
     * 刻意不设上限、不主动清理，仅随进程存活：合集总量与媒体库条目同量级且基本固定，用极小内存换取翻页 / 回退零重复请求。
     */
    private val boxSetCoverCache = ConcurrentHashMap<Pair<String, UUID>, FindroidImages>()

    // 限制合集封面试探的并发数，避免合集库一页内的多个缺图合集同时发起请求
    private val boxSetCoverSemaphore = Semaphore(BOX_SET_COVER_CONCURRENCY)

    override suspend fun getPublicSystemInfo(): PublicSystemInfo =
        withContext(Dispatchers.IO) { jellyfinApi.systemApi.getPublicSystemInfo().content }

    override suspend fun getUserViews(): List<BaseItemDto> =
        withContext(Dispatchers.IO) {
            jellyfinApi.viewsApi.getUserViews(jellyfinApi.userId!!).content.items
        }

    override suspend fun getEpisode(itemId: UUID): FindroidEpisode =
        withContext(Dispatchers.IO) {
            jellyfinApi.userLibraryApi
                .getItem(itemId, jellyfinApi.userId!!)
                .content
                .toFindroidEpisode(this@JellyfinRepositoryImpl, database)!!
        }

    override suspend fun getMovie(itemId: UUID): FindroidMovie =
        withContext(Dispatchers.IO) {
            jellyfinApi.userLibraryApi
                .getItem(itemId, jellyfinApi.userId!!)
                .content
                .toFindroidMovie(this@JellyfinRepositoryImpl, database)
        }

    override suspend fun getShow(itemId: UUID): FindroidShow =
        withContext(Dispatchers.IO) {
            jellyfinApi.userLibraryApi
                .getItem(itemId, jellyfinApi.userId!!)
                .content
                .toFindroidShow(this@JellyfinRepositoryImpl)
        }

    override suspend fun getSeason(itemId: UUID): FindroidSeason =
        withContext(Dispatchers.IO) {
            jellyfinApi.userLibraryApi
                .getItem(itemId, jellyfinApi.userId!!)
                .content
                .toFindroidSeason(this@JellyfinRepositoryImpl)
        }

    override suspend fun getLibraries(): List<FindroidCollection> =
        withContext(Dispatchers.IO) {
            jellyfinApi.itemsApi.getItems(jellyfinApi.userId!!).content.items.mapNotNull {
                it.toFindroidCollection(this@JellyfinRepositoryImpl)
            }
        }

    override suspend fun getItem(itemId: UUID): FindroidItem? =
        withContext(Dispatchers.IO) {
            jellyfinApi.userLibraryApi
                .getItem(itemId = itemId, userId = jellyfinApi.userId!!)
                .content
                .toFindroidItem(this@JellyfinRepositoryImpl)
        }

    /**
     * 合集（BoxSet）自身在服务器上通常没有图片，取其内部任一条目的图片作为合集封面；取不到时返回 null。
     *
     * 只服务于 [fillBoxSetCovers] 的兜底流程，没有别的调用方，故留在实现内而不进入 [JellyfinRepository]
     * 接口（离线实现本也拿不到合集数据，无需被迫写空实现）。
     */
    private suspend fun getBoxSetCoverImages(boxSetId: UUID): FindroidImages? =
        withContext(Dispatchers.IO) {
            val cacheKey = getBaseUrl() to boxSetId
            boxSetCoverCache[cacheKey]?.let { cached ->
                return@withContext cached.takeIf { it.hasCover }
            }

            val candidates =
                boxSetCoverSemaphore.withPermit {
                    jellyfinApi.itemsApi
                        .getItems(
                            jellyfinApi.userId!!,
                            parentId = boxSetId,
                            recursive = true,
                            limit = BOX_SET_COVER_CANDIDATE_LIMIT,
                        )
                        .content
                        .items
                        .map { it.toFindroidImages(this@JellyfinRepositoryImpl) }
                }

            // 横图卡片优先取 backdrop，先挑有 backdrop 的候选更贴合卡片；都没有再退回任意可用图片
            val cover =
                candidates.firstOrNull { it.backdrop != null }
                    ?: candidates.firstOrNull { it.hasCover }

            // 取不到也缓存，避免同一合集在翻页时被反复试探
            boxSetCoverCache[cacheKey] = cover ?: FindroidImages()
            cover
        }

    override suspend fun getItems(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
        startIndex: Int?,
        limit: Int?,
        genreIds: List<UUID>?,
        studioIds: List<UUID>?,
        tags: List<String>?,
        officialRatings: List<String>?,
        years: List<Int>?,
        personIds: List<UUID>?,
    ): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.itemsApi
                .getItems(
                    jellyfinApi.userId!!,
                    parentId = parentId,
                    includeItemTypes = includeTypes,
                    recursive = recursive,
                    sortBy = listOf(ItemSortBy.fromName(sortBy.sortString)),
                    sortOrder = listOf(ItemSortOrder.fromName(sortOrder.sortString)),
                    startIndex = startIndex,
                    limit = limit,
                    genreIds = genreIds,
                    studioIds = studioIds,
                    tags = tags,
                    officialRatings = officialRatings,
                    years = years,
                    personIds = personIds,
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
                .fillBoxSetCovers()
        }

    /**
     * 用合集内部条目的封面补齐合集自身的封面：服务器上的合集（BoxSet）通常没有图片，直接展示会是空白卡片。
     *
     * 只对确实缺图的合集发起单独试探，多个合集并行但受 [boxSetCoverSemaphore] 限流；已有结果的合集走
     * [boxSetCoverCache]，翻页时不会重复请求。
     */
    private suspend fun List<FindroidItem>.fillBoxSetCovers(): List<FindroidItem> =
        coroutineScope {
            map { item ->
                    async {
                        if (item is FindroidBoxSet && !item.images.hasCover) {
                            item.copy(images = getBoxSetCoverSafely(item.id) ?: item.images)
                        } else {
                            item
                        }
                    }
                }
                .awaitAll()
        }

    /**
     * 试探合集封面，失败时返回 null。
     *
     * 封面补齐只是展示上的可选增强，网络异常不应让整个列表加载失败，因此在此兜底并记录日志。
     */
    private suspend fun getBoxSetCoverSafely(boxSetId: UUID): FindroidImages? =
        try {
            getBoxSetCoverImages(boxSetId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(e, "补齐合集 %s 封面失败", boxSetId)
            null
        }

    override suspend fun getItemsPaging(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
        genreIds: List<UUID>?,
        studioIds: List<UUID>?,
        tags: List<String>?,
        officialRatings: List<String>?,
        years: List<Int>?,
        personIds: List<UUID>?,
    ): Flow<PagingData<FindroidItem>> {
        return Pager(
                config = PagingConfig(pageSize = 10, enablePlaceholders = false),
                pagingSourceFactory = {
                    ItemsPagingSource(
                        jellyfinRepository = this,
                        parentId = parentId,
                        includeTypes = includeTypes,
                        recursive = recursive,
                        sortBy = sortBy,
                        sortOrder = sortOrder,
                        genreIds = genreIds,
                        studioIds = studioIds,
                        tags = tags,
                        officialRatings = officialRatings,
                        years = years,
                        personIds = personIds,
                    )
                },
            )
            .flow
    }

    // 服务端「按 id 取单个 Person 条目」不可靠：实测 Jellyfin 10.10.7 上 /Items/{id} 与
    // /Users/{userId}/Items/{id} 都会长时间无响应或直接 500，而 /Items?ids= 正常返回同一条目，
    // 字段（Name / ImageTags / Overview）足够构造 FindroidPerson，因此这里走查询式接口
    override suspend fun getPerson(personId: UUID): FindroidPerson =
        withContext(Dispatchers.IO) {
            jellyfinApi.itemsApi
                .getItems(
                    jellyfinApi.userId!!,
                    ids = listOf(personId),
                    fields = listOf(ItemFields.OVERVIEW),
                )
                .content
                .items
                .firstOrNull()
                ?.toFindroidPerson(this@JellyfinRepositoryImpl)
                ?: throw NoSuchElementException("服务端没有 id 为 $personId 的人物")
        }

    override suspend fun getPersonItems(
        personIds: List<UUID>,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
    ): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.itemsApi
                .getItems(
                    jellyfinApi.userId!!,
                    personIds = personIds,
                    includeItemTypes = includeTypes,
                    recursive = recursive,
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getGenres(): List<FindroidNamedItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.genresApi
                .getGenres(
                    userId = jellyfinApi.userId!!,
                    limit = NAMED_ITEM_LIMIT,
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                    sortOrder = listOf(ItemSortOrder.ASCENDING),
                )
                .content
                .items
                .map { it.toFindroidNamedItem(this@JellyfinRepositoryImpl) }
        }

    // /Studios 不接受排序参数，返回顺序由服务端决定
    override suspend fun getStudios(): List<FindroidNamedItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.studiosApi
                .getStudios(
                    userId = jellyfinApi.userId!!,
                    limit = NAMED_ITEM_LIMIT,
                )
                .content
                .items
                .map { it.toFindroidNamedItem(this@JellyfinRepositoryImpl) }
        }

    override suspend fun getPersons(
        personTypes: List<String>,
        limit: Int,
        searchTerm: String?,
    ): List<FindroidPerson> =
        withContext(Dispatchers.IO) {
            // 列表页只用姓名与头像，不额外请求 ItemFields：调用方一次可能取 500 人，带上简介会让响应体大出一截
            val persons =
                jellyfinApi.personsApi
                    .getPersons(
                        userId = jellyfinApi.userId!!,
                        limit = limit,
                        searchTerm = searchTerm,
                        personTypes = personTypes,
                    )
                    .content
                    .items

            AppLog.d("人物列表：类型 %s，limit %d，返回 %d 人", personTypes, limit, persons.size)

            persons.map { it.toFindroidPerson(this@JellyfinRepositoryImpl) }
        }

    override suspend fun getFilterValues(): FilterValues =
        withContext(Dispatchers.IO) {
            val filters = jellyfinApi.filterApi.getQueryFiltersLegacy(jellyfinApi.userId!!).content

            // 较新的服务端上该接口可能不再返回这些值，此时对应入口会显示空列表；日志用于区分
            // 「服务端没给值」与「库里确实没有」
            AppLog.d(
                "可用筛选值：标签 %d，分级 %d，年份 %d",
                filters.tags.orEmpty().size,
                filters.officialRatings.orEmpty().size,
                filters.years.orEmpty().size,
            )

            FilterValues(
                // 标签与年份在界面上直接展示，这里就排好序，避免各页面重复处理
                tags = filters.tags.orEmpty().sorted(),
                officialRatings = filters.officialRatings.orEmpty(),
                years = filters.years.orEmpty().sortedDescending(),
            )
        }

    override suspend fun getFavoriteItems(): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.itemsApi
                .getItems(
                    jellyfinApi.userId!!,
                    filters = listOf(ItemFilter.IS_FAVORITE),
                    includeItemTypes =
                        listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE),
                    recursive = true,
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getSearchItems(query: String): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.itemsApi
                .getItems(
                    jellyfinApi.userId!!,
                    searchTerm = query,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    recursive = true,
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getSuggestions(): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.suggestionsApi
                .getSuggestions(
                    jellyfinApi.userId!!,
                    limit = 6,
                    type = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getResumeItems(): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.itemsApi
                .getResumeItems(
                    jellyfinApi.userId!!,
                    limit = 12,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getLatestMedia(parentId: UUID): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.userLibraryApi
                .getLatestMedia(jellyfinApi.userId!!, parentId = parentId, limit = 16)
                .content
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getSeasons(seriesId: UUID, offline: Boolean): List<FindroidSeason> =
        withContext(Dispatchers.IO) {
            if (!offline) {
                jellyfinApi.showsApi.getSeasons(seriesId, jellyfinApi.userId!!).content.items.map {
                    it.toFindroidSeason(this@JellyfinRepositoryImpl)
                }
            } else {
                database.getSeasonsByShowId(seriesId).map {
                    it.toFindroidSeason(database, jellyfinApi.userId!!)
                }
            }
        }

    override suspend fun getNextUp(seriesId: UUID?): List<FindroidEpisode> =
        withContext(Dispatchers.IO) {
            jellyfinApi.showsApi
                .getNextUp(
                    jellyfinApi.userId!!,
                    limit = 24,
                    seriesId = seriesId,
                    enableResumable = false,
                )
                .content
                .items
                .mapNotNull { it.toFindroidEpisode(this@JellyfinRepositoryImpl) }
        }

    override suspend fun getEpisodes(
        seriesId: UUID,
        seasonId: UUID,
        fields: List<ItemFields>?,
        startItemId: UUID?,
        limit: Int?,
        offline: Boolean,
    ): List<FindroidEpisode> =
        withContext(Dispatchers.IO) {
            if (!offline) {
                jellyfinApi.showsApi
                    .getEpisodes(
                        seriesId,
                        jellyfinApi.userId!!,
                        seasonId = seasonId,
                        fields = fields,
                        startItemId = startItemId,
                        limit = limit,
                    )
                    .content
                    .items
                    .mapNotNull { it.toFindroidEpisode(this@JellyfinRepositoryImpl, database) }
            } else {
                database.getEpisodesBySeasonId(seasonId).map {
                    it.toFindroidEpisode(database, jellyfinApi.userId!!)
                }
            }
        }

    override suspend fun getMediaSources(itemId: UUID, includePath: Boolean): List<FindroidSource> =
        withContext(Dispatchers.IO) {
            val sources = mutableListOf<FindroidSource>()
            sources.addAll(
                jellyfinApi.mediaInfoApi
                    .getPostedPlaybackInfo(
                        itemId,
                        PlaybackInfoDto(
                            userId = jellyfinApi.userId!!,
                            deviceProfile =
                                DeviceProfile(
                                    name = "Direct play all",
                                    maxStaticBitrate = 1_000_000_000,
                                    maxStreamingBitrate = 1_000_000_000,
                                    codecProfiles = emptyList(),
                                    containerProfiles = emptyList(),
                                    directPlayProfiles = emptyList(),
                                    transcodingProfiles = emptyList(),
                                    subtitleProfiles =
                                        listOf(
                                            SubtitleProfile("srt", SubtitleDeliveryMethod.EXTERNAL),
                                            SubtitleProfile("ass", SubtitleDeliveryMethod.EXTERNAL),
                                        ),
                                ),
                            maxStreamingBitrate = 1_000_000_000,
                        ),
                    )
                    .content
                    .mediaSources
                    .map { it.toFindroidSource(this@JellyfinRepositoryImpl, itemId, includePath) }
            )
            sources.addAll(database.getSources(itemId).map { it.toFindroidSource(database) })
            sources
        }

    override suspend fun getStreamUrl(itemId: UUID, mediaSourceId: String): String =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.videosApi.getVideoStreamUrl(
                    itemId,
                    static = true,
                    mediaSourceId = mediaSourceId,
                )
            } catch (e: Exception) {
                AppLog.e(e)
                ""
            }
        }

    override suspend fun getSegments(itemId: UUID): List<FindroidSegment> =
        withContext(Dispatchers.IO) {
            val databaseSegments = database.getSegments(itemId).map { it.toFindroidSegment() }

            if (databaseSegments.isNotEmpty()) {
                return@withContext databaseSegments
            }

            try {
                val apiSegments =
                    jellyfinApi.mediaSegmentsApi.getItemSegments(itemId).content.items.map {
                        it.toFindroidSegment()
                    }

                return@withContext apiSegments
            } catch (e: Exception) {
                AppLog.e(e)
                return@withContext emptyList()
            }
        }

    override suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                try {
                    val sources = File(context.filesDir, "trickplay/$itemId").listFiles()
                    if (sources != null) {
                        return@withContext File(sources.first(), index.toString()).readBytes()
                    }
                } catch (_: Exception) {}

                return@withContext jellyfinApi.trickplayApi
                    .getTrickplayTileImage(itemId, width, index)
                    .content
            } catch (_: Exception) {
                return@withContext null
            }
        }

    override suspend fun postCapabilities() {
        AppLog.d("Sending capabilities")
        withContext(Dispatchers.IO) {
            jellyfinApi.sessionApi.postCapabilities(
                playableMediaTypes = listOf(MediaType.VIDEO),
                supportedCommands =
                    listOf(
                        GeneralCommandType.VOLUME_UP,
                        GeneralCommandType.VOLUME_DOWN,
                        GeneralCommandType.TOGGLE_MUTE,
                        GeneralCommandType.SET_AUDIO_STREAM_INDEX,
                        GeneralCommandType.SET_SUBTITLE_STREAM_INDEX,
                        GeneralCommandType.MUTE,
                        GeneralCommandType.UNMUTE,
                        GeneralCommandType.SET_VOLUME,
                        GeneralCommandType.DISPLAY_MESSAGE,
                        GeneralCommandType.PLAY,
                        GeneralCommandType.PLAY_STATE,
                        GeneralCommandType.PLAY_NEXT,
                        GeneralCommandType.PLAY_MEDIA_SOURCE,
                    ),
                supportsMediaControl = true,
            )
        }
    }

    override suspend fun postPlaybackStart(itemId: UUID) {
        AppLog.d("Sending start $itemId")
        withContext(Dispatchers.IO) {
            jellyfinApi.playStateApi.reportPlaybackStart(
                PlaybackStartInfo(
                    itemId = itemId,
                    canSeek = true,
                    isPaused = false,
                    isMuted = false,
                    playMethod = PlayMethod.DIRECT_PLAY,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                )
            )
        }
    }

    override suspend fun postPlaybackStop(
        itemId: UUID,
        positionTicks: Long,
        playedPercentage: Int,
    ) {
        AppLog.d("Sending stop $itemId")
        withContext(Dispatchers.IO) {
            when {
                playedPercentage < 10 -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, 0)
                    database.setPlayed(jellyfinApi.userId!!, itemId, false)
                }
                playedPercentage > 90 -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, 0)
                    database.setPlayed(jellyfinApi.userId!!, itemId, true)
                }
                else -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, positionTicks)
                    database.setPlayed(jellyfinApi.userId!!, itemId, false)
                }
            }
            try {
                jellyfinApi.playStateApi.reportPlaybackStopped(
                    PlaybackStopInfo(itemId = itemId, positionTicks = positionTicks, failed = false)
                )
            } catch (_: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun postPlaybackProgress(
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean,
    ) {
        AppLog.d("Posting progress of $itemId, position: $positionTicks")
        withContext(Dispatchers.IO) {
            database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, positionTicks)
            try {
                jellyfinApi.playStateApi.reportPlaybackProgress(
                    PlaybackProgressInfo(
                        itemId = itemId,
                        canSeek = true,
                        isPaused = isPaused,
                        isMuted = false,
                        playMethod = PlayMethod.DIRECT_PLAY,
                        repeatMode = RepeatMode.REPEAT_NONE,
                        playbackOrder = PlaybackOrder.DEFAULT,
                        positionTicks = positionTicks,
                    )
                )
            } catch (_: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun markAsFavorite(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setFavorite(jellyfinApi.userId!!, itemId, true)
            try {
                jellyfinApi.userLibraryApi.markFavoriteItem(itemId)
            } catch (_: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun unmarkAsFavorite(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setFavorite(jellyfinApi.userId!!, itemId, false)
            try {
                jellyfinApi.userLibraryApi.unmarkFavoriteItem(itemId)
            } catch (_: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun markAsPlayed(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setPlayed(jellyfinApi.userId!!, itemId, true)
            try {
                jellyfinApi.playStateApi.markPlayedItem(itemId)
            } catch (_: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun markAsUnplayed(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setPlayed(jellyfinApi.userId!!, itemId, false)
            try {
                jellyfinApi.playStateApi.markUnplayedItem(itemId)
            } catch (_: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun getItemImages(itemId: UUID): List<FindroidItemImage> =
        withContext(Dispatchers.IO) {
            // 地址为空说明还没连上服务器，拼出来的是没有 scheme / host 的相对路径，
            // 图片只会静默加载失败；这里直接报错，让界面把原因显示出来
            val baseUrl = getBaseUrl()
            check(baseUrl.isNotEmpty()) { "服务器地址为空，无法获取图片" }

            jellyfinApi.imageApi
                .getItemImageInfos(itemId)
                .content
                .map { it.toFindroidItemImage(itemId, baseUrl) }
        }

    override suspend fun deleteItemImages(itemId: UUID, images: List<FindroidItemImage>) {
        withContext(Dispatchers.IO) {
            images
                .groupBy { it.imageType }
                .forEach { (imageType, imagesOfType) ->
                    // 多图类型（如背景图）删掉一张后索引会前移，从大索引往小删才不会漏删；
                    // 单图类型的 imageIndex 为 null，SDK 不带索引时服务端按 0 处理
                    imagesOfType
                        .sortedByDescending { it.imageIndex ?: 0 }
                        .forEach { image ->
                            jellyfinApi.imageApi.deleteItemImage(
                                itemId,
                                imageType,
                                image.imageIndex,
                            )
                        }
                }
        }
    }

    override suspend fun setItemImage(itemId: UUID, imageType: ImageType, imageBytes: ByteArray) {
        withContext(Dispatchers.IO) {
            // 迁就服务端 ImageController 的实现：它拿到请求体后会先做 Base64 解码（CryptoStream +
            // FromBase64Transform），所以必须发 Base64 文本而不是原始字节，否则解码失败、接口回 500。
            // 注意 Content-Type 仍为 image/jpeg（服务端据此决定落盘扩展名），与实际的 ASCII 正文并不一致；
            // 若目标服务端版本取消了解码，这里要改回发送原始字节。
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            jellyfinApi.imageApi.setItemImage(
                itemId,
                imageType,
                base64.toByteArray(Charsets.US_ASCII).toFileInfo(JPEG_MEDIA_TYPE),
            )
        }
    }

    // DELETE /Items/{itemId}：服务端会一并删掉条目记录、媒体文件与关联的图片 / nfo
    override suspend fun deleteItem(itemId: UUID) {
        withContext(Dispatchers.IO) { jellyfinApi.libraryApi.deleteItem(itemId) }
    }

    override suspend fun getItemMetadata(itemId: UUID): ItemMetadataEdit =
        withContext(Dispatchers.IO) {
            val item = getItemForMetadataEdit(itemId)
            ItemMetadataEdit(
                name = item.name.orEmpty(),
                originalTitle = item.originalTitle.orEmpty(),
                overview = item.overview.orEmpty(),
                genres = item.genres.orEmpty(),
                tags = item.tags.orEmpty(),
                studios = item.studios.orEmpty().mapNotNull { it.name },
                productionLocations = item.productionLocations.orEmpty(),
                // 服务端单条目只保存一条标语，读回来自然也最多一条
                tagline = item.taglines.orEmpty().firstOrNull().orEmpty(),
                officialRating = item.officialRating.orEmpty(),
                productionYear = item.productionYear,
                premiereDate = item.premiereDate,
                communityRating = item.communityRating,
            )
        }

    override suspend fun updateItemMetadata(itemId: UUID, metadata: ItemMetadataEdit) {
        require(metadata.name.isNotBlank()) { "标题不能为空" }

        withContext(Dispatchers.IO) {
            // UpdateItem 是覆盖式更新：请求体里没出现的 Name / Overview / ProductionYear 等会被清空，
            // 所以先整份读回来，再只覆盖本弹窗能编辑的字段，其余（刮削 ID、锁定状态……）原样写回
            val item = getItemForMetadataEdit(itemId)

            jellyfinApi.itemUpdateApi.updateItem(
                itemId,
                item.copy(
                    name = metadata.name,
                    // 本表单不编辑演职员，传 null 让服务端保持原有演职员，避免整体重写一遍
                    people = null,
                    originalTitle = metadata.originalTitle.ifBlank { null },
                    overview = metadata.overview.ifBlank { null },
                    genres = metadata.genres,
                    tags = metadata.tags,
                    studios = metadata.studios.map { toStudioNameGuidPair(it, item.studios) },
                    productionLocations = metadata.productionLocations,
                    // 传 null 会被服务端当成「未提供」而保留旧值，清空标语必须显式传空列表
                    taglines =
                        metadata.tagline.ifBlank { null }?.let { tagline -> listOf(tagline) }
                            ?: emptyList(),
                    officialRating = metadata.officialRating.ifBlank { null },
                    productionYear = metadata.productionYear,
                    premiereDate = metadata.premiereDate,
                    communityRating = metadata.communityRating,
                ),
            )
        }
    }

    /**
     * 读取条目用于读取 / 编辑元数据。
     *
     * 除弹窗要编辑的字段外，还必须读回演职员、刮削 ID、锁定状态等：写回时这些字段是整体替换语义，漏读等于删掉。
     */
    private suspend fun getItemForMetadataEdit(itemId: UUID): BaseItemDto {
        // 没有当前用户 ID 时无法保证查回的是本人数据，直接放弃而不是拿一份不可信的数据去覆盖
        val userId = jellyfinApi.userId ?: throw IllegalStateException("未登录，无法读取条目信息")

        return jellyfinApi.itemsApi
            .getItems(userId = userId, ids = listOf(itemId), fields = METADATA_EDIT_FIELDS)
            .content
            .items
            .firstOrNull()
            ?: throw IllegalStateException("服务器上找不到该条目")
    }

    override fun getBaseUrl() = jellyfinApi.api.baseUrl.orEmpty()

    override suspend fun updateDeviceName(name: String) {
        withContext(Dispatchers.IO) {
            jellyfinApi.jellyfin.deviceInfo?.id?.let { id ->
                jellyfinApi.devicesApi.updateDeviceOptions(
                    id,
                    DeviceOptionsDto(0, customName = name),
                )
            }
        }
    }

    override suspend fun getUserConfiguration(): UserConfiguration =
        withContext(Dispatchers.IO) { jellyfinApi.userApi.getCurrentUser().content.configuration!! }

    override suspend fun getDownloads(): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            val items = mutableListOf<FindroidItem>()
            items.addAll(
                database
                    .getMoviesByServerId(appPreferences.getValue(appPreferences.currentServer)!!)
                    .map { it.toFindroidMovie(database, jellyfinApi.userId!!) }
            )
            items.addAll(
                database
                    .getShowsByServerId(appPreferences.getValue(appPreferences.currentServer)!!)
                    .map { it.toFindroidShow(database, jellyfinApi.userId!!) }
            )
            items
        }

    override fun getUserId(): UUID {
        return jellyfinApi.userId!!
    }
}
