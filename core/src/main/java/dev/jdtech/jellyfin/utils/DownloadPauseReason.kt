package dev.jdtech.jellyfin.utils

/** [DownloadStatus.PAUSED] 的具体原因，用于给用户更准确的提示。 */
enum class DownloadPauseReason {
    /** 并非暂停，或原因未知。 */
    NONE,

    /** 当前网络不满足下载约束，例如仅允许 WLAN 时正处于移动数据。 */
    WAITING_FOR_NETWORK,

    /** 上一次请求失败，正在退避重试。 */
    WAITING_TO_RETRY,
}
