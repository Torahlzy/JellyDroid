package dev.jdtech.jellyfin.utils

/**
 * 下载任务的运行状态。
 *
 * 取代原先直接用系统 `DownloadManager.STATUS_*` 常量的做法，让下载状态与具体下载实现解耦。
 */
enum class DownloadStatus {
    /** 已入队但还没开始跑，含等待网络约束的情况。 */
    QUEUED,

    /** 正在下载。 */
    RUNNING,

    /** 中途停下，等待退避后重试。 */
    PAUSED,

    /** 已经下载完成。 */
    SUCCESSFUL,

    /** 下载失败且不再自动重试。 */
    FAILED,

    /** 查不到任务记录（未开始、已被取消或记录已清理）。 */
    UNKNOWN,
}
