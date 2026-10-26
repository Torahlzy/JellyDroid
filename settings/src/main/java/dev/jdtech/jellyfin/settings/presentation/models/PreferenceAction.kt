package dev.jdtech.jellyfin.settings.presentation.models

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.jdtech.jellyfin.settings.domain.models.Preference as PreferenceBackend
import dev.jdtech.jellyfin.settings.presentation.enums.DeviceType

/**
 * 「点一下做一件事」的偏好项（如站点登录）：右侧显示当前状态，点击触发 [onClick]。
 *
 * 与 [PreferenceSwitch] 的区别是不承载某个布尔值，状态文案由 ViewModel 每次加载时刷新。
 */
data class PreferenceAction(
    @param:StringRes override val nameStringResource: Int,
    @param:StringRes override val descriptionStringRes: Int? = null,
    @param:DrawableRes override val iconDrawableId: Int? = null,
    override val enabled: Boolean = true,
    override val dependencies: List<PreferenceBackend<Boolean>> = emptyList(),
    override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.PHONE, DeviceType.TV),
    /** 当前状态文案（如已登录的账号名），空串表示未登录。 */
    val valueText: String = "",
    /** 是否在状态文案旁显示 VIP 徽标（账号行用，登录时按站点返回的用户类型判定）。 */
    val vipBadge: Boolean = false,
    val onClick: (PreferenceAction) -> Unit = {},
) : Preference
