package dev.jdtech.jellyfin.setup.data

import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.ExceptionUiText
import dev.jdtech.jellyfin.models.ExceptionUiTexts
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.ServerAddress
import dev.jdtech.jellyfin.models.ServerWithAddresses
import dev.jdtech.jellyfin.models.ServerWithAddressesAndUsers
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.setup.R as SetupR
import dev.jdtech.jellyfin.setup.domain.AccountsSnapshot
import dev.jdtech.jellyfin.setup.domain.SetupRepository
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.discovery.RecommendedServerInfo
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.discovery.RecommendedServerIssue
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.api.QuickConnectResult
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo

class SetupRepositoryImpl(
    private val jellyfinApi: JellyfinApi,
    private val database: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
) : SetupRepository {
    private var cachedAccounts: AccountsSnapshot? = null

    override fun getCachedAccounts(): AccountsSnapshot? {
        val serverId = appPreferences.getValue(appPreferences.currentServer)
        cachedAccounts?.takeIf { it.currentServerId == serverId }?.let { return it }
        val saved = jellyfinApi.currentServer?.takeIf { it.server.id == serverId } ?: return null
        val server = saved.server.copy()
        return AccountsSnapshot(
            servers =
                listOf(
                    ServerWithAddresses(
                        server = server,
                        addresses = saved.addresses,
                        user = saved.users.firstOrNull { it.id == server.currentUserId },
                    )
                ),
            users = saved.users,
            currentServerId = server.id,
            currentUserId = server.currentUserId,
        )
    }

    override suspend fun getAccounts(): AccountsSnapshot {
        val servers = database.getServersWithAddresses()
        val serverId = appPreferences.getValue(appPreferences.currentServer)
        val current = servers.firstOrNull { it.server.id == serverId }
        val users = current?.let { database.getUsers(it.server.id) }.orEmpty()
        // 添加服务器可能临时改变 SDK 连接，返回后用同一份快照恢复，避免重复查询。
        jellyfinApi.updateCurrentServer(
            current?.let { ServerWithAddressesAndUsers(it.server, it.addresses, users) }
        )
        return AccountsSnapshot(
            servers = servers,
            users = users,
            currentServerId = current?.server?.id,
            currentUserId = current?.server?.currentUserId,
        ).also { cachedAccounts = it }
    }

    override fun discoverServers(): Flow<ServerDiscoveryInfo> {
        return jellyfinApi.jellyfin.discovery.discoverLocalServers()
    }

    override suspend fun getServers(): List<ServerWithAddresses> {
        return database.getServersWithAddresses()
    }

    override suspend fun getCurrentServer(): Server? {
        return appPreferences.getValue(appPreferences.currentServer)?.let { id ->
            database.getServer(id)
        }
    }

    override suspend fun deleteServer(serverId: String) {
        database.deleteServer(serverId)
        cachedAccounts = null
        if (jellyfinApi.currentServer?.server?.id == serverId) {
            jellyfinApi.cacheCurrentServer(null)
        }
    }

    override suspend fun getIsQuickConnectEnabled(): Boolean =
        withContext(Dispatchers.IO) { jellyfinApi.quickConnectApi.getQuickConnectEnabled().content }

    override suspend fun initiateQuickConnect(): QuickConnectResult =
        withContext(Dispatchers.IO) { jellyfinApi.quickConnectApi.initiateQuickConnect().content }

    override suspend fun getQuickConnectState(secret: String): QuickConnectResult =
        withContext(Dispatchers.IO) {
            jellyfinApi.quickConnectApi.getQuickConnectState(secret).content
        }

    override suspend fun setCurrentServer(serverId: String) {
        val saved = database.getServerWithAddressesAndUsers(serverId) ?: return
        jellyfinApi.updateCurrentServer(saved)
        cachedAccounts = null
    }

    private suspend fun cacheCurrentServer(serverId: String) {
        jellyfinApi.cacheCurrentServer(database.getServerWithAddressesAndUsers(serverId))
        cachedAccounts = null
    }

    override suspend fun addServer(address: String): Server = withContext(Dispatchers.IO) {
        if (address.isBlank()) {
            throw ExceptionUiText(
                UiText.StringResource(SetupR.string.add_server_error_empty_address)
            )
        }

        val candidates = jellyfinApi.jellyfin.discovery.getAddressCandidates(address)
        val recommended =
            jellyfinApi.jellyfin.discovery.getRecommendedServers(
                candidates,
                RecommendedServerInfoScore.OK,
            )
        val goodServers = mutableListOf<RecommendedServerInfo>()
        val okServers = mutableListOf<RecommendedServerInfo>()

        for (recommendedServerInfo in recommended) {
            when (recommendedServerInfo.score) {
                RecommendedServerInfoScore.GREAT -> {
                    return@withContext saveServerInDatabase(recommendedServerInfo)
                }
                RecommendedServerInfoScore.GOOD -> goodServers.add(recommendedServerInfo)
                RecommendedServerInfoScore.OK -> okServers.add(recommendedServerInfo)
                RecommendedServerInfoScore.BAD -> Unit
            }
        }

        when {
            goodServers.isNotEmpty() -> {
                return@withContext saveServerInDatabase(goodServers.first())
            }
            okServers.isNotEmpty() -> {
                val okServer = okServers.first()
                throw ExceptionUiTexts(createIssuesString(okServer))
            }
            else -> {
                throw ExceptionUiText(
                    UiText.StringResource(SetupR.string.add_server_error_not_found)
                )
            }
        }
    }

    private suspend fun saveServerInDatabase(
        recommendedServerInfo: RecommendedServerInfo
    ): Server = withContext(Dispatchers.IO) {
        val serverInfo =
            recommendedServerInfo.systemInfo.getOrNull()
                ?: throw ExceptionUiText(
                    UiText.StringResource(SetupR.string.add_server_error_no_id)
                )

        AppLog.d("已找到可连接的服务器")

        val serverInDatabase = database.getServer(serverInfo.id!!)

        // 已保存的服务器只补充新地址，保留当前地址与账号选择。
        val server =
            if (serverInDatabase != null) {
                val addresses = database.getServerWithAddresses(serverInDatabase.id).addresses
                if (addresses.none { it.address == recommendedServerInfo.address }) {
                    val serverAddress =
                        ServerAddress(
                            id = UUID.randomUUID(),
                            serverId = serverInDatabase.id,
                            address = recommendedServerInfo.address,
                        )

                    database.insertServerAddress(serverAddress)
                }
                serverInDatabase
            } else {
                val serverAddress =
                    ServerAddress(
                        id = UUID.randomUUID(),
                        serverId = serverInfo.id!!,
                        address = recommendedServerInfo.address,
                    )

                val server =
                    Server(
                        id = serverInfo.id!!,
                        name = serverInfo.serverName!!,
                        currentServerAddressId = serverAddress.id,
                        currentUserId = null,
                    )

                database.insertServer(server)
                database.insertServerAddress(serverAddress)
                server
            }

        jellyfinApi.apply {
            api.update(baseUrl = recommendedServerInfo.address, accessToken = null)
        }
        cachedAccounts = null

        server
    }

    /** 将服务器兼容性问题转为界面可展示的文案。 */
    private fun createIssuesString(server: RecommendedServerInfo): Collection<UiText> {
        return server.issues.map {
            when (it) {
                is RecommendedServerIssue.OutdatedServerVersion -> {
                    UiText.StringResource(SetupR.string.add_server_error_outdated, it.version)
                }
                is RecommendedServerIssue.InvalidProductName -> {
                    UiText.StringResource(
                        SetupR.string.add_server_error_not_jellyfin,
                        it.productName ?: "",
                    )
                }
                is RecommendedServerIssue.UnsupportedServerVersion -> {
                    UiText.StringResource(SetupR.string.add_server_error_version, it.version)
                }
                is RecommendedServerIssue.SlowResponse -> {
                    UiText.StringResource(SetupR.string.add_server_error_slow, it.responseTime)
                }
                else -> {
                    UiText.StringResource(CoreR.string.unknown_error)
                }
            }
        }
    }

    override suspend fun loadDisclaimer(): String? =
        withContext(Dispatchers.IO) {
            jellyfinApi.brandingApi.getBrandingOptions().content.loginDisclaimer
        }

    override suspend fun login(username: String, password: String) {
        withContext(Dispatchers.IO) {
            val authenticationResult by
                jellyfinApi.userApi.authenticateUserByName(
                    data = AuthenticateUserByName(username = username, pw = password)
                )

            saveAuthenticationResult(authenticationResult)
        }
    }

    override suspend fun loginWithSecret(secret: String) {
        withContext(Dispatchers.IO) {
            val authenticationResult by
                jellyfinApi.userApi.authenticateWithQuickConnect(
                    data = QuickConnectDto(secret = secret)
                )

            saveAuthenticationResult(authenticationResult)
        }
    }

    private suspend fun saveAuthenticationResult(authenticationResult: AuthenticationResult) {
        val user =
            User(
                id = authenticationResult.user!!.id,
                name = authenticationResult.user!!.name!!,
                serverId = authenticationResult.serverId!!,
                accessToken = authenticationResult.accessToken!!,
            )

        database.insertUser(user)
        database.updateServerCurrentUser(authenticationResult.serverId!!, user.id)

        jellyfinApi.apply {
            api.update(accessToken = authenticationResult.accessToken)
            userId = authenticationResult.user?.id
        }
        cacheCurrentServer(user.serverId)
    }

    override suspend fun getUsers(serverId: String): List<User> {
        return database.getUsers(serverId)
    }

    override suspend fun getPublicUsers(serverId: String): List<User> =
        withContext(Dispatchers.IO) {
            jellyfinApi.userApi.getPublicUsers().content.mapNotNull {
                User(id = it.id, name = it.name ?: return@mapNotNull null, serverId = serverId)
            }
        }

    override suspend fun getCurrentUser(): User? {
        val currentServer = getCurrentServer() ?: return null
        return database.getServerCurrentUser(currentServer.id)
    }

    override suspend fun deleteUser(userId: UUID) {
        database.deleteUser(userId)
        cachedAccounts = null
        jellyfinApi.currentServer?.server?.id?.let { cacheCurrentServer(it) }
    }

    override suspend fun setCurrentUser(userId: UUID) {
        val server = getCurrentServer() ?: return
        val user = database.getUser(userId) ?: return
        server.currentUserId = user.id
        database.updateServer(server)

        jellyfinApi.apply {
            api.update(accessToken = user.accessToken)
            this.userId = user.id
        }
        cacheCurrentServer(server.id)
    }

    override suspend fun setCurrentAddress(addressId: UUID) {
        val server = getCurrentServer() ?: return
        val address = database.getAddress(id = addressId)
        if (address.serverId != server.id) {
            return
        }
        server.currentServerAddressId = address.id
        database.updateServer(server)
        jellyfinApi.apply { api.update(baseUrl = address.address) }
        cacheCurrentServer(server.id)
    }
}
