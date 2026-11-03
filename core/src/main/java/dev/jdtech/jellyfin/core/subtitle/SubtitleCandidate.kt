package dev.jdtech.jellyfin.core.subtitle

/**
 * 字幕搜索结果里的一条，只知道名字与详情页地址。
 *
 * 具体有哪些语言要打开详情页才知道（见 [SubtitleScraper.resolve]），
 * 因此搜索与解析拆成两步：先按站点顺序把标题发给界面，再逐条打开详情页补全语言与时长差。
 */
data class SubtitleCandidate(val title: String, val pageUrl: String)
