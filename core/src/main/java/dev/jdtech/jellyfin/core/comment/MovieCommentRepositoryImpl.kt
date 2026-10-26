package dev.jdtech.jellyfin.core.comment

import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MovieCommentRepository] 的默认实现：先查缓存，再抓取，最后按设置里的上限写回缓存。
 *
 * 缓存命中时不产生任何网络请求，因此同一部影片在有效期内重复打开只有第一次会走代理。
 */
@Singleton
class MovieCommentRepositoryImpl
@Inject
constructor(
    private val source: MovieCommentSource,
    private val cache: CommentCache,
    private val appPreferences: AppPreferences,
) : MovieCommentRepository {

    override suspend fun getComments(keyword: String): List<MovieComment> {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("短评关键词为空")
        }

        val validMillis = appPreferences.getValue(appPreferences.commentCacheDays).daysToMillis()
        cache.read(trimmed, validMillis)?.let { cached ->
            AppLog.d("短评命中缓存：%s（%d 条）", trimmed, cached.size)
            return cached
        }

        val useProxy = appPreferences.getValue(appPreferences.commentsUseProxy)
        val comments = source.load(trimmed, useProxy)
        val maxBytes = appPreferences.getValue(appPreferences.commentCacheSize).megabytesToBytes()
        cache.write(trimmed, comments, maxBytes)
        return comments
    }

    private fun Int.daysToMillis(): Long = this.coerceAtLeast(0).toLong() * MILLIS_PER_DAY

    private fun Int.megabytesToBytes(): Long = this.coerceAtLeast(0).toLong() * BYTES_PER_MB

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
        const val BYTES_PER_MB = 1024L * 1024
    }
}
