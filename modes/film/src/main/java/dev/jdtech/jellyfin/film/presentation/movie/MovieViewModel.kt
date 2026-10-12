package dev.jdtech.jellyfin.film.presentation.movie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.scraper.AvidParser
import dev.jdtech.jellyfin.core.scraper.ImageScraper
import dev.jdtech.jellyfin.core.scraper.MetadataScraper
import dev.jdtech.jellyfin.core.scraper.ScrapedImage
import dev.jdtech.jellyfin.core.scraper.ScraperSite
import dev.jdtech.jellyfin.core.scraper.SiteImageScrapeResult
import dev.jdtech.jellyfin.core.scraper.SiteMetadataScrapeResult
import dev.jdtech.jellyfin.core.scraper.toItemMetadataEdit
import dev.jdtech.jellyfin.core.translator.MetadataTranslator
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
import dev.jdtech.jellyfin.settings.domain.models.TranslateSettings
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
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
    private val imageScraper: ImageScraper,
    private val translator: MetadataTranslator,
) : ViewModel() {
    // 抓取站点来自本地配置（BuildConfig），整个会话内不会变，直接放进初始 state，
    // 免得打开「编辑封面」/「编辑 nfo」弹窗时要等一次异步加载、先闪一下「没配站点」的空态；
    // 翻译设置同理，只取决于本地偏好，与具体影片无关
    private val _state =
        MutableStateFlow(
            MovieState(
                imageScrapeSites = imageScraper.sites,
                metadataScrapeSites = metadataScraper.sites,
                translateSettings = appPreferences.getTranslateSettings(),
            )
        )
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<MovieEvent>()
    val events = eventsChannel.receiveAsFlow()

    lateinit var movieId: UUID

    /** 正在跑的抓取任务，用户取消时直接 cancel 掉。 */
    private var scrapeJob: Job? = null

    /** 正在进行的抓取使用的关键词；取消时要把已抓到的结果备成表单（标题兜底用），结束后即失效。 */
    private var activeScrapeKeyword: String = ""

    /** 正在跑的图片抓取任务，用户取消时直接 cancel 掉。 */
    private var imageScrapeJob: Job? = null

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
            is MovieAction.SelectScrapeSource -> {
                selectScrapeSource(action.site)
            }
            is MovieAction.DismissScrapeFailure -> {
                viewModelScope.launch {
                    _state.emit(
                        _state.value.copy(scrapeFailed = false, metadataScrapeResults = emptyList())
                    )
                }
            }
            is MovieAction.CancelScrape -> {
                cancelScrape()
            }
            is MovieAction.UpdateScrapeProxy -> {
                updateScrapeProxy(action.address)
            }
            is MovieAction.UpdateTranslateSettings -> {
                updateTranslateSettings(action.settings)
            }
            is MovieAction.TranslateMetadata -> {
                translateExistingMetadata(action.metadata, action.settings)
            }
            is MovieAction.ScrapeItemImages -> {
                scrapeItemImages(action.keyword)
            }
            is MovieAction.CloseImageScrape -> {
                closeImageScrape()
            }
            is MovieAction.UploadScrapedImages -> {
                uploadScrapedImages(action.images)
            }
            else -> Unit
        }
    }

    /** 加载服务器上的图片列表，供「编辑封面」弹窗以「当前服务器使用」列出。 */
    fun loadItemImages() {
        viewModelScope.launch {
            _state.emit(
                _state.value.copy(
                    isLoadingItemImages = true,
                    itemImagesError = null,
                    // 每进一次弹窗都从服务器重新取，上一轮抓取留下的结果一并清掉（图片都在内存里）
                    imageScrapeResults = emptyList(),
                    isScrapingImages = false,
                )
            )
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
                    scrapedSiteMetadata = emptyList(),
                    selectedScrapeSite = null,
                    scrapeFailed = false,
                    metadataScrapeResults = emptyList(),
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
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(e, "更新服务器 nfo 失败：%s", movieId)
                eventsChannel.send(MovieEvent.MetadataUpdateFailed(e))
            }
        }
    }

    /**
     * 以 [keyword] 为关键词到各站点抓取 nfo 元数据。
     *
     * 各站点**同时**抓一遍，成功的结果都备进 state：弹窗里点站点名即可切换成那一站的内容。
     * 抓到的内容不写服务器，用户确认后才会走 [updateItemMetadata]。所有站点都没抓到时不抛异常，
     * 只把 [MovieState.metadataScrapeResults] 里的失败原因留在 state 里给用户看。
     * 用户中途取消由 [cancelScrape] 按「部分成功」收尾。
     */
    private fun scrapeMetadata(keyword: String) {
        // 上一次可能还没跑完，先取消，避免两份结果混在同一份 state 里
        scrapeJob?.cancel()
        val trimmed = keyword.trim()
        activeScrapeKeyword = trimmed
        scrapeJob =
            // 抓取是「发请求 + 解析 HTML」，解析阶段是纯 CPU 活，整段放到 IO 线程，别占着主线程
            viewModelScope.launch(Dispatchers.IO) {
                _state.emit(
                    _state.value.copy(
                        isScraping = true,
                        metadataScrapeResults = emptyList(),
                        scrapedMetadata = null,
                        scrapedSiteMetadata = emptyList(),
                        selectedScrapeSite = null,
                        scrapeFailed = false,
                        // 上一轮的翻译提示可能因为中途取消没清掉，这里统一归零
                        isTranslating = false,
                    )
                )
                val results = mutableListOf<SiteMetadataScrapeResult>()
                val scraped =
                    try {
                        metadataScraper.scrape(trimmed) { result ->
                            // 用户已取消就不再回填：迟到的成功会把取消时补上的「已取消」失败冲掉
                            if (!coroutineContext.isActive) return@scrape
                            results += result
                            _state.emit(_state.value.copy(metadataScrapeResults = results.toList()))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 抓取器内部已把单站失败转成结果，这里兜住兜底异常，不让详情页崩掉
                        AppLog.e(e, "抓取元数据出错：%s", trimmed)
                        emptyList()
                    }
                val prepared = prepareScrapedSites(scraped, trimmed)
                // 翻译期间沿用 isScraping = true：抓取进度弹窗是靠 scrapedMetadata 变成非空才关掉的，
                // 提前置回 false 会让它短暂显示「没抓到任何信息」的总结，用户也可能在这几秒里白编辑表单
                val translated = translateScrapedSites(prepared)
                _state.emit(
                    _state.value.copy(
                        isScraping = false,
                        isTranslating = false,
                        scrapeFailed = prepared.isEmpty(),
                        metadataScrapeResults = results.toList(),
                        scrapedSiteMetadata = translated,
                        // 默认用站点顺序里第一个成功的站点：与「逐站尝试、第一个成功就用」的老行为一致
                        selectedScrapeSite = translated.firstOrNull()?.site,
                        scrapedMetadata = translated.firstOrNull()?.metadata,
                    )
                )
            }
    }

    /**
     * 切换「编辑 nfo」弹窗里的内容来源。
     *
     * [site] 为某个成功抓到的站点时把该站点的结果填进表单；为 null（选项行里的「原来」）或列表里
     * 找不到该站点时，[MovieState.scrapedMetadata] 变回 null，弹窗会重新按服务器元数据回填。
     */
    private fun selectScrapeSource(site: ScraperSite?) {
        viewModelScope.launch {
            val scraped = _state.value.scrapedSiteMetadata.firstOrNull { it.site == site }
            _state.emit(
                _state.value.copy(
                    scrapedMetadata = scraped?.metadata,
                    selectedScrapeSite = scraped?.site,
                )
            )
        }
    }

    /**
     * 用户中途取消抓取。取消也按「部分成功」处理：已抓到结果的站点照常备进表单（与正常完成一致），
     * 还没出结果的站点补成「已取消」的失败；一个都没抓到时弹窗保留，让用户看清各站点的情况。
     *
     * 协程取消是协作式的：正在进行的那几次 OkHttp 请求会等它自己超时或返回，但各站点的抓取协程
     * 会在下一个挂起点退出、结果不再回填 state（回调里有守卫）。取消时不等翻译（翻译中的请求随
     * 协程一起取消），备进表单的是各站点原文，需要可用「翻译现有」手动翻。
     */
    private fun cancelScrape() {
        scrapeJob?.cancel()
        scrapeJob = null
        viewModelScope.launch {
            // 还没出结果的站点补成失败，进度列表里每个配了地址的站点都有交代
            val known = _state.value.metadataScrapeResults
            val completed =
                known +
                    _state.value.metadataScrapeSites
                        .filter { site -> known.none { it.site == site } }
                        .map { site ->
                            SiteMetadataScrapeResult.Failure(
                                site,
                                SiteMetadataScrapeResult.CANCELLED_REASON,
                            )
                        }
            val prepared =
                prepareScrapedSites(
                    completed.filterIsInstance<SiteMetadataScrapeResult.Success>(),
                    activeScrapeKeyword,
                )
            _state.emit(
                _state.value.copy(
                    isScraping = false,
                    isTranslating = false,
                    metadataScrapeResults = completed,
                    scrapeFailed = prepared.isEmpty(),
                    scrapedSiteMetadata = prepared,
                    selectedScrapeSite = prepared.firstOrNull()?.site,
                    scrapedMetadata = prepared.firstOrNull()?.metadata,
                )
            )
        }
    }

    /** 保存抓取专用的本地代理，只影响之后的抓取请求。 */
    private fun updateScrapeProxy(address: String) {
        appPreferences.setValue(appPreferences.scrapeProxy, address)
        viewModelScope.launch { _state.emit(_state.value.copy(scrapeProxy = address)) }
    }

    /** 保存翻译设置；只落本地偏好，不动已经抓到的内容。 */
    private fun updateTranslateSettings(settings: TranslateSettings) {
        appPreferences.setTranslateSettings(settings)
        viewModelScope.launch { _state.emit(_state.value.copy(translateSettings = settings)) }
    }

    /**
     * 抓取后自动翻译各站点抓到的标题 / 简介。
     *
     * 逐站各发一次请求（每站「标题 + 简介」合成一次），站点之间并发——弹窗里切到哪个站点都要能
     * 立刻看到译文。关掉自动翻译、设置没填全、或要翻的字段都是空的，都直接返回原文。
     * 某一站翻不出来就保留该站原文（抓到的内容比译文重要），有站点失败时提示一次、原因取第一个。
     */
    private suspend fun translateScrapedSites(
        sites: List<ScrapedSiteMetadata>,
    ): List<ScrapedSiteMetadata> {
        val settings = _state.value.translateSettings
        if (!settings.autoTranslate || !settings.isConfigured) return sites
        val jobs =
            sites.mapNotNull { site ->
                val requested = translatableFields(site.metadata, settings)
                if (requested.isEmpty()) null else site to requested
            }
        if (jobs.isEmpty()) return sites
        // 翻译要跑好几秒，先让进度弹窗把提示切成「正在翻译」，别让用户以为卡在抓取上
        _state.emit(_state.value.copy(isTranslating = true))
        val results =
            coroutineScope {
                jobs
                    .map { (site, requested) ->
                        async { site.site to translateFields(site.metadata, requested) }
                    }
                    .awaitAll()
            }
        // 一次故障往往几个站点一起失败，只提示一次，免得连着弹好几个同样的 Toast
        results
            .mapNotNull { (_, result) ->
                when (result) {
                    is SiteTranslation.Failure -> result.error
                    is SiteTranslation.Success -> null
                }
            }
            .firstOrNull()
            ?.let { error -> eventsChannel.send(MovieEvent.MetadataTranslateFailed(error)) }
        // 没翻出来的站点原样返回：切到它时看到的是站点原文
        val translatedBySite =
            results
                .mapNotNull { (site, result) ->
                    when (result) {
                        is SiteTranslation.Success -> site to result.metadata
                        is SiteTranslation.Failure -> null
                    }
                }
                .toMap()
        return sites.map { site ->
            translatedBySite[site.site]?.let { translated -> site.copy(metadata = translated) } ?: site
        }
    }

    /**
     * 「翻译现有」：用弹窗里刚填好的设置，把编辑 nfo 表单里的标题 / 简介翻一遍再填回去。
     *
     * 与抓取后的自动翻译共用同一条链路，区别在内容来源与失败处理：这里翻不出来就什么都不动，
     * 用户手里的内容原样留着，也不标成「已更新（未保存）」。翻成功则把当前表单标成抓取内容，
     * 并把当前来源站点的备好内容一并换成译文——否则切走再切回来看到的还是没有翻过的旧内容。
     */
    private fun translateExistingMetadata(metadata: ItemMetadataEdit, settings: TranslateSettings) {
        viewModelScope.launch {
            appPreferences.setTranslateSettings(settings)
            _state.emit(_state.value.copy(translateSettings = settings))
            // 已经在翻就只落设置，不再叠一次请求：两次结果会把同一个表单先后覆盖一遍
            if (_state.value.isTranslating) return@launch
            val requested = translatableFields(metadata, settings)
            if (requested.isEmpty()) return@launch
            _state.emit(_state.value.copy(isTranslating = true))
            when (val translated = translateFields(metadata, requested)) {
                is SiteTranslation.Success ->
                    _state.emit(
                        _state.value.copy(
                            isTranslating = false,
                            scrapedMetadata = translated.metadata,
                            scrapedSiteMetadata = replaceSelectedSiteContent(translated.metadata),
                        )
                    )
                is SiteTranslation.Failure -> {
                    eventsChannel.send(MovieEvent.MetadataTranslateFailed(translated.error))
                    _state.emit(_state.value.copy(isTranslating = false))
                }
            }
        }
    }

    /** 把「翻译现有」的译文写回当前来源站点的备好内容；当前来源是「原来」时列表原样返回。 */
    private fun replaceSelectedSiteContent(translated: ItemMetadataEdit): List<ScrapedSiteMetadata> {
        val selected = _state.value.selectedScrapeSite ?: return _state.value.scrapedSiteMetadata
        return _state.value.scrapedSiteMetadata.map { site ->
            if (site.site == selected) site.copy(metadata = translated) else site
        }
    }

    /** 按设置挑出要翻译的字段（「字段名 -> 原文」）；没勾或原文为空都不放进来。 */
    private fun translatableFields(
        metadata: ItemMetadataEdit,
        settings: TranslateSettings,
    ): Map<String, String> =
        buildMap {
            if (settings.translateTitle && metadata.name.isNotBlank()) put(FIELD_NAME, metadata.name)
            if (settings.translatePlot && metadata.overview.isNotBlank()) {
                put(FIELD_OVERVIEW, metadata.overview)
            }
        }

    /**
     * 调接口翻译 [requested]，把译文落回 [metadata]；响应里缺哪个字段就用哪个字段的原文。
     *
     * 请求失败时返回失败结果（原因已记日志），由调用方决定是退回原文、保留现有内容，还是提示用户。
     */
    private suspend fun translateFields(
        metadata: ItemMetadataEdit,
        requested: Map<String, String>,
    ): SiteTranslation =
        try {
            val translated = translator.translate(requested)
            SiteTranslation.Success(
                metadata.copy(
                    name = translated[FIELD_NAME] ?: metadata.name,
                    overview = translated[FIELD_OVERVIEW] ?: metadata.overview,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(e, "翻译元数据失败：%s", movieId)
            SiteTranslation.Failure(e)
        }

    /** 一次翻译的结果：要么拿到译文，要么留下失败原因（原因已记日志），由调用方决定怎么兜底。 */
    private sealed interface SiteTranslation {
        data class Success(val metadata: ItemMetadataEdit) : SiteTranslation

        data class Failure(val error: Exception) : SiteTranslation
    }

    /**
     * 以 [keyword] 为关键词抓取封面图片（横图 + 竖图），配了地址的站点并发尝试一遍，互不等待。
     *
     * 抓到的图片只留在内存里供界面展示与逐站上传，不写服务器；用户点某一行的「上传」才走
     * [uploadScrapedImages]。抓取失败不抛异常，各站点的失败原因随结果一起交给界面展示。
     */
    private fun scrapeItemImages(keyword: String) {
        // 上一次可能还没跑完，先取消，避免两份结果混在同一份 state 里
        imageScrapeJob?.cancel()
        imageScrapeJob =
            viewModelScope.launch {
                // 先切成「抓取中」再切到 IO：状态不等调度器派发就写下去，界面才不会先闪一下空态
                _state.emit(
                    _state.value.copy(isScrapingImages = true, imageScrapeResults = emptyList())
                )
                val results = mutableListOf<SiteImageScrapeResult>()
                // 抓页面 + 下载图片都是纯网络 IO，放到 IO 线程，别占着主线程
                withContext(Dispatchers.IO) {
                    try {
                        imageScraper.scrapeImages(keyword) { result ->
                            results += result
                            _state.emit(_state.value.copy(imageScrapeResults = results.toList()))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 抓取器内部已把单站失败转成结果，这里兜住兜底异常，不让详情页崩掉
                        AppLog.e(e, "抓取封面图片出错：%s", keyword)
                    }
                }
                _state.emit(_state.value.copy(isScrapingImages = false))
            }
    }

    /** 关闭抓取结果弹窗：中断进行中的抓取，并丢掉已抓到的图片（都只在内存里）。 */
    private fun closeImageScrape() {
        imageScrapeJob?.cancel()
        imageScrapeJob = null
        viewModelScope.launch {
            _state.emit(
                _state.value.copy(isScrapingImages = false, imageScrapeResults = emptyList())
            )
        }
    }

    /**
     * 把抓到的结果备成表单内容：转 [ItemMetadataEdit]、与服务器现值按字段合并。
     *
     * 标题兜底：站点连番号和片名都给不出时，优先保留服务器上的现标题，别拿番号占位冲掉它；
     * 服务器标题也没有（或没加载出来）才用抓取关键词，保证必填的标题不至于卡住确认按钮。
     */
    private fun prepareScrapedSites(
        results: List<SiteMetadataScrapeResult>,
        keyword: String,
    ): List<ScrapedSiteMetadata> {
        if (results.isEmpty()) return emptyList()
        val fallbackTitle = _state.value.itemMetadata?.name?.takeIf { it.isNotBlank() } ?: keyword
        return results
            .filterIsInstance<SiteMetadataScrapeResult.Success>()
            .map { result ->
                ScrapedSiteMetadata(
                    site = result.site,
                    metadata = result.movie.toItemMetadataEdit(fallbackTitle).mergeWithCurrent(),
                )
            }
    }

    /**
     * 抓取结果与服务器现值按字段合并：站点没给的字段保留服务器上的现值，不照单全收清掉已有信息。
     *
     * [ItemMetadataEdit] 的字段是「留空即清空」的整体替换语义，而各站点的信息覆盖面不一
     * （如 Jav321 不给演员列表），直接保存会丢掉服务器上已有的内容；
     * 站点给了值就以站点为准（用户选这个站点就是想要它的内容），没给才回退现值。
     * 服务器元数据没加载出来时只能兜住演职员（详情页的人员列表还在），其余字段没有可回退的现值。
     */
    private fun ItemMetadataEdit.mergeWithCurrent(): ItemMetadataEdit {
        val current =
            _state.value.itemMetadata
                ?: ItemMetadataEdit(
                    actresses = _state.value.actors.map { it.name },
                    directors = listOfNotNull(_state.value.director?.name),
                )
        return copy(
            name = name.ifBlank { current.name },
            originalTitle = originalTitle.ifBlank { current.originalTitle },
            overview = overview.ifBlank { current.overview },
            genres = genres.ifEmpty { current.genres },
            tags = tags.ifEmpty { current.tags },
            actresses = actresses.ifEmpty { current.actresses },
            directors = directors.ifEmpty { current.directors },
            studios = studios.ifEmpty { current.studios },
            productionLocations = productionLocations.ifEmpty { current.productionLocations },
            tagline = tagline.ifBlank { current.tagline },
            officialRating = officialRating.ifBlank { current.officialRating },
            productionYear = productionYear ?: current.productionYear,
            premiereDate = premiereDate ?: current.premiereDate,
            communityRating = communityRating ?: current.communityRating,
        )
    }

    /**
     * 把 [images]（用户选中某个站点抓到的横图 / 竖图）上传到服务器，成功后刷新页面让新封面生效。
     *
     * 上传前先删掉服务器上同类型的旧图：`setItemImage` 只覆盖同类图片的第 0 张，
     * 不先删的话原有的多张背景（或同类多张）会和新图并存，看起来就像「老图没被替换」。
     * 只删与本次上传同类型的图，没抓取的类型（如 Logo）保持不动。
     */
    private fun uploadScrapedImages(images: List<ScrapedImage>) {
        if (images.isEmpty() || _state.value.isUploadingImages) return
        val scrapedTypes = images.map { it.imageType }.toSet()
        viewModelScope.launch {
            _state.emit(_state.value.copy(isUploadingImages = true))
            try {
                // 重新取一次服务器列表而不是用 state 里的缓存：用户可能刚点过「清理」
                val replacedImages =
                    repository.getItemImages(movieId).filter { it.imageType in scrapedTypes }
                if (replacedImages.isNotEmpty()) {
                    repository.deleteItemImages(movieId, replacedImages)
                }
                images.forEach { image ->
                    repository.setItemImage(movieId, image.imageType, image.bytes)
                }
                // 刷新「当前服务器使用」，编辑封面弹窗里立刻能看到刚上传的图
                refreshItemImages()
                eventsChannel.send(MovieEvent.ItemImagesUploaded)
                loadMovie(movieId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(e, "上传抓取到的封面图片失败：%s", movieId)
                eventsChannel.send(MovieEvent.ItemImagesUploadFailed(e))
            } finally {
                _state.emit(_state.value.copy(isUploadingImages = false))
            }
        }
    }

    /** 只重新拉取服务器图片列表，不动抓取结果（上传后刷新「当前服务器使用」用）。 */
    private suspend fun refreshItemImages() {
        try {
            _state.emit(
                _state.value.copy(
                    itemImages = repository.getItemImages(movieId),
                    itemImagesError = null,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(e, "刷新服务器图片列表失败：%s", movieId)
        }
    }

    /** 删除服务器上的条目本身，界面收到事件后返回上一页。 */
    private fun deleteItemWithFiles() {
        viewModelScope.launch {
            try {
                repository.deleteItem(movieId)
                eventsChannel.send(MovieEvent.ItemDeleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(e, "删除服务器条目失败：%s", movieId)
                eventsChannel.send(MovieEvent.ItemDeleteFailed(e))
            }
        }
    }

    private companion object {
        /** 一次翻译请求里区分标题与简介的键名，接口按同样的键把译文回传。 */
        const val FIELD_NAME = "title"
        const val FIELD_OVERVIEW = "plot"
    }
}
