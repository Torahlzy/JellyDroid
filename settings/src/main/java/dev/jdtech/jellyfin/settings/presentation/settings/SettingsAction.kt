package dev.jdtech.jellyfin.settings.presentation.settings

import dev.jdtech.jellyfin.settings.presentation.models.Preference

sealed interface SettingsAction {
    data object OnBackClick : SettingsAction

    data class OnUpdate(val preference: Preference) : SettingsAction

    /** 关闭站点账号弹窗。 */
    data object CloseJavDbAccount : SettingsAction

    /** 到内置网页里登录站点账号（唯一的登录方式）。 */
    data object OpenJavDbWebLogin : SettingsAction

    /** 退出站点登录。 */
    data object JavDbLogout : SettingsAction

    /** 打开「抓取网址」弹窗，读取各站点当前生效的网址。 */
    data object OpenScrapeUrls : SettingsAction

    /** 保存「抓取网址」弹窗里填写的地址（scheme 补全已在弹窗里做过）。 */
    data class SaveScrapeUrls(val urls: Map<String, String>) : SettingsAction

    /** 关闭「抓取网址」弹窗。 */
    data object CloseScrapeUrls : SettingsAction
}
