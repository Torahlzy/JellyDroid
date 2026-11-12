package dev.jdtech.jellyfin.api

import android.content.Context
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.ServerWithAddressesAndUsers
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.jdtech.jellyfin.data.BuildConfig
import dev.jdtech.jellyfin.settings.domain.Constants
import java.util.UUID
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.extensions.brandingApi
import org.jellyfin.sdk.api.client.extensions.devicesApi
import org.jellyfin.sdk.api.client.extensions.filterApi
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.itemUpdateApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
import org.jellyfin.sdk.api.client.extensions.personsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.studiosApi
import org.jellyfin.sdk.api.client.extensions.subtitleApi
import org.jellyfin.sdk.api.client.extensions.suggestionsApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.trickplayApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.ServerVersion

/** 封装 Jellyfin SDK，连接配置由异步初始化恢复，依赖注入不等待数据库。 */
class JellyfinApi(
    androidContext: Context,
    requestTimeout: Long = Constants.NETWORK_DEFAULT_REQUEST_TIMEOUT,
    connectTimeout: Long = Constants.NETWORK_DEFAULT_CONNECT_TIMEOUT,
    socketTimeout: Long = Constants.NETWORK_DEFAULT_SOCKET_TIMEOUT,
) {
    val jellyfin = createJellyfin {
        clientInfo =
            ClientInfo(
                name =
                    androidContext.applicationInfo
                        .loadLabel(androidContext.packageManager)
                        .toString(),
                version = BuildConfig.VERSION_NAME,
            )
        context = androidContext
        // 降低 SDK 的最低服务器版本门槛（默认 10.11.0），以兼容 10.10.x 服务器
        minimumServerVersion = ServerVersion(10, 10, 0)
    }
    val api =
        jellyfin.createApi(
            httpClientOptions =
                HttpClientOptions(
                    requestTimeout = requestTimeout.toDuration(DurationUnit.MILLISECONDS),
                    connectTimeout = connectTimeout.toDuration(DurationUnit.MILLISECONDS),
                    socketTimeout = socketTimeout.toDuration(DurationUnit.MILLISECONDS),
                )
        )
    var userId: UUID? = null
    var currentServer: ServerWithAddressesAndUsers? = null
        private set

    val brandingApi = api.brandingApi
    val devicesApi = api.devicesApi
    val filterApi = api.filterApi
    val genresApi = api.genresApi
    val imageApi = api.imageApi
    val itemsApi = api.itemsApi
    val itemUpdateApi = api.itemUpdateApi
    val libraryApi = api.libraryApi
    val mediaInfoApi = api.mediaInfoApi
    val mediaSegmentsApi = api.mediaSegmentsApi
    val personsApi = api.personsApi
    val playStateApi = api.playStateApi
    val quickConnectApi = api.quickConnectApi
    val sessionApi = api.sessionApi
    val showsApi = api.tvShowsApi
    val studiosApi = api.studiosApi
    val subtitleApi = api.subtitleApi
    val suggestionsApi = api.suggestionsApi
    val systemApi = api.systemApi
    val trickplayApi = api.trickplayApi
    val userApi = api.userApi
    val userLibraryApi = api.userLibraryApi
    val videosApi = api.videosApi
    val viewsApi = api.userViewsApi

    private val sessionMutex = Mutex()
    private var isSessionRestored = false

    /** 异步恢复连接信息，避免依赖注入在主线程等待 Room。 */
    suspend fun initialize(database: ServerDatabaseDao, preferences: AppPreferences) {
        sessionMutex.withLock {
            if (isSessionRestored) return
            val serverId = preferences.getValue(preferences.currentServer)
            updateCurrentServer(serverId?.let { database.getServerWithAddressesAndUsers(it) })
            isSessionRestored = true
        }
    }

    /** 保留已恢复的本地账号快照，账号页首帧无需重新等待 Room。 */
    fun updateCurrentServer(saved: ServerWithAddressesAndUsers?) {
        cacheCurrentServer(saved)
        if (saved == null) return
        val address = saved.addresses.firstOrNull {
            it.id == saved.server.currentServerAddressId
        } ?: return
        val user = saved.users.firstOrNull { it.id == saved.server.currentUserId }
        api.update(baseUrl = address.address, accessToken = user?.accessToken)
        userId = user?.id
    }

    /** 账号记录变更只更新快照，保留本次登录或选址已建立的 SDK 连接。 */
    fun cacheCurrentServer(saved: ServerWithAddressesAndUsers?) {
        currentServer = saved?.copy(server = saved.server.copy())
    }

    companion object {
        @Volatile private var INSTANCE: JellyfinApi? = null

        fun getInstance(
            context: Context,
            requestTimeout: Long = Constants.NETWORK_DEFAULT_REQUEST_TIMEOUT,
            connectTimeout: Long = Constants.NETWORK_DEFAULT_CONNECT_TIMEOUT,
            socketTimeout: Long = Constants.NETWORK_DEFAULT_SOCKET_TIMEOUT,
        ): JellyfinApi {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance =
                        JellyfinApi(
                            androidContext = context.applicationContext,
                            requestTimeout = requestTimeout,
                            connectTimeout = connectTimeout,
                            socketTimeout = socketTimeout,
                        )
                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}
