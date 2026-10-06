package dev.jdtech.jellyfin.utils

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.core.net.toUri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSources
import dev.jdtech.jellyfin.models.FindroidTrickplayInfo
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.toFindroidEpisodeDto
import dev.jdtech.jellyfin.models.toFindroidMediaStreamDto
import dev.jdtech.jellyfin.models.toFindroidMovieDto
import dev.jdtech.jellyfin.models.toFindroidSeasonDto
import dev.jdtech.jellyfin.models.toFindroidSegmentsDto
import dev.jdtech.jellyfin.models.toFindroidShowDto
import dev.jdtech.jellyfin.models.toFindroidSource
import dev.jdtech.jellyfin.models.toFindroidSourceDto
import dev.jdtech.jellyfin.models.toFindroidTrickplayInfoDto
import dev.jdtech.jellyfin.models.toFindroidUserDataDto
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.work.ImagesDownloaderWorker
import java.io.File
import java.util.UUID
import kotlin.Exception
import kotlin.math.ceil
import kotlinx.coroutines.coroutineScope

// 日志去重表最多保留的下载任务数，超出后淘汰最早写入的一条
private const val LAST_LOGGED_STATUS_LIMIT = 16

class DownloaderImpl(
    private val context: Context,
    private val database: ServerDatabaseDao,
    private val jellyfinRepository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val workManager: WorkManager,
) : Downloader {
    private val downloadManager = context.getSystemService(DownloadManager::class.java)
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    /**
     * 记录每个下载任务上一次已打日志的状态码，避免每秒轮询都刷屏。
     *
     * 用带上限的 LinkedHashMap：长期运行会把历史 downloadId 全攒在内存里，超过 [LAST_LOGGED_STATUS_LIMIT]
     * 时淘汰最早写入的一条（界面同一时刻只跟踪一个下载，没必要保留全部）。
     */
    private val lastLoggedStatus =
        object : LinkedHashMap<Long, Int>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Int>?): Boolean =
                size > LAST_LOGGED_STATUS_LIMIT
        }

    // TODO: We should probably move most (if not all) code to a worker.
    //  At this moment it is possible that some things are not downloaded due to the user leaving
    //  the current screen
    override suspend fun downloadItem(
        item: FindroidItem,
        sourceId: String,
        storageIndex: Int,
    ): Pair<Long, UiText?> = coroutineScope {
        try {
            val source =
                jellyfinRepository.getMediaSources(item.id, true).first { it.id == sourceId }
            val segments = jellyfinRepository.getSegments(item.id)
            val trickplayInfo =
                if (item is FindroidSources) {
                    item.trickplayInfo?.get(sourceId)
                } else {
                    null
                }
            val storageLocation = context.getExternalFilesDirs(null)[storageIndex]
            if (
                storageLocation == null ||
                    Environment.getExternalStorageState(storageLocation) !=
                        Environment.MEDIA_MOUNTED
            ) {
                return@coroutineScope Pair(
                    -1,
                    UiText.StringResource(CoreR.string.storage_unavailable),
                )
            }
            val path =
                Uri.fromFile(File(storageLocation, "downloads/${item.id}.${source.id}.download"))
            val stats = StatFs(storageLocation.path)
            if (stats.availableBytes < source.size) {
                return@coroutineScope Pair(
                    -1,
                    UiText.StringResource(
                        CoreR.string.not_enough_storage,
                        Formatter.formatFileSize(context, source.size),
                        Formatter.formatFileSize(context, stats.availableBytes),
                    ),
                )
            }
            val request =
                DownloadManager.Request(source.path.toUri())
                    .setTitle(item.name)
                    .setAllowedOverMetered(
                        appPreferences.getValue(appPreferences.downloadOverMobileData)
                    )
                    .setAllowedOverRoaming(
                        appPreferences.getValue(appPreferences.downloadWhenRoaming)
                    )
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationUri(path)
            logNetworkState()
            val downloadId = downloadManager.enqueue(request)
            AppLog.i("下载已入队：itemId=%s，downloadId=%d", item.id, downloadId)

            when (item) {
                is FindroidMovie -> {
                    database.insertMovie(
                        item.toFindroidMovieDto(
                            appPreferences.getValue(appPreferences.currentServer)
                        )
                    )
                }
                is FindroidEpisode -> {
                    val show = jellyfinRepository.getShow(item.seriesId)
                    database.insertShow(
                        show.toFindroidShowDto(
                            appPreferences.getValue(appPreferences.currentServer)
                        )
                    )
                    val season = jellyfinRepository.getSeason(item.seasonId)
                    database.insertSeason(season.toFindroidSeasonDto())
                    database.insertEpisode(
                        item.toFindroidEpisodeDto(
                            appPreferences.getValue(appPreferences.currentServer)
                        )
                    )

                    startImagesDownloader(show)
                    startImagesDownloader(season)
                }
            }

            val sourceDto = source.toFindroidSourceDto(item.id, path.path.orEmpty())

            database.insertSource(sourceDto.copy(downloadId = downloadId))
            database.insertUserData(item.toFindroidUserDataDto(jellyfinRepository.getUserId()))

            downloadExternalMediaStreams(item, source, storageIndex)

            segments.forEach { database.insertSegment(it.toFindroidSegmentsDto(item.id)) }

            if (trickplayInfo != null) {
                downloadTrickplayData(item.id, sourceId, trickplayInfo)
            }

            startImagesDownloader(item)
            return@coroutineScope Pair(downloadId, null)
        } catch (e: Exception) {
            try {
                val source = jellyfinRepository.getMediaSources(item.id).first { it.id == sourceId }
                deleteItem(item, source)
            } catch (_: Exception) {}
            AppLog.e(e)
            return@coroutineScope Pair(
                -1,
                if (e.message != null) UiText.DynamicString(e.message!!)
                else UiText.StringResource(CoreR.string.unknown_error),
            )
        }
    }

    override suspend fun cancelDownload(item: FindroidItem, downloadId: Long) {
        val source =
            database.getSourceByDownloadId(downloadId)?.toFindroidSource(database) ?: return
        if (source.downloadId != null) {
            downloadManager.remove(source.downloadId!!)
        }
        deleteItem(item, source)
    }

    override suspend fun deleteItem(item: FindroidItem, source: FindroidSource) {
        when (item) {
            is FindroidMovie -> {
                database.deleteMovie(item.id)
            }
            is FindroidEpisode -> {
                database.deleteEpisode(item.id)
                val remainingEpisodes = database.getEpisodesBySeasonId(item.seasonId)
                if (remainingEpisodes.isEmpty()) {
                    database.deleteSeason(item.seasonId)
                    database.deleteUserData(item.seasonId)
                    File(context.filesDir, "trickplay/${item.seasonId}").deleteRecursively()
                    File(context.filesDir, "images/${item.seasonId}").deleteRecursively()
                    val remainingSeasons = database.getSeasonsByShowId(item.seriesId)
                    if (remainingSeasons.isEmpty()) {
                        database.deleteShow(item.seriesId)
                        database.deleteUserData(item.seriesId)
                        File(context.filesDir, "trickplay/${item.seriesId}").deleteRecursively()
                        File(context.filesDir, "images/${item.seriesId}").deleteRecursively()
                    }
                }
            }
        }

        database.deleteSource(source.id)
        File(source.path).delete()

        val mediaStreams = database.getMediaStreamsBySourceId(source.id)
        for (mediaStream in mediaStreams) {
            File(mediaStream.path).delete()
        }
        database.deleteMediaStreamsBySourceId(source.id)

        database.deleteUserData(item.id)

        File(context.filesDir, "trickplay/${item.id}").deleteRecursively()
        File(context.filesDir, "images/${item.id}").deleteRecursively()
    }

    override suspend fun getProgress(downloadId: Long?): Pair<Int, Int> {
        var downloadStatus = -1
        var progress = -1
        if (downloadId == null) {
            // 本地下载记录里没有 downloadId，无法向 DownloadManager 查询，状态与进度都是未知
            return Pair(downloadStatus, progress)
        }
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (cursor.moveToFirst()) {
                downloadStatus =
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val totalBytes =
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                val downloadedBytes =
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                        )
                    )
                // 只在上报状态变化时打日志，避免每秒刷屏；reason 用于区分排队、网络错误重试等内部状态
                if (lastLoggedStatus.put(downloadId, downloadStatus) != downloadStatus) {
                    AppLog.i(
                        "下载 %d 状态=%d，reason=%d，已下载 %d/%d 字节",
                        downloadId,
                        downloadStatus,
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                        downloadedBytes,
                        totalBytes,
                    )
                }
                when (downloadStatus) {
                    DownloadManager.STATUS_RUNNING -> {
                        // totalBytes<=0 说明 DownloadManager 还没拿到文件总大小，此时进度是未知而不是 0
                        if (totalBytes > 0) {
                            progress = downloadedBytes.times(100).div(totalBytes).toInt()
                        }
                    }

                    DownloadManager.STATUS_SUCCESSFUL -> {
                        progress = 100
                    }
                }
            } else {
                // 记录消失后每秒轮询都会走到这里，复用状态去重只在第一次打日志，避免刷屏
                if (
                    lastLoggedStatus.put(downloadId, DownloadManager.STATUS_FAILED) !=
                        DownloadManager.STATUS_FAILED
                ) {
                    AppLog.w("下载 %d 在 DownloadManager 中已不存在", downloadId)
                }
                downloadStatus = DownloadManager.STATUS_FAILED
            }
        }
        return Pair(downloadStatus, progress)
    }

    /** 打印提交下载时的网络形态，便于排查"下载被代理接管导致连不上服务器"这类问题。 */
    private fun logNetworkState() {
        val capabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        AppLog.i(
            "提交下载时网络：已连接=%b，VPN=%b，WLAN=%b",
            capabilities != null,
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false,
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false,
        )
    }

    private suspend fun downloadExternalMediaStreams(
        item: FindroidItem,
        source: FindroidSource,
        storageIndex: Int = 0,
    ) {
        val storageLocation = context.getExternalFilesDirs(null)[storageIndex]
        for (mediaStream in source.mediaStreams.filter { it.isExternal }) {
            val id = UUID.randomUUID()
            val streamPath =
                Uri.fromFile(
                    File(storageLocation, "downloads/${item.id}.${source.id}.$id.download")
                )
            database.insertMediaStream(
                mediaStream.toFindroidMediaStreamDto(id, source.id, streamPath.path.orEmpty())
            )
            val request =
                DownloadManager.Request(mediaStream.path!!.toUri())
                    .setTitle(mediaStream.title)
                    .setAllowedOverMetered(
                        appPreferences.getValue(appPreferences.downloadOverMobileData)
                    )
                    .setAllowedOverRoaming(
                        appPreferences.getValue(appPreferences.downloadWhenRoaming)
                    )
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
                    .setDestinationUri(streamPath)
            val downloadId = downloadManager.enqueue(request)
            database.setMediaStreamDownloadId(id, downloadId)
        }
    }

    private suspend fun downloadTrickplayData(
        itemId: UUID,
        sourceId: String,
        trickplayInfo: FindroidTrickplayInfo,
    ) {
        val maxIndex =
            ceil(
                    trickplayInfo.thumbnailCount
                        .toDouble()
                        .div(trickplayInfo.tileWidth * trickplayInfo.tileHeight)
                )
                .toInt()
        val byteArrays = mutableListOf<ByteArray>()
        for (i in 0..maxIndex) {
            jellyfinRepository.getTrickplayData(itemId, trickplayInfo.width, i)?.let { byteArray ->
                byteArrays.add(byteArray)
            }
        }
        saveTrickplayData(itemId, sourceId, trickplayInfo, byteArrays)
    }

    private suspend fun saveTrickplayData(
        itemId: UUID,
        sourceId: String,
        trickplayInfo: FindroidTrickplayInfo,
        byteArrays: List<ByteArray>,
    ) {
        val basePath = "trickplay/$itemId/$sourceId"
        database.insertTrickplayInfo(trickplayInfo.toFindroidTrickplayInfoDto(sourceId))
        File(context.filesDir, basePath).mkdirs()
        for ((i, byteArray) in byteArrays.withIndex()) {
            val file = File(context.filesDir, "$basePath/$i")
            file.writeBytes(byteArray)
        }
    }

    private fun startImagesDownloader(item: FindroidItem) {
        val downloadImagesRequest =
            OneTimeWorkRequestBuilder<ImagesDownloaderWorker>()
                .setInputData(workDataOf(ImagesDownloaderWorker.KEY_ITEM_ID to item.id.toString()))
                .build()

        workManager.enqueue(downloadImagesRequest)
    }
}
