package dev.jdtech.jellyfin.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.utils.DownloadExportFailure
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 将已完成的离线视频复制到 Download/JellyDroid，发布前对其他应用隐藏不完整文件。 */
@HiltWorker
class DownloadExportWorker
@AssistedInject
constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val database: ServerDatabaseDao,
) : CoroutineWorker(appContext, params) {
    private var lastProgressAt = 0L
    private val pendingFileName: String
        get() = "$id.pending"

    private val relativeDownloadPath: String
        get() = "${Environment.DIRECTORY_DOWNLOADS}/$DIRECTORY_NAME/"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
                return@withContext failed(DownloadExportFailure.STORAGE_UNAVAILABLE)
            }
            // 进程被终止时 finally 不会执行，重跑须先回收残留，避免被其占用的空间挡住。
            clearPreviousPendingFile()
            val file = findSourceFile() ?: return@withContext failed(DownloadExportFailure.FILE_MISSING)
            if (StatFs(Environment.getExternalStorageDirectory().path).availableBytes < file.length()) {
                return@withContext failed(DownloadExportFailure.NO_SPACE)
            }
            setForeground(getForegroundInfo())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                copyToMediaStore(file)
            } else {
                copyToLegacyDownloads(file)
            }
            Result.success(workDataOf(KEY_SAVED to true))
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            AppLog.e(e, "保存离线视频缺少存储授权")
            failed(DownloadExportFailure.PERMISSION_REQUIRED)
        } catch (e: Exception) {
            AppLog.e(e, "保存离线视频失败")
            failed(DownloadExportFailure.OTHER)
        }
    }

    private suspend fun findSourceFile(): File? {
        val itemId = inputData.getString(KEY_ITEM_ID)?.let(UUID::fromString) ?: return null
        val sourceId = inputData.getString(KEY_SOURCE_ID) ?: return null
        val source = database.getSources(itemId).firstOrNull { it.id == sourceId } ?: return null
        if (source.type != FindroidSourceType.LOCAL || source.downloadTaskId != null) return null
        return File(source.path).takeIf { it.isFile && it.length() > 0L }
    }

    private fun clearPreviousPendingFile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            clearPendingMediaStoreFiles()
        } else {
            requireLegacyStoragePermission()
            val pending = File(legacyDownloadDirectory(), ".$pendingFileName")
            check(!pending.exists() || pending.delete()) { "无法清理上次未完成的公共下载文件" }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Suppress("DEPRECATION")
    private fun clearPendingMediaStoreFiles() {
        val resolver = appContext.contentResolver
        // Android 10 尚不支持 QUERY_ARG_MATCH_PENDING，兼容接口显式包含未发布记录。
        val collection = MediaStore.setIncludePending(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        val selection =
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND " +
                "${MediaStore.Downloads.RELATIVE_PATH} = ? AND " +
                "${MediaStore.Downloads.OWNER_PACKAGE_NAME} = ? AND " +
                "${MediaStore.Downloads.IS_PENDING} = 1"
        val cursor =
            resolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                selection,
                arrayOf(pendingFileName, relativeDownloadPath, appContext.packageName),
                null,
            ) ?: throw IOException("无法查询上次未完成的公共下载文件")
        val pendingUris =
            cursor.use {
                buildList {
                    while (it.moveToNext()) add(ContentUris.withAppendedId(collection, it.getLong(0)))
                }
            }
        for (uri in pendingUris) {
            check(resolver.delete(uri, "${MediaStore.Downloads.IS_PENDING} = 1", null) == 1) {
                "无法清理上次未完成的公共下载文件"
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun copyToMediaStore(file: File) {
        val resolver = appContext.contentResolver
        val values =
            ContentValues().apply {
                // 任务 id 与待发布文件一起落盘，插入后立即退出进程也能在重跑时找回。
                put(MediaStore.Downloads.DISPLAY_NAME, pendingFileName)
                put(MediaStore.Downloads.MIME_TYPE, BINARY_MIME_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, relativeDownloadPath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        val uri =
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("无法创建公共下载文件")
        var published = false
        try {
            val output =
                resolver.openOutputStream(uri, "w") ?: throw IOException("无法打开公共下载文件")
            output.use { copyFile(file, it) }
            currentCoroutineContext().ensureActive()
            // 发布时恢复原文件名，MediaStore 自动处理与已有文件的同名冲突。
            val completed =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType(file))
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
            check(resolver.update(uri, completed, null, null) == 1) { "无法发布公共下载文件" }
            published = true
        } finally {
            if (!published) removePendingFile(uri)
        }
    }

    private suspend fun removePendingFile(uri: Uri) =
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                appContext.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                AppLog.e(e, "清理未完成的公共下载文件失败")
            }
        }

    private fun requireLegacyStoragePermission() {
        if (
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("保存公共下载文件需要存储授权")
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyDownloadDirectory(): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            DIRECTORY_NAME,
        )

    private suspend fun copyToLegacyDownloads(file: File) {
        val directory = legacyDownloadDirectory()
        check(directory.isDirectory || directory.mkdirs()) { "无法创建公共下载目录" }
        val pending = File(directory, ".$pendingFileName")
        var destination: File? = null
        try {
            pending.outputStream().use { copyFile(file, it) }
            currentCoroutineContext().ensureActive()
            destination = reserveUniqueFile(directory, file.name)
            check(pending.renameTo(destination)) { "无法发布公共下载文件" }
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(destination.absolutePath),
                arrayOf(mimeType(file)),
                null,
            )
        } catch (e: Exception) {
            destination?.delete()
            throw e
        } finally {
            pending.delete()
        }
    }

    private fun reserveUniqueFile(directory: File, name: String): File {
        var index = 0
        while (true) {
            val candidate =
                if (index == 0) name
                else {
                    val extension = name.substringAfterLast('.', "")
                    if (extension.isEmpty()) "$name ($index)"
                    else "${name.dropLast(extension.length + 1)} ($index).$extension"
                }
            val file = File(directory, candidate)
            if (file.createNewFile()) return file
            index++
        }
    }

    private suspend fun copyFile(file: File, output: OutputStream) {
        val size = file.length()
        var copied = 0L
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        file.inputStream().use { input ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                copied += count
                reportProgress((copied.toDouble() / size).toFloat().coerceIn(0f, 1f))
            }
        }
        check(copied == size && file.length() == size) { "复制期间离线文件发生变化" }
    }

    private suspend fun reportProgress(progress: Float) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastProgressAt < PROGRESS_INTERVAL_MS) return
        lastProgressAt = now
        setProgress(workDataOf(KEY_PROGRESS to progress))
        setForeground(createForegroundInfo(progress))
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(0f)

    private fun createForegroundInfo(progress: Float): ForegroundInfo {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    appContext.getString(CoreR.string.download_export_title),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        val notification =
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(CoreR.drawable.ic_download)
                .setContentTitle(appContext.getString(CoreR.string.download_export_saving))
                .setContentText(inputData.getString(KEY_FILE_NAME))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(MAX_PROGRESS, (progress * MAX_PROGRESS).toInt(), false)
                .addAction(
                    0,
                    appContext.getString(CoreR.string.cancel),
                    WorkManager.getInstance(appContext).createCancelPendingIntent(id),
                )
                .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id.hashCode(), notification)
        }
    }

    private fun mimeType(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: BINARY_MIME_TYPE

    // 单文件失败写入输出而不阻断依赖链，让其他已选视频仍能保存。
    private fun failed(failure: DownloadExportFailure): Result =
        Result.success(workDataOf(KEY_SAVED to false, KEY_ERROR to failure.name))

    companion object {
        const val KEY_ITEM_ID = "item_id"
        const val KEY_SOURCE_ID = "source_id"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_PROGRESS = "progress"
        const val KEY_SAVED = "saved"
        const val KEY_ERROR = "error"
        private const val DIRECTORY_NAME = "JellyDroid"
        private const val BINARY_MIME_TYPE = "application/octet-stream"
        private const val CHANNEL_ID = "download_export"
        private const val COPY_BUFFER_SIZE = 256 * 1024
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val MAX_PROGRESS = 100
    }
}
