package dev.jdtech.jellyfin.core.subtitle

/**
 * 字幕候选列表里的一条，按三态渲染：下载中 / 可用（可上传 / 可对照）/ 不可用。
 *
 * 下载到的字节不放在这里——界面不需要，由 [SubtitleDownloadController] 按 [candidate] 存在内存里。
 */
sealed interface SubtitleCandidateState {
    /** 站点给出的候选，同时用来把流式结果对回列表里的某一行。 */
    val candidate: SubtitleCandidate

    /** 正在打开详情页 / 下载 / 解析时长，界面右侧显示转圈。 */
    data class Loading(override val candidate: SubtitleCandidate) : SubtitleCandidateState

    /** 已下到一份可用字幕，可直接上传。 */
    data class Ready(
        override val candidate: SubtitleCandidate,
        val language: SubtitleLanguage,
        /** 字幕最后时间点减视频时长：负数表示比视频短，null 表示无法比较（视频时长未知）。 */
        val durationDiffMs: Long?,
        /** 差值落在容差内，界面上标「推荐」。 */
        val isRecommended: Boolean,
    ) : SubtitleCandidateState

    /** 没有中英文字幕，或详情页打不开、字幕下载失败。 */
    data class Unavailable(override val candidate: SubtitleCandidate) : SubtitleCandidateState
}
