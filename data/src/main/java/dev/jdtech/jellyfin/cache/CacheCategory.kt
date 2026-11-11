package dev.jdtech.jellyfin.cache

/** 可独立清理、共同占用共享配额的缓存分类；在线图片缓存不在此列。 */
enum class CacheCategory {
    /** 下载配套的离线封面与预览图，不含图片框架自管的在线图片缓存。 */
    IMAGES,
    COMMENTS,
    SUBTITLES,
    PLAYER,
    TRANSLATIONS,
    WEB,
}
