package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 重置 nfo 的确认弹窗。
 *
 * 只读展示：列出会被重置的字段，以及重置后的标题（回退为服务器文件名），不提供逐项勾选。
 */
@Composable
fun ResetMetadataDialog(
    serverFileName: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = { Text(text = stringResource(CoreR.string.reset_item_metadata)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = stringResource(CoreR.string.reset_metadata_message))
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                Text(
                    text = stringResource(CoreR.string.reset_metadata_fields_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(CoreR.string.reset_metadata_fields),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                Text(
                    text = stringResource(CoreR.string.reset_metadata_after_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                // 服务器文件名取不到时，重置会把标题清空（而非保持原样），必须显式告知用户
                Text(
                    text =
                        if (serverFileName == null) {
                            stringResource(CoreR.string.reset_metadata_new_title_empty)
                        } else {
                            stringResource(CoreR.string.reset_metadata_new_title, serverFileName)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(CoreR.string.reset_metadata_keep_provider_ids),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = stringResource(CoreR.string.reset)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(CoreR.string.cancel)) }
        },
    )
}

@Preview
@Composable
private fun ResetMetadataDialogPreview() {
    FindroidTheme {
        ResetMetadataDialog(
            serverFileName = "The.Movie.2024.1080p.mkv",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
