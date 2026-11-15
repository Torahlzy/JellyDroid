package dev.jdtech.jellyfin.settings.presentation.settings

import dev.jdtech.jellyfin.settings.presentation.models.PreferenceGroup

data class SettingsState(
    val isLoading: Boolean = false,
    val preferenceGroups: List<PreferenceGroup> = emptyList(),
    /** 站点账号弹窗是否打开：登录在站点网页里完成，弹窗只提供入口与退出登录。 */
    val showJavDbAccountDialog: Boolean = false,
    /** 当前已登录的账号，空串表示未登录。 */
    val javdbLoggedInUser: String = "",
    /** 「抓取网址」弹窗是否打开。 */
    val showScrapeUrlsDialog: Boolean = false,
    /** 各站点实际生效的网址（含打包配置），按站点枚举顺序、字幕站附在末尾，供弹窗回填。 */
    val scrapeSiteUrls: Map<String, String> = emptyMap(),
)
