package dev.jdtech.jellyfin.core.subtitle

/**
 * 字幕弹窗里那些「整批」进行中的动作，由 ViewModel 发出。
 *
 * 逐条候选的下载进度不在这里：它属于候选列表里某一行的状态（见 `SubtitleCandidateState`），
 * 同一时刻可能有好几条在跑。
 */
sealed interface SubtitleDownloadStage {
    /** 正在站点上搜索候选。 */
    data object Searching : SubtitleDownloadStage

    /** 正在把用户选中的 [candidate] 上传到服务器；上传期间其余候选的按钮一并禁用。 */
    data class Uploading(val candidate: SubtitleCandidate) : SubtitleDownloadStage
}
