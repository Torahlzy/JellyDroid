package dev.jdtech.jellyfin.settings.presentation.settings

import dev.jdtech.jellyfin.settings.presentation.models.PreferenceGroup

data class SettingsState(
    val isLoading: Boolean = false,
    val preferenceGroups: List<PreferenceGroup> = emptyList(),
    /** 站点登录弹窗的状态，见 [JavDbLoginState]。 */
    val javdbLogin: JavDbLoginState = JavDbLoginState.Hidden,
    /** 上次登录用过的账号，用于回填登录弹窗（密码不保存）。 */
    val javdbAccount: String = "",
    /** 当前已登录的账号，空串表示未登录。 */
    val javdbLoggedInUser: String = "",
)

/**
 * 站点账号登录弹窗的状态。
 *
 * 验证码是站点图（GIF），放在状态里交给界面解码；提交成功后弹窗关闭，密码不落盘。
 */
sealed interface JavDbLoginState {
    /** 弹窗关闭。 */
    data object Hidden : JavDbLoginState

    /** 正在取登录页 / 验证码。 */
    data object Loading : JavDbLoginState

    /**
     * 等待用户输入。
     *
     * [captchaImage] 为验证码图片字节；[error] 为上一次提交失败的原因；[isSubmitting] 提交中。
     */
    data class Input(
        val captchaImage: ByteArray?,
        val error: String? = null,
        val isSubmitting: Boolean = false,
    ) : JavDbLoginState
}
