package dev.jdtech.jellyfin.presentation.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceTextInput

/** 文本型偏好：显示当前值（空值显示「未设置」），点击弹输入框。 */
@Composable
fun SettingsTextInputCard(
    preference: PreferenceTextInput,
    onUpdate: (value: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    SettingsValueCard(
        preference = preference,
        text = preference.value.ifBlank { stringResource(SettingsR.string.settings_value_not_set) },
        onClick = { showDialog = true },
        modifier = modifier,
    )

    if (showDialog) {
        SettingsTextInputDialog(
            preference = preference,
            onUpdate = { value ->
                showDialog = false
                onUpdate(value)
            },
            onDismissRequest = { showDialog = false },
        )
    }
}
