package dev.jdtech.jellyfin.core.subtitle

import java.util.UUID

/**
 * 「下载字幕」的一次性结果，由 [SubtitleDownloadController] 发出。
 *
 * 持有方（详情页 ViewModel / 播放页 Activity）各自收集自己那份 controller 的事件，
 * 不要一边读 controller 的 state、一边另起一套事件通道。
 */
sealed interface SubtitleDownloadEvent {
    /**
     * 字幕已上传到服务器。
     *
     * [subtitle] 一并带出去：播放页要在当前播放器上立即侧载这份字幕，否则「选择字幕轨」里
     * 要等服务器重新索引（时机不确定）才看得到，用户会以为没上传成功。
     * 详情页没有播放器，忽略它即可。
     */
    data class Uploaded(
        val target: SubtitleDownloadController.Target,
        val language: SubtitleLanguage,
        val subtitle: DownloadedSubtitle,
    ) : SubtitleDownloadEvent

    /** 搜索、下载或上传失败，[error] 用于向用户展示失败原因。 */
    data class Failed(val error: Exception) : SubtitleDownloadEvent

    /** 对照用的字幕快照已落盘到 [itemId] 对应的目录，可以进播放页开对照面板了。 */
    data class CompareReady(val itemId: UUID, val target: SubtitleDownloadController.Target) : SubtitleDownloadEvent
}
