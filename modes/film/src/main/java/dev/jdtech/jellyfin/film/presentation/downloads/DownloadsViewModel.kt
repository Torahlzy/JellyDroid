package dev.jdtech.jellyfin.film.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.collection.buildCollectionSections
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.utils.DownloadExporter
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
                        exportError = UiText.StringResource(CoreR.string.download_export_failed)
                    )
                )
            }
        }
    }

    fun onAction(action: DownloadsAction) =
        when (action) {
            is DownloadsAction.OnItemClick -> Unit
            DownloadsAction.OpenExportDialog -> openExportDialog()
            DownloadsAction.CloseExportDialog -> setDialogVisible(false)
            is DownloadsAction.ToggleExportItem -> changeSelection(action.sourceId)
            DownloadsAction.ToggleSelectAll -> changeSelection(null)
            DownloadsAction.ExportSelected -> exportSelected()
            DownloadsAction.CancelExport -> cancelExport()
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

    private fun setDialogVisible(visible: Boolean) {
        viewModelScope.launch { _state.emit(_state.value.copy(showExportDialog = visible)) }
    }

    private fun openExportDialog() {
        viewModelScope.launch {
            _state.emit(_state.value.copy(showExportDialog = true))
            if (_state.value.isLoadingExportItems) return@launch
            _state.emit(
                _state.value.copy(isLoadingExportItems = true, exportError = null)
            )
            try {
                val items = exporter.getItems()
                val availableSourceIds = items.map { it.sourceId }.toSet()
                _state.emit(
                    _state.value.copy(
                        exportItems = items,
                        selectedSourceIds =
                            _state.value.selectedSourceIds.intersect(availableSourceIds),
                        isLoadingExportItems = false,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(e, "加载可保存的离线视频失败")
                _state.emit(
                    _state.value.copy(
                        isLoadingExportItems = false,
                        exportError = UiText.StringResource(CoreR.string.download_export_load_failed),
                    )
                )
            }
        }
    }

    private fun changeSelection(sourceId: String?) {
        if (_state.value.isExportBusy || _state.value.isLoadingExportItems) return
        viewModelScope.launch {
            val selected = _state.value.selectedSourceIds
            val all = _state.value.exportItems.map { it.sourceId }.toSet()
            val next =
                when {
                    sourceId == null -> if (selected == all) emptySet() else all
                    sourceId !in all -> selected
                    sourceId in selected -> selected - sourceId
                    else -> selected + sourceId
                }
            _state.emit(_state.value.copy(selectedSourceIds = next))
        }
    }

    private fun exportSelected() {
        val items = _state.value.selectedItems
        if (_state.value.isExportBusy || _state.value.isLoadingExportItems || items.isEmpty()) return
        viewModelScope.launch {
            _state.emit(_state.value.copy(isStartingExport = true, exportError = null))
            try {
                exporter.export(items)
                _state.emit(_state.value.copy(isStartingExport = false))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(e, "创建公共目录保存任务失败")
                _state.emit(
                    _state.value.copy(
                        isStartingExport = false,
                        exportError = UiText.StringResource(CoreR.string.download_export_failed),
                    )
                )
            }
        }
    }

    private fun cancelExport() {
        viewModelScope.launch {
            try {
                exporter.cancel()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(e, "取消公共目录保存任务失败")
                _state.emit(
                    _state.value.copy(
                        exportError = UiText.StringResource(CoreR.string.download_export_failed)
                    )
                )
            }
        }
    }

    private fun showStoragePermissionError() {
        viewModelScope.launch {
            _state.emit(
                _state.value.copy(
                    exportError =
                        UiText.StringResource(CoreR.string.download_export_permission_required)
                )
            )
        }
    }
}
