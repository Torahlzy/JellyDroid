package dev.jdtech.jellyfin.film.presentation.movie

import dev.jdtech.jellyfin.core.subtitle.SubtitleLanguage

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

    /** 字幕已下载并上传到服务器，[language] 为用户选中的那份的语言。 */
    data class SubtitleUploaded(val language: SubtitleLanguage) : MovieEvent

    /** 字幕搜索或上传失败，[error] 用于向用户展示失败原因。 */
    data class SubtitleDownloadFailed(val error: Exception) : MovieEvent

    /**
     * 字幕对照快照已落盘，界面应关掉下载字幕弹窗并带着对照标记进播放器。
     *
     * 关弹窗要走独立路径（不能触发 `onDismiss` 里的清理）：快照刚写到磁盘，
     * 再走一次「取消字幕下载」容易让人误以为候选被清空了。
     */
    data object SubtitleCompareReady : MovieEvent
}
