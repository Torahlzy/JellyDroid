package dev.jdtech.jellyfin.settings.domain.models

import dev.jdtech.jellyfin.settings.domain.Constants

/**
 * nfo 翻译设置（OpenAI 兼容的对话接口）。
 *
 * 抓取到的标题 / 简介是日文，用大模型翻成中文后再填进「编辑 nfo」弹窗。
 * 各项都保存在本机，对应 [dev.jdtech.jellyfin.settings.domain.AppPreferences] 里的同名偏好项。
 */
data class TranslateSettings(
    val url: String = Constants.TRANSLATE_DEFAULT_URL,
    val apiKey: String = "",
    val model: String = Constants.TRANSLATE_DEFAULT_MODEL,
    /** 是否翻译标题。 */
    val translateTitle: Boolean = true,
    /** 是否翻译简介。 */
    val translatePlot: Boolean = true,
    /** 抓取完成后是否自动翻译；关掉后抓取结果保持原文。 */
    val autoTranslate: Boolean = true,
) {
    /** 地址、密钥、模型都填了才可能翻译成功，缺任何一项都直接跳过翻译。 */
    val isConfigured: Boolean
        get() = url.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}
