package dev.jdtech.jellyfin.setup.presentation.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.setup.domain.SetupRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 合并服务器与用户管理，切换身份后重启界面以释放旧页面的业务状态。 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val repository: SetupRepository,
    private val preferences: AppPreferences,
) : ViewModel() {
    private val _state =
        MutableStateFlow(AccountsState(offlineMode = preferences.getValue(preferences.offlineMode)))
    val state = _state.asStateFlow()
    private val eventsChannel = Channel<AccountsEvent>()
    val events = eventsChannel.receiveAsFlow()
    private val initialServerId = preferences.getValue(preferences.currentServer)
    private var publicUsersJob: Job? = null

    init {
        // 服务器与用户都在本地 Room，进场立即读取，不等界面 ON_RESUME，避免首帧空白
        onAction(AccountsAction.Refresh)
    }

    /**
     * 页面恢复时补刷，覆盖从添加服务器 / 管理地址 / 登录页返回后的数据变化。
     * 首次进场若 init 的加载尚未结束，onAction 的 isLoading 守卫会合并重复请求。
     */
    fun onResume() {
        onAction(AccountsAction.Refresh)
    }

    fun onAction(action: AccountsAction) {
        if (action == AccountsAction.Back) {
            publicUsersJob?.cancel()
            viewModelScope.launch {
                eventsChannel.send(if (_state.value.hasChangedServer) AccountsEvent.Restart else AccountsEvent.Back)
            }
            return
        }
        if (action == AccountsAction.ToggleOfflineMode) {
            toggleOfflineMode()
            return
        }
        if (_state.value.isLoading) return
        publicUsersJob?.cancel()
        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoading = true, error = null))
            try {
                when (action) {
                    AccountsAction.Back -> Unit
                    // 上面已提前处理，这里只补全密封分支
                    AccountsAction.ToggleOfflineMode -> Unit
                    AccountsAction.Refresh -> load()
                    is AccountsAction.SelectServer -> {
                        _state.emit(_state.value.copy(hasChangedServer = true))
                        repository.setCurrentServer(action.serverId)
                        preferences.setValue(preferences.currentServer, action.serverId)
                        load()
                    }
                    is AccountsAction.SelectUser -> {
                        repository.setCurrentUser(action.userId)
                        eventsChannel.send(AccountsEvent.Restart)
                    }
                    is AccountsAction.DeleteServer -> {
                        repository.deleteServer(action.serverId)
                        if (action.serverId == _state.value.currentServerId) {
                            preferences.setValue(preferences.currentServer, null)
                            eventsChannel.send(AccountsEvent.Restart)
                        } else load()
                    }
                    is AccountsAction.DeleteUser -> {
                        repository.deleteUser(action.userId)
                        if (action.userId == _state.value.currentUserId) eventsChannel.send(AccountsEvent.Restart)
                        else load()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(e, "加载或更新服务器与用户失败")
                _state.emit(_state.value.copy(error = e))
            } finally {
                _state.emit(_state.value.copy(isLoading = false))
            }
        }
    }

    /** 离线模式决定全局 Repository 与底栏，切换后必须重启界面才能生效。 */
    private fun toggleOfflineMode() {
        val enabled = !_state.value.offlineMode
        preferences.setValue(preferences.offlineMode, enabled)
        viewModelScope.launch {
            _state.emit(_state.value.copy(offlineMode = enabled))
            eventsChannel.send(AccountsEvent.Restart)
        }
    }

    private suspend fun load() {
        val servers = repository.getServers()
        val current = repository.getCurrentServer()
        // 添加服务器可能改过 SDK 的连接状态，返回后与当前保存的服务器重新对齐。
        current?.let { repository.setCurrentServer(it.id) }
        val users = current?.let { repository.getUsers(it.id) }.orEmpty()
        _state.emit(
            _state.value.copy(
                servers = servers,
                users = users,
                publicUsers = emptyList(),
                currentServerId = current?.id,
                currentUserId = current?.currentUserId,
                hasChangedServer = _state.value.hasChangedServer || current?.id != initialServerId,
            )
        )
        if (current != null && !preferences.getValue(preferences.offlineMode)) {
            loadPublicUsers(current.id, users.map { it.id }.toSet())
        }
    }

    /** 公共用户是补充数据，请求期间仍允许管理本地服务器和用户。 */
    private fun loadPublicUsers(serverId: String, knownUserIds: Set<UUID>) {
        publicUsersJob = viewModelScope.launch {
            var error: Exception? = null
            val users =
                try {
                    repository.getPublicUsers(serverId).filterNot { it.id in knownUserIds }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("加载公共用户失败，错误类型：%s", e.javaClass.simpleName)
                    error = e
                    emptyList()
                }
            // 连接已切换或请求已取消时，迟到的结果和错误都不能覆盖新列表。
            currentCoroutineContext().ensureActive()
            if (_state.value.currentServerId != serverId) return@launch
            _state.emit(_state.value.copy(publicUsers = users, error = error))
        }
    }
}
