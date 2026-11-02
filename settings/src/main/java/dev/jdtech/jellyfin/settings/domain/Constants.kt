package dev.jdtech.jellyfin.settings.domain

object Constants {
    // Player - 字幕轨记忆

    /** [AppPreferences.playerLastSubtitleTrack] 的特殊值：用户手动关闭了字幕。 */
    const val SUBTITLE_TRACK_OFF = "off"

    // Player - Media Segments
    object PlayerMediaSegmentsAutoSkip {
        const val ALWAYS = "always"
        const val PIP = "pip"
    }

    // Network
    const val NETWORK_DEFAULT_REQUEST_TIMEOUT = 30_000L
    const val NETWORK_DEFAULT_CONNECT_TIMEOUT = 6_000L
    const val NETWORK_DEFAULT_SOCKET_TIMEOUT = 10_000L

    // 外网评论缓存
    const val COMMENT_CACHE_DEFAULT_SIZE_MB = 800
    const val COMMENT_CACHE_DEFAULT_DAYS = 7

    // Translate - 默认填 DeepSeek 的 OpenAI 兼容接口（地址要写到 chat/completions）
    const val TRANSLATE_DEFAULT_URL = "https://api.deepseek.com/chat/completions"
    const val TRANSLATE_DEFAULT_MODEL = "deepseek-flash"
}
