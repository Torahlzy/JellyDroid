package dev.jdtech.jellyfin.core.subtitle

import dev.jdtech.jellyfin.logging.AppLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CancellationException

/**
 * 字幕挑选规则（**语言优先级高于时长匹配度**）：
 *
 * 1. 取搜索结果的前 [MAX_CANDIDATES] 个候选，逐个打开详情页，把每页能下的语言（简体 / 繁体 / 英文）
 *    都解析出来存起来——同一页可能同时有简体和繁体；
 * 2. 先定语言：只有在简体候选一份都没有时，才退到繁体，再没有才用英文。不会出现
 *   「繁体时长更贴合所以改用繁体」——那是用户看不懂的字幕；
 * 3. 在选定语言的候选里逐份下载、比较「字幕最后时间点」与视频时长：一旦落进容差就采用并停止，
 *   都不在容差内才取误差最小的那一份（总比不给字幕好）。
 *
 * 单份候选解析 / 下载失败只跳过这一份，不影响其它候选；整组候选都没解析出可用语言时返回 null。
 */
@Singleton
class SubtitleDownloaderImpl
@Inject
constructor(
    private val scraper: SubtitleScraper,
    private val cache: SubtitleCache,
) : SubtitleDownloader {

    override suspend fun download(
        keyword: String,
        videoDurationMs: Long,
        onStage: suspend (SubtitleDownloadStage) -> Unit,
    ): DownloadedSubtitle? {
        cache.deleteExpired()
        onStage(SubtitleDownloadStage.Searching)
        val candidates = search(keyword)
        if (candidates.isEmpty()) {
            AppLog.d("字幕站上没有搜到候选：%s", keyword)
            return null
        }

        val downloads = resolveAll(candidates, onStage)
        if (downloads.isEmpty()) {
            AppLog.d("候选里没有可用的中英文字幕：%s", keyword)
            return null
        }
        // 语言优先级先定死：只有当前语言一份都下不下来（注意是「下不下来」，不是「没有」）
        // 才往下降一档，不会因为另一种语言时长更贴合就换语言
        for (language in SubtitleLanguage.entries) {
            val pool = downloads.filter { it.language == language }
            if (pool.isEmpty()) continue
            AppLog.d("选定语言 %s，共 %d 份候选可比时长", language, pool.size)
            val picked = pickByDuration(pool, videoDurationMs, onStage)
            if (picked != null) return picked
            AppLog.d("%s 的候选全部下载失败，退到下一种语言", language)
        }
        return null
    }

    /** 逐个打开候选页，把可用语言全部收集起来；单页失败只跳过这一页。 */
    private suspend fun resolveAll(
        candidates: List<SubtitleCandidate>,
        onStage: suspend (SubtitleDownloadStage) -> Unit,
    ): List<SubtitleDownload> {
        val downloads = mutableListOf<SubtitleDownload>()
        candidates.forEachIndexed { index, candidate ->
            onStage(SubtitleDownloadStage.Resolving(index = index + 1, total = candidates.size))
            downloads += resolve(candidate)
        }
        return downloads
    }

    /**
     * 在同一种语言的候选里挑与视频时长最贴合的一份。
     *
     * 差值落在容差内就采用并停止（按站点热度顺序，越靠前越热门）；都不在容差内时把该语言的候选
     * 全部下载完再取误差最小的——「差太多才继续看下一个」就是这个意思。
     * 视频时长未知（或字幕里没有时间戳）无法比较，直接用第一份。
     */
    private suspend fun pickByDuration(
        pool: List<SubtitleDownload>,
        videoDurationMs: Long,
        onStage: suspend (SubtitleDownloadStage) -> Unit,
    ): DownloadedSubtitle? {
        val toleranceMs = alignmentToleranceMs(videoDurationMs)
        var closest: DownloadedSubtitle? = null
        for ((index, subtitle) in pool.withIndex()) {
            onStage(
                SubtitleDownloadStage.Downloading(
                    index = index + 1,
                    total = pool.size,
                    language = subtitle.language,
                )
            )
            val bytes = fetch(subtitle) ?: continue
            cache.save(subtitle, bytes)
            val result = DownloadedSubtitle(subtitle, bytes, durationDiffMs(bytes, videoDurationMs))
            val diffMs = result.durationDiffMs
            if (diffMs == null) {
                AppLog.d("视频时长未知，直接采用第 %d 份候选（%s）", index + 1, subtitle.language)
                return result
            }
            if (diffMs <= toleranceMs) {
                AppLog.d(
                    "采用第 %d 份候选（%s）：与视频时长差 %d ms，在容差 %d ms 内",
                    index + 1,
                    subtitle.language,
                    diffMs,
                    toleranceMs,
                )
                return result
            }
            AppLog.d(
                "第 %d 份候选（%s）与视频时长差 %d ms，超出容差 %d ms，继续看下一份",
                index + 1,
                subtitle.language,
                diffMs,
                toleranceMs,
            )
            if (closest == null || (closest?.durationDiffMs ?: Long.MAX_VALUE) > diffMs) {
                closest = result
            }
        }
        if (closest != null) {
            AppLog.d(
                "都不在容差 %d ms 内，取误差最小的一份：差 %s ms",
                toleranceMs,
                closest?.durationDiffMs,
            )
        }
        return closest
    }

    private suspend fun search(keyword: String): List<SubtitleCandidate> =
        try {
            scraper.search(keyword, MAX_CANDIDATES)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(e, "搜索字幕失败：%s", keyword)
            emptyList()
        }

    private suspend fun resolve(candidate: SubtitleCandidate): List<SubtitleDownload> =
        try {
            scraper.resolve(candidate)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(e, "读取字幕候选页失败：%s", candidate.pageUrl)
            emptyList()
        }

    private suspend fun fetch(subtitle: SubtitleDownload): ByteArray? =
        try {
            scraper.download(subtitle)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(e, "下载字幕失败：%s", subtitle.url)
            null
        }

    /** 字幕最后时间点与视频时长的差；视频时长未知或字幕里没有时间戳时返回 null（视为不比较）。 */
    private fun durationDiffMs(bytes: ByteArray, videoDurationMs: Long): Long? {
        if (videoDurationMs <= 0) return null
        val endMs = SubtitleTiming.lastTimestampMillis(String(bytes, Charsets.UTF_8)) ?: return null
        return abs(endMs - videoDurationMs)
    }

    /**
     * 对齐容差：视频时长的 [TOLERANCE_RATIO_PERCENT]%，并限制在 [MIN_TOLERANCE_MS] ~ [MAX_TOLERANCE_MS]。
     *
     * 这只是启发式判据，**不是严格相等**：字幕最后一句台词不等于视频结束——片尾可能长时间没有台词
     * （黑场、片尾字幕滚动、纯音乐），实测 2 小时作品的字幕最后时间点比视频总长早 20~30 秒；
     * 所以容差太小会把正确字幕误判成「版本不符」。而换片长 / 删减版本的字幕往往差好几分钟，
     * 按时长 2%（下限 1 分钟、上限 3 分钟）能把这两种情况分开。
     */
    private fun alignmentToleranceMs(videoDurationMs: Long): Long =
        (videoDurationMs * TOLERANCE_RATIO_PERCENT / 100)
            .coerceIn(MIN_TOLERANCE_MS, MAX_TOLERANCE_MS)

    private companion object {
        /** 最多看前 5 个搜索结果，再多也只是把时间耗在翻页上。 */
        const val MAX_CANDIDATES = 5

        /** 容差占视频时长的比例。 */
        const val TOLERANCE_RATIO_PERCENT = 2

        const val MIN_TOLERANCE_MS = 60_000L

        const val MAX_TOLERANCE_MS = 180_000L
    }
}
