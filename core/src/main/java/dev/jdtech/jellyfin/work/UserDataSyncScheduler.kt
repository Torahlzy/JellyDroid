package dev.jdtech.jellyfin.work

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 监听离线修改并触发同步，周期任务兜底处理进程退出与任务收尾的交错。 */
@Singleton
class UserDataSyncScheduler
@Inject
constructor(private val database: ServerDatabaseDao, private val workManager: WorkManager) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        workManager.enqueueUniquePeriodicWork(
            "syncUserDataPeriodic",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build(),
        )
        scope.launch {
            database.observePendingUserData().distinctUntilChanged().collect { pending ->
                if (pending.isNotEmpty()) {
                    workManager.enqueueUniqueWork(
                        "syncUserData",
                        ExistingWorkPolicy.KEEP,
                        OneTimeWorkRequestBuilder<SyncWorker>()
                            .setConstraints(constraints)
                            .build(),
                    )
                }
            }
        }
    }
}
