package dev.jdtech.jellyfin.presentation.player.subtitle

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.subtitle.labelRes
import dev.jdtech.jellyfin.core.scraper.AvidParser
import dev.jdtech.jellyfin.core.subtitle.SubtitleCandidate
import dev.jdtech.jellyfin.core.subtitle.SubtitleCompareCache
import dev.jdtech.jellyfin.core.subtitle.SubtitleCompareEntry
import dev.jdtech.jellyfin.core.subtitle.SubtitleDownloadController
import dev.jdtech.jellyfin.core.subtitle.SubtitleDownloadEvent
import dev.jdtech.jellyfin.core.subtitle.SubtitleDownloadState
import dev.jdtech.jellyfin.core.subtitle.SubtitleLanguage
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.player.local.mpv.MPVPlayer
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 播放页「下载字幕（入口 B）与字幕对照面板（二期）」的编排：弹窗与对照面板的状态、
 * 状态机事件、面板开关、上传后侧载切轨、对照快照的生命周期都在这里，
 * `PlayerActivity` 只负责装配与生命周期接线（见 `wiki/subtitle-download.md` 第五节）。
 *
 * 与 [SubtitleAdjustManager] 一样由播放页手动构造（不是 Hilt 单例）：依赖的协程作用域、
 * 播放器与几个界面动作都来自播放页，随播放页生灭。
 */
