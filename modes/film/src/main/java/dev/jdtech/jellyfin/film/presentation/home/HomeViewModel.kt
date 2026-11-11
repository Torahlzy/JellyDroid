package dev.jdtech.jellyfin.film.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.models.HomeSection
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.toView
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 拉取服务器信息校正名称的超时时间，服务器不可达时不至于长期占用协程 */
private const val SERVER_NAME_SYNC_TIMEOUT_MS = 5_000L

@HiltViewModel
class HomeViewModel
@Inject
constructor(
    val repository: JellyfinRepository,
    val appPreferences: AppPreferences,
    val database: ServerDatabaseDao,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    private val uuidSuggestions = UUID.fromString("31e47044-9b79-4bb0-99d0-0e477ed65420")
    private val uuidContinueWatching =
        UUID(4937169328197226115, -4704919157662094443) // 44845958-8326-4e83-beb4-c4f42e9eeb95
    private val uuidNextUp =
        UUID(1783371395749072194, -6164625418200444295) // 18bfced5-f237-4d42-aa72-d9d7fed19279

    private val uiTextContinueWatching = UiText.StringResource(FilmR.string.continue_watching)
    private val uiTextNextUp = UiText.StringResource(FilmR.string.next_up)

    // 本地的服务器名称只在添加服务器时写入一次，服务端改名后不会同步。
    // 按服务器记录：每台服务器每次进入首页最多校正一次，会话内切换服务器后新服务器也要校正，
    // 同时避免每次刷新都发一次网络请求。
    private var syncedServerNameFor: String? = null

    /**
     * 加载首页各分区数据。
     *
     * @param refreshSuggestions 是否强制重新拉取 banner（推荐轮播）；下拉刷新、切换服务器时传 true
     */
    fun loadData(refreshSuggestions: Boolean = false) {
        AppLog.i("Loading data")
        viewModelScope.launch(Dispatchers.Default) {
            _state.emit(_state.value.copy(isLoading = true, error = null))
            try {
                appPreferences.getValue(appPreferences.currentServer)?.let { serverId ->
                    loadServerName(serverId)
                    // 校正名称要联网，单独起协程，服务器不可达时不拖住其它分区的加载
                    launch { syncServerName(serverId) }
                }

                loadSuggestions(refreshSuggestions)
                loadResumeItems()
                loadNextUpItems()
                loadViews()
            } catch (e: Exception) {
                // 各分区的异常都在这里统一收敛，不打日志就无法定位是哪一步失败的
                AppLog.e(e, "加载首页数据失败")
                _state.emit(_state.value.copy(error = e))
            }
            _state.emit(_state.value.copy(isLoading = false))
        }
    }

    private suspend fun loadServerName(serverId: String) {
        val server = database.getServer(serverId)
        if (server != null) {
            _state.emit(_state.value.copy(server = server))
        }
    }

    /**
     * 用服务器返回的系统信息校正本地记录的服务器名称。
     *
     * 服务端改名后本地数据库不会更新（名称只在添加服务器时写入一次），这里补上同步。
     * 任何失败都只保留旧名称，不影响首页其它数据的展示。
     */
    private suspend fun syncServerName(serverId: String) {
        // 离线模式没有网络，只能沿用本地名称
        if (syncedServerNameFor == serverId || appPreferences.getValue(appPreferences.offlineMode)) return
        syncedServerNameFor = serverId

        val server = database.getServer(serverId) ?: return
        var fetchFailure: Throwable? = null
        val systemInfo =
            withTimeoutOrNull(SERVER_NAME_SYNC_TIMEOUT_MS) {
                runCatching { repository.getPublicSystemInfo() }
                    .onFailure { fetchFailure = it }
                    .getOrNull()
            }
        if (systemInfo == null) {
            // 超时不会抛异常、只有请求本身失败才带异常，两种情况都留一条线索
            val failure = fetchFailure
            if (failure != null) {
                AppLog.w(failure, "获取服务器系统信息失败，沿用本地名称")
            } else {
                AppLog.w("获取服务器系统信息超时，沿用本地名称")
            }
            // 这次没拿到，下次进入首页再试
            syncedServerNameFor = null
            return
        }

        val serverName = systemInfo.serverName?.takeIf { it.isNotBlank() } ?: return
        if (serverName == server.name) return

        AppLog.i("服务器名称已变更，同步本地记录：%s -> %s", server.name, serverName)
        val renamedServer = server.copy(name = serverName)
        runCatching { database.updateServer(renamedServer) }
            .onFailure { AppLog.w(it, "更新本地服务器名称失败") }

        // 期间可能已切换服务器，只在仍停留在同一台服务器上时刷新界面
        if (_state.value.server?.id == serverId) {
            _state.emit(_state.value.copy(server = renamedServer))
        }
    }

    private suspend fun loadSuggestions(refreshSuggestions: Boolean) {
        AppLog.i("Loading suggestions")
        // banner 只在首次进入与下拉刷新时拉取：每次从详情页返回首页都重拉会重建轮播、
        // 重新加载封面图，观感上像整页被刷新了一遍
        if (!refreshSuggestions && _state.value.suggestionsSection != null) {
            return
        }

        val items = repository.getSuggestions()

        val section =
            if (items.isEmpty()) {
                null
            } else {
                HomeItem.Suggestions(id = uuidSuggestions, items = items)
            }

        _state.emit(_state.value.copy(suggestionsSection = section))
    }

    private suspend fun loadResumeItems() {
        AppLog.i("Loading resume items")
        val resumeItems = repository.getResumeItems()

        val section =
            if (resumeItems.isEmpty()) {
                null
            } else {
                HomeItem.Section(
                    HomeSection(uuidContinueWatching, uiTextContinueWatching, resumeItems)
                )
            }

        _state.emit(_state.value.copy(resumeSection = section))
    }

    private suspend fun loadNextUpItems() {
        AppLog.i("Loading next up items")
        val nextUpItems = repository.getNextUp()

        val section =
            if (nextUpItems.isEmpty()) {
                null
            } else {
                HomeItem.Section(HomeSection(uuidNextUp, uiTextNextUp, nextUpItems))
            }

        _state.emit(_state.value.copy(nextUpSection = section))
    }

    private suspend fun loadViews() {
        AppLog.i("Loading views")
        val items =
            repository
                    .getUserViews()
                    .filter { view ->
                        CollectionType.fromString(view.collectionType?.serialName) in
                            CollectionType.supported
                    }
                    .map { view -> view to repository.getLatestMedia(view.id) }
                    .filter { (_, latest) -> latest.isNotEmpty() }
                    .map { (view, latest) -> view.toView(latest) }
                    .map { HomeItem.ViewItem(it) }

        _state.emit(_state.value.copy(views = items))
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.OnRetryClick -> {
                loadData()
            }
            is HomeAction.OnRefresh -> {
                loadData(refreshSuggestions = true)
            }
            else -> Unit
        }
    }
}
