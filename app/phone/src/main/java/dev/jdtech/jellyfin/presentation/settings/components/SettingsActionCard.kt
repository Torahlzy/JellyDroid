package dev.jdtech.jellyfin.presentation.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceAction

/** 动作型偏好行：左侧名称与说明，右侧当前状态（如已登录账号），点击触发偏好自带的动作。 */
@Composable
fun SettingsActionCard(
    preference: PreferenceAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsBaseCard(preference = preference, onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.padding(MaterialTheme.spacings.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val iconRes = preference.iconDrawableId
                if (iconRes != null) {
                    Icon(painter = painterResource(iconRes), contentDescription = null)
                    Spacer(modifier = Modifier.width(MaterialTheme.spacings.default))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(preference.nameStringResource),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    preference.descriptionStringRes?.let {
                        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
                        Text(text = stringResource(it), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            // 值（如账号邮箱）单开一行：放在右侧同行会把标题列挤到一行只剩几个字
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
            if (preference.valueText.isBlank()) {
                Text(
                    text = stringResource(SettingsR.string.settings_javdb_not_logged_in),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                ) {
                    Text(
                        text = preference.valueText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (preference.vipBadge) {
                        Text(
                            text = stringResource(SettingsR.string.settings_javdb_vip_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier =
                                Modifier.background(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.shapes.small,
                                    )
                                    .padding(horizontal = MaterialTheme.spacings.small / 2),
                        )
                    }
                }
            }
        }
    }
}
