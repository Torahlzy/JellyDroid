package dev.jdtech.jellyfin.core.translator

/**
 * 批量翻译分类 / 标签的结果。
 *
 * [translations] 只包含确实拿到译文的值（词典、本地缓存与引擎的译文都算）；
 * [error] 非空表示这一段里交给引擎的值失败了，此时 [translations] 里仍然是已命中的部分，
 * 调用方据此保留部分译文，同时照旧计数与提示，不必整批退回原文。
 */
data class TagTranslationResult(
    val translations: Map<String, String>,
    val error: Exception? = null,
)
