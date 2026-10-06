package dev.jdtech.jellyfin.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMediaStream
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
import dev.jdtech.jellyfin.work.DownloadWorker
import dev.jdtech.jellyfin.work.ImagesDownloaderWorker
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.Exception
import kotlin.math.ceil
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** 失败后的退避起始间隔。 */
private const val BACKOFF_DELAY_MINUTES = 1L

/** 下载文件统一放在外部私有目录的该子目录下。 */
private const val DOWNLOAD_DIRECTORY = "downloads"

/** 文件名里不允许出现的字符（含控制字符），统一替换成下划线。 */
private val ILLEGAL_FILE_NAME_CHARS = Regex("""[\\/:*?"<>|\x00-\x1F]""")

/** 文件名长度上限，给去重序号和 `.download` 后缀留出余量。 */
private const val MAX_FILE_NAME_LENGTH = 100

/** 从下载地址推断扩展名时，认作扩展名的最大长度。 */
private const val MAX_EXTENSION_LENGTH = 5

/** 主文件名不可用时的字幕兜底名。 */
private const val DEFAULT_SUBTITLE_NAME = "subtitle"

/** 外部流地址末尾拿不到扩展名时，按编码格式兜底。 */
private val SUBTITLE_CODEC_EXTENSIONS =
    mapOf(
        "subrip" to "srt",
        "srt" to "srt",
        "ass" to "ass",
        "ssa" to "ass",
        "mov_text" to "ttml",
        "webvtt" to "vtt",
        "vtt" to "vtt",
        "sub" to "sub",
        "dvd_subtitle" to "sub",
        "hdmv_pgs_subtitle" to "sup",
    )

/**
 * 基于 OkHttp + WorkManager 的下载实现。
 *
 * 每个下载任务对应一个 [DownloadWorker]：文件写入、续传、进度上报都由 Worker 负责，
 * 本类只做任务入队、数据库记录维护与进度读取。相较原先的系统 `DownloadManager`，
 * 好处是能自定义网络栈（见 `DownloadNetworkModule` 已禁用系统代理），且离开界面后下载继续。
 */
