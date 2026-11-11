package dev.jdtech.jellyfin.core.subtitle

import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 「下载字幕」的状态机：搜索 / 逐条下载 / 上传 / 落盘对照快照。
 *
 * 抽到 `:core` 是为了让详情页与播放页共用同一套 UI 与流程。**刻意不加 `@Singleton`**：
 * 两处各注入一份自己的实例，否则一边的候选列表与「上传中」状态会污染另一边。
 *
 * 协程作用域由持有方传入（详情页用 `viewModelScope`，播放页用 `lifecycleScope`），
 * controller 自己不持有不受管的 job；[cancel] 与 [clear] 会同步中断正在跑的任务。
 */
class SubtitleDownloadController
@Inject
constructor(
    private val downloader: SubtitleDownloader,
    private val repository: JellyfinRepository,
    private val compareCache: SubtitleCompareCache,
) {

    /** 一批候选固定归属于服务器、用户与条目，代次用于拒绝迟到结果。 */
    data class Target(val server: String, val userId: UUID, val itemId: UUID, val generation: Long)

    private var generation = 0L
    private var batchTarget: Target? = null
    private val compareMutex = Mutex()

    private fun targetFor(itemId: UUID) = Target(repository.getBaseUrl(), repository.getUserId(), itemId, generation)

    fun isCurrentTarget(target: Target, itemId: UUID): Boolean = target == targetFor(itemId)

    private val _state = MutableStateFlow(SubtitleDownloadState())
    val state: StateFlow<SubtitleDownloadState> = _state.asStateFlow()

    private val eventsChannel = Channel<SubtitleDownloadEvent>()
    val events: Flow<SubtitleDownloadEvent> = eventsChannel.receiveAsFlow()

    /** 正在跑的候选搜索 / 下载任务。 */
    private var downloadJob: Job? = null

    /** 正在跑的上传任务。 */
    private var uploadJob: Job? = null

    /** 已下载的候选字幕（按候选定位），只留在内存里；上传成功或取消后即丢弃。 */
    private var downloadedSubtitles: Map<SubtitleCandidate, DownloadedSubtitle> = emptyMap()

    /**
     * 正在对照的条目 id；非 null 表示用户已经点过「播放视频时对比」。
     *
     * 期间每下完一条候选就补进快照（[SubtitleCompareCache.upsert]），这样先比着看、后到的字幕
     * 也能自己出现在面板上，不必让用户重开面板。
     */
    private var compareItemId: UUID? = null

    /**
     * 本批对照快照的批次令牌，[compareItemId] 非 null 时有效。
     *
     * 详情页与播放页各有一份 controller，却共用同一个单例缓存；快照被别处 clear 过
     * （另一边点了新一轮对比、退出播放页等）时令牌即过期，剩余候选的追加写入会被
     * [SubtitleCompareCache.upsert] 拒绝，不会把旧字幕按行号顶进新一轮的快照。
     */
    private var compareBatchToken: Long = 0L

    /**
     * 以 [keyword] 搜索并逐条下载候选，结果流式写进 [state]。
     *
     * 语言优先级与时长容差判据都在 [SubtitleDownloader] 里，这里只负责状态与（用户选定后的）上传。
     * [videoDurationMs] 传 0 表示视频时长未知，界面显示「未知」。
     */
    fun download(scope: CoroutineScope, keyword: String, videoDurationMs: Long, itemId: UUID) {
        if (keyword.isBlank()) return
        cancelJobs()
        batchTarget = targetFor(itemId)
        val currentGeneration = generation
        downloadedSubtitles = emptyMap()
        // 新一批候选重新开始：之前那次对照的关联就此断开（用户要对比得再点一次）
        invalidateCompareBatch()
        _state.value =
            SubtitleDownloadState(stage = SubtitleDownloadStage.Searching)
        downloadJob =
            scope.launch {
                try {
                    downloader
                        .download(keyword, videoDurationMs)
                        .collect { result ->
                            if (currentGeneration == generation) handleResult(result)
                        }
                } catch (e: CancellationException) {
                    AppLog.d("字幕搜索已取消：%s", keyword)
                    throw e
                } catch (e: Exception) {
                    AppLog.e(e, "搜索 / 下载字幕失败：%s", keyword)
                    eventsChannel.send(SubtitleDownloadEvent.Failed(e))
                    _state.value = _state.value.copy(stage = null)
                }
            }
    }

    /**
     * 上传用户选中的候选字幕到 [itemId]。
     *
     * 用户已经做出选择，剩余候选的下载随即取消。失败时列表与已下载的字节都保留：
     * 只解禁按钮，用户可重试或改选别的。
     */
    fun upload(scope: CoroutineScope, itemId: UUID, candidate: SubtitleCandidate) {
        val requestTarget = batchTarget?.takeIf { isCurrentTarget(it, itemId) } ?: return
        val downloaded = downloadedSubtitles[candidate] ?: return
        if (uploadJob?.isActive == true) return
        downloadJob?.cancel()
        downloadJob = null
        _state.value = _state.value.copy(stage = SubtitleDownloadStage.Uploading(candidate))
        uploadJob =
            scope.launch {
                try {
                    uploadToServer(itemId, downloaded)
                    if (!isCurrentTarget(requestTarget, itemId)) return@launch
                    AppLog.tag("SubSideload").d(
                        "服务器上传成功，准备发 Uploaded：item=%s，bytes=%d",
                        itemId,
                        downloaded.bytes.size,
                    )
                    reset()
                    eventsChannel.send(
                        SubtitleDownloadEvent.Uploaded(
                            target = requestTarget,
                            language = downloaded.subtitle.language,
                            subtitle = downloaded,
                        )
                    )
                    AppLog.tag("SubSideload").d("Uploaded 事件已被消费（send 返回）")
                } catch (e: CancellationException) {
                    AppLog.d("字幕上传已取消：%s", candidate.title)
                    throw e
                } catch (e: Exception) {
                    AppLog.e(e, "上传字幕失败：%s", itemId)
                    eventsChannel.send(SubtitleDownloadEvent.Failed(e))
                    _state.value = _state.value.copy(stage = null)
                }
            }
    }

    /**
     * 落盘对照快照：把已 [SubtitleCandidateState.Ready] 的候选写进缓存，发
     * [SubtitleDownloadEvent.CompareReady] 让持有方打开面板。
     *
     * **剩余候选继续下载，不取消**：候选常常要下十几秒，用户看到前两条可用了就想先比着看；
     * 剩下的一下完就自动补进同一份快照（见 [handleResult]），面板会自己「长」出新行。
     * 因此这里也不 [reset]：下载协程还要把后续结果写进列表与缓存。
     */
    suspend fun prepareCompare(itemId: UUID) {
        val requestGeneration = generation
        val event = compareMutex.withLock {
            if (requestGeneration != generation) return@withLock null
            val requestTarget = batchTarget?.takeIf { isCurrentTarget(it, itemId) }
                ?: return@withLock null
            if (readySubtitlesByOrder().isEmpty()) return@withLock null
            if (!isCurrentBatch(itemId)) {
                // 新批次先清理旧行号，clear 返回的令牌与后续追加共用。
                compareBatchToken = compareCache.clear(itemId)
                if (!isCurrentTarget(requestTarget, itemId)) return@withLock null
            }
            compareItemId = itemId
            // 清理挂起期间可能有候选就绪，登记关联后再取一次完整快照。
            val ready = readySubtitlesByOrder()
            try {
                compareCache.upsert(itemId, compareBatchToken, ready)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isCurrentBatch(itemId)) {
                    AppLog.d("对照快照已换代，本批落盘作废：%s", itemId)
                    invalidateCompareBatch()
                    return@withLock null
                }
                AppLog.e(e, "字幕对照快照落盘失败：%s", itemId)
                return@withLock SubtitleDownloadEvent.Failed(e)
            }
            if (!isCurrentTarget(requestTarget, itemId)) return@withLock null
            SubtitleDownloadEvent.CompareReady(itemId, requestTarget)
        }
        // 事件消费者可能暂停；发送时不持有缓存锁，后到候选仍能追加。
        if (requestGeneration == generation && event != null) eventsChannel.send(event)
    }

    /**
     * 已就绪的候选，键是行号（候选在搜索结果里的位置 + 1）。
     *
     * 行号按候选位置算而不是按完成顺序算：第 1 条还在下、第 2 条先好时，
     * 面板上应当显示 ②，等第 1 条补进来再出现 ①——否则既有行会在用户眼前换位置。
     */
    private fun readySubtitlesByOrder(): Map<Int, DownloadedSubtitle> =
        _state.value.candidates
            .mapIndexedNotNull { index, state ->
                val ready = state as? SubtitleCandidateState.Ready ?: return@mapIndexedNotNull null
                downloadedSubtitles[ready.candidate]?.let { downloaded -> index + 1 to downloaded }
            }
            .toMap()

    /** 本批快照是否还作数：关联未断，且令牌没有被别处 clear 换代。 */
    private fun isCurrentBatch(itemId: UUID): Boolean =
        compareItemId == itemId && compareCache.batchToken(itemId) == compareBatchToken

    /**
     * 断开关联：剩余候选不再往一份已经不属于本批的快照里补写。
     *
     * 播放页应用了某条字幕后也调用它结束本批，避免后续候选继续追加到旧快照。
     */
    fun invalidateCompareBatch() {
        compareItemId = null
        compareBatchToken = 0L
    }

    /** 中断搜索 / 剩余下载 / 上传，回到关键词输入状态。 */
    fun cancel() {
        cancelJobs()
        reset()
    }

    /**
     * 只清状态与内存里的字节（不碰已落盘的对照快照）。
     *
     * 行为与 [cancel] 完全一致，留两个名字只为让调用处读起来贴合意图：
     * 用户点「取消」用 [cancel]，页面销毁被动收尾用 [clear]。
     */
    fun clear() = cancel()

    private fun cancelJobs() {
        generation++
        batchTarget = null
        downloadJob?.cancel()
        downloadJob = null
        uploadJob?.cancel()
        uploadJob = null
    }

    private fun reset() {
        downloadedSubtitles = emptyMap()
        invalidateCompareBatch()
        _state.value = SubtitleDownloadState()
    }

    private suspend fun uploadToServer(itemId: UUID, downloaded: DownloadedSubtitle) {
        repository.uploadSubtitle(
            itemId = itemId,
            subtitleBytes = downloaded.bytes,
            language = downloaded.subtitle.language.jellyfinLanguage,
            format = downloaded.subtitle.format,
        )
        AppLog.d(
            "字幕已上传：%s（%s，%d 字节），与视频时长差 %s ms",
            downloaded.subtitle.language,
            downloaded.subtitle.format,
            downloaded.bytes.size,
            downloaded.durationDiffMs,
        )
    }

    /** 把下载器的流式结果落到候选列表上：先铺标题，再逐条填状态。 */
    private suspend fun handleResult(result: SubtitleCandidateResult) {
        when (result) {
            is SubtitleCandidateResult.Found ->
                _state.value =
                    SubtitleDownloadState(
                        stage = null,
                        // 搜索一回来就把标题铺出来，右侧先显示下载中，不等任何一条下完
                        candidates = result.candidates.map { SubtitleCandidateState.Loading(it) },
                        searchFoundNothing = result.candidates.isEmpty(),
                    )
            is SubtitleCandidateResult.Available -> {
                val downloaded = result.subtitle
                downloadedSubtitles = downloadedSubtitles + (downloaded.candidate to downloaded)
                _state.value =
                    _state.value.copy(
                        candidates =
                            _state.value.candidates.map { item ->
                                if (item.candidate == downloaded.candidate) {
                                    SubtitleCandidateState.Ready(
                                        candidate = downloaded.candidate,
                                        language = downloaded.subtitle.language,
                                        durationDiffMs = downloaded.durationDiffMs,
                                        isRecommended = downloaded.isRecommended,
                                    )
                                } else {
                                    item
                                }
                            }
                    )
                // 已经在对照中：把这一条补进快照，播放页的面板会自己多出一行
                appendToCompareSnapshot(downloaded)
            }
            is SubtitleCandidateResult.Unavailable ->
                _state.value =
                    _state.value.copy(
                        candidates =
                            _state.value.candidates.map { item ->
                                if (item.candidate == result.candidate) {
                                    SubtitleCandidateState.Unavailable(result.candidate)
                                } else {
                                    item
                                }
                            }
                    )
        }
    }

    /** 对照期间下完的候选补进快照；快照被换代（令牌过期）即作废本批关联，写失败只记日志，不打断剩余下载。 */
    private suspend fun appendToCompareSnapshot(downloaded: DownloadedSubtitle) = compareMutex.withLock {
        val itemId = compareItemId ?: return@withLock
        val index = _state.value.candidates.indexOfFirst { it.candidate == downloaded.candidate }
        if (index < 0) return@withLock
        try {
            compareCache.upsert(itemId, compareBatchToken, mapOf(index + 1 to downloaded))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isCurrentBatch(itemId)) {
                // 新一轮对比已清掉快照：本批关联随之作废，剩余候选不再往新快照里补
                AppLog.d("对照快照已换代，本批剩余候选不再补写：%s", itemId)
                invalidateCompareBatch()
            } else {
                AppLog.w(e, "把新下载的字幕补进对照快照失败：%s", itemId)
            }
        }
    }
}
