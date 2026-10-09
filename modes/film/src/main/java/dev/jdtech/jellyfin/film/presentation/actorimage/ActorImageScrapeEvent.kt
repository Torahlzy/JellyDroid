package dev.jdtech.jellyfin.film.presentation.actorimage

/** 「获取头像」弹窗的一次性事件，界面据此给出 Toast 提示并刷新列表。 */
sealed interface ActorImageScrapeEvent {
    /** 演员头像已上传到服务器，界面可刷新人物列表 / 演员行。 */
    data object Uploaded : ActorImageScrapeEvent

    /** 上传演员头像失败，[error] 用于向用户展示失败原因。 */
    data class UploadFailed(val error: Exception) : ActorImageScrapeEvent
}
