package dev.jdtech.jellyfin.settings.presentation.settings

import dev.jdtech.jellyfin.settings.presentation.models.Preference

sealed interface SettingsAction {
    data object OnBackClick : SettingsAction

    data class OnUpdate(val preference: Preference) : SettingsAction

    /** 打开站点登录弹窗并重新取一张验证码。 */
    data object OpenJavDbLogin : SettingsAction

    /** 换一张验证码（账号密码保留在弹窗里）。 */
    data object RefreshJavDbCaptcha : SettingsAction

    /** 提交账号、密码与验证码。 */
    data class SubmitJavDbLogin(
        val account: String,
        val password: String,
        val captcha: String,
    ) : SettingsAction

    /** 退出站点登录。 */
    data object JavDbLogout : SettingsAction

    /** 关闭登录弹窗。 */
    data object CloseJavDbLogin : SettingsAction

    /** 打开「抓取网址」弹窗，读取各站点当前生效的网址。 */
    data object OpenScrapeUrls : SettingsAction

    /** 保存「抓取网址」弹窗里填写的地址（scheme 补全已在弹窗里做过）。 */
    data class SaveScrapeUrls(val urls: Map<String, String>) : SettingsAction

    /** 关闭「抓取网址」弹窗。 */
    data object CloseScrapeUrls : SettingsAction
}
