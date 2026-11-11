package dev.jdtech.jellyfin.cache

/** 可独立清理、共同占用磁盘配额的缓存分类。 */
enum class CacheCategory {
    IMAGES,
    COMMENTS,
    SUBTITLES,
    PLAYER,
    TRANSLATIONS,
    WEB,
}
