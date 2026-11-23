package dev.jdtech.jellyfin.repository

/**
 * 搜索查询的归一化变体：解决番号类标题里的分隔符导致搜不到的问题。
 * 标题「MIDA-242」中间有连字符，用户输入「mida242」或「mida 242」都不是它的子串，
 * 原样交给服务端 / 数据库的子串匹配会失配，这里生成等价变体去兜底。
 */

// 空格与番号标题常见分隔符（- _ .），连续出现视为一个分隔符
private val SEPARATORS = Regex("[\\s\\-_.]+")

// 字母与数字的交界处（如 mida|242），番号标题在这里通常有连字符
private val LETTER_DIGIT_BOUNDARY =
    Regex("(?<=[A-Za-z])(?=[0-9])|(?<=[0-9])(?=[A-Za-z])")

/**
 * 在线搜索的查询变体，按优先级排列：原样、去分隔符（mida 242 -> mida242）、
 * 字母数字边界补连字符（mida242 -> mida-242）。调用方依次尝试，取首个非空结果。
 */
internal fun buildSearchQueryVariants(query: String): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return listOf(trimmed)
    val compact = SEPARATORS.replace(trimmed, "")
    val variants = mutableListOf(trimmed)
    if (compact.isNotEmpty() && compact != trimmed) variants += compact
    val hyphenated = LETTER_DIGIT_BOUNDARY.replace(compact, "-")
    if (hyphenated.isNotEmpty() && hyphenated !in variants) variants += hyphenated
    return variants
}

/**
 * 离线 LIKE 的匹配串：先转义用户输入里的 LIKE 通配符（%、_）和转义符本身，
 * 再把分隔符与字母数字边界替换成 %，使「mida242」变成「mida%242」，
 * 一次查询即可同时命中 mida-242 / mida 242 / mida242。
 */
internal fun toOfflineLikePattern(query: String): String {
    val escaped = query
        .trim()
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return LETTER_DIGIT_BOUNDARY.replace(SEPARATORS.replace(escaped, "%"), "%")
}
