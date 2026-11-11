package dev.jdtech.jellyfin.work

import android.content.Context
import dev.jdtech.jellyfin.cache.CacheManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.io.File
import java.io.Closeable
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@HiltWorker
class ImagesDownloaderWorker
@AssistedInject
constructor(
    @Assisted private val appContext: Context,
    @Assisted private val params: WorkerParameters,
    private val repository: JellyfinRepository,
    private val cacheManager: CacheManager,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val itemId = inputData.getString(KEY_ITEM_ID)?.let(UUID::fromString)
            ?: return@withContext Result.failure()
        var lease: Closeable? = null
        try {
            repository.initialize()
            val item = repository.getItem(itemId) ?: return@withContext Result.failure()
            val baseDir = File(appContext.filesDir, "images/${item.id}")
            lease = cacheManager.protect(baseDir)
            check(baseDir.isDirectory || baseDir.mkdirs()) { "无法创建封面目录" }
            val client = OkHttpClient()
            val uris = mapOf("primary" to item.images.primary, "backdrop" to item.images.backdrop)
            var needsRetry = false
            for ((name, uri) in uris) {
                if (uri == null) continue
                val target = File(baseDir, name)
                if (target.isFile && target.length() > 0L) continue
                val temporary = File.createTempFile(name, ".part", baseDir)
                try {
                    val request = Request.Builder().url(uri.toString()).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            needsRetry = true
                            AppLog.w("下载封面图失败，HTTP %d", response.code)
                            return@use
                        }
                        response.body.byteStream().use { input ->
                            temporary.outputStream().use { output -> input.copyTo(output) }
                        }
                        currentCoroutineContext().ensureActive()
                        cacheManager.access {
                            check(temporary.length() > 0L && temporary.renameTo(target)) { "封面写入失败" }
                            cacheManager.touch(baseDir)
                            cacheManager.trim()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    needsRetry = true
                    AppLog.w("封面写入失败，错误类型：%s", e.javaClass.simpleName)
                } finally {
                    temporary.delete()
                }
            }
            if (needsRetry) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("封面预下载失败，错误类型：%s", e.javaClass.simpleName)
            Result.retry()
        } finally {
            lease?.close()
        }
    }

    companion object {
        const val KEY_ITEM_ID = "KEY_ITEM_ID"
    }
}
