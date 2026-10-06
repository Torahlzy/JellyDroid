package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 电影详情页「更多」菜单：删除封面 / 编辑 nfo / 删除全部。
 *
 * 三项都会真正改动服务器上的内容，因此各自还会再弹一次确认。
 */
@Composable
fun MoreMenuDialog(
    onDeleteImagesClick: () -> Unit,
    onEditMetadataClick: () -> Unit,
    onDeleteItemClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = { Text(text = stringResource(CoreR.string.more_menu_title)) },
        text = {
            Column {
                MenuItemOption(
                    text = stringResource(CoreR.string.delete_item_images),
                    onClick = onDeleteImagesClick,
                )
                MenuItemOption(
                    text = stringResource(CoreR.string.edit_item_metadata),
                    onClick = onEditMetadataClick,
                )
                // 会连视频文件一起删，用错误色区分
                MenuItemOption(
                    text = stringResource(CoreR.string.delete_item_with_files),
                    onClick = onDeleteItemClick,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(CoreR.string.cancel)) }
        },
    )
}

@Composable
private fun MenuItemOption(text: String, onClick: () -> Unit, color: Color = Color.Unspecified) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyLarge,
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = MaterialTheme.spacings.small),
    )
}

@Preview
@Composable
private fun MoreMenuDialogPreview() {
    FindroidTheme {
        MoreMenuDialog(
            onDeleteImagesClick = {},
            onEditMetadataClick = {},
            onDeleteItemClick = {},
            onDismiss = {},
        )
    }
}
