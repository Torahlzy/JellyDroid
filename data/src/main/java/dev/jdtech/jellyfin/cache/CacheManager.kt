package dev.jdtech.jellyfin.cache

import java.io.Closeable
import java.io.File

/** 协调各缓存的读写、占用与淘汰；文件操作由调用方切到 IO 线程。 */
interface CacheManager {
    val quotaBytes: Long
    fun directory(category: CacheCategory): File
    fun usage(): CacheUsage
    fun setQuota(megabytes: Int): CacheUsage
    fun setImageQuota(megabytes: Int): CacheUsage
    fun clear(category: CacheCategory): CacheUsage
    fun trim()
    fun touch(file: File)
    fun protect(file: File): Closeable
    fun isProtected(file: File): Boolean
    fun <T> access(block: () -> T): T

    companion object {
        /** 在线图片缓存目录名；图片加载框架与占用统计共用，须保持一致。 */
        const val IMAGE_CACHE_DIR = "image_cache"
    }
}
