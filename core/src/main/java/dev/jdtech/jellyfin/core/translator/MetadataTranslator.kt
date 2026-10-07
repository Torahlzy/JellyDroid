package dev.jdtech.jellyfin.core.translator

import dev.jdtech.jellyfin.settings.domain.models.TranslateSettings

/**
 * 把抓取到的日文元数据翻译成中文。
 *
 * 走哪个大模型、翻哪些字段都由本机的 [TranslateSettings] 决定；调用方（抓取流程）只负责
 * 决定「翻哪几段」与「翻失败怎么办」，翻译本身不写服务器、不碰界面。
 */
interface MetadataTranslator {
    /**
     * 一次请求翻译多段文本。
     *
     * [texts] 是「字段名 -> 原文」，返回「字段名 -> 译文」，**只包含确实拿到了译文的字段**：
     * 模型漏答某一段时该段不会出现在结果里，由调用方决定是保留原文还是别的兜底。
     *
     * 整次请求失败（没配置、网络出错、回复完全无法解析）时抛 [TranslateException]——
     * 翻译只是锦上添花，不该让整个抓取流程失败。
     */
    suspend fun translate(texts: Map<String, String>): Map<String, String>
}
