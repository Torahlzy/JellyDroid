package dev.jdtech.jellyfin.core.subtitle

/**
 * 字幕搜索结果里的一条，只知道名字与详情页地址。
 *
 * 具体有哪些语言要打开详情页才知道（见 [SubtitleScraper.resolve]），
 * 因此搜索与解析拆成两步：候选按站点顺序逐个尝试，命中即停，不必把 5 个页面全翻一遍。
 */
data class SubtitleCandidate(val title: String, val pageUrl: String)
