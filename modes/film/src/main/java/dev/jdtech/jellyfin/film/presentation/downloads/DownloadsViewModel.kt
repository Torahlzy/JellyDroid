package dev.jdtech.jellyfin.film.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.collection.buildCollectionSections
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.utils.DownloadExporter
import dev.jdtech.jellyfin.utils.Downloader
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class DownloadsViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val exporter: DownloadExporter,
    private val downloader: Downloader,
) : ViewModel() {
    private val _state = MutableStateFlow(DownloadsState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                exporter.observeState().collect { progress ->
                    _state.emit(_state.value.copy(exportState = progress))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(e, "读取公共目录保存进度失败")
                _state.emit(
                    _state.value.copy(
                        operationError = UiText.StringResource(CoreR.string.download_export_failed)
                    )
                )
            }
        }
    }

    fun onAction(action: DownloadsAction) =
        when (action) {
            is DownloadsAction.OnItemClick -> Unit
            is DownloadsAction.OnItemMenu -> showItemMenu(action.item)
            DownloadsAction.DismissItemMenu -> dismissItemMenu()
            is DownloadsAction.OnItemExport -> showExportConfirm(action.item)
            DownloadsAction.DismissExportConfirm -> dismissExportConfirm()
            DownloadsAction.ConfirmExport -> exportConfirmedItem()
            is DownloadsAction.OnItemDelete -> showDeleteConfirm(action.item)
            DownloadsAction.DismissDeleteConfirm -> dismissDeleteConfirm()
            DownloadsAction.ConfirmDelete -> deleteConfirmedItem()
            DownloadsAction.StoragePermissionDenied -> showStoragePermissionError()
            DownloadsAction.Retry -> loadItems()
        }

    fun loadItems() {
        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoading = true, error = null))

            try {
                val items = repository.getDownloads()

                val sections = withContext(Dispatchers.Default) { buildCollectionSections(items) }

                _state.emit(_state.value.copy(isLoading = false, sections = sections))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(e, "加载已下载条目失败")
                _state.emit(_state.value.copy(isLoading = false, error = e))
            }
        }
    }

    private fun showItemMenu(item: FindroidItem) {
        viewModelScope.launch {
            _state.emit(_state.value.copy(itemMenuItem = item, operationError = null))
        }
    }

    private fun dismissItemMenu() {
        viewModelScope.launch {
            _state.emit(_state.value.copy(itemMenuItem = null))
        }
    }

    private fun showExportConfirm(item: FindroidItem) {
        viewModelScope.launch {
            _state.emit(
                _state.value.copy(
                    itemMenuItem = null,
                    exportConfirmItem = item,
                    operationError = null,
                )
            )
        }
    }

    private fun dismissExportConfirm() {
        viewModelScope.launch {
            _state.emit(_state.value.copy(exportConfirmItem = null))
        }
    }

    private fun exportConfirmedItem() {
        val item = _state.value.exportConfirmItem ?: return
        if (_state.value.isExportBusy) return
        viewModelScope.launch {
            // 确认后立即关闭弹窗，复制在后台进行，进度显示在下载页顶部
            _state.emit(_state.value.copy(exportConfirmItem = null, operationError = null))
            try {
                val sources = exporter.getItems().filter { it.itemId == item.id }
                if (sources.isEmpty()) {
                    // 数据库或文件缺失时给用户明确反馈，而不是静默失败
                    _state.emit(
                        _state.value.copy(
                            operationError =
                                UiText.StringResource(CoreR.string.download_export_file_missing)
                        )
                    )
                    return@launch
                }
                exporter.export(sources)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(e, "创建公共目录保存任务失败")
                _state.emit(
                    _state.value.copy(
                        operationError = UiText.StringResource(CoreR.string.download_export_failed)
                    )
                )
            }
        }
    }

    private fun showDeleteConfirm(item: FindroidItem) {
        viewModelScope.launch {
            _state.emit(_state.value.copy(itemMenuItem = null, deleteConfirmItem = item))
        }
    }

    private fun dismissDeleteConfirm() {
        viewModelScope.launch {
            _state.emit(_state.value.copy(deleteConfirmItem = null))
        }
    }

    private fun deleteConfirmedItem() {
        val item = _state.value.deleteConfirmItem ?: return
        viewModelScope.launch {
            // 与详情页删除一致：删掉本地文件与离线库记录，随后刷新下载列表
            _state.emit(_state.value.copy(deleteConfirmItem = null, operationError = null))
            val source = item.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
            if (source == null) {
                _state.emit(
                    _state.value.copy(
                        operationError = UiText.StringResource(CoreR.string.download_delete_failed)
                    )
                )
                return@launch
            }
            try {
                downloader.deleteItem(item, source)
                loadItems()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(e, "删除已下载条目失败")
                _state.emit(
                    _state.value.copy(
                        operationError = UiText.StringResource(CoreR.string.download_delete_failed)
                    )
                )
            }
        }
    }

    private fun showStoragePermissionError() {
        viewModelScope.launch {
            _state.emit(
                _state.value.copy(
                    operationError =
                        UiText.StringResource(CoreR.string.download_export_permission_required)
                )
            )
        }
    }
}
