package dev.jdtech.jellyfin.core.subtitle

import dev.jdtech.jellyfin.logging.AppLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * 逐条处理搜索结果，每条产出一份「该候选能拿到的最好字幕」：
 *
 * 1. 先搜索，把标题列表一次性发出去（界面据此立刻铺出候选列表）；
 * 2. 再按站点顺序逐条处理：打开详情页 → 在**这条候选内部**按简体 > 繁体 > 英文取第一份可下载的
 *    srt / vtt → 下载 → 解析字幕最后时间点 → 与视频时长求差；
 * 3. 每条处理完立刻发出去，用户不必等后面的候选；单条失败（没有中英文字幕 / 详情页打不开 /
 *    下载失败）只把这条标为不可用。
 *
 * 不做跨候选的语言降档：语言由用户看着列表自己选，选错了重下一遍也比默默换成别的语言好。
 */
@Singleton
class SubtitleDownloaderImpl
@Inject
constructor(private val scraper: SubtitleScraper) : SubtitleDownloader {

    override fun download(keyword: String, videoDurationMs: Long): Flow<SubtitleCandidateResult> =
        channelFlow {
            val candidates = search(keyword)
            send(SubtitleCandidateResult.Found(candidates))
            if (candidates.isEmpty()) {
                AppLog.d("字幕站上没有搜到候选：%s", keyword)
                return@channelFlow
            }
            candidates.forEach { candidate ->
                send(process(candidate, videoDurationMs))
            }
        }

    /** 处理单条候选：解析可用语言 → 取优先级最高的一份 → 下载 → 求与视频时长的差。 */
    private suspend fun process(
        candidate: SubtitleCandidate,
        videoDurationMs: Long,
    ): SubtitleCandidateResult {
        // resolve 已按语言优先级（简 > 繁 > 英）返回该页可下载的语言，取第一份即可
        val subtitle = resolve(candidate).firstOrNull()
        if (subtitle == null) {
            AppLog.d("候选页没有可用的中英文字幕：%s", candidate.pageUrl)
            return SubtitleCandidateResult.Unavailable(candidate)
        }
        val rawBytes = fetch(subtitle) ?: return SubtitleCandidateResult.Unavailable(candidate)
        // 时间行未必标准，先统一成 SRT / VTT 再用于时长判断、落盘、上传与侧载
        val bytes = SubtitleTiming.normalizeTimingLines(rawBytes, subtitle.format)
        val diffMs = durationDiffMs(bytes, videoDurationMs)
        AppLog.d(
            "候选可用：%s（%s），与视频时长差 %s ms",
            candidate.title,
            subtitle.language,
            diffMs,
        )
        return SubtitleCandidateResult.Available(
            DownloadedSubtitle(
                candidate = candidate,
                subtitle = subtitle,
                bytes = bytes,
                durationDiffMs = diffMs,
                isRecommended =
                    diffMs != null && abs(diffMs) <= alignmentToleranceMs(videoDurationMs),
            )
        )
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

    /**
     * 字幕最后时间点与视频时长的差，**带符号**：负数表示字幕比视频短。
     *
     * 视频时长未知（服务端没给 runtime）时返回 null，界面显示「未知」。
     *
     * 上界取「视频时长 + [MAX_TOLERANCE_MS]」：字幕文件末尾常有工具加的签名 cue，时间码是假的
     * （实测 `23:59:58,000` 的 WhisperJAV 签名行），按它算会得到「字幕比视频长 20 小时」。
     * 超出上界的时间戳一律忽略，取上界内最晚的那个；全都超出（时间轴完全对不上）时返回 null。
     */
    private fun durationDiffMs(bytes: ByteArray, videoDurationMs: Long): Long? {
        if (videoDurationMs <= 0) return null
        // 时间行可能带全角冒号等非 ASCII 字符，必须按字幕实际编码解码后再找时间戳；
        // 三种编码都解不出时时间戳同样不可信，直接按「时长未知」处理
        val text = SubtitleTextDecoder.decode(bytes)?.text ?: return null
        val upperBoundMs = videoDurationMs + MAX_TOLERANCE_MS
        val endMs = SubtitleTiming.lastTimestampMillis(text, upperBoundMs) ?: return null
        val rawEndMs = SubtitleTiming.lastTimestampMillis(text)
        if (rawEndMs != null && rawEndMs > upperBoundMs) {
            AppLog.d(
                "字幕末尾时间点 %d ms 超出视频时长上界 %d ms，已忽略，按 %d ms 计",
                rawEndMs,
                upperBoundMs,
                endMs,
            )
        }
        return endMs - videoDurationMs
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
        /** 最多列出前 5 个搜索结果，再多也只是把时间耗在翻页上。 */
        const val MAX_CANDIDATES = 5

        /** 容差占视频时长的比例。 */
        const val TOLERANCE_RATIO_PERCENT = 2

        const val MIN_TOLERANCE_MS = 60_000L

        const val MAX_TOLERANCE_MS = 180_000L
    }
}
