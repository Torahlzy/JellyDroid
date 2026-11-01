package dev.jdtech.jellyfin.core.subtitle

/**
 * 按关键词找一份「配得上这部片子」的字幕。
 *
 * 两条规则，**语言优先于时长**：先按简体 > 繁体 > 英文定下语言（一个简体候选都没有才退到繁体），
 * 再在该语言的候选里比「字幕最后时间点」与视频时长挑最贴合的一份。
 * 时长不符只用来在同语言内排序，不会让实现改用另一种语言。
 */
interface SubtitleDownloader {
    /**
     * 搜索并下载 [keyword] 的字幕，返回挑中的那一份；站点上没有可用的中英文一字幕时返回 null。
     *
     * [videoDurationMs] 为视频时长（毫秒），小于等于 0 表示未知，此时不比较时长、直接用第一个候选。
     * [onStage] 在每一步开始时回调，供界面显示进度。
     */
    suspend fun download(
        keyword: String,
        videoDurationMs: Long,
        onStage: suspend (SubtitleDownloadStage) -> Unit,
    ): DownloadedSubtitle?
}
