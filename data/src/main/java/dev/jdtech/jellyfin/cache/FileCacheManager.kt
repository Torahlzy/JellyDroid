package dev.jdtech.jellyfin.cache

import android.content.Context
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 用同一个锁和最近访问顺序管理共享磁盘缓存；在线图片缓存由图片框架自管，下载目录始终不参与回收。 */
class FileCacheManager(
    private val context: Context,
    private val preferences: AppPreferences,
) : CacheManager {
    private val lock = ReentrantLock()
    private val leases = mutableMapOf<File, Int>()
    private val cleanupRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (request in cleanupRequests) {
                // 租约会密集释放，合并回收请求以减少目录扫描。
                delay(CLEANUP_DEBOUNCE_MS)
                try {
                    trim()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.w(e, "共享缓存后台回收失败")
                }
            }
        }
    }

    override val quotaBytes: Long
        get() = preferences.getValue(preferences.cacheQuotaMb).coerceAtLeast(1) * BYTES_PER_MB

    private val imageQuotaBytes: Long
        get() = preferences.getValue(preferences.imageCacheQuotaMb).coerceAtLeast(1) * BYTES_PER_MB

    override fun directory(category: CacheCategory): File = when (category) {
        // 离线封面与预览图由下载器写在 filesDir，不放在系统可随时回收的 cacheDir
        CacheCategory.IMAGES -> File(context.filesDir, "images")
        CacheCategory.COMMENTS -> File(context.cacheDir, "comments")
        CacheCategory.SUBTITLES -> File(context.cacheDir, "subtitle_compare")
        CacheCategory.PLAYER -> File(context.cacheDir, "mpv")
        CacheCategory.TRANSLATIONS -> File(context.cacheDir, "translations")
        CacheCategory.WEB -> File(context.cacheDir, "WebView")
    }

    override fun <T> access(block: () -> T): T = lock.withLock(block)

    override fun touch(file: File) = access {
        if (file.exists() && !file.setLastModified(System.currentTimeMillis())) {
            AppLog.w("无法更新缓存访问时间：%s", file.name)
        }
    }

    override fun protect(file: File): Closeable = access {
        val path = file.canonicalFile
        leases[path] = (leases[path] ?: 0) + 1
        var closed = false
        Closeable {
            access {
                if (!closed) {
                    closed = true
                    val count = (leases[path] ?: 1) - 1
                    if (count == 0) leases.remove(path) else leases[path] = count
                    cleanupRequests.trySend(Unit)
                }
            }
        }
    }

    override fun isProtected(file: File): Boolean = access {
        val path = file.canonicalFile.toPath()
        leases.keys.any { path.startsWith(it.toPath()) || it.toPath().startsWith(path) }
    }

    override fun usage(): CacheUsage = access {
        val entries = entries()
        CacheUsage(
            quotaBytes = quotaBytes,
            imageQuotaBytes = imageQuotaBytes,
            // 图片框架的缓存只统计占用，由它自己按上限淘汰
            imageCacheBytes = sizeOf(File(context.cacheDir, CacheManager.IMAGE_CACHE_DIR)),
            downloadedBytes = context.getExternalFilesDirs(null).filterNotNull()
                .sumOf { sizeOf(File(it, "downloads")) },
            categories = CacheCategory.entries.associateWith { category ->
                entries.filter { it.category == category }.sumOf { it.bytes }
            },
            protectedBytes = entries.filter { isProtected(it.file) }.sumOf { it.bytes },
        )
    }

    override fun setQuota(megabytes: Int): CacheUsage = access {
        require(megabytes > 0) { "缓存配额必须大于零" }
        preferences.setValue(preferences.cacheQuotaMb, megabytes)
        trim()
        usage()
    }

    /** 在线图片缓存不参与共享回收，这里只保存偏好；新上限由图片框架在下次启动时读取。 */
    override fun setImageQuota(megabytes: Int): CacheUsage = access {
        require(megabytes > 0) { "图片缓存配额必须大于零" }
        preferences.setValue(preferences.imageCacheQuotaMb, megabytes)
        usage()
    }

    override fun clear(category: CacheCategory): CacheUsage = access {
        val failed = entries().filter { it.category == category && !isProtected(it.file) }
            .filterNot { it.file.deleteRecursively() }
        if (failed.isNotEmpty()) throw IOException("部分缓存文件无法删除")
        usage()
    }

    override fun trim() = access {
        val entries = entries()
        var bytes = entries.sumOf { it.bytes }
        for (entry in entries.sortedBy { it.file.lastModified() }) {
            if (bytes <= quotaBytes) break
            if (isProtected(entry.file)) continue
            if (entry.file.deleteRecursively()) bytes -= entry.bytes
            else AppLog.w("无法淘汰缓存文件：%s", entry.file.name)
        }
    }

    /** 每个直接子项作为一个原子单元，避免只删字幕清单或图片的一半。 */
    private fun entries(): List<Entry> = CacheCategory.entries.flatMap { category ->
        roots(category).flatMap { root ->
            children(root).map { Entry(category, it, sizeOf(it)) }
        }
    }

    private fun roots(category: CacheCategory): List<File> = when (category) {
        // 在线图片缓存由图片框架自管，这里只统计下载配套的离线封面与预览图
        CacheCategory.IMAGES -> listOf(directory(category), File(context.filesDir, "trickplay"))
        CacheCategory.SUBTITLES -> listOf(directory(category), File(context.cacheDir, "subtitle_sideload"))
        CacheCategory.PLAYER -> listOf(directory(category), File(context.cacheDir, "fontconfig"))
        else -> listOf(directory(category))
    }

    private fun children(file: File): List<File> {
        if (!file.exists()) return emptyList()
        return file.listFiles()?.toList() ?: throw IOException("无法读取缓存目录：${file.name}")
    }

    private fun sizeOf(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return children(file).sumOf(::sizeOf)
    }

    private data class Entry(val category: CacheCategory, val file: File, val bytes: Long)

    private companion object {
        const val CLEANUP_DEBOUNCE_MS = 250L
        const val BYTES_PER_MB = 1024L * 1024
    }
}
