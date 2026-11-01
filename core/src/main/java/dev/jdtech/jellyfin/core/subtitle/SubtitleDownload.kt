package dev.jdtech.jellyfin.core.subtitle

/**
 * 从某个候选页面解析出的、可下载的一种语言的字幕。
 *
 * [format] 取自直链扩展名（本仓库只接受 srt / vtt），上传给服务器时要一并带上；
 * [title] 是搜索结果里的字幕名，只用于日志与进度提示。
 */
data class SubtitleDownload(
    val title: String,
    val language: SubtitleLanguage,
    val format: String,
    val url: String,
)
