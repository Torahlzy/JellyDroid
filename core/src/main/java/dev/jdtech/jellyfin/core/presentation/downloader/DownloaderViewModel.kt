package dev.jdtech.jellyfin.core.presentation.downloader

import android.app.DownloadManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.isDownloading
import dev.jdtech.jellyfin.utils.Downloader
import javax.inject.Inject
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DownloaderViewModel @Inject constructor(private val downloader: Downloader) : ViewModel() {
    private val _state = MutableStateFlow(DownloaderState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<DownloaderEvent>()
    val events = eventsChannel.receiveAsFlow()

    var downloadId: Long? = null

    private val handler = Handler(Looper.getMainLooper())

    /** 本轮轮询的起点，用于识别长时间排不上队的下载。 */
    private var pollStartedAt = 0L

    /** 是否已就"下载迟迟不开始"提醒过用户，避免反复打日志。 */
    private var isStallLogged = false

    fun update(item: FindroidItem) {
        viewModelScope.launch {
            if (item.isDownloading()) {
                val source =
                    item.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                        ?: return@launch
                this@DownloaderViewModel.downloadId = source.downloadId
                pollDownloadProgress(source.downloadId)
            }
        }
    }

    private fun download(item: FindroidItem, storageIndex: Int = 0) {
        viewModelScope.launch {
            _state.emit(DownloaderState(status = DownloadManager.STATUS_PENDING))
            val (downloadId, uiText) =
                downloader.downloadItem(
                    item = item,
                    sourceId = item.sources.first().id,
                    storageIndex = storageIndex,
                )
            if (downloadId != -1L) {
                this@DownloaderViewModel.downloadId = downloadId
                pollDownloadProgress(downloadId)
            } else {
                _state.emit(
                    DownloaderState(status = DownloadManager.STATUS_FAILED, errorText = uiText)
                )
            }
        }
    }

    private fun cancelDownload(item: FindroidItem) {
        viewModelScope.launch {
            // Stop progress polling
            handler.removeCallbacksAndMessages(null)

            // Cancel the download
            downloadId?.let { downloader.cancelDownload(item = item, downloadId = it) }

            // Emit empty DownloadState
            _state.emit(DownloaderState())
        }
    }

    private fun deleteDownload(item: FindroidItem) {
        viewModelScope.launch {
            downloader.deleteItem(
                item = item,
                source = item.sources.first { it.type == FindroidSourceType.LOCAL },
            )
            eventsChannel.send(DownloaderEvent.Deleted)
        }
    }

    private fun pollDownloadProgress(downloadId: Long?) {
        handler.removeCallbacksAndMessages(null)
        pollStartedAt = SystemClock.elapsedRealtime()
        isStallLogged = false
        val downloadProgressRunnable =
            object : Runnable {
                override fun run() {
                    viewModelScope.launch {
                        val (status, progress) = downloader.getProgress(downloadId)
                        // progress 为 -1 表示 DownloadManager 还没拿到文件总大小，此时是"进度未知"而不是 0%
                        val knownProgress = progress.takeIf { it >= 0 }?.div(100f)
                        // DownloadManager 连不上服务器时会持续处于"等待重试"（对外表现为 PENDING）且不上报错误，
                        // 只能靠等待时长兜底提醒，否则界面会永远停在"等待中"
                        val isStalled =
                            status == DownloadManager.STATUS_PENDING &&
                                knownProgress == null &&
                                SystemClock.elapsedRealtime() - pollStartedAt > STALL_TIMEOUT_MS
                        if (isStalled && !isStallLogged) {
                            isStallLogged = true
                            AppLog.w("下载等待 %d 秒仍未开始，请检查网络或代理设置", STALL_TIMEOUT_MS / 1000)
                        }
                        _state.emit(
                            DownloaderState(
                                status = status,
                                progress = knownProgress,
                                errorText =
                                    if (isStalled) {
                                        UiText.StringResource(CoreR.string.download_stalled)
                                    } else {
                                        null
                                    },
                            )
                        )
                    }

                    if (_state.value.status == DownloadManager.STATUS_SUCCESSFUL) {
                        eventsChannel.trySend(DownloaderEvent.Successful)
                    }

                    if (_state.value.isDownloading) {
                        handler.postDelayed(this, 1000L)
                    }
                }
            }
        handler.post(downloadProgressRunnable)
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
        handler.removeCallbacksAndMessages(null)
    }

    private companion object {
        /** 等待超过该时长仍未真正开始下载就认为卡住了（DownloadManager 单次连接超时为 20 秒）。 */
        const val STALL_TIMEOUT_MS = 60_000L
    }
}
