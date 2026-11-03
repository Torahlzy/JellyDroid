package dev.jdtech.jellyfin.core.subtitle

/**
 * 「下载字幕」弹窗的全部状态，由 [SubtitleDownloadController] 持有。
 *
 * 详情页与播放页各有一份 controller 实例（见 [SubtitleDownloadController] 的说明），
 * 两处的弹窗都只读自己那份状态，互不干扰。
 */
data class SubtitleDownloadState(
    /**
     * 整批进行中的动作；null 表示没有整批动作在跑（搜索或上传）。
     *
     * 逐条候选的下载进度不在这里：它属于候选列表里某一行的状态（见 [SubtitleCandidateState]）。
     */
    val stage: SubtitleDownloadStage? = null,
    /** 候选列表（含逐条下载状态），按站点给出的顺序展示；为空表示还没搜到候选。 */
    val candidates: List<SubtitleCandidateState> = emptyList(),
    /** 上一次搜索一个候选都没搜到，关键词输入框下方给出提示。 */
    val searchFoundNothing: Boolean = false,
) {
    /** 已下到可用字幕的条数；为 0 时「播放视频时对比」不可点。 */
    val readyCount: Int
        get() = candidates.count { it is SubtitleCandidateState.Ready }
}
