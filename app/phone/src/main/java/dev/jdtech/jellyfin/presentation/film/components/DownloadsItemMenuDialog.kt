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

/**
 * 下载页条目长按菜单：保存到下载文件夹 / 删除下载。
 *
 * 「删除下载」不可逆，参照详情页更多操作弹窗的做法：不放进菜单列表，
 * 用错误色放在标题行右上角提醒风险，点击后仍会再弹一次确认。
 */
@Composable
fun DownloadsItemMenuDialog(
    onExportClick: () -> Unit,
    onDeleteClick: () -> Unit,
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
                    // 标题占满剩余宽度，把「删除下载」推到最右侧
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onDeleteClick,
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                ) {
                    Text(text = stringResource(CoreR.string.delete_download))
                }
            }
        },
        text = {
            Column {
                MenuItemOption(
                    text = stringResource(CoreR.string.download_export_title),
                    onClick = onExportClick,
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
private fun DownloadsItemMenuDialogPreview() {
    FindroidTheme {
        DownloadsItemMenuDialog(
            onExportClick = {},
            onDeleteClick = {},
            onDismiss = {},
        )
    }
}
