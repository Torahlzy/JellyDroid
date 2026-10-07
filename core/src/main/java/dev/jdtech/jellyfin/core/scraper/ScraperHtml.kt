package dev.jdtech.jellyfin.core.scraper

import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements

// jsoup 小工具，替代 JavSP 里 lxml 的 xpath 便利写法。
// 三个站点都靠「标签 + 紧随其后的取值」组织字段，但取值可能落在文本节点上
// （形如 `<span>識別碼:</span> ABC-123`），也可能是兄弟元素，这里统一处理两种形态。

/** 取紧随当前元素之后的第一段非空文本，跳过空白与注释节点。 */
internal fun Element.nextSiblingText(): String? {
    var node = nextSibling()
    while (node != null) {
        val text =
            when (node) {
                is TextNode -> node.text()
                is Element -> node.text()
                else -> null
            }
        if (!text.isNullOrBlank()) return text.trim()
        node = node.nextSibling()
    }
    return null
}

/** 找到文本等于 [labels] 之一的标签（`<strong>` 或 `<span>`），返回紧随其后的取值。 */
internal fun Element.valueAfterLabel(vararg labels: String): String? =
    findLabel(*labels)?.nextSiblingText()?.replace(": ", "")?.trim()?.takeIf { it.isNotEmpty() }

/** 在 [labels] 中查找文本完全匹配标签名的元素，找不到返回 null。 */
internal fun Element.findLabel(vararg labels: String): Element? =
    select("strong, span").firstOrNull { element -> element.text().trim() in labels }

/** 取文本等于 [label] 的 `<b>` 标签紧随其后的取值（jav321 用 `<b>` 做字段名）。 */
internal fun Element.bTagValue(label: String): String? =
    select("b")
        .firstOrNull { it.text().trim() == label }
        ?.nextSiblingText()
        ?.replace(": ", "")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/** 提取一批元素的文本，去掉空项。 */
internal fun Elements.texts(): List<String> =
    mapNotNull { it.text().trim().takeIf { text -> text.isNotEmpty() } }

/**
 * 取 `<img>` 的图片地址。
 *
 * 顺序与 JavSP 一致（它三个站点都读 `src`，能正常工作），懒加载属性只作兜底；
 * 同时过滤掉 `data:` 内联图与解析不出绝对地址的情况。
 */
internal fun Element.imageUrl(): String? =
    sequenceOf("src", "data-src", "data-original")
        .map { key -> absUrl(key).trim() }
        .firstOrNull { url -> url.startsWith("http") }

/** 取链接的绝对地址：站点的原图常挂在 `a` 的 `href` 上，解析不出 http(s) 时返回 null。 */
internal fun Element.imageLink(): String? =
    absUrl("href").trim().takeIf { url -> url.startsWith("http") }
