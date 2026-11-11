package dev.jdtech.jellyfin.presentation.settings.cache

import coil3.disk.DiskCache
import dev.jdtech.jellyfin.cache.CacheCategory
import dev.jdtech.jellyfin.cache.CacheManager
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath

/** 图片条目交给共享配额管理；快照和写入期间持有租约，避免清理正在使用的文件。 */
class SharedImageDiskCache(private val cache: CacheManager) : DiskCache {
    private val root = cache.directory(CacheCategory.IMAGES)
    private var closed = false
    private val snapshots = mutableSetOf<ImageSnapshot>()
    private val editors = mutableSetOf<ImageEditor>()

    override val directory: Path get() = root.toOkioPath()
    override val fileSystem: FileSystem get() = FileSystem.SYSTEM
    override val maxSize: Long get() = cache.quotaBytes
    override val size: Long get() = cache.access { root.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

    override fun openSnapshot(key: String): DiskCache.Snapshot? = cache.access {
        check(!closed)
        snapshot(entry(key))
    }

    private fun snapshot(file: File): ImageSnapshot? {
        if (!File(file, "data").isFile || !File(file, "metadata").isFile) return null
        if (editors.any { it.target == file }) return null
        cache.touch(file)
        return ImageSnapshot(file, cache.protect(file)).also { snapshots.add(it) }
    }

    override fun openEditor(key: String): DiskCache.Editor? = cache.access {
        check(!closed)
        editor(entry(key))
    }

    private fun editor(target: File): ImageEditor? {
        if (closed) return null
        if (cache.isProtected(target) || editors.any { it.target == target }) return null
        check(root.isDirectory || root.mkdirs())
        val staging = File(root, "${target.name}.tmp")
        check(!staging.exists() || staging.deleteRecursively())
        check(staging.mkdirs())
        // 条件请求可能只改元数据，因此编辑前保留旧条目的两份文件。
        for (name in listOf("metadata", "data")) {
            val previous = File(target, name)
            if (previous.isFile) previous.copyTo(File(staging, name))
            else File(staging, name).createNewFile()
        }
        return ImageEditor(target, staging, cache.protect(staging), cache.protect(target))
            .also { editors.add(it) }
    }

    override fun remove(key: String): Boolean = cache.access {
        val file = entry(key)
        !cache.isProtected(file) && file.exists() && file.deleteRecursively()
    }

    override fun clear() { cache.clear(CacheCategory.IMAGES) }

    override fun shutdown() = cache.access {
        if (!closed) {
            closed = true
            snapshots.toList().forEach { it.close() }
            editors.toList().forEach { it.abort() }
        }
    }

    private fun entry(key: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(root, hash)
    }

    private inner class ImageSnapshot(
        val target: File,
        private val lease: Closeable,
    ) : DiskCache.Snapshot {
        override val metadata: Path get() = File(target, "metadata").toOkioPath()
        override val data: Path get() = File(target, "data").toOkioPath()
        override fun close() = cache.access {
            if (snapshots.remove(this)) lease.close()
        }
        override fun closeAndOpenEditor(): DiskCache.Editor? = cache.access {
            close()
            editor(target)
        }
    }

    private inner class ImageEditor(
        val target: File,
        private val staging: File,
        private val stagingLease: Closeable,
        private val targetLease: Closeable,
    ) : DiskCache.Editor {
        override val metadata: Path get() = File(staging, "metadata").toOkioPath()
        override val data: Path get() = File(staging, "data").toOkioPath()

        private fun publish() {
            check(editors.remove(this))
            try {
                check(!target.exists() || target.deleteRecursively())
                check(staging.renameTo(target))
                cache.touch(target)
            } finally {
                stagingLease.close()
                targetLease.close()
            }
        }

        override fun commit() = cache.access {
            publish()
            cache.trim()
        }

        override fun commitAndOpenSnapshot(): DiskCache.Snapshot? = cache.access {
            publish()
            val snapshot = snapshot(target)
            cache.trim()
            snapshot
        }

        override fun abort() = cache.access {
            if (editors.remove(this)) {
                staging.deleteRecursively()
                stagingLease.close()
                targetLease.close()
            }
        }
    }
}