class DownloaderImpl(
    private val context: Context,
    private val database: ServerDatabaseDao,
    private val jellyfinRepository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val workManager: WorkManager,
) : Downloader {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override suspend fun downloadItem(
        item: FindroidItem,
        sourceId: String,
        storageIndex: Int,
    ): Pair<String?, UiText?> = coroutineScope {
        val storageLocation = context.getExternalFilesDirs(null)?.getOrNull(storageIndex)
        if (
            storageLocation == null ||
                Environment.getExternalStorageState(storageLocation) != Environment.MEDIA_MOUNTED
        ) {
            return@coroutineScope Pair(null, UiText.StringResource(CoreR.string.storage_unavailable))
        }

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
            val stats = StatFs(storageLocation.path)
            if (stats.availableBytes < source.size) {
                return@coroutineScope Pair(
                    null,
                    UiText.StringResource(
                        CoreR.string.not_enough_storage,
                        Formatter.formatFileSize(context, source.size),
                        Formatter.formatFileSize(context, stats.availableBytes),
                    ),
                )
            }

            val file = buildDownloadFile(storageLocation, item.id, source.id)
            val uniqueName = "download_file_${item.id}_${source.id}"
            val request =
                buildDownloadRequest(
                    sourceId = source.id,
                    recordId = source.id,
                    recordType = DownloadWorker.RECORD_TYPE_SOURCE,
                    url = source.path,
                    filePath = file.absolutePath,
                    displayName = item.name,
                    outputFileName = resolveServerFileName(item, source),
                )
            val taskId = request.id.toString()

            // 必须先落库再入队：Worker 结束时会按 taskId 找回记录并回写文件路径
            insertItemRecords(item, source, file.absolutePath, taskId)

            logNetworkState()
            workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
            AppLog.i("下载已入队：itemId=%s，taskId=%s", item.id, taskId)

            downloadExternalMediaStreams(item, source, storageIndex)

            segments.forEach { database.insertSegment(it.toFindroidSegmentsDto(item.id)) }

            if (trickplayInfo != null) {
                downloadTrickplayData(item.id, sourceId, trickplayInfo)
            }

            startImagesDownloader(item)
            return@coroutineScope Pair(taskId, null)
        } catch (e: Exception) {
            try {
                val source = jellyfinRepository.getMediaSources(item.id).first { it.id == sourceId }
                deleteItem(item, source)
            } catch (_: Exception) {}
            AppLog.e(e)
            return@coroutineScope Pair(
                null,
                if (e.message != null) UiText.DynamicString(e.message.orEmpty())
                else UiText.StringResource(CoreR.string.unknown_error),
            )
        }
    }

    override suspend fun cancelDownload(item: FindroidItem, taskId: String) {
        val source = database.getSourceByTaskId(taskId)?.toFindroidSource(database) ?: return
        // 主文件与外部媒体流是同一批任务，按 tag 一起取消
        workManager.cancelAllWorkByTag(sourceDownloadTag(source.id))
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
        // 分片下载会留下 `<临时文件>.partN` 中间文件，删主文件时一并清掉，避免占用磁盘
        DownloadWorker.deleteSegmentFiles(source.path)
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

    override fun observeProgress(taskId: String?): Flow<DownloadProgress> {
        val workId = taskId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (workId == null) {
            return flowOf(DownloadProgress(status = DownloadStatus.UNKNOWN, progress = null))
        }
        return workManager.getWorkInfoByIdFlow(workId).map { it.toDownloadProgress() }
    }

    /** 把 WorkManager 的任务信息翻译成界面需要的状态。 */
    private fun WorkInfo?.toDownloadProgress(): DownloadProgress {
        if (this == null) {
            return DownloadProgress(status = DownloadStatus.UNKNOWN, progress = null)
        }
        val downloadedBytes = progress.getLong(DownloadWorker.KEY_PROGRESS_BYTES, 0L)
        val totalBytes = progress.getLong(DownloadWorker.KEY_PROGRESS_TOTAL, 0L)
        // 总大小未知时进度是"未知"而不是 0，避免误导用户
        val ratio = if (totalBytes > 0L) downloadedBytes.toFloat() / totalBytes else null
        // 只有真正在传输时速度才有意义；Worker 首帧测不出速度，这里按"无速度"处理
        val speedBytesPerSecond =
            progress
                .getLong(DownloadWorker.KEY_PROGRESS_SPEED, 0L)
                .takeIf { state == WorkInfo.State.RUNNING && it > 0L }

        return when (state) {
            WorkInfo.State.ENQUEUED ->
                when {
                    runAttemptCount > 0 ->
                        // 还在退避等待重试
                        DownloadProgress(
                            status = DownloadStatus.PAUSED,
                            progress = ratio,
                            reason = DownloadPauseReason.WAITING_TO_RETRY,
                        )
                    // 网络约束不满足时 WorkManager 会把任务留在 ENQUEUED（不会进入 BLOCKED），
                    // 只能自己比对当前网络，否则界面会一直显示"等待中"而看不出真实原因
                    !isDownloadNetworkSatisfied() ->
                        DownloadProgress(
                            status = DownloadStatus.PAUSED,
                            progress = ratio,
                            reason = DownloadPauseReason.WAITING_FOR_NETWORK,
                        )
                    else -> DownloadProgress(status = DownloadStatus.QUEUED, progress = ratio)
                }
            WorkInfo.State.RUNNING ->
                DownloadProgress(
                    status = DownloadStatus.RUNNING,
                    progress = ratio,
                    speedBytesPerSecond = speedBytesPerSecond,
                )
            WorkInfo.State.SUCCEEDED ->
                DownloadProgress(status = DownloadStatus.SUCCESSFUL, progress = 1f)
            WorkInfo.State.FAILED ->
                DownloadProgress(status = DownloadStatus.FAILED, progress = ratio)
            WorkInfo.State.BLOCKED ->
                DownloadProgress(
                    status = DownloadStatus.PAUSED,
                    progress = ratio,
                    reason = DownloadPauseReason.WAITING_FOR_NETWORK,
                )
            WorkInfo.State.CANCELLED ->
                DownloadProgress(status = DownloadStatus.UNKNOWN, progress = null)
        }
    }

    /** 下载完成的判定依赖 `.download` 后缀，这里统一构造临时文件。 */
    private fun buildDownloadFile(storageLocation: File, itemId: UUID, sourceId: String): File =
        File(storageLocation, "$DOWNLOAD_DIRECTORY/$itemId.$sourceId${DownloadWorker.TEMP_SUFFIX}")

    /**
     * 落盘后的文件名：优先对齐服务端文件名（媒体源在服务器上的真实路径），
     * 拿不到时退回条目名，避免用户看到的是一串 UUID。
     */
    private fun resolveServerFileName(item: FindroidItem, source: FindroidSource): String {
        val fromServer = source.remoteFilePath.substringAfterLast('/').substringAfterLast('\\')
        return sanitizeFileName(fromServer.ifBlank { item.name }).ifBlank { item.id.toString() }
    }

    /** 去掉文件名里不能出现在存储上的字符与首尾的空白、点，并限制长度。 */
    private fun sanitizeFileName(name: String): String =
        name.replace(ILLEGAL_FILE_NAME_CHARS, "_").trim().trim('.').take(MAX_FILE_NAME_LENGTH)

    /** 字幕等外部流不参与播放寻址，文件名只求可读、唯一、扩展名正确。 */
    private fun resolveSubtitleFileName(
        sourceFileName: String,
        index: Int,
        mediaStream: FindroidMediaStream,
    ): String {
        val baseName =
            sourceFileName.substringBeforeLast('.', sourceFileName).ifBlank { DEFAULT_SUBTITLE_NAME }
        val extension = resolveSubtitleExtension(mediaStream)
        return if (extension.isEmpty()) "$baseName.$index" else "$baseName.$index.$extension"
    }

    /** 外部流扩展名优先从下载地址末尾取（形如 `.../Subtitles/2/Stream.srt`），取不到再按编码兜底。 */
    private fun resolveSubtitleExtension(mediaStream: FindroidMediaStream): String {
        val fromUrl =
            mediaStream.path
                ?.substringBefore('?')
                ?.substringAfterLast('/')
                ?.substringAfterLast('.', "")
                ?.lowercase()
                ?.takeIf { candidate ->
                    candidate.isNotEmpty() &&
                        candidate.length <= MAX_EXTENSION_LENGTH &&
                        candidate.all(Char::isLetterOrDigit)
                }
        return fromUrl ?: SUBTITLE_CODEC_EXTENSIONS[mediaStream.codec.lowercase()].orEmpty()
    }

    private fun buildDownloadRequest(
        sourceId: String,
        recordId: String,
        recordType: String,
        url: String,
        filePath: String,
        displayName: String,
        outputFileName: String,
    ): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(buildNetworkConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_MINUTES,
                TimeUnit.MINUTES,
            )
            .addTag(sourceDownloadTag(sourceId))
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_RECORD_ID to recordId,
                    DownloadWorker.KEY_RECORD_TYPE to recordType,
                    DownloadWorker.KEY_URL to url,
                    DownloadWorker.KEY_FILE_PATH to filePath,
                    DownloadWorker.KEY_DISPLAY_NAME to displayName,
                    DownloadWorker.KEY_OUTPUT_FILE_NAME to outputFileName,
                )
            )
            .build()

    /** 把下载偏好翻译成 WorkManager 的网络约束，不满足时任务会停在等待状态而不是直接失败。 */
    private fun buildNetworkConstraints(): Constraints =
        Constraints.Builder().setRequiredNetworkType(resolveRequiredNetworkType()).build()

    private fun resolveRequiredNetworkType(): NetworkType {
        val allowMobileData = appPreferences.getValue(appPreferences.downloadOverMobileData)
        val allowRoaming = appPreferences.getValue(appPreferences.downloadWhenRoaming)
        return when {
            !allowMobileData -> NetworkType.UNMETERED
            // 不允许漫游时限制在非漫游网络（API 30 以下等同于 CONNECTED）
            !allowRoaming -> NetworkType.NOT_ROAMING
            else -> NetworkType.CONNECTED
        }
    }

    /** 当前网络是否满足下载约束，用于把「排队等待」与「在等网络」区分开。 */
    private fun isDownloadNetworkSatisfied(): Boolean {
        val manager = connectivityManager ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return when (resolveRequiredNetworkType()) {
            NetworkType.UNMETERED ->
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            NetworkType.NOT_ROAMING ->
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
            else -> true
        }
    }

    private fun sourceDownloadTag(sourceId: String) = "download_source_$sourceId"

    /** 记录主文件与附属数据：Worker 结束时会按 taskId 找回 source 记录。 */
    private suspend fun insertItemRecords(
        item: FindroidItem,
        source: FindroidSource,
        filePath: String,
        taskId: String,
    ) {
        when (item) {
            is FindroidMovie -> {
                database.insertMovie(
                    item.toFindroidMovieDto(appPreferences.getValue(appPreferences.currentServer))
                )
            }
            is FindroidEpisode -> {
                val show = jellyfinRepository.getShow(item.seriesId)
                database.insertShow(
                    show.toFindroidShowDto(appPreferences.getValue(appPreferences.currentServer))
                )
                val season = jellyfinRepository.getSeason(item.seasonId)
                database.insertSeason(season.toFindroidSeasonDto())
                database.insertEpisode(
                    item.toFindroidEpisodeDto(appPreferences.getValue(appPreferences.currentServer))
                )

                startImagesDownloader(show)
                startImagesDownloader(season)
            }
        }

        database.insertSource(
            source.toFindroidSourceDto(item.id, filePath).copy(downloadTaskId = taskId)
        )
        database.insertUserData(item.toFindroidUserDataDto(jellyfinRepository.getUserId()))
    }

    /** 打印提交下载时的网络形态，便于排查网络类问题。 */
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
        val storageLocation = context.getExternalFilesDirs(null)?.getOrNull(storageIndex) ?: return
        // 每个外部流每次都会分配新的 UUID，重试前必须先清掉上一轮的记录与残留临时文件，
        // 否则字幕等外部流会在库里越攒越多
        deleteExternalMediaStreams(source.id)
        val sourceFileName = resolveServerFileName(item, source)
        // 字幕等外部流靠 lang/title 命名容易重名，用「主文件名 + 序号 + 扩展名」保证可读且唯一
        for ((index, mediaStream) in source.mediaStreams.filter { it.isExternal }.withIndex()) {
            val streamUrl = mediaStream.path ?: continue
            val id = UUID.randomUUID()
            val file =
                File(
                    storageLocation,
                    "$DOWNLOAD_DIRECTORY/${item.id}.${source.id}.$id${DownloadWorker.TEMP_SUFFIX}",
                )
            val uniqueName = "download_stream_$id"
            val request =
                buildDownloadRequest(
                    sourceId = source.id,
                    recordId = id.toString(),
                    recordType = DownloadWorker.RECORD_TYPE_STREAM,
                    url = streamUrl,
                    filePath = file.absolutePath,
                    displayName = mediaStream.title,
                    outputFileName = resolveSubtitleFileName(sourceFileName, index, mediaStream),
                )
            // 同样先落库，Worker 结束后按 taskId 回写字幕等外部流的本地路径
            database.insertMediaStream(
                mediaStream
                    .toFindroidMediaStreamDto(id, source.id, file.absolutePath)
                    .copy(downloadTaskId = request.id.toString())
            )
            workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
        }
    }

    /** 清掉某个媒体源下已登记的外部流及其本地文件，保证每次下载都是同一批记录。 */
    private suspend fun deleteExternalMediaStreams(sourceId: String) {
        val existingStreams = database.getMediaStreamsBySourceId(sourceId)
        for (mediaStream in existingStreams) {
            File(mediaStream.path).delete()
        }
        database.deleteMediaStreamsBySourceId(sourceId)
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
