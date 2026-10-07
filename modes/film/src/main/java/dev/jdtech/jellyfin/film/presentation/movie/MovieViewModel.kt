package dev.jdtech.jellyfin.film.presentation.movie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.scraper.AvidParser
import dev.jdtech.jellyfin.core.scraper.MetadataScraper
import dev.jdtech.jellyfin.core.scraper.ScrapeProgress
import dev.jdtech.jellyfin.core.scraper.toItemMetadataEdit
import dev.jdtech.jellyfin.film.domain.VideoMetadataParser
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.FindroidItemImage
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.ItemMetadataEdit
import dev.jdtech.jellyfin.models.pickPlaybackSource
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.PersonKind

@HiltViewModel
class MovieViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val videoMetadataParser: VideoMetadataParser,
    private val metadataScraper: MetadataScraper,
) : ViewModel() {
    private val _state = MutableStateFlow(MovieState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<MovieEvent>()
    val events = eventsChannel.receiveAsFlow()

    lateinit var movieId: UUID

    /** 正在跑的抓取任务，用户取消时直接 cancel 掉。 */
    private var scrapeJob: Job? = null

    fun loadMovie(movieId: UUID) {
        this.movieId = movieId
        viewModelScope.launch {
            try {
                val movie = repository.getMovie(movieId)
                // 可选播放来源改用与播放端相同的接口：条目自带的 sources 与 getPostedPlaybackInfo 的顺序未必一致，
                // 而播放端是按下标取源，多这一次请求换「界面下标 = 播放端下标」，不会选错版本；
                // 接口失败时退回条目自带的来源，至少让详情页能正常显示
                val playbackSources =
                    runCatching { repository.getMediaSources(movieId, false) }
                        .getOrElse { e ->
                            AppLog.e(e, "加载可播放来源失败，回退到条目自带来源：%s", movieId)
                            movie.sources
                        }
                logSources(movie, playbackSources)
                // 与播放端选源规则保持一致：解析实际会播放的那个来源，避免详情页展示的清晰度 / 编码与实际播放的不符
                val videoMetadata =
                    playbackSources.pickPlaybackSource()?.let { videoMetadataParser.parse(it) }
                val actors = getActors(movie)
                val director = getDirector(movie)
                val writers = getWriters(movie)
                val displayExtraInfo = appPreferences.getValue(appPreferences.displayExtraInfo)
                _state.emit(
                    _state.value.copy(
                        movie = movie,
                        videoMetadata = videoMetadata,
                        actors = actors,
                        director = director,
                        writers = writers,
                        displayExtraInfo = displayExtraInfo,
                        playbackSources = playbackSources,
                        defaultScrapeKeyword = recognizeKeyword(movie, playbackSources),
                        scrapeProxy = appPreferences.getValue(appPreferences.scrapeProxy),
                    )
                )
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e))
            }
        }
    }

    /**
     * 打印将要用于展示与播放的来源列表，便于核对服务器上的多个版本是否被归入同一条影片。
     *
     * 形如：
     * ```
     * Playback sources of XXX (uuid): 3
     * Source[0] type=REMOTE id=xxx remote=/media/movies/xxx.mkv local=
     * Source[1] type=LOCAL  id=xxx remote= local=/storage/emulated/0/xxx.mkv
     * ```
     */
    private fun logSources(movie: FindroidMovie, sources: List<FindroidSource>) {
        AppLog.d("Playback sources of %s (%s): %d", movie.name, movie.id, sources.size)
        sources.forEachIndexed { index, source ->
            AppLog.d(
                "Source[%d] type=%s id=%s remote=%s local=%s",
                index,
                source.type,
                source.id,
                source.remoteFilePath,
                source.localFilePath,
            )
        }
    }

    /**
     * 猜一个抓取用的番号：优先用已下载的本地文件名，其次用服务器上的路径，最后退回标题。
     *
     * 识别不到时返回空串，界面上就是个空的关键词输入框，由用户自己填。
     */
    private fun recognizeKeyword(movie: FindroidMovie, sources: List<FindroidSource>): String {
        val path =
            sources
                .firstOrNull { it.type == FindroidSourceType.LOCAL && it.localFilePath.isNotBlank() }
                ?.localFilePath
                ?: sources.firstOrNull { it.remoteFilePath.isNotBlank() }?.remoteFilePath
        return AvidParser.getDvdId(path ?: movie.name)
    }

    private suspend fun getActors(item: FindroidMovie): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.filter { it.type == PersonKind.ACTOR }
        }
    }

    private suspend fun getDirector(item: FindroidMovie): FindroidItemPerson? {
        return withContext(Dispatchers.Default) {
            item.people.firstOrNull { it.type == PersonKind.DIRECTOR }
        }
    }

    private suspend fun getWriters(item: FindroidMovie): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.filter { it.type == PersonKind.WRITER }
        }
    }

    fun onAction(action: MovieAction) {
        when (action) {
            is MovieAction.MarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsPlayed(movieId)
                    loadMovie(movieId)
                }
            }
            is MovieAction.UnmarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsUnplayed(movieId)
                    loadMovie(movieId)
                }
            }
            is MovieAction.MarkAsFavorite -> {
                viewModelScope.launch {
                    repository.markAsFavorite(movieId)
                    loadMovie(movieId)
                }
            }
            is MovieAction.UnmarkAsFavorite -> {
                viewModelScope.launch {
                    repository.unmarkAsFavorite(movieId)
                    loadMovie(movieId)
                }
            }
            is MovieAction.DeleteItemImages -> {
                deleteItemImages(action.images)
            }
            is MovieAction.UpdateItemMetadata -> {
                updateItemMetadata(action.metadata)
            }
            is MovieAction.DeleteItemWithFiles -> {
                deleteItemWithFiles()
            }
            is MovieAction.ScrapeMetadata -> {
                scrapeMetadata(action.keyword)
            }
            is MovieAction.DismissScrapeFailure -> {
                viewModelScope.launch {
                    _state.emit(_state.value.copy(scrapeFailed = false, scrapeSteps = emptyList()))
                }
            }
            is MovieAction.CancelScrape -> {
                cancelScrape()
            }
            is MovieAction.UpdateScrapeProxy -> {
                updateScrapeProxy(action.address)
            }
            else -> Unit
        }
    }

    /** 加载服务器上的图片列表，供删除前逐张列出确认。 */
    fun loadItemImages() {
        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoadingItemImages = true, itemImagesError = null))
            try {
                _state.emit(
                    _state.value.copy(
                        itemImages = repository.getItemImages(movieId),
                        isLoadingItemImages = false,
                    )
                )
            } catch (e: Exception) {
                AppLog.e(e, "加载服务器图片列表失败：%s", movieId)
                // 失败时清掉上一次的列表，避免弹窗里继续显示可能已被删掉的旧图片
                // 失败原因只落到 state：弹窗此时是开着的，直接在弹窗里显示即可，不再额外弹 Toast
                _state.emit(
                    _state.value.copy(
                        itemImages = emptyList(),
                        isLoadingItemImages = false,
                        itemImagesError = e,
                    )
                )
            }
        }
    }

    /** 加载服务器上可编辑的 nfo 元数据，供「编辑 nfo」弹窗回填。 */
    fun loadItemMetadata() {
        viewModelScope.launch {
            _state.emit(
                _state.value.copy(
                    isLoadingItemMetadata = true,
                    itemMetadataError = null,
                    // 每进一次弹窗都从服务器重新取，上次抓取留下的未保存结果一并清掉
                    scrapedMetadata = null,
                    scrapeFailed = false,
                    scrapeSteps = emptyList(),
                )
            )
            try {
                _state.emit(
                    _state.value.copy(
                        itemMetadata = repository.getItemMetadata(movieId),
                        isLoadingItemMetadata = false,
                    )
                )
            } catch (e: Exception) {
                AppLog.e(e, "加载服务器元数据失败：%s", movieId)
                // 失败原因只落到 state：弹窗此时是开着的，直接在弹窗里显示即可，不再额外弹 Toast
                _state.emit(
                    _state.value.copy(
                        itemMetadata = null,
                        isLoadingItemMetadata = false,
                        itemMetadataError = e,
                    )
                )
            }
        }
    }

    /** 删除服务器上选中的封面图片，成功后刷新页面让封面回退到下一张。 */
    private fun deleteItemImages(images: List<FindroidItemImage>) {
        viewModelScope.launch {
            try {
                repository.deleteItemImages(movieId, images)
                // 列表里的图片已被删掉，先清空；下次打开弹窗会重新拉取
                _state.emit(_state.value.copy(itemImages = emptyList()))
                eventsChannel.send(MovieEvent.ItemImagesDeleted)
                loadMovie(movieId)
            } catch (e: Exception) {
                AppLog.e(e, "删除服务器封面失败：%s", movieId)
                eventsChannel.send(MovieEvent.ItemImagesDeleteFailed(e))
            }
        }
    }

    /** 用弹窗提交的内容覆盖服务器上的 nfo。 */
    private fun updateItemMetadata(metadata: ItemMetadataEdit) {
        viewModelScope.launch {
            try {
                repository.updateItemMetadata(movieId, metadata)
                // 元数据已变，清掉表单缓存，避免下次打开弹窗看到旧值
                _state.emit(_state.value.copy(itemMetadata = null))
                eventsChannel.send(MovieEvent.MetadataUpdated)
                loadMovie(movieId)
            } catch (e: Exception) {
                AppLog.e(e, "更新服务器 nfo 失败：%s", movieId)
                eventsChannel.send(MovieEvent.MetadataUpdateFailed(e))
            }
        }
    }

    /**
     * 以 [keyword] 为关键词到各站点抓取 nfo 元数据。
     *
     * 抓到的内容只落进 state 供弹窗回填，不写服务器；用户确认后才会走 [updateItemMetadata]。
     * 所有站点都没抓到时不抛异常，只把 [MovieState.scrapeSteps] 里的失败原因留在 state 里给用户看。
     */
    private fun scrapeMetadata(keyword: String) {
        // 上一次可能还没跑完，先取消，避免两份进度混在同一份 state 里
        scrapeJob?.cancel()
        scrapeJob =
            // 抓取是「发请求 + 解析 HTML」，解析阶段是纯 CPU 活，整段放到 IO 线程，别占着主线程
            viewModelScope.launch(Dispatchers.IO) {
                _state.emit(
                    _state.value.copy(
                        isScraping = true,
                        scrapeSteps = emptyList(),
                        scrapeFailed = false,
                        scrapedMetadata = null,
                    )
                )
                val steps = mutableListOf<ScrapeProgress>()
                val scraped =
                    try {
                        metadataScraper.scrape(keyword) { progress ->
                            steps += progress
                            _state.emit(_state.value.copy(scrapeSteps = steps.toList()))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 抓取器内部已把单站失败转成进度，这里兜住兜底异常，不让详情页崩掉
                        AppLog.e(e, "抓取元数据出错：%s", keyword)
                        null
                    }
                // 标题兜底传番号：站点页面标题只有番号时，剥掉番号会留下空标题，表单会卡在必填校验
                _state.emit(
                    _state.value.copy(
                        isScraping = false,
                        scrapeFailed = scraped == null,
                        scrapedMetadata = scraped?.toItemMetadataEdit(keyword.trim()),
                    )
                )
            }
    }

    /**
     * 用户中途取消抓取。
     *
     * 协程取消是协作式的：正在进行的那次 OkHttp 请求会等它自己超时或返回，但界面立刻收起进度弹窗，
     * 抓取循环也会在下一个挂起点退出，不会再往 state 里回填进度。
     */
    private fun cancelScrape() {
        scrapeJob?.cancel()
        scrapeJob = null
        viewModelScope.launch {
            _state.emit(
                _state.value.copy(
                    isScraping = false,
                    scrapeSteps = emptyList(),
                    scrapeFailed = false,
                )
            )
        }
    }

    /** 保存抓取专用的本地代理，只影响之后的抓取请求。 */
    private fun updateScrapeProxy(address: String) {
        appPreferences.setValue(appPreferences.scrapeProxy, address)
        viewModelScope.launch { _state.emit(_state.value.copy(scrapeProxy = address)) }
    }

    /** 删除服务器上的条目本身，界面收到事件后返回上一页。 */
    private fun deleteItemWithFiles() {
        viewModelScope.launch {
            try {
                repository.deleteItem(movieId)
                eventsChannel.send(MovieEvent.ItemDeleted)
            } catch (e: Exception) {
                AppLog.e(e, "删除服务器条目失败：%s", movieId)
                eventsChannel.send(MovieEvent.ItemDeleteFailed(e))
            }
        }
    }
}
