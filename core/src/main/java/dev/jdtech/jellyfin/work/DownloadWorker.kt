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
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * 单个下载任务：把 [KEY_URL] 指定的地址拉取到 [KEY_FILE_PATH]，完成后去掉 `.download` 后缀并回写数据库。
 *
 * 走 OkHttp 直连（[DownloadOkHttpClient] 已禁用系统代理），并以前台服务的方式运行，
 * 因此离开界面后下载仍会继续。文件已存在时带 `Range` 头续传。
 *
 * 大文件（剩余超过 [SEGMENT_MIN_REMAINING_BYTES]）且服务端支持 Range 时切成 [SEGMENT_COUNT] 段并发下载：
 * 单条 TCP 连接的吞吐受带宽时延积限制，多条连接在跨公网 / 高延迟链路上能明显更快。
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
        val request = readDownloadRequest() ?: return Result.failure()

        val temporaryFile = File(request.filePath)
        temporaryFile.parentFile?.mkdirs()
        enterForeground(request.displayName)

        return try {
            val resumeFrom = if (temporaryFile.exists()) temporaryFile.length() else 0L
            // OkHttp 的 execute 是阻塞调用，显式挪到 IO 线程，别占住 doWork 默认的 Default 调度器
            val totalBytes = withContext(Dispatchers.IO) { probeSegmentSupport(request.url) }
            if (totalBytes != null && totalBytes - resumeFrom > SEGMENT_MIN_REMAINING_BYTES) {
                try {
                    downloadInSegments(request, temporaryFile, resumeFrom, totalBytes)
                } catch (e: SegmentNotSupportedException) {
                    // 探测说支持、真下分片时却拿不到 206：退回单连接，别让用户白等几轮重试
                    AppLog.w("任务 %s 的分片请求未被接受(%s)，退回单连接下载", id, e.message.orEmpty())
                    withContext(Dispatchers.IO) { downloadInSingleConnection(request, temporaryFile) }
                }
            } else {
                withContext(Dispatchers.IO) { downloadInSingleConnection(request, temporaryFile) }
            }
        } catch (e: IOException) {
            AppLog.w(e, "下载任务 %s 中断", id)
            if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure()
        }
    }

    /** 读取入队时写入的参数；缺字段说明任务数据本身异常，直接判失败。 */
    private fun readDownloadRequest(): DownloadRequest? {
        val recordId = inputData.getString(KEY_RECORD_ID) ?: return null
        val recordType = inputData.getString(KEY_RECORD_TYPE) ?: return null
        val url = inputData.getString(KEY_URL) ?: return null
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return null
        return DownloadRequest(
            recordId = recordId,
            recordType = recordType,
            url = url,
            filePath = filePath,
            displayName = inputData.getString(KEY_DISPLAY_NAME).orEmpty(),
            outputFileName = inputData.getString(KEY_OUTPUT_FILE_NAME),
        )
    }

    /**
     * 探测服务端能否按区间下载：请求第 0 个字节，只有返回 206 且能读出总大小才算支持。
     *
     * 返回文件总大小；null 表示走不了分片（不支持 Range、状态码异常或拿不到总大小），
     * 调用方退回单连接下载，功能不受影响。
     */
    private fun probeSegmentSupport(url: String): Long? {
        val request = Request.Builder().url(url).header(HEADER_RANGE, "bytes=0-0").build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (response.code != HTTP_PARTIAL_CONTENT) return null
            response
                .header(HEADER_CONTENT_RANGE)
                ?.substringAfter('/')
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
        }
    }

    /** 单连接顺序下载：服务端不支持分片、或剩余数据太少时走这条路。 */
    private suspend fun downloadInSingleConnection(
        request: DownloadRequest,
        temporaryFile: File,
    ): Result {
        // 分片残留的起点是按分片边界算的，与单连接续传的起点对不上，必须清掉
        deleteSegmentFiles(temporaryFile.absolutePath)

        val resumeFrom = if (temporaryFile.exists()) temporaryFile.length() else 0L
        val httpRequest =
            Request.Builder()
                .url(request.url)
                .apply { if (resumeFrom > 0L) header(HEADER_RANGE, "bytes=$resumeFrom-") }
                .build()

        okHttpClient.newCall(httpRequest).execute().use { response ->
            return when {
                response.isSuccessful -> {
                    // 只有服务端确认支持续传（206）才追加写入；返回 200 说明要整体重来
                    val append = resumeFrom > 0L && response.code == HTTP_PARTIAL_CONTENT
                    if (!append) temporaryFile.delete()
                    val totalBytes = resolveTotalBytes(response, resumeFrom, append)
                    if (
                        writeBody(
                            response,
                            temporaryFile,
                            append,
                            totalBytes,
                            request.displayName,
                        )
                    ) {
                        finishDownload(request, temporaryFile)
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
                        request = request,
                    )
                else -> handleHttpError(response.code, request.displayName)
            }
        }
    }

    /**
     * 多连接分片下载：把文件按固定边界切成若干段并发拉取，各段写各自的 part 文件，最后按序合并。
     *
     * 分片只累加已下载字节，进度与通知由单独的上报协程刷新，避免多个协程同时写 WorkManager。
     */
    private suspend fun downloadInSegments(
        request: DownloadRequest,
        temporaryFile: File,
        resumeFrom: Long,
        totalBytes: Long,
    ): Result {
        val segments = buildSegments(resumeFrom, totalBytes)
        // 起点错位的 part 文件对应的区间已经变了，留着会被误当成续传进度
        deleteStaleSegmentFiles(temporaryFile, segments)

        // 进度起点是主临时文件已有的前缀加上各 part 已下载的字节
        val downloadedBytes =
            AtomicLong(resumeFrom + resolveSegmentProgress(temporaryFile, segments))
        val outcomes =
            coroutineScope {
                val progressJob = launch { pollProgress(downloadedBytes, totalBytes, request) }
                try {
                    segments
                        .map { segment ->
                            async(Dispatchers.IO) {
                                downloadSegment(request.url, temporaryFile, segment, downloadedBytes)
                            }
                        }
                        .awaitAll()
                } finally {
                    // 分片全部结束或抛出异常时都要停掉上报
                    progressJob.cancel()
                }
            }

        // 先处理"不支持区间下载"：此时连同其他分片一起重试没有意义，退回单连接往往能下完
        if (outcomes.any { it == SegmentOutcome.NotSupported }) {
            throw SegmentNotSupportedException("HTTP 非 206")
        }
        outcomes.filterIsInstance<SegmentOutcome.Failed>().firstOrNull()?.let { throw it.error }
        // 真正被取消时整个 coroutineScope 会抛 CancellationException，走不到这里；
        // 保留这一分支是为了兜住「协程仍在、但 Worker 已被要求停止」的情况
        if (outcomes.any { it == SegmentOutcome.Cancelled }) return Result.failure()

        // 段数据不足说明响应被截断，宁可重下也不能合并出一个损坏的文件
        val incompleteSegment =
            segments.firstOrNull { segmentFile(temporaryFile, it).length() != it.length }
        if (incompleteSegment != null) {
            AppLog.w("分片 %d 数据不完整，将重新下载", incompleteSegment.downloadedFrom)
            deleteSegmentFiles(temporaryFile.absolutePath)
            temporaryFile.delete()
            throw IOException("分片数据不完整")
        }

        if (!mergeSegments(temporaryFile, segments)) return Result.failure()

        // 收尾前的最后一道校验：长度对不上就是拼错了，宁可重下也不能入库一个坏文件
        if (temporaryFile.length() != totalBytes) {
            AppLog.w("分片合并后得到 %d 字节，与预期的 %d 不符，将重新下载", temporaryFile.length(), totalBytes)
            deleteSegmentFiles(temporaryFile.absolutePath)
            temporaryFile.delete()
            throw IOException("分片合并结果不完整")
        }

        AppLog.d("分片下载完成：%d 段，共 %d 字节", segments.size, totalBytes)
        return finishDownload(request, temporaryFile)
    }

    /**
     * 按固定边界把文件均分成 [SEGMENT_COUNT] 段。
     *
     * 边界只与文件总大小有关，与续传位置无关；[resumeFrom] 之前的数据已经在主临时文件里，
     * 因此落在该前缀内的段直接跳过，跨在前缀上的段从 [resumeFrom] 开始下。
     */
    private fun buildSegments(resumeFrom: Long, totalBytes: Long): List<Segment> {
        val segmentLength = totalBytes / SEGMENT_COUNT
        return (0 until SEGMENT_COUNT)
            .mapNotNull { index ->
                val start = segmentLength * index
                // 最后一段吃掉整除余数，保证各段首尾相接、不重不漏
                val endInclusive =
                    if (index == SEGMENT_COUNT - 1) totalBytes - 1
                    else start + segmentLength - 1
                if (endInclusive < resumeFrom) return@mapNotNull null
                Segment(
                    start = start,
                    endInclusive = endInclusive,
                    downloadedFrom = maxOf(start, resumeFrom),
                )
            }
    }

    /**
     * 下载单个分片。
     *
     * 分片已有的字节数就是 part 文件的长度，据此续传；[downloadedBytes] 是各分片共享的累计值，
     * 用来驱动进度与速度。失败以返回值表达而不抛异常：异常会取消兄弟协程，
     * 多个分片同时失败时真正的原因会被协程取消吞掉；代价是某个分片失败后其余分片仍会跑完。
     */
    private suspend fun downloadSegment(
        url: String,
        temporaryFile: File,
        segment: Segment,
        downloadedBytes: AtomicLong,
    ): SegmentOutcome {
        val partFile = segmentFile(temporaryFile, segment)
        val alreadyDownloaded = partFile.length().coerceAtMost(segment.length)
        if (alreadyDownloaded == segment.length) return SegmentOutcome.Completed

        val rangeStart = segment.downloadedFrom + alreadyDownloaded
        val request =
            Request.Builder()
                .url(url)
                .header(HEADER_RANGE, "bytes=$rangeStart-${segment.endInclusive}")
                .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                // 分片区间拿不到 206，说明这个地址其实不支持按区间下载，交给上层退回单连接
                if (response.code != HTTP_PARTIAL_CONTENT) {
                    return SegmentOutcome.NotSupported
                }
                val body = response.body ?: throw IOException("响应体为空")
                val buffer = ByteArray(BUFFER_SIZE)
                FileOutputStream(partFile, true).use { output ->
                    body.byteStream().use { input ->
                        while (true) {
                            // 阻塞读不会随协程取消而中断，只能主动检查
                            if (isStopped || !coroutineContext.isActive) {
                                return SegmentOutcome.Cancelled
                            }
                            val readCount = input.read(buffer)
                            if (readCount == -1) break
                            output.write(buffer, 0, readCount)
                            downloadedBytes.addAndGet(readCount.toLong())
                        }
                        output.flush()
                    }
                }
            }
            SegmentOutcome.Completed
        } catch (e: IOException) {
            SegmentOutcome.Failed(e)
        }
    }

    /**
     * 把各 part 文件按区间顺序追加到主临时文件末尾，读完即删，磁盘占用不会翻倍。
     *
     * 返回 false 表示合并过程中任务被取消。已合并的字节始终是文件的连续前缀，
     * 下次重试会从这个长度继续，不会把文件写坏。
     *
     * 合并被中断时，进行到一半的那段会因下载起点变化而不再匹配原有 part 文件，
     * 下次重试会重下该段尾部：宁可多下一段，也不冒拼接出错的风险。
     */
    private fun mergeSegments(temporaryFile: File, segments: List<Segment>): Boolean {
        FileOutputStream(temporaryFile, true).use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            for (segment in segments.sortedBy { it.start }) {
                val partFile = segmentFile(temporaryFile, segment)
                partFile.inputStream().use { input ->
                    while (true) {
                        if (isStopped) return false
                        val readCount = input.read(buffer)
                        if (readCount == -1) break
                        output.write(buffer, 0, readCount)
                    }
                }
                partFile.delete()
            }
            output.flush()
        }
        // 兜底清掉历史遗留（例如上次合并失败留下的、边界已失效的 part 文件）
        deleteSegmentFiles(temporaryFile.absolutePath)
        return true
    }

    /** 各 part 文件已下载的字节总数，续传时作为进度起点。 */
    private fun resolveSegmentProgress(temporaryFile: File, segments: List<Segment>): Long =
        segments.sumOf { segment ->
            segmentFile(temporaryFile, segment).length().coerceAtMost(segment.length)
        }

    /**
     * 分片下载时的进度上报循环。
     *
     * 这里只负责按固定间隔取一次累计字节数（任务名取自 [request]），
     * 实际上报频率与速度计算由 [reportProgress] 决定。
     */
    private suspend fun pollProgress(
        downloadedBytes: AtomicLong,
        totalBytes: Long,
        request: DownloadRequest,
    ) {
        while (true) {
            delay(PROGRESS_POLL_INTERVAL_MS)
            reportProgress(downloadedBytes.get(), totalBytes, request.displayName)
        }
    }

    /** 分片文件路径：偏移量写进文件名，避免与上一次分片边界的残留数据混用。 */
    private fun segmentFile(temporaryFile: File, segment: Segment): File =
        File(temporaryFile.parentFile, segmentFileName(temporaryFile.name, segment.downloadedFrom))

    /** 清掉起点不在本次分片边界上的 part 文件。 */
    private fun deleteStaleSegmentFiles(temporaryFile: File, segments: List<Segment>) {
        val validFiles = segments.map { segmentFile(temporaryFile, it) }
        listSegmentFiles(temporaryFile)
            .filterNot { it in validFiles }
            .forEach(File::delete)
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
        request: DownloadRequest,
        temporaryFile: File,
    ): Result {
        val finalFile = resolveFinalFile(temporaryFile, request.outputFileName)
        if (!temporaryFile.renameTo(finalFile)) {
            AppLog.e("下载文件重命名失败：%s", temporaryFile.name)
            return Result.failure()
        }

        return when (request.recordType) {
            RECORD_TYPE_SOURCE -> {
                database.setSourcePath(request.recordId, finalFile.absolutePath)
                AppLog.i("下载完成：%s", finalFile.name)
                Result.success()
            }
            RECORD_TYPE_STREAM -> {
                val streamId = runCatching { UUID.fromString(request.recordId) }.getOrNull()
                if (streamId == null) {
                    AppLog.e("媒体流 id 非法：%s", request.recordId)
                    return Result.failure()
                }
                database.setMediaStreamPath(streamId, finalFile.absolutePath)
                Result.success()
            }
            else -> {
                AppLog.e("未知的下载记录类型：%s", request.recordType)
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
        request: DownloadRequest,
    ): Result {
        val totalFromRange =
            response.header(HEADER_CONTENT_RANGE)?.substringAfter('/')?.toLongOrNull()
        if (totalFromRange != null && resumeFrom >= totalFromRange) {
            AppLog.i("下载 %s 的临时文件已完整，跳过续传直接收尾", request.displayName)
            return finishDownload(request, temporaryFile)
        }

        AppLog.w("下载 %s 的续传请求被拒绝（HTTP 416），将重新下载", request.displayName)
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

    /** 服务端对分片请求没有返回 206：该地址实际上不支持按区间下载，需要退回单连接。 */
    private class SegmentNotSupportedException(message: String) : IOException(message)

    /** 单个分片的下载结果，失败用返回值表达，避免协程取消把真实原因吞掉。 */
    private sealed interface SegmentOutcome {
        /** 该段数据已经齐了。 */
        data object Completed : SegmentOutcome

        /** 任务被取消（用户取消下载或 Worker 被停止）。 */
        data object Cancelled : SegmentOutcome

        /** 服务端没有按区间返回数据，该地址其实不支持分片。 */
        data object NotSupported : SegmentOutcome

        /** 传输中断等可重试的失败。 */
        data class Failed(val error: IOException) : SegmentOutcome
    }

    /** 单次下载任务的入参，避免在多个私有函数之间反复传递同一串 inputData 字段。 */
    private data class DownloadRequest(
        val recordId: String,
        val recordType: String,
        val url: String,
        val filePath: String,
        val displayName: String,
        val outputFileName: String?,
    )

    /**
     * 一段分片下载区间（闭区间）。
     *
     * [start] 是该段在文件中的固定起点；[downloadedFrom] 是本次实际的下载起点——
     * 主临时文件已有的前缀落在该段内部时，前面那截已经在文件里，从这里接着下即可。
     */
    private data class Segment(val start: Long, val endInclusive: Long, val downloadedFrom: Long) {
        /** 该段本次需要下载的字节数。 */
        val length: Long
            get() = endInclusive - downloadedFrom + 1
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

        /** 分片中间文件后缀，后面接该段在文件中的起始偏移（如 `xxx.download.part10485760`）。 */
        const val SEGMENT_SUFFIX = ".part"

        /** 分片中间文件名：偏移量进文件名，续传时只有边界对得上的残留数据才会被复用。 */
        private fun segmentFileName(temporaryFileName: String, offset: Long): String =
            "$temporaryFileName$SEGMENT_SUFFIX$offset"

        /** 列出某个下载任务的全部 part 文件。 */
        private fun listSegmentFiles(temporaryFile: File): List<File> {
            val parent = temporaryFile.parentFile ?: return emptyList()
            val prefix = "${temporaryFile.name}$SEGMENT_SUFFIX"
            return parent.listFiles { file -> file.name.startsWith(prefix) }?.toList().orEmpty()
        }

        /**
         * 清掉某个下载任务留下的全部 part 文件。
         *
         * 取消下载时主临时文件由 `DownloaderImpl` 删除，分片文件由这里兜底清理，避免留下磁盘垃圾。
         */
        fun deleteSegmentFiles(temporaryFilePath: String) {
            listSegmentFiles(File(temporaryFilePath)).forEach(File::delete)
        }

        private const val NOTIFICATION_CHANNEL_ID = "downloads"
        private const val BUFFER_SIZE = 64 * 1024

        /** 进度上报频率：每次上报都会写一遍 WorkManager 记录，没必要太密。 */
        private const val PROGRESS_REPORT_INTERVAL_MS = 1_000L

        /** 分片下载时读取累计字节数的间隔；实际上报频率仍由 [PROGRESS_REPORT_INTERVAL_MS] 控制。 */
        private const val PROGRESS_POLL_INTERVAL_MS = 500L
        private const val MAX_RETRY_COUNT = 3
        private const val MAX_PROGRESS = 100
        private const val MILLIS_PER_SECOND = 1_000L

        /** 剩余字节不足该值就不分片：段太小的话，多出来的请求开销反而拖慢下载。 */
        private const val SEGMENT_MIN_REMAINING_BYTES = 32L * 1024L * 1024L

        /** 分片数量：再多会明显加重服务端与 Wi-Fi 的调度压力，收益也不再线性增长。 */
        private const val SEGMENT_COUNT = 4

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
