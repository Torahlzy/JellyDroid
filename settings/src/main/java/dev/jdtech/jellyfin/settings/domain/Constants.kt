package dev.jdtech.jellyfin.settings.domain

object Constants {
    // Player - Media Segments
    object PlayerMediaSegmentsAutoSkip {
        const val ALWAYS = "always"
        const val PIP = "pip"
    }

    // Network
    const val NETWORK_DEFAULT_REQUEST_TIMEOUT = 30_000L
    const val NETWORK_DEFAULT_CONNECT_TIMEOUT = 6_000L
    const val NETWORK_DEFAULT_SOCKET_TIMEOUT = 10_000L

    // Translate - 默认填 DeepSeek 的 OpenAI 兼容接口（地址要写到 chat/completions）
    const val TRANSLATE_DEFAULT_URL = "https://api.deepseek.com/chat/completions"
    const val TRANSLATE_DEFAULT_MODEL = "deepseek-flash"
}
