package dev.jdtech.jellyfin.core.presentation.downloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.isDownloading
import dev.jdtech.jellyfin.utils.DownloadPauseReason
import dev.jdtech.jellyfin.utils.DownloadCancelledException
import dev.jdtech.jellyfin.utils.DownloadProgress
import dev.jdtech.jellyfin.utils.DownloadStatus
import dev.jdtech.jellyfin.utils.Downloader
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DownloaderViewModel @Inject constructor(private val downloader: Downloader) : ViewModel() {
    private val _state = MutableStateFlow(DownloaderState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<DownloaderEvent>()
    val events = eventsChannel.receiveAsFlow()

    /** 当前界面正在跟踪的下载任务 id。 */
    private var taskId: String? = null

    /** 进度收集协程，切换任务或取消下载时需要先停掉。 */
    private var progressJob: Job? = null
    private var preparationJob: Job? = null
    private val operationMutex = Mutex()
    private val cancelledTaskIds = mutableSetOf<String>()

    fun update(item: FindroidItem) {
        viewModelScope.launch {
            operationMutex.withLock {
                if (!item.isDownloading() || preparationJob?.isActive == true) return@withLock
                val source = item.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                    ?: return@withLock
                val sourceTaskId = source.downloadTaskId ?: return@withLock
                if (sourceTaskId in cancelledTaskIds) return@withLock
                taskId = sourceTaskId
                observeProgress(sourceTaskId)
            }
        }
    }

    private fun download(item: FindroidItem, storageIndex: Int = 0) {
        preparationJob?.cancel(DownloadCancelledException())
        preparationJob = viewModelScope.launch {
            operationMutex.withLock {
                progressJob?.cancelAndJoin()
                taskId = null
                _state.emit(DownloaderState(status = DownloadStatus.QUEUED))
                val (newTaskId, errorText) =
                    downloader.downloadItem(
                        item = item,
                        sourceId = item.sources.first().id,
                        storageIndex = storageIndex,
                    )
                if (newTaskId != null) {
                    taskId = newTaskId
                    observeProgress(newTaskId)
                } else {
                    _state.emit(DownloaderState(status = DownloadStatus.FAILED, errorText = errorText))
                }
            }
        }
    }

    private fun cancelDownload(item: FindroidItem) {
        val pendingPreparation = preparationJob
        val pendingTaskId = taskId ?: item.sources.firstOrNull {
            it.type == FindroidSourceType.LOCAL
        }?.downloadTaskId
        pendingTaskId?.let(cancelledTaskIds::add)
        item.sources.mapNotNullTo(cancelledTaskIds) { it.downloadTaskId }
        pendingPreparation?.cancel(DownloadCancelledException())
        viewModelScope.launch {
            operationMutex.withLock {
                pendingPreparation?.join()
                progressJob?.cancelAndJoin()
                try {
                    pendingTaskId?.let { downloader.cancelDownload(item = item, taskId = it) }
                    taskId = null
                    _state.emit(DownloaderState())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showOperationError(e)
                }
            }
        }
    }

    private fun deleteDownload(item: FindroidItem) {
        viewModelScope.launch {
            operationMutex.withLock {
                try {
                    progressJob?.cancelAndJoin()
                    downloader.deleteItem(
                        item = item,
                        source = item.sources.first { it.type == FindroidSourceType.LOCAL },
                    )
                    eventsChannel.send(DownloaderEvent.Deleted)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showOperationError(e)
                }
            }
        }
    }

    private suspend fun showOperationError(error: Exception) {
        AppLog.e("下载操作失败，错误类型：%s", error.javaClass.simpleName)
        _state.emit(_state.value.copy(
            status = DownloadStatus.FAILED,
            errorText = error.localizedMessage?.let { UiText.DynamicString(it) }
                ?: UiText.StringResource(CoreR.string.unknown_error),
        ))
    }

    private fun observeProgress(taskId: String?) {
        progressJob?.cancel()
        progressJob =
            viewModelScope.launch {
                // 卡住提示给出后要一直保持到任务真的动起来，否则每次进度刷新都会把文案冲掉
                var stallHint: UiText? = null
                downloader.observeProgress(taskId).collectLatest { progress ->
                    _state.emit(
                        DownloaderState(
                            status = progress.status,
                            progress = progress.progress,
                            speedBytesPerSecond = progress.speedBytesPerSecond,
                            errorText = resolveHintText(progress) ?: stallHint,
                        )
                    )

                    if (progress.status == DownloadStatus.SUCCESSFUL) {
                        eventsChannel.trySend(DownloaderEvent.Successful)
                        return@collectLatest
                    }

                    // 一直排在队列里且拿不到总大小，说明任务根本没跑起来（例如网络约束始终不满足）。
                    // 用 collectLatest + delay 做超时：期间状态一旦变化，本次等待会被取消。
                    if (
                        progress.status == DownloadStatus.QUEUED &&
                            progress.progress == null &&
                            stallHint == null
                    ) {
                        delay(STALL_TIMEOUT_MS)
                        AppLog.w(
                            "下载等待 %d 秒仍未开始，请检查网络设置",
                            STALL_TIMEOUT_MS / 1000,
                        )
                        stallHint = UiText.StringResource(CoreR.string.download_stalled)
                        _state.emit(_state.value.copy(errorText = stallHint))
                    }
                }
            }
    }

    /** 把下载状态与原因翻译成用户看得懂的提示，避免下载卡住时界面毫无反馈。 */
    private fun resolveHintText(progress: DownloadProgress): UiText? =
        when {
            progress.status == DownloadStatus.PAUSED &&
                progress.reason == DownloadPauseReason.WAITING_TO_RETRY ->
                UiText.StringResource(CoreR.string.download_waiting_to_retry)
            progress.status == DownloadStatus.PAUSED &&
                progress.reason == DownloadPauseReason.WAITING_FOR_NETWORK ->
                UiText.StringResource(CoreR.string.download_waiting_for_network)
            else -> null
        }

    fun onAction(action: DownloaderAction) {
        when (action) {
            is DownloaderAction.Download -> download(action.item, action.storageIndex)
            is DownloaderAction.DeleteDownload -> deleteDownload(action.item)
            is DownloaderAction.CancelDownload -> cancelDownload(action.item)
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
    }

    private companion object {
        /** 等待超过该时长仍未真正开始下载就认为卡住了。 */
        const val STALL_TIMEOUT_MS = 60_000L
    }
}
