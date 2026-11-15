package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsAction
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

/** 单个条目保存到公共下载目录前的确认弹窗；确认后复制在后台进行。 */
@Composable
fun DownloadExportConfirmDialog(
    item: FindroidItem,
    confirmEnabled: Boolean,
    onAction: (DownloadsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = { onAction(DownloadsAction.DismissExportConfirm) },
        title = { Text(stringResource(CoreR.string.download_export_title)) },
        text = { Text(stringResource(CoreR.string.download_export_confirm, item.name)) },
        confirmButton = {
            TextButton(
                enabled = confirmEnabled,
                onClick = { onAction(DownloadsAction.ConfirmExport) },
            ) {
                Text(stringResource(CoreR.string.download_export_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(DownloadsAction.DismissExportConfirm) }) {
                Text(stringResource(CoreR.string.close))
            }
        },
    )
}

@PreviewScreenSizes
@Composable
private fun DownloadExportConfirmDialogPreview() {
    FindroidTheme {
        DownloadExportConfirmDialog(
            item = dummyMovie,
            confirmEnabled = true,
            onAction = {},
        )
    }
}