class SubtitlePlaybackCoordinator(
    /** Toast 与二次确认弹窗用；随播放页生命周期 */
    private val context: Context,
    /** 编排期间启动协程用；随播放页生命周期（lifecycleScope） */
    private val scope: CoroutineScope,
    /** 当前播放器；两条后端的切轨 API 各走各的 */
    private val player: Player,
    private val appPreferences: AppPreferences,
    private val repository: JellyfinRepository,
    /** 播放页自己的一份字幕下载状态机（**不是单例**，与详情页互不干扰） */
    private val subtitleController: SubtitleDownloadController,
    private val compareCache: SubtitleCompareCache,
    private val subtitleAdjustManager: SubtitleAdjustManager,
    /** 收起播放器控制层（打开对照面板时调用） */
    private val hidePlayerController: () -> Unit,
    /** 播放器当前条目 id（未开播为 null），由宿主从 PlayerViewModel 转发 */
    private val currentPlayerItemId: () -> UUID?,
    /** ExoPlayer 后端的侧载切轨（重载当前条目），由宿主转发给 PlayerViewModel */
    private val reloadWithExternalSubtitle: (uri: Uri, label: String, language: String) -> Unit,
) {

    companion object {
        /** 等侧载字幕轨出现的最长时间；超时保留侧载文件，交给缓存回收。 */
        private const val AWAIT_SUBTITLE_TRACK_TIMEOUT_MS = 8_000L

        /** 对照面板的重算间隔（ms）：100~200ms 之间，肉眼跟得上又不会太费。 */
        private const val COMPARE_REFRESH_INTERVAL_MS = 150L
    }

    /** 对照面板的状态与取句；覆盖层 Composable 直接观察它。 */
    val compareManager = SubtitleCompareManager(player, appPreferences, compareCache)

    /** 是否显示「下载字幕」弹窗（入口 B）。 */
    private val _isDialogShowing = MutableStateFlow(false)
    val isDialogShowing: StateFlow<Boolean> = _isDialogShowing.asStateFlow()

    /** 下载弹窗的状态机状态，直接转发 controller 的同一份。 */
    val downloadState: StateFlow<SubtitleDownloadState> = subtitleController.state

    /** 播放页里打开下载弹窗时预填的关键词，由来源路径识别（与详情页同一套规则）。 */
    private val _keyword = MutableStateFlow("")
    val keyword: StateFlow<String> = _keyword.asStateFlow()

    /** 抓取代理地址，与详情页共用同一个偏好；改动即时生效于后续请求。 */
    private val _proxy =
        MutableStateFlow(appPreferences.getValue(appPreferences.scrapeProxy))
    val proxy: StateFlow<String> = _proxy.asStateFlow()

    /**
     * 当前播放的条目 id，取自启动参数；播放中的条目未必已切换完成，弹窗操作以它为准。
     *
     * 任何入口（菜单、弹窗、轮询）都在宿主 `onPlaybackStarted` 之后才会触达，lateinit 安全。
     */
    private lateinit var playedItemId: UUID

    /** 上一次观察到的播放条目。 */
    private var observedItemId: UUID? = null

    /** 进画中画前面板是开着的，退出小窗后重新打开。 */
    private var wasCompareShowingInPip = false

    /**
     * 开播了：登记字幕操作的目标条目并预识别关键词，详情页点了「播放视频时对比」时直接铺出面板。
     *
     * 换了一部影片（`onNewIntent`）也要走这里：关键词与目标条目都得跟着换。
     */
    fun onPlaybackStarted(itemId: UUID, openSubtitleCompare: Boolean) {
        playedItemId = itemId
        loadDefaultKeyword()
        if (openSubtitleCompare) {
            openComparePanel(itemId)
        }
    }

    /** 打开「下载字幕」弹窗（播放页菜单入口 B）。 */
    fun showDownloadDialog() {
        AppLog.d("打开下载字幕弹窗：条目 %s，默认关键词「%s」", playedItemId, _keyword.value)
        // 对照面板与下载弹窗不同时开：先关面板。缓存保留（clearCache = false），
        // 用户没重新搜索时再点「对比」还能用这一批
        if (compareManager.state.value.isShowing) closeComparePanel(clearCache = false)
        if (_keyword.value.isEmpty()) loadDefaultKeyword()
        _isDialogShowing.value = true
    }

    /** 更新抓取代理：写回偏好，让详情页与后续请求共用同一个值。 */
    fun updateProxy(address: String) {
        appPreferences.setValue(appPreferences.scrapeProxy, address)
        _proxy.value = address
    }

    fun downloadSubtitle(keyword: String) {
        subtitleController.download(scope, keyword, currentVideoDurationMs())
    }

    fun uploadSubtitle(candidate: SubtitleCandidate) {
        subtitleController.upload(scope, targetItemId(), candidate)
    }

    /** 弹窗里点「取消」：中断搜索 / 剩余下载，弹窗留在原地回到关键词输入。 */
    fun cancelDownload() {
        subtitleController.cancel()
    }

    /** 弹窗关闭（含点弹窗外）：关闭弹窗并中断进行中的下载。 */
    fun dismissDialog() {
        AppLog.tag("SubSideload").d("下载弹窗 dismissDialog 被调用（会 cancel 上传任务）")
        _isDialogShowing.value = false
        subtitleController.cancel()
    }

    /** 「播放视频时对比」：先把已下好的候选落盘，再由事件打开对照面板（与详情页同一条路径）。 */
    fun prepareCompare() {
        scope.launch { subtitleController.prepareCompare(targetItemId()) }
    }

    /** 收集字幕下载状态机的事件（上传 / 失败 / 快照就绪），转成界面反馈。 */
    suspend fun observeSubtitleEvents() {
        subtitleController.events.collect { event ->
            AppLog.tag("SubSideload").d("播放页收到事件：%s", event.javaClass.simpleName)
            handleSubtitleEvent(event)
        }
    }

    /**
     * 面板打开期间剩余候选可能还在下载（详情页或本页的 controller 都写同一份快照）：
     * 每补进一条就重新载入，新行会自己出现在面板上，不用重开。
     */
    suspend fun observeCompareUpdates() {
        compareCache.updates.collect { updatedItemId ->
            if (updatedItemId == compareManager.currentItemId()) {
                AppLog.d("对照快照有新增，重新载入面板：%s", updatedItemId)
                compareManager.load(updatedItemId)
            }
        }
    }

    /**
     * 播放页 STARTED 期间的轮询循环：发现播放器内部切集（上 / 下一集、自动连播），
     * 并按当前播放位置重算对照面板各行文本。放在 repeatOnLifecycle(STARTED) 里由宿主启动，
     * 进后台即停，不白烧电。
     */
    suspend fun playbackLoop() {
        while (true) {
            currentPlayerItemId()?.let { currentItemId ->
                if (currentItemId != observedItemId) {
                    observedItemId = currentItemId
                    onPlaybackItemChanged(currentItemId)
                }
            }
            compareManager.refresh()
            delay(COMPARE_REFRESH_INTERVAL_MS)
        }
    }

    val isCompareShowing: Boolean
        get() = compareManager.state.value.isShowing

    /**
     * 打开对照面板。
     *
     * 快照缺失（进程被杀后 `cacheDir` 被回收）时提示「本地字幕已失效」并打开下载弹窗，
     * **不静默重下**：重下依赖关键词且是网络操作，静默重下看起来像卡住。
     */
    fun openComparePanel(itemId: UUID, hintIfMissing: Boolean = true) {
        AppLog.d(
            "请求打开对照面板：%s（当前条目 %s，面板已开 %b，提示缺失 %b）",
            itemId,
            currentPlayerItemId(),
            compareManager.state.value.isShowing,
            hintIfMissing,
        )
        if (compareManager.state.value.isShowing) return
        scope.launch {
            if (!compareManager.load(itemId)) {
                if (hintIfMissing) {
                    Toast.makeText(context, CoreR.string.subtitle_compare_cache_missing, Toast.LENGTH_LONG)
                        .show()
                    showDownloadDialog()
                }
                return@launch
            }
            // 两个面板互斥：开对照前先退出字幕调节模式
            if (subtitleAdjustManager.state.value.isAdjustMode) subtitleAdjustManager.close()
            // 面板已铺 5 行，关掉真实字幕轨，否则画面上会堆成 6 行
            subtitleAdjustManager.setRealSubtitleVisible(false)
            hidePlayerController()
        }
    }

    fun closeComparePanel(clearCache: Boolean = true) {
        val itemId = compareManager.currentItemId() ?: targetItemId()
        AppLog.d("关闭对照面板：%s（清缓存 %b）", itemId, clearCache)
        compareManager.hide()
        subtitleAdjustManager.setRealSubtitleVisible(true)
        if (clearCache) {
            scope.launch { compareCache.clear(itemId) }
        }
    }

    /** 「应用」前的二次确认：正文必须写明同语言旧字幕会被覆盖（服务端行为）。 */
    fun confirmApplyCompare(row: SubtitleCompareManager.CompareRow) {
        val entry = compareManager.entryOrNull(row.order) ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle(CoreR.string.subtitle_compare_apply_confirm_title)
            .setMessage(
                context.getString(
                    CoreR.string.subtitle_compare_apply_confirm_message,
                    entry.title,
                    context.getString(entry.language.labelRes()),
                )
            )
            .setNegativeButton(CoreR.string.cancel, null)
            .setPositiveButton(CoreR.string.subtitle_compare_apply) { _, _ ->
                applyCompareEntry(entry)
            }
            .show()
    }

    /** 进画中画：面板是 Compose 覆盖层，会跟着进小窗——整个隐藏并恢复真实字幕轨，缓存保留。 */
    fun onEnterPictureInPicture() {
        wasCompareShowingInPip = compareManager.state.value.isShowing
        if (wasCompareShowingInPip) {
            closeComparePanel(clearCache = false)
        }
    }

    /** 退出画中画：进小窗前面板是开着的就把它开回来（缓存保留过，不用重下）。 */
    fun onExitPictureInPicture() {
        if (wasCompareShowingInPip) {
            wasCompareShowingInPip = false
            openComparePanel(targetItemId(), hintIfMissing = false)
        }
    }

    /** 播放页销毁：清掉本页的下载状态，并把对照快照交给缓存自己的作用域删除。 */
    fun release() {
        // 离开播放页时把本页那份字幕下载状态清掉（候选字节只在内存里）
        subtitleController.clear()
        // 对照快照只服务当前这次播放：面板可能还开着（顶栏返回直接 finish，不走关闭逻辑），
        // 此时页面 scope 已被取消，交给 cache 自己的作用域删，否则残留的快照会在
        // 下一轮对比时被按行号合并进面板
        compareCache.clearAsync(compareManager.currentItemId() ?: playedItemId)
    }

    private fun handleSubtitleEvent(event: SubtitleDownloadEvent) {
        when (event) {
            is SubtitleDownloadEvent.Uploaded -> {
                AppLog.tag("SubSideload").d(
                    "处理 Uploaded：title=%s，language=%s",
                    event.subtitle.candidate.title,
                    event.language,
                )
                Toast.makeText(
                        context,
                        getStringWithLanguage(CoreR.string.subtitle_uploaded, event.language),
                        Toast.LENGTH_SHORT,
                    )
                    .show()
                _isDialogShowing.value = false
                // 上传只改了服务器，当前 MediaItem 不会自己多出这条轨：用本地字节立即侧载并切过去，
                // 「选择字幕轨」里马上就有它，不必等服务器重新索引（时机不确定）也不用重开播放
                applyUploadedSubtitle(event)
            }
            is SubtitleDownloadEvent.Failed ->
                Toast.makeText(
                        context,
                        context.getString(
                            CoreR.string.subtitle_download_failed,
                            event.error.localizedMessage.orEmpty(),
                        ),
                        Toast.LENGTH_LONG,
                    )
                    .show()
            is SubtitleDownloadEvent.CompareReady -> {
                _isDialogShowing.value = false
                openComparePanel(event.itemId)
            }
        }
    }

    /**
     * 播放器内部切了条目（上 / 下一集、自动连播）。
     *
     * 三件事必须跟着切：操作目标条目（否则字幕上传 / 侧载落到上一集）、默认关键词、
     * 以及上一集还开着的对照面板（继续留着会让用户以为「这一集就是这个轴」）。
     */
    private fun onPlaybackItemChanged(itemId: UUID) {
        if (itemId == playedItemId) return
        AppLog.d("播放条目已切换：%s → %s", playedItemId, itemId)
        playedItemId = itemId
        loadDefaultKeyword()
        if (compareManager.state.value.isShowing) closeComparePanel()
    }

    /**
     * 当前「字幕操作目标」条目。
     *
     * 优先取播放器当前条目：自动切集后 [playedItemId] 可能还停在启动时那一集，
     * 直接用它上传字幕或侧载文件会落到错误的条目上。
     */
    private fun targetItemId(): UUID = currentPlayerItemId() ?: playedItemId

    /**
     * 用来源路径识别字幕关键词（与详情页同一套 [AvidParser] 规则）。
     *
     * 播放页没有详情页的 `defaultScrapeKeyword`，只能自己从服务器返回的来源路径推一遍；
     * 识别不到就留空输入框，与详情页一致。
     */
    private fun loadDefaultKeyword() {
        // 离线模式的来源是本地路径，识别不出番号，菜单里也没有「下载字幕」入口，不必白跑
        if (appPreferences.getValue(appPreferences.offlineMode)) {
            _keyword.value = ""
            return
        }
        val itemId = targetItemId()
        scope.launch {
            _keyword.value =
                try {
                    val sources = repository.getMediaSources(itemId, false)
                    val path =
                        sources
                            .firstOrNull { it.remoteFilePath.isNotBlank() }
                            ?.remoteFilePath
                            .orEmpty()
                    AvidParser.getDvdId(path)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.w(e, "识别播放页字幕关键词失败：%s", itemId)
                    ""
                }
        }
    }

    /** 视频真实时长（ms）；尚未 ready 或取不到时返回 0，界面显示「未知」。 */
    private fun currentVideoDurationMs(): Long {
        val duration = player.duration
        return if (duration == C.TIME_UNSET || duration < 0) 0L else duration
    }

    /**
     * 上传选中的那条字幕并切成当前字幕轨。
     *
     * 成功路径的顺序是有意的：切轨 → 关面板 → 等新字幕轨出现 → 清缓存。
     * 清快照会把侧载的字幕文件一起删掉，必须等播放器解析完这份字幕之后再清，否则轨根本不会出现。
     * 失败时留在面板、缓存保留，用户可重试或改选别的（不必重新下载）。
     */
    private fun applyCompareEntry(entry: SubtitleCompareEntry) {
        val itemId = targetItemId()
        AppLog.d(
            "开始应用对照字幕：第 %d 行 %s（%s，%s，%d 字节）→ 条目 %s",
            entry.order,
            entry.title,
            entry.language,
            entry.format,
            entry.file.length(),
            itemId,
        )
        compareManager.setApplying(true)
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { entry.file.readBytes() }
                repository.uploadSubtitle(
                    itemId = itemId,
                    subtitleBytes = bytes,
                    language = entry.language.jellyfinLanguage,
                    format = entry.format,
                )
                AppLog.tag("SubSideload").d("对照字幕已上传到服务器，开始切轨")
                switchSubtitleTo(
                    file = entry.file,
                    title = entry.title,
                    language = entry.language.jellyfinLanguage,
                )
                compareManager.hide()
                subtitleAdjustManager.setRealSubtitleVisible(true)
                val appeared =
                    awaitSubtitleTrack(entry.title, AWAIT_SUBTITLE_TRACK_TIMEOUT_MS)
                AppLog.tag("SubSideload").d("对照路径等待轨道结果：appeared=%b", appeared)
                logTextTracks()
                // 用户已选定一条字幕，本批对照到此结束，剩余候选不再补进这份快照
                subtitleController.invalidateCompareBatch()
                if (appeared) {
                    // 轨已出现说明播放器读完了字幕，这时清理才不会把侧载文件删掉
                    compareCache.clear(itemId)
                } else {
                    // clear 会删掉 entry.file，等不到轨时删了就再也读不到；
                    // 下一轮 prepareCompare 会因关联作废重新落盘，退出播放页由 clearAsync 回收
                    AppLog.tag("SubSideload").w("侧载字幕轨迟迟未出现，保留快照供播放器继续读取：%s", entry.file)
                }
                Toast.makeText(
                        context,
                        getStringWithLanguage(CoreR.string.subtitle_uploaded, entry.language),
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(e, "应用对照字幕失败：%s", entry.file)
                Toast.makeText(
                        context,
                        context.getString(
                            CoreR.string.subtitle_download_failed,
                            e.localizedMessage.orEmpty(),
                        ),
                        Toast.LENGTH_LONG,
                    )
                    .show()
            } finally {
                compareManager.setApplying(false)
            }
        }
    }

    /**
     * 上传成功后把这份字幕立即挂到当前播放器上（两条后端各走各的 API）。
     *
     * 失败只记日志、不打断播放：字幕已经在服务器上了，下次播放照样会有这条轨。
     */
    private fun applyUploadedSubtitle(event: SubtitleDownloadEvent.Uploaded) {
        val title = event.subtitle.candidate.title
        AppLog.tag("SubSideload").d("上传完成，立即侧载到当前播放器：%s（%s）", title, event.language)
        scope.launch {
            val file =
                try {
                    compareCache.stageForPlayback(targetItemId(), event.subtitle)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.w(e, "写出侧载字幕文件失败，本次不切换字幕轨")
                    return@launch
                }
            AppLog.tag("SubSideload").d(
                "侧载文件就绪：%s，存在=%b，大小=%d",
                file,
                file.isFile,
                file.length(),
            )
            switchSubtitleTo(
                file = file,
                title = title,
                language = event.language.jellyfinLanguage,
            )
            // 等播放器认出这条轨再删文件：注入是异步的，早删会读不到字幕，
            // 「选择字幕轨」里就永远没有它。等不到时保留文件交给缓存回收，别再帮倒忙
            val appeared = awaitSubtitleTrack(title, AWAIT_SUBTITLE_TRACK_TIMEOUT_MS)
            AppLog.tag("SubSideload").d("等待轨道结果：appeared=%b", appeared)
            logTextTracks()
            if (!appeared) {
                AppLog.tag("SubSideload").w("侧载字幕轨迟迟未出现，保留文件供缓存回收：%s", file)
                return@launch
            }
            if (!file.delete()) {
                AppLog.d("侧载字幕文件暂时未删除，留给缓存回收：%s", file)
            }
        }
    }

    /**
     * 打印当前播放器里的字幕轨清单。
     *
     * 「选择字幕轨」读的是 `player.currentTracks` 里 isSupported 的文本组，
     * 日志按同一口径标出可用 / 选中状态；侧载完还看不到新轨时，
     * 一眼就能判断是没注入、轨不被支持，还是播放器没识别。
     */
    private fun logTextTracks() {
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        AppLog.tag("SubSideload").d(
            "当前字幕轨共 %d 组（可用 %d 组）：%s",
            groups.size,
            groups.count { it.isSupported },
            groups.joinToString(" | ") { group ->
                val formats =
                    (0 until group.length).joinToString(",") { index ->
                        val format = group.getTrackFormat(index)
                        "${format.label}/${format.language}"
                    }
                "$formats[supported=${group.isSupported}, selected=${group.isSelected}]"
            },
        )
    }

    /** 用本地缓存文件切到该字幕（不等服务器把新字幕流发回来，两条后端各走各的 API）。 */
    private fun switchSubtitleTo(file: File, title: String, language: String) {
        val uri = Uri.fromFile(file)
        val backend = if (player is MPVPlayer) "mpv" else "exoplayer"
        AppLog.tag("SubSideload").d(
            "开始注入：uri=%s，后端=%s，文件存在=%b",
            uri,
            backend,
            file.isFile,
        )
        if (player is MPVPlayer) {
            player.addExternalSubtitle(uri, title, language)
        } else {
            reloadWithExternalSubtitle(uri, title, language)
        }
    }

    /**
     * 等 [label] 这条侧载字幕轨真正出现在播放器轨道列表里。
     *
     * 不能只等 `playbackState == STATE_READY`：注入侧载字幕走 `setMediaItems` 重建（ExoPlayer）
     * 或 `sub-add`（mpv），都是异步的，调用刚返回时播放器往往还停在旧的 READY。
     * 那时继续往下走会打印旧轨清单、并删掉还没被读取的字幕文件，
     * 结果「选择字幕轨」里始终没有新轨。轨道变化回调到来才说明这份字幕已被解析完。
     */
    private suspend fun awaitSubtitleTrack(label: String, timeoutMs: Long): Boolean {
        val baseline = textGroupCount(player.currentTracks)
        AppLog.tag("SubSideload").d("开始等轨道：label=%s，起始文本组=%d", label, baseline)
        val appeared =
            withTimeoutOrNull(timeoutMs) {
                if (isTrackVisible(player.currentTracks, label, baseline)) {
                    return@withTimeoutOrNull true
                }
                suspendCancellableCoroutine { continuation ->
                    val listener =
                        object : Player.Listener {
                            override fun onTracksChanged(tracks: Tracks) {
                                val hit = isTrackVisible(tracks, label, baseline)
                                AppLog.tag("SubSideload").d(
                                    "onTracksChanged：文本组=%d（起始 %d），命中=%b",
                                    textGroupCount(tracks),
                                    baseline,
                                    hit,
                                )
                                if (hit) {
                                    player.removeListener(this)
                                    continuation.resume(true)
                                }
                            }
                        }
                    player.addListener(listener)
                    continuation.invokeOnCancellation { player.removeListener(listener) }
                }
            } ?: false
        AppLog.tag("SubSideload").d(
            "等待结束：appeared=%b，最终文本组=%d",
            appeared,
            textGroupCount(player.currentTracks),
        )
        return appeared
    }

    /** 服务器给的字幕轨 label 可能为空，所以除按标签找，也认「文本组数量变多」。 */
    private fun isTrackVisible(tracks: Tracks, label: String, baseline: Int): Boolean =
        textGroupCount(tracks) > baseline || hasSubtitleTrack(tracks, label)

    /** 文本轨组数，诊断日志用：区分「没注入」与「注入了但被 isSupported 过滤」。 */
    private fun textGroupCount(tracks: Tracks): Int =
        tracks.groups.count { it.type == C.TRACK_TYPE_TEXT }

    /** 与「选择字幕轨」同一口径：只认 isSupported 的文本组，按轨道标签找刚侧载的那条。 */
    private fun hasSubtitleTrack(tracks: Tracks, label: String): Boolean =
        tracks.groups.any { group ->
            group.type == C.TRACK_TYPE_TEXT &&
                group.isSupported &&
                (0 until group.length).any { group.getTrackFormat(it).label == label }
        }

    /**
     * 带语言名的提示文案；文案资源与格式化都走宿主 [context]。
     *
     * 刻意不叫 `getString`：那样会遮蔽本类里所有 `getString(资源, 格式化参数)` 的调用，
     * 让「传错误信息字符串」的地方全部类型不匹配。
     */
    private fun getStringWithLanguage(resId: Int, language: SubtitleLanguage): String =
        context.getString(resId, context.getString(language.labelRes()))
}
