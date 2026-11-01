package dev.jdtech.jellyfin.core.subtitle

/**
 * 接受下载的字幕语言，**声明顺序即优先级**（简体 > 繁体 > 英文）。
 *
 * [subtitlecatKey] 是 subtitlecat 详情页里下载入口 id 的后缀（如 `download_zh-CN`）；
 * [jellyfinLanguage] 是上传给服务器时用的语言代码。必须用 3 字母的 ISO 639-2：
 * 实测服务端收到 `zh-CN` 这类 2 字母代码时不会报错，但媒体流里的语言会存成空值，
 * 播放器界面上既看不到语言、也没法按语言自动选中。繁简都归到 `chi`——接口没有别的字段
 * 能区分，服务端落盘文件名也只用这个代码。
 */
enum class SubtitleLanguage(val subtitlecatKey: String, val jellyfinLanguage: String) {
    SIMPLIFIED_CHINESE("zh-CN", "chi"),
    TRADITIONAL_CHINESE("zh-TW", "chi"),
    ENGLISH("en", "eng"),
}
