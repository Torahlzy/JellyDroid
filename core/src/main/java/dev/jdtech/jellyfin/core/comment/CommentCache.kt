package dev.jdtech.jellyfin.core.comment

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.logging.AppLog
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 短评的磁盘缓存：一部影片一个 JSON 文件，放在 `cacheDir/comments` 下。
 *
 * 之所以放 `cacheDir` 而不是 `filesDir`：这是可以随时重建的纯缓存，交给系统在空间不足时回收。
 * 每次写入后按「总大小上限」淘汰最旧的文件（按最后修改时间），保证缓存不会无限增长。
 */
@Singleton
class CommentCache
@Inject
constructor(@ApplicationContext private val context: Context) {

    private val directory: File
        get() = File(context.cacheDir, CACHE_DIR_NAME)

    /** 串行化读写与淘汰，避免并发写同一目录时统计到中间状态。 */
    private val mutex = Mutex()

    /**
     * 读缓存，未命中或已过期返回 null（过期的文件顺手删掉）。
     *
     * [validMillis] 为缓存有效期，小于等于 0 视为立即过期。
     */
    suspend fun read(keyword: String, validMillis: Long): List<MovieComment>? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val file = fileFor(keyword)
                if (!file.exists()) return@withLock null
                val cached =
                    runCatching { readFile(file) }.getOrElse { e ->
                        AppLog.w(e, "短评缓存损坏，丢弃后重新请求：%s", file.name)
                        null
                    }
                if (cached == null) {
                    file.delete()
                    return@withLock null
                }
                val age = System.currentTimeMillis() - cached.savedAt
                if (validMillis <= 0 || age > validMillis) {
                    AppLog.d("短评缓存已过期（%d ms），重新请求：%s", age, keyword)
                    file.delete()
                    return@withLock null
                }
                cached.comments
            }
        }

    /**
     * 清空全部短评缓存。
     *
     * 站点登录 / 退出登录后会改变站点给出的评论条数，旧缓存（可能只有 3 条）必须作废，
     * 否则要等缓存过期才会重新请求。
     */
    suspend fun clear() =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                directory.listFiles()?.forEach { file -> file.delete() }
            }
        }

    /** 写入缓存并按 [maxBytes] 淘汰最旧的文件。 */
    suspend fun write(keyword: String, comments: List<MovieComment>, maxBytes: Long) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val file = fileFor(keyword)
                runCatching {
                        file.parentFile?.mkdirs()
                        file.writeText(toJson(comments))
                    }
                    .onFailure { e -> AppLog.w(e, "写入短评缓存失败：%s", file.name) }
                evictIfNeeded(maxBytes)
            }
        }

    /** 超过上限时从最旧的文件开始删，至少要保留刚写入的那个（它是最新的）。 */
    private fun evictIfNeeded(maxBytes: Long) {
        if (maxBytes <= 0) return
        val files = directory.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        val oldestFirst = files.sortedBy { it.lastModified() }
        for (file in oldestFirst) {
            if (total <= maxBytes) break
            val length = file.length()
            if (file.delete()) {
                total -= length
                AppLog.d("短评缓存超出上限，删除最旧文件：%s", file.name)
            }
        }
    }

    private fun fileFor(keyword: String): File = File(directory, "${hash(keyword)}.json")

    /** 关键词可能含中文与斜杠，用摘要做文件名，避免非法字符与超长路径。 */
    private fun hash(keyword: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(keyword.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun readFile(file: File): CachedComments? {
        val json = JSONObject(file.readText())
        val array = json.optJSONArray(JSON_KEYS) ?: JSONArray()
        val comments =
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                MovieComment(
                    id = item.optString("id"),
                    userName = item.optString("userName"),
                    content = item.optString("content"),
                    date = item.optString("date").takeIf { it.isNotEmpty() },
                    likes = if (item.has("likes")) item.optInt("likes") else null,
                )
            }
        return CachedComments(savedAt = json.optLong("savedAt"), comments = comments)
    }

    private fun toJson(comments: List<MovieComment>): String {
        val array = JSONArray()
        comments.forEach { comment ->
            array.put(
                JSONObject().apply {
                    put("id", comment.id)
                    put("userName", comment.userName)
                    put("content", comment.content)
                    comment.date?.let { put("date", it) }
                    comment.likes?.let { put("likes", it) }
                }
            )
        }
        return JSONObject().apply {
                put("savedAt", System.currentTimeMillis())
                put(JSON_KEYS, array)
            }
            .toString()
    }

    private data class CachedComments(val savedAt: Long, val comments: List<MovieComment>)

    private companion object {
        const val CACHE_DIR_NAME = "comments"
        const val JSON_KEYS = "comments"
    }
}
