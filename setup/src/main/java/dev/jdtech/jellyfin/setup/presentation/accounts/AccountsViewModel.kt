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
        MutableStateFlow(
            repository.getCachedAccounts().let { saved ->
                AccountsState(
                    servers = saved?.servers.orEmpty(),
                    users = saved?.users.orEmpty(),
                    currentServerId = saved?.currentServerId,
                    currentUserId = saved?.currentUserId,
                    offlineMode = preferences.getValue(preferences.offlineMode),
                )
            }
        )
    val state = _state.asStateFlow()
    private val eventsChannel = Channel<AccountsEvent>()
    val events = eventsChannel.receiveAsFlow()
    private val initialServerId = preferences.getValue(preferences.currentServer)
    private var refreshJob: Job? = null
    private var publicUsersJob: Job? = null
    private var hasResumed = false

    init {
        // 首帧复用会话快照，Room 只在后台补齐其它服务器及账号变更。
        onAction(AccountsAction.Refresh)
    }

    /**
     * 页面恢复时补刷，覆盖从添加服务器 / 管理地址 / 登录页返回后的数据变化。
     * 与首次进场共用静默刷新，进行中的刷新会被合并；数据没变时 State 不变，界面不重组。
     */
    fun onResume() {
        // 首次恢复已有 init 发起的刷新，避免本地查询刚结束就取消并重发公共用户请求。
        if (!hasResumed) {
            hasResumed = true
            return
        }
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
        if (action == AccountsAction.Refresh) {
            refresh()
            return
        }
        if (action is AccountsAction.SelectServer && action.serverId == _state.value.currentServerId) return
        if (action is AccountsAction.SelectUser && action.userId == _state.value.currentUserId) return
        if (_state.value.isLoading) return
        refreshJob?.cancel()
        publicUsersJob?.cancel()
        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoading = true, error = null))
            try {
                when (action) {
                    // 上面已提前处理，这里只补全密封分支
                    AccountsAction.Back -> Unit
                    AccountsAction.ToggleOfflineMode -> Unit
                    AccountsAction.Refresh -> Unit
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

    /**
     * 进场与页面恢复时的静默刷新：加载态不写进 State，数据没变化时 StateFlow 靠 equals
     * 去重，界面不会因一次刷新整体重组；身份变更操作会取消它，避免两次加载交叉写 State。
     */
    private fun refresh() {
        if (_state.value.isLoading || refreshJob?.isActive == true) return
        publicUsersJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(e, "加载或更新服务器与用户失败")
                _state.emit(_state.value.copy(error = e))
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
        val saved = repository.getAccounts()
        val offlineMode = preferences.getValue(preferences.offlineMode)
        val isSameServer = saved.currentServerId != null && _state.value.currentServerId == saved.currentServerId
        val knownUserIds = saved.users.map { it.id }.toSet()
        _state.emit(
            _state.value.copy(
                servers = saved.servers,
                users = saved.users,
                // 同一服务器且联网时保留公共用户，刷新不再把列表清空后再异步填回。
                publicUsers = if (isSameServer && !offlineMode) {
                    _state.value.publicUsers.filterNot { it.id in knownUserIds }
                } else emptyList(),
                currentServerId = saved.currentServerId,
                currentUserId = saved.currentUserId,
                offlineMode = offlineMode,
                hasChangedServer = _state.value.hasChangedServer || saved.currentServerId != initialServerId,
                error = if (isSameServer && !offlineMode) _state.value.error else null,
            )
        )
        if (saved.currentServerId != null && !offlineMode) {
            loadPublicUsers(saved.currentServerId, knownUserIds)
        }
    }

    /** 公共用户是补充数据，请求期间仍允许管理本地服务器和用户。 */
    private fun loadPublicUsers(serverId: String, knownUserIds: Set<UUID>) {
        publicUsersJob = viewModelScope.launch {
            val users =
                try {
                    repository.getPublicUsers(serverId).filterNot { it.id in knownUserIds }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("加载公共用户失败，错误类型：%s", e.javaClass.simpleName)
                    currentCoroutineContext().ensureActive()
                    if (_state.value.currentServerId == serverId) {
                        _state.emit(_state.value.copy(error = e))
                    }
                    return@launch
                }
            // 连接已切换或请求已取消时，迟到的结果和错误都不能覆盖新列表。
            currentCoroutineContext().ensureActive()
            if (_state.value.currentServerId != serverId) return@launch
            _state.emit(_state.value.copy(publicUsers = users, error = null))
        }
    }
}
