package dev.jdtech.jellyfin.utils

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.work.DownloadExportWorker
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 从离线数据库读取可保存的视频，复用 WorkManager 串行复制，避免大文件挤占磁盘带宽。 */
class DownloadExporterImpl(
    private val database: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
    private val workManager: WorkManager,
) : DownloadExporter {
    private val enqueueMutex = Mutex()

    override suspend fun getItems(): List<DownloadExportItem> =
        withContext(Dispatchers.IO) {
            val serverId =
                appPreferences.getValue(appPreferences.currentServer)
                    ?: return@withContext emptyList()
            buildList {
                for (movie in database.getMoviesByServerId(serverId)) {
                    addAll(getSources(movie.id, movie.name))
                }
                for (episode in database.getEpisodesByServerId(serverId)) {
                    val season = episode.parentIndexNumber.toString().padStart(2, '0')
                    val number = episode.indexNumber.toString().padStart(2, '0')
                    val title = "${episode.seriesName} · S${season}E${number} · ${episode.name}"
                    addAll(getSources(episode.id, title))
                }
            }
        }

    private suspend fun getSources(itemId: UUID, title: String): List<DownloadExportItem> =
        database.getSources(itemId).mapNotNull { source ->
            // 仅使用已完成记录，不能把 .download 临时文件或分片当成完整视频。
            if (source.type != FindroidSourceType.LOCAL || source.downloadTaskId != null) {
                return@mapNotNull null
            }
            val file = File(source.path)
            if (!file.isFile || file.length() <= 0L) return@mapNotNull null
            DownloadExportItem(itemId, source.id, title, file.name, file.length())
        }

    override suspend fun export(items: List<DownloadExportItem>) =
        withContext(Dispatchers.IO) {
            enqueueMutex.withLock {
                require(items.isNotEmpty())
                check(
                    workManager.getWorkInfosForUniqueWork(WORK_NAME).get().none {
                        !it.state.isFinished
                    }
                )
                val requests = items.distinctBy { it.sourceId }.map(::createRequest)
                // 每个文件各用一个请求，避免整批来源 id 超过 WorkManager 的 10 KB 输入限制。
                var chain =
                    workManager.beginUniqueWork(
                        WORK_NAME,
                        ExistingWorkPolicy.REPLACE,
                        requests.first(),
                    )
                for (request in requests.drop(1)) chain = chain.then(request)
                chain.enqueue().result.get()
                Unit
            }
        }

    private fun createRequest(item: DownloadExportItem): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<DownloadExportWorker>()
            .setInputData(
                workDataOf(
                    DownloadExportWorker.KEY_ITEM_ID to item.itemId.toString(),
                    DownloadExportWorker.KEY_SOURCE_ID to item.sourceId,
                    DownloadExportWorker.KEY_FILE_NAME to item.fileName,
                )
            )
            .build()

    override fun observeState(): Flow<DownloadExportState> =
        workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).map(::buildExportState)

    private fun buildExportState(works: List<WorkInfo>): DownloadExportState {
        val saved = works.count { it.wasSaved() }
        val failed =
            works.count {
                it.state == WorkInfo.State.FAILED ||
                    (it.state == WorkInfo.State.SUCCEEDED && !it.wasSaved())
            }
        val cancelled = works.count { it.state == WorkInfo.State.CANCELLED }
        val progress =
            works.sumOf { work ->
                if (work.state.isFinished) 1.0
                else work.progress.getFloat(DownloadExportWorker.KEY_PROGRESS, 0f).toDouble()
            }
        val failure =
            works.firstNotNullOfOrNull { work ->
                val name = work.outputData.getString(DownloadExportWorker.KEY_ERROR)
                DownloadExportFailure.entries.firstOrNull { it.name == name }
            }
        return DownloadExportState(
            total = works.size,
            saved = saved,
            failed = failed,
            cancelled = cancelled,
            isRunning = works.any { !it.state.isFinished },
            progress = if (works.isEmpty()) 0f else (progress / works.size).toFloat(),
            error =
                failure?.let { UiText.StringResource(it.messageRes) }
                    ?: if (failed > 0) UiText.StringResource(CoreR.string.download_export_failed) else null,
        )
    }

    private fun WorkInfo.wasSaved(): Boolean =
        state == WorkInfo.State.SUCCEEDED &&
            outputData.getBoolean(DownloadExportWorker.KEY_SAVED, false)

    override suspend fun cancel() =
        withContext(Dispatchers.IO) {
            workManager.cancelUniqueWork(WORK_NAME).result.get()
            Unit
        }

    private companion object {
        const val WORK_NAME = "public_download_export"
    }
}
