package dev.jdtech.jellyfin.core.subtitle

/**
 * 外部字幕站的搜索 / 解析 / 下载。
 *
 * 拆成三步而不是一个方法：搜索结果可能有好几条，每条要打开详情页才知道有哪些语言，
 * 由调用方（[SubtitleDownloader]）逐条解析出一条结果发给界面，再交用户决定用哪条。
 */
interface SubtitleScraper {
    /** 站点名称，用于拼失败原因与日志。 */
    val siteName: String

    /** 按 [keyword] 搜索，最多返回 [limit] 条候选，顺序与站点给出的一致。 */
    suspend fun search(keyword: String, limit: Int): List<SubtitleCandidate>

    /** 打开候选详情页，返回该页所有可下载的语言（同一页可能同时有简体与繁体）；没有可用语言时返回空表。 */
    suspend fun resolve(candidate: SubtitleCandidate): List<SubtitleDownload>

    /** 下载字幕原文件（不改编码、不重新打包）。 */
    suspend fun download(subtitle: SubtitleDownload): ByteArray
}
