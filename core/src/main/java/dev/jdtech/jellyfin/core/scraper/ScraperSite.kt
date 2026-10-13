package dev.jdtech.jellyfin.core.scraper

import dev.jdtech.jellyfin.core.BuildConfig

/**
 * 支持抓取的站点（文字、封面、头像抓取共用）。
 *
 * 枚举顺序只是展示与默认选择的顺序：各站点会**同时**抓取、全部尝试，不会在某个站点成功后停下，
 * 有内容时默认选中声明在最前面的那个。站点地址不写死在代码里，有两个来源：
 * - 打包时的本地配置（`local.properties` 的 `scraper.<站点>.url`，见 `:core` 的 `build.gradle.kts`），
 *   **只有 debug 包**会带入真实值，release / staging 为空；
 * - 用户在「更多」菜单里按 [key] 运行时填写的自定义网址（经 [ScraperUrlProvider] 解析，优先于打包配置）。
 *
 * 两处都没配置的站点 [baseUrl] 为 null（由 [ScraperUrlProvider] 归一化），抓取时直接跳过。
 *
 * [key] 是站点标识（也是 `local.properties` 里 key 的末段与偏好存储的后缀），刻意不含完整网址。
 */
enum class ScraperSite(val displayName: String, val key: String) {
    JAV321("Jav321", "jav321"),
    JAVBUS("JavBus", "javbus"),
    JAVDB("JavDB", "javdb");

    /** 打包配置里的站点地址；release / staging 恒为 null（BuildConfig 只有空串占位）。 */
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
