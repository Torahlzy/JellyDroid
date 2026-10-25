package dev.jdtech.jellyfin.film.presentation.movie

/** 电影详情页的一次性事件，用于向界面反馈那些无法从状态推断的结果。 */
sealed interface MovieEvent {
    /** 封面图片删除完成。 */
    data object ItemImagesDeleted : MovieEvent

    /** nfo 更新完成。 */
    data object MetadataUpdated : MovieEvent

    /** 提交的内容与服务器现值完全一致，跳过了写入。 */
    data object MetadataUnchanged : MovieEvent

    /** 抓取到的封面图片已上传到服务器。 */
    data object ItemImagesUploaded : MovieEvent

    /** 条目已从服务器删除，界面应返回上一页。 */
    data object ItemDeleted : MovieEvent

    /** 删除封面失败，[error] 用于向用户展示失败原因。 */
    data class ItemImagesDeleteFailed(val error: Exception) : MovieEvent

    /** 更新 nfo 失败，[error] 用于向用户展示失败原因。 */
    data class MetadataUpdateFailed(val error: Exception) : MovieEvent

    /** 上传抓取到的封面图片失败，[error] 用于向用户展示失败原因。 */
    data class ItemImagesUploadFailed(val error: Exception) : MovieEvent

    /** 删除服务器条目失败，[error] 用于向用户展示失败原因。 */
    data class ItemDeleteFailed(val error: Exception) : MovieEvent

    /**
     * 翻译抓取结果失败，[error] 用于向用户展示失败原因。
     *
     * 失败的那部分内容保留原文；分类 / 标签里已由词典或缓存命中的译文仍会填上。
     */
    data class MetadataTranslateFailed(val error: Exception) : MovieEvent
}
