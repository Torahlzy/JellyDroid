package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
 * 「删除全部」的确认弹窗。
 *
 * 这一项会连服务器上的视频文件一起删，属于不可撤销的操作，所以单独再确认一次。
 */
@Composable
fun DeleteItemWithFilesDialog(
    itemName: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = { Text(text = stringResource(CoreR.string.delete_item_with_files)) },
        text = {
            Column {
                Text(text = stringResource(CoreR.string.delete_all_message))
                itemName?.let { name ->
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    Text(
                        text = stringResource(CoreR.string.delete_all_item_name, name),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
            ) {
                Text(text = stringResource(CoreR.string.remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(CoreR.string.cancel)) }
        },
    )
}

@Preview
@Composable
private fun DeleteItemWithFilesDialogPreview() {
    FindroidTheme {
        DeleteItemWithFilesDialog(itemName = "示例影片", onConfirm = {}, onDismiss = {})
    }
}
