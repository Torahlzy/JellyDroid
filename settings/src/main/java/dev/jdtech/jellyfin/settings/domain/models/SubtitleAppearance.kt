package dev.jdtech.jellyfin.settings.domain.models

import org.json.JSONObject

/**
 * 字幕外观的全局设置（与屏幕尺寸无关）。字号倍率按屏幕高度另存（subtitleScales），不在这里。
 *
 * - [textOpacityPercent]：字体不透明度百分比（0–100），默认 100。
 *
 * 序列化为扁平 JSON 存进 [dev.jdtech.jellyfin.settings.domain.AppPreferences.subtitleAppearance]，
 * 空串 / 解析失败一律回落默认值。
 */
data class SubtitleAppearance(
    val textOpacityPercent: Int = DEFAULT_OPACITY_PERCENT,
) {

    /** 与默认值完全一致时无需落盘，按"未设置"处理。 */
    fun isDefault(): Boolean = textOpacityPercent == DEFAULT_OPACITY_PERCENT

    fun toJson(): String {
        val json = JSONObject()
        if (textOpacityPercent != DEFAULT_OPACITY_PERCENT) {
            json.put(KEY_TEXT_OPACITY, textOpacityPercent)
        }
        return json.toString()
    }

    companion object {
        const val DEFAULT_OPACITY_PERCENT = 100

        private const val KEY_TEXT_OPACITY = "textOpacity"

        fun fromJson(json: String?): SubtitleAppearance {
            if (json.isNullOrBlank()) {
                return SubtitleAppearance()
            }
            return runCatching {
                val obj = JSONObject(json)
                SubtitleAppearance(
                    textOpacityPercent =
                        if (obj.has(KEY_TEXT_OPACITY)) {
                            obj.getInt(KEY_TEXT_OPACITY).coerceIn(0, 100)
                        } else {
                            DEFAULT_OPACITY_PERCENT
                        },
                )
            }
                .getOrElse { SubtitleAppearance() }
        }
    }
}
