package dev.jdtech.jellyfin.settings.presentation.models

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.jdtech.jellyfin.settings.domain.models.Preference as PreferenceBackend

/** 自由文本输入型偏好，点击后弹输入框（目前用于「本地代理」这类地址）。 */
data class PreferenceTextInput(
    @param:StringRes override val nameStringResource: Int,
    @param:StringRes override val descriptionStringRes: Int? = null,
    @param:DrawableRes override val iconDrawableId: Int? = null,
    override val enabled: Boolean = true,
    override val dependencies: List<PreferenceBackend<Boolean>> = emptyList(),
    val backendPreference: PreferenceBackend<String>,
    val value: String = "",
    /** 是否在弹窗里显示连通性测试按钮；只有网络地址类偏好才需要。 */
    val supportsConnectionTest: Boolean = false,
) : Preference
