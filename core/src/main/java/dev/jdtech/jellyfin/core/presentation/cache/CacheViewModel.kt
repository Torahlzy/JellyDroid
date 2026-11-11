package dev.jdtech.jellyfin.core.presentation.cache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.cache.CacheManager
import dev.jdtech.jellyfin.cache.CacheUsage
import dev.jdtech.jellyfin.logging.AppLog
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class CacheViewModel @Inject constructor(private val cache: CacheManager) : ViewModel() {
    private val _state = MutableStateFlow(CacheState())
    val state = _state.asStateFlow()

    fun onAction(action: CacheAction) {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            _state.emit(_state.value.copy(
                isLoading = true,
                clearing = (action as? CacheAction.Clear)?.category,
                error = null,
            ))
            try {
                val usage = withContext(Dispatchers.IO) { execute(action) }
                _state.emit(CacheState(usage = usage))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(e, "缓存管理操作失败")
                _state.emit(_state.value.copy(isLoading = false, clearing = null, error = e))
            }
        }
    }

    private fun execute(action: CacheAction): CacheUsage = when (action) {
        CacheAction.Refresh -> { cache.trim(); cache.usage() }
        is CacheAction.SetQuota -> cache.setQuota(action.megabytes)
        is CacheAction.Clear -> cache.clear(action.category)
    }
}
