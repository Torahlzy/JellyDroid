package dev.jdtech.jellyfin.core.subtitle

import kotlinx.coroutines.flow.Flow

/**
 * 到字幕站按关键词找候选，并把每条候选处理成「可直接上传的一份字幕」。
 *
 * 语言优先级在**每条候选内部**生效（简体 > 繁体 > 英文）：用户看得到每条候选实际能拿到的语言，
 * 自己决定用哪条，因此这里不再跨候选统一降档。
 */
interface SubtitleDownloader {
    /**
     * 搜索 [keyword] 并按站点顺序逐条处理候选，依次发出：
     * 1. [SubtitleCandidateResult.Found]：候选标题列表（可能为空），界面据此立刻把列表铺出来；
     * 2. 每条候选处理完发一次 [SubtitleCandidateResult.Available] 或
     *    [SubtitleCandidateResult.Unavailable]，先处理完的先发。
     *
     * [videoDurationMs] 为视频时长（毫秒），小于等于 0 表示未知，此时不算时长差（结果为 null）。
     * 收集方取消即中断剩余候选的处理。
     */
    fun download(keyword: String, videoDurationMs: Long): Flow<SubtitleCandidateResult>
}
