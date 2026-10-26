package dev.jdtech.jellyfin.presentation.settings.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.presentation.components.BaseDialog
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.settings.presentation.settings.JavDbLoginState

/**
 * 站点账号登录弹窗。
 *
 * 站点登录带图形验证码，因此弹窗先把验证码图拉下来给用户看，账号密码验证码一起提交；
 * 提交失败时上层会自动换一张验证码（一次性），这里只负责展示与输入。
 */
@Composable
fun JavDbLoginDialog(
    state: JavDbLoginState,
    loggedInAccount: String,
    initialAccount: String,
    onRefreshCaptcha: () -> Unit,
    onSubmit: (account: String, password: String, captcha: String) -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var account by remember { mutableStateOf(initialAccount) }
    var password by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf("") }

    val input = state as? JavDbLoginState.Input
    val isSubmitting = input?.isSubmitting == true

    // 验证码是一次性的：上层失败后会自动换图，旧答案已经作废，图一变就清掉输入框
    LaunchedEffect(input?.captchaImage) { captcha = "" }

    BaseDialog(
        title = stringResource(SettingsR.string.settings_javdb_login_title),
        onDismiss = onDismiss,
        negativeButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(SettingsR.string.cancel))
            }
        },
        positiveButton = {
            TextButton(
                onClick = { onSubmit(account, password, captcha) },
                enabled = !isSubmitting && captcha.isNotBlank() && account.isNotBlank(),
            ) {
                Text(text = stringResource(SettingsR.string.settings_javdb_login_submit))
            }
        },
    ) { contentPadding ->
        Column(
            modifier = modifier.padding(contentPadding).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        ) {
            Text(
                text = stringResource(SettingsR.string.settings_javdb_login_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(SettingsR.string.settings_javdb_login_account)) },
                singleLine = true,
                enabled = !isSubmitting,
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(text = stringResource(SettingsR.string.settings_javdb_login_password))
                },
                singleLine = true,
                enabled = !isSubmitting,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = captcha,
                    onValueChange = { value -> captcha = value.take(CAPTCHA_LENGTH) },
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(text = stringResource(SettingsR.string.settings_javdb_login_captcha))
                    },
                    singleLine = true,
                    enabled = !isSubmitting,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                )
                CaptchaImage(
                    bytes = input?.captchaImage,
                    onRefresh = onRefreshCaptcha,
                    enabled = !isSubmitting,
                )
            }

            input?.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

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

/** 验证码图（站点给 GIF，取首帧）；加载中显示转圈，拿不到时给一个「换一张」入口。 */
@Composable
private fun CaptchaImage(
    bytes: ByteArray?,
    onRefresh: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val image =
        remember(bytes) {
            bytes?.let { data ->
                BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
            }
        }

    Box(
        modifier = modifier.size(width = CAPTCHA_WIDTH, height = CAPTCHA_HEIGHT).clickable(
            enabled = enabled,
            onClick = onRefresh,
        ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            image != null -> Image(bitmap = image, contentDescription = null)
            bytes == null ->
                CircularProgressIndicator(modifier = Modifier.size(MaterialTheme.spacings.default))
            else ->
                Text(
                    text = stringResource(SettingsR.string.settings_javdb_captcha_refresh),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
        }
    }
}

/** 站点验证码固定 5 位字母。 */
private const val CAPTCHA_LENGTH = 5

private val CAPTCHA_WIDTH = 100.dp
private val CAPTCHA_HEIGHT = 40.dp
