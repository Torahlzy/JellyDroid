package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 电影详情页「更多」菜单：编辑封面 / 编辑 nfo / 编辑演员头像。
 *
 * 「删除全部」会连视频文件一起删且不可撤销，属于低频的破坏性操作，因此不放进菜单列表，
 * 而是用错误色放在标题行右上角，既与列表项区分、也能提醒用户这里有风险操作；
 * 点击后仍会再弹一次确认。右上角布局与「编辑封面」的「清理」、「编辑 nfo」的「清空」保持一致。
 */
@Composable
fun MoreMenuDialog(
    onEditImagesClick: () -> Unit,
    onEditMetadataClick: () -> Unit,
    onEditActorImagesClick: () -> Unit,
    onDeleteItemClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(CoreR.string.more_menu_title),
                    // 标题占满剩余宽度，把「删除全部」推到最右侧
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onDeleteItemClick,
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                ) {
                    Text(text = stringResource(CoreR.string.delete_all))
                }
            }
        },
        text = {
            Column {
                MenuItemOption(
                    text = stringResource(CoreR.string.edit_item_images),
                    onClick = onEditImagesClick,
                )
                MenuItemOption(
                    text = stringResource(CoreR.string.edit_item_metadata),
                    onClick = onEditMetadataClick,
                )
                MenuItemOption(
                    text = stringResource(CoreR.string.edit_actor_images),
                    onClick = onEditActorImagesClick,
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
private fun MenuItemOption(text: String, onClick: () -> Unit) {
    Text(
        text = text,
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
            onEditImagesClick = {},
            onEditMetadataClick = {},
            onEditActorImagesClick = {},
            onDeleteItemClick = {},
            onDismiss = {},
        )
    }
}
