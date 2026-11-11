package dev.jdtech.jellyfin.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.logging.AppLog
import kotlinx.coroutines.CancellationException
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.work.workDataOf

@HiltWorker
class SyncWorker
@AssistedInject
constructor(
    @Assisted private val context: Context,
    @Assisted private val workerParams: WorkerParameters,
    val database: ServerDatabaseDao,
    val appPreferences: AppPreferences,
) : CoroutineWorker(context, workerParams) {

    private var hasTerminalFailure = false

    override suspend fun doWork(): Result = syncMutex.withLock {
        hasTerminalFailure = false
        try {
            syncAllUsers()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("准备离线同步失败，错误类型：%s", e.javaClass.simpleName)
            Result.retry()
        }
    }

    private suspend fun syncAllUsers(): Result {
        val jellyfinApi =
            JellyfinApi(
                androidContext = context.applicationContext,
                requestTimeout = appPreferences.getValue(appPreferences.requestTimeout),
                connectTimeout = appPreferences.getValue(appPreferences.connectTimeout),
                socketTimeout = appPreferences.getValue(appPreferences.socketTimeout),
            )

        val servers = database.getServers()
        var allSynced = true

        for (server in servers) {
            val serverWithAddressesAndUsers =
                database.getServerWithAddressesAndUsers(server.id) ?: continue
            val serverAddress =
                serverWithAddressesAndUsers.addresses.firstOrNull {
                    it.id == server.currentServerAddressId
                } ?: continue
            for (user in serverWithAddressesAndUsers.users) {
                jellyfinApi.apply {
                    api.update(baseUrl = serverAddress.address, accessToken = user.accessToken)
                    userId = user.id
                }
                val movies =
                    database.getMoviesByServerId(server.id).map {
                        it.toFindroidMovie(database, user.id)
                    }
                val episodes =
                    database.getEpisodesByServerId(server.id).map {
                        it.toFindroidEpisode(database, user.id)
                    }

                if (!syncUserData(jellyfinApi, user, movies)) allSynced = false
                if (!syncUserData(jellyfinApi, user, episodes)) allSynced = false
            }
        }

        return when {
            !allSynced -> Result.retry()
            hasTerminalFailure -> Result.failure(workDataOf("requiresUserAction" to true))
            else -> Result.success()
        }
    }

    private suspend fun syncUserData(
        jellyfinApi: JellyfinApi,
        user: User,
        items: List<FindroidItem>,
    ): Boolean {
        var allSynced = true
        for (item in items) {
            val userData = database.getUserDataToBeSynced(user.id, item.id) ?: continue

            try {
                jellyfinApi.itemsApi.updateItemUserData(
                    itemId = item.id,
                    userId = user.id,
                    data =
                        UpdateUserItemDataDto(
                            playbackPositionTicks = userData.playbackPositionTicks,
                            isFavorite = userData.favorite,
                            played = userData.played,
                        ),
                )

                database.acknowledgeUserData(
                    user.id,
                    item.id,
                    userData.playbackPositionTicks,
                    userData.favorite,
                    userData.played,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidStatusException) {
                if (e.status in 400..499 && e.status != 408 && e.status != 429) {
                    hasTerminalFailure = true
                    AppLog.w("离线同步被服务器拒绝，HTTP %d；保留待同步数据", e.status)
                    // 认证失效后继续提交同一用户的其他条目没有意义。
                    if (e.status == 401 || e.status == 403) return allSynced
                } else {
                    allSynced = false
                }
            } catch (e: Exception) {
                allSynced = false
                AppLog.w("同步条目 %s 失败，错误类型：%s", item.id, e.javaClass.simpleName)
            }
        }
        return allSynced
    }

    private companion object {
        val syncMutex = Mutex()
    }
}
