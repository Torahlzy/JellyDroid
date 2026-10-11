package dev.jdtech.jellyfin.core.scraper

import dev.jdtech.jellyfin.core.BuildConfig

/**
 * 支持抓取的站点（文字、封面、头像抓取共用）。
 *
 * 枚举顺序只是展示与默认选择的顺序：各站点会**同时**抓取、全部尝试，不会在某个站点成功后停下，
 * 有内容时默认选中声明在最前面的那个。站点地址不在代码里写死，而由本地配置提供
 * （`local.properties` 的 `scraper.<站点>.url`，见 `:core` 的 `build.gradle.kts`）；
 * 没配置的站点 [baseUrl] 为 null，抓取时直接跳过。
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
