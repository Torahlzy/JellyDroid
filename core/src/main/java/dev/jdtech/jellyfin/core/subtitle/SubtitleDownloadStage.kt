package dev.jdtech.jellyfin.core.subtitle

/**
 * 字幕下载的当前阶段，供界面提示用户「卡在哪一步」。
 *
 * [Uploading] 由 ViewModel 发出（上传走 `JellyfinRepository`），其余由 [SubtitleDownloader] 发出。
 */
sealed interface SubtitleDownloadStage {
    /** 正在站点上搜索候选。 */
    data object Searching : SubtitleDownloadStage

    /**
     * 正在读取第 [index] 个候选页（共 [total] 个），确认页面上有哪些语言的字幕可下。
     *
     * 这几个页面必须先全部读完才能定语言：只在「简体一份都没有」时才退到繁体，
     * 所以读到一半就下第一份，会漏掉后面候选里的简体。
     */
    data class Resolving(val index: Int, val total: Int) : SubtitleDownloadStage

    /**
     * 正在尝试选定语言里的第 [index] 份候选（共 [total] 份），语言为 [language]。
     */
    data class Downloading(
        val index: Int,
        val total: Int,
        val language: SubtitleLanguage,
    ) : SubtitleDownloadStage

    /** 已挑好字幕，正在上传到服务器。 */
    data object Uploading : SubtitleDownloadStage
}
