package dev.jdtech.jellyfin.core.scraper

import dev.jdtech.jellyfin.core.BuildConfig

/**
 * 支持抓取元数据的站点。
 *
 * 枚举顺序即抓取顺序：从前到后依次尝试，任一站点成功即停止。
 * 站点地址不在代码里写死，而由本地配置提供（`local.properties` 的 `scraper.<站点>.url`，
 * 见 `:core` 的 `build.gradle.kts`）；没配置的站点 [baseUrl] 为 null，抓取时直接跳过。
 */
enum class ScraperSite(val displayName: String) {
    JAV321("Jav321"),
    JAVBUS("JavBus"),
    JAVDB("JavDB");

    /** 站点地址，未在本地配置里填写时为 null。 */
    val baseUrl: String?
        get() =
            when (this) {
                JAVDB -> BuildConfig.SCRAPER_JAVDB_URL
                JAVBUS -> BuildConfig.SCRAPER_JAVBUS_URL
                JAV321 -> BuildConfig.SCRAPER_JAV321_URL
            }
                .trim()
                .trimEnd('/')
                .takeIf { it.isNotEmpty() }
}
