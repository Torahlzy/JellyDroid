package dev.jdtech.jellyfin.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.presentation.components.BaseDialog
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.R as SettingsR

/**
 * 站点账号弹窗。
 *
 * 站点登录（图形验证码与 Cloudflare 人机验证）整体在过盾页的 WebView 里完成，App 不接触
 * 账号密码，因此这里只提供「在网页里登录」入口，已登录时再给一个退出登录。
 */
@Composable
fun JavDbAccountDialog(
    loggedInAccount: String,
    onOpenWebLogin: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseDialog(
        title = stringResource(SettingsR.string.settings_javdb_account),
        onDismiss = onDismiss,
        negativeButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(SettingsR.string.cancel))
            }
        },
        positiveButton = {
            TextButton(onClick = onOpenWebLogin) {
                Text(text = stringResource(SettingsR.string.settings_javdb_web_login))
            }
        },
    ) { contentPadding ->
        Column(
            modifier = modifier.padding(contentPadding).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        ) {
            Text(
                text = stringResource(SettingsR.string.settings_javdb_login_web_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (loggedInAccount.isNotBlank()) {
                TextButton(onClick = onLogout) {
                    Text(
                        text =
                            stringResource(
                                SettingsR.string.settings_javdb_logout,
                                loggedInAccount,
                            ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
