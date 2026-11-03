package dev.jdtech.jellyfin.core.subtitle

/** 字幕候选的流式结果，语义见 [SubtitleDownloader.download]。 */
sealed interface SubtitleCandidateResult {
    /** 搜索完成，[candidates] 为站点给出的候选（可能为空）；此时还没有下载任何字幕。 */
    data class Found(val candidates: List<SubtitleCandidate>) : SubtitleCandidateResult

    /** 某条候选下到了可用字幕。 */
    data class Available(val subtitle: DownloadedSubtitle) : SubtitleCandidateResult

    /** 某条候选不可用：没有中英文字幕，或详情页打不开、字幕下载失败。 */
    data class Unavailable(val candidate: SubtitleCandidate) : SubtitleCandidateResult
}
