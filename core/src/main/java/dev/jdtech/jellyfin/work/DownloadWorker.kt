package dev.jdtech.jellyfin.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.di.DownloadOkHttpClient
import dev.jdtech.jellyfin.logging.AppLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * 单个下载任务：把 [KEY_URL] 指定的地址拉取到 [KEY_FILE_PATH]，完成后去掉 `.download` 后缀并回写数据库。
 *
 * 走 OkHttp 直连（[DownloadOkHttpClient] 已禁用系统代理），并以前台服务的方式运行，
 * 因此离开界面后下载仍会继续。文件已存在时带 `Range` 头续传。
 */
@HiltWorker
class DownloadWorker
@AssistedInject
constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val database: ServerDatabaseDao,
    @DownloadOkHttpClient private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, params) {
    /** 上一次上报进度的时刻，用于节流，避免每读满一个缓冲区就写一遍 WorkManager 与通知。 */
    private var lastProgressReportAt = 0L

    /** 上一次上报时的已下载字节数，与 [lastProgressReportAt] 一起用于估算下载速度。 */
    private var lastReportedBytes = 0L

    /** 指数移动平均后的下载速度（字节/秒），用来抹平网络抖动导致的读数跳变。 */
    private var smoothedSpeedBytesPerSecond = 0.0

    /** 通知渠道全局只存在一次，记录本任务内是否已确认过，避免每次刷新进度都查询一遍。 */
    private var isNotificationChannelCreated = false

    override suspend fun doWork(): Result {
        val recordId = inputData.getString(KEY_RECORD_ID) ?: return Result.failure()
        val recordType = inputData.getString(KEY_RECORD_TYPE) ?: return Result.failure()
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val displayName = inputData.getString(KEY_DISPLAY_NAME).orEmpty()
        val outputFileName = inputData.getString(KEY_OUTPUT_FILE_NAME)

        val temporaryFile = File(filePath)
        temporaryFile.parentFile?.mkdirs()
        enterForeground(displayName)

        return try {
            val resumeFrom = if (temporaryFile.exists()) temporaryFile.length() else 0L
            val request =
                Request.Builder()
                    .url(url)
                    .apply { if (resumeFrom > 0L) header(HEADER_RANGE, "bytes=$resumeFrom-") }
                    .build()

            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        // 只有服务端确认支持续传（206）才追加写入；返回 200 说明要整体重来
                        val append = resumeFrom > 0L && response.code == HTTP_PARTIAL_CONTENT
                        if (!append) temporaryFile.delete()
                        val totalBytes = resolveTotalBytes(response, resumeFrom, append)
                        if (writeBody(response, temporaryFile, append, totalBytes, displayName)) {
                            finishDownload(recordId, recordType, temporaryFile, outputFileName)
                        } else {
                            // 任务已被取消，WorkManager 会忽略返回值
                            Result.failure()
                        }
                    }
                    response.code == HTTP_RANGE_NOT_SATISFIABLE ->
                        handleRangeNotSatisfiable(
                            response = response,
                            resumeFrom = resumeFrom,
                            temporaryFile = temporaryFile,
                            recordId = recordId,
                            recordType = recordType,
                            displayName = displayName,
                            outputFileName = outputFileName,
                        )
                    else -> handleHttpError(response.code, displayName)
                }
            }
        } catch (e: IOException) {
            AppLog.w(e, "下载任务 %s 中断", id)
            if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure()
        }
    }

    /** 进入前台会受 Android 12+ 的后台启动限制，失败时降级为普通后台任务继续下载。 */
    private suspend fun enterForeground(displayName: String) {
        runCatching { setForeground(createForegroundInfo(displayName, null)) }
            .onFailure { AppLog.w(it, "下载任务 %s 无法进入前台服务", id) }
    }

    /**
     * 把响应体写入文件，返回是否完整读完（false 表示任务在写入过程中被取消）。
     *
     * OkHttp 的阻塞式读取不会随协程取消而中断，只能在循环里主动检查 [isStopped]。
     */
    private suspend fun writeBody(
        response: Response,
        file: File,
        append: Boolean,
        totalBytes: Long,
        displayName: String,
    ): Boolean {
        val body = response.body ?: throw IOException("响应体为空")
        var downloadedBytes = if (append) file.length() else 0L
        val buffer = ByteArray(BUFFER_SIZE)

        FileOutputStream(file, append).use { output ->
            body.byteStream().use { input ->
                while (true) {
                    if (isStopped) return false
                    val readCount = input.read(buffer)
                    if (readCount == -1) break
                    output.write(buffer, 0, readCount)
                    downloadedBytes += readCount
                    reportProgress(downloadedBytes, totalBytes, displayName)
                }
                output.flush()
            }
        }
        return true
    }

    private suspend fun reportProgress(
        downloadedBytes: Long,
        totalBytes: Long,
        displayName: String,
    ) {
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastProgressReportAt
        if (elapsed < PROGRESS_REPORT_INTERVAL_MS) return

        val speedBytesPerSecond = updateSpeed(downloadedBytes, elapsed)
        lastProgressReportAt = now
        lastReportedBytes = downloadedBytes

        setProgress(
            workDataOf(
                KEY_PROGRESS_BYTES to downloadedBytes,
                KEY_PROGRESS_TOTAL to totalBytes,
                KEY_PROGRESS_SPEED to speedBytesPerSecond,
            )
        )
        val percent =
            if (totalBytes > 0L) ((downloadedBytes * MAX_PROGRESS) / totalBytes).toInt() else null
        runCatching { setForeground(createForegroundInfo(displayName, percent)) }
    }

    /**
     * 用两次上报之间的字节差估算下载速度（字节/秒）。
     *
     * 首次上报没有可比基准（续传时已下载字节数等于文件已有长度），只记录基准、
     * 上报 0 表示"还没测出速度"；之后做指数移动平均，抑制读数跳动。
     */
    private fun updateSpeed(downloadedBytes: Long, elapsedMs: Long): Long {
        if (lastProgressReportAt == 0L || elapsedMs <= 0L) return 0L
        val deltaBytes = (downloadedBytes - lastReportedBytes).coerceAtLeast(0L)
        val instantSpeed = deltaBytes * MILLIS_PER_SECOND / elapsedMs
        smoothedSpeedBytesPerSecond =
            if (smoothedSpeedBytesPerSecond <= 0.0) {
                instantSpeed.toDouble()
            } else {
                smoothedSpeedBytesPerSecond * (1 - SPEED_SMOOTHING_FACTOR) +
                    instantSpeed * SPEED_SMOOTHING_FACTOR
            }
        return smoothedSpeedBytesPerSecond.toLong()
    }

    private suspend fun finishDownload(
        recordId: String,
        recordType: String,
        temporaryFile: File,
        outputFileName: String?,
    ): Result {
        val finalFile = resolveFinalFile(temporaryFile, outputFileName)
        if (!temporaryFile.renameTo(finalFile)) {
            AppLog.e("下载文件重命名失败：%s", temporaryFile.name)
            return Result.failure()
        }

        return when (recordType) {
            RECORD_TYPE_SOURCE -> {
                database.setSourcePath(recordId, finalFile.absolutePath)
                AppLog.i("下载完成：%s", finalFile.name)
                Result.success()
            }
            RECORD_TYPE_STREAM -> {
                val streamId = runCatching { UUID.fromString(recordId) }.getOrNull()
                if (streamId == null) {
                    AppLog.e("媒体流 id 非法：%s", recordId)
                    return Result.failure()
                }
                database.setMediaStreamPath(streamId, finalFile.absolutePath)
                Result.success()
            }
            else -> {
                AppLog.e("未知的下载记录类型：%s", recordType)
                Result.failure()
            }
        }
    }

    /**
     * 决定最终文件名：用入队时给的服务端文件名，同名则加序号。
     *
     * 临时文件里的 UUID 只是为了保证不同任务互不干扰，最终名要对用户可读；
     * 同名的另一个媒体源不能被覆盖，因此这里只找空位、不删已有文件。
     */
    private fun resolveFinalFile(temporaryFile: File, outputFileName: String?): File {
        val directory = temporaryFile.parentFile
        val desiredName =
            outputFileName?.takeIf { it.isNotBlank() }
                ?: temporaryFile.name.removeSuffix(TEMP_SUFFIX)
        var candidate = File(directory, desiredName)
        var index = FIRST_DUPLICATE_INDEX
        while (candidate.exists() && index <= MAX_DUPLICATE_INDEX) {
            candidate = File(directory, appendIndex(desiredName, index))
            index++
        }
        return candidate
    }

    /** 把 `Movie.mkv` 变成 `Movie (2).mkv`；没有扩展名时序号直接追加在末尾。 */
    private fun appendIndex(fileName: String, index: Int): String {
        val extension = fileName.substringAfterLast('.', "")
        if (extension.isEmpty()) return "$fileName ($index)"
        val baseName = fileName.dropLast(extension.length + 1)
        return "$baseName ($index).$extension"
    }

    /** 客户端错误（鉴权失效、资源不存在）重试无意义，其余状态码按退避策略重试。 */
    private fun handleHttpError(code: Int, displayName: String): Result {
        AppLog.e("下载 %s 失败，HTTP %d", displayName, code)
        return if (code in HTTP_CLIENT_ERROR_RANGE) {
            Result.failure()
        } else if (runAttemptCount < MAX_RETRY_COUNT) {
            Result.retry()
        } else {
            Result.failure()
        }
    }

    /**
     * 续传请求被拒（416）：说明本地临时文件已经不小于服务端文件大小。
     *
     * 最常见的原因是上一轮已把字节写完、只是没来得及重命名就被中断，此时直接收尾即可。
     * 服务端没给出总大小时无法判断，只能删掉残留文件整体重下。
     */
    private suspend fun handleRangeNotSatisfiable(
        response: Response,
        resumeFrom: Long,
        temporaryFile: File,
        recordId: String,
        recordType: String,
        displayName: String,
        outputFileName: String?,
    ): Result {
        val totalFromRange =
            response.header(HEADER_CONTENT_RANGE)?.substringAfter('/')?.toLongOrNull()
        if (totalFromRange != null && resumeFrom >= totalFromRange) {
            AppLog.i("下载 %s 的临时文件已完整，跳过续传直接收尾", displayName)
            return finishDownload(recordId, recordType, temporaryFile, outputFileName)
        }

        AppLog.w("下载 %s 的续传请求被拒绝（HTTP 416），将重新下载", displayName)
        temporaryFile.delete()
        return if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure()
    }

    /** 续传时 `Content-Length` 只是剩余字节数，总大小要从 `Content-Range` 的「/总数」里取。 */
    private fun resolveTotalBytes(response: Response, resumeFrom: Long, append: Boolean): Long {
        val contentLength = response.body?.contentLength()?.takeIf { it >= 0L } ?: 0L
        if (!append) return contentLength

        val totalFromRange =
            response.header(HEADER_CONTENT_RANGE)?.substringAfter('/')?.toLongOrNull()
        return totalFromRange ?: (resumeFrom + contentLength)
    }

    private fun createForegroundInfo(displayName: String, percent: Int?): ForegroundInfo {
        ensureNotificationChannel()
        val notification =
            NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(CoreR.drawable.ic_download)
                .setContentTitle(appContext.getString(CoreR.string.download_notification_title))
                .setContentText(displayName)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(
                    MAX_PROGRESS,
                    percent ?: 0,
                    // 拿不到总大小时用不确定进度条
                    percent == null,
                )
                .build()

        val notificationId = id.hashCode()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (isNotificationChannelCreated) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            val channel =
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    appContext.getString(CoreR.string.download_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
        }
        isNotificationChannelCreated = true
    }

    companion object {
        const val KEY_RECORD_ID = "KEY_RECORD_ID"
        const val KEY_RECORD_TYPE = "KEY_RECORD_TYPE"
        const val KEY_URL = "KEY_URL"
        const val KEY_FILE_PATH = "KEY_FILE_PATH"
        const val KEY_DISPLAY_NAME = "KEY_DISPLAY_NAME"

        /** 下载完成后期望的文件名（含扩展名），用于对齐服务端文件名。 */
        const val KEY_OUTPUT_FILE_NAME = "KEY_OUTPUT_FILE_NAME"
        const val KEY_PROGRESS_BYTES = "KEY_PROGRESS_BYTES"
        const val KEY_PROGRESS_TOTAL = "KEY_PROGRESS_TOTAL"

        /** 已测出的下载速度（字节/秒），0 表示还没测出来。 */
        const val KEY_PROGRESS_SPEED = "KEY_PROGRESS_SPEED"

        /** 记录的是媒体源文件还是外部媒体流，决定完成后回写哪张表。 */
        const val RECORD_TYPE_SOURCE = "source"
        const val RECORD_TYPE_STREAM = "stream"

        /** 下载中的临时文件后缀，写完后再去掉；判断"是否下载完成"也依赖它。 */
        const val TEMP_SUFFIX = ".download"

        private const val NOTIFICATION_CHANNEL_ID = "downloads"
        private const val BUFFER_SIZE = 64 * 1024

        /** 进度上报频率：每次上报都会写一遍 WorkManager 记录，没必要太密。 */
        private const val PROGRESS_REPORT_INTERVAL_MS = 1_000L
        private const val MAX_RETRY_COUNT = 3
        private const val MAX_PROGRESS = 100
        private const val MILLIS_PER_SECOND = 1_000L

        /** 速度平滑系数：新读数占的比重，越大越灵敏、越小越稳。 */
        private const val SPEED_SMOOTHING_FACTOR = 0.4

        /** 同名文件从 `(2)` 开始编号，最多试到该值，避免极端情况下无限循环。 */
        private const val FIRST_DUPLICATE_INDEX = 2
        private const val MAX_DUPLICATE_INDEX = 99
        private const val HTTP_PARTIAL_CONTENT = 206

        /** 请求的续传起点已不小于服务端文件大小时返回的状态码。 */
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private val HTTP_CLIENT_ERROR_RANGE = 400..499
        private const val HEADER_RANGE = "Range"
        private const val HEADER_CONTENT_RANGE = "Content-Range"
    }
}
