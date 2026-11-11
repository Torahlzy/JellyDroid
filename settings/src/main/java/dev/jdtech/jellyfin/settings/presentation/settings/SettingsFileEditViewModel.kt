package dev.jdtech.jellyfin.settings.presentation.settings

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.logging.AppLog
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsFileEditViewModel @Inject constructor(private val application: Application) :
    ViewModel() {
    private val _state = MutableStateFlow(SettingsFileEditState())
    val state = _state.asStateFlow()
    private var filePath = ""

    fun loadFile(filePath: String) {
        if (this.filePath == filePath && _state.value.isLoaded) return
        this.filePath = filePath
        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoading = true, error = null))
            try {
                val text = withContext(Dispatchers.IO) {
                    val file = File(application.filesDir, filePath)
                    if (file.exists() || File(file.path + ".bak").exists()) {
                        AtomicFile(file).openRead().bufferedReader().use { it.readText() }
                    } else ""
                }
                _state.emit(SettingsFileEditState(initialText = text, isLoaded = true))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(e, "读取播放器配置失败")
                _state.emit(_state.value.copy(isLoading = false, error = e))
            }
        }
    }

    private fun saveFile(text: String) {
        if (!_state.value.isLoaded || _state.value.isSaving) return
        viewModelScope.launch {
            _state.emit(_state.value.copy(isSaving = true, error = null))
            try {
                withContext(Dispatchers.IO) {
                    val file = AtomicFile(File(application.filesDir, filePath))
                    val output = file.startWrite()
                    try {
                        output.write(text.toByteArray(Charsets.UTF_8))
                        file.finishWrite(output)
                    } catch (e: Exception) {
                        file.failWrite(output)
                        throw e
                    }
                }
                _state.emit(_state.value.copy(initialText = text, isSaving = false))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(e, "保存播放器配置失败")
                _state.emit(_state.value.copy(isSaving = false, error = e))
            }
        }
    }

    fun onAction(action: SettingsFileEditAction) {
        when (action) {
            is SettingsFileEditAction.OnSave -> saveFile(action.text)
            else -> Unit
        }
    }
}
