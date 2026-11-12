package dev.jdtech.jellyfin.presentation.film.components

import android.text.format.Formatter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovies
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsAction
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsState
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.Motion
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.DownloadExportItem

/** 复用系统 M3 弹窗选择完整视频，关闭弹窗不会中断后台复制。 */
@Composable
fun DownloadExportDialog(
    state: DownloadsState,
    onAction: (DownloadsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = { onAction(DownloadsAction.CloseExportDialog) },
        title = { Text(stringResource(CoreR.string.download_export_title)) },
        text = { DownloadExportContent(state, onAction) },
        confirmButton = {
            TextButton(
                enabled =
                    !state.isExportBusy &&
                        !state.isLoadingExportItems &&
                        state.selectedItems.isNotEmpty(),
                onClick = { onAction(DownloadsAction.ExportSelected) },
            ) {
                Text(stringResource(CoreR.string.download_export_save, state.selectedItems.size))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(DownloadsAction.CloseExportDialog) }) {
                Text(stringResource(CoreR.string.close))
            }
        },
    )
}

@Composable
private fun DownloadExportContent(state: DownloadsState, onAction: (DownloadsAction) -> Unit) {
    val progress = state.exportState
    LazyColumn(
        modifier =
            Modifier.heightIn(
                max = LocalConfiguration.current.screenHeightDp.dp * DIALOG_CONTENT_HEIGHT_FRACTION
            ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
    ) {
        item { Text(stringResource(CoreR.string.download_export_hint)) }
        if (state.isExportBusy) {
            item { DownloadExportProgress(state, onAction) }
        }
        if (progress.total > 0) {
            item {
                Text(
                    stringResource(
                        CoreR.string.download_export_summary,
                        progress.saved,
                        progress.total,
                        progress.failed,
                        progress.cancelled,
                    )
                )
            }
        }
        (state.exportError ?: progress.error)?.let {
            item { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
        }
        if (state.isLoadingExportItems) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        } else if (state.exportItems.isEmpty()) {
            item { Text(stringResource(CoreR.string.download_export_empty)) }
        } else {
            item { DownloadExportSelection(state, onAction) }
            items(state.exportItems, key = { it.sourceId }) { item ->
                DownloadExportRow(
                    item = item,
                    selected = item.sourceId in state.selectedSourceIds,
                    enabled = !state.isExportBusy,
                    onToggle = { onAction(DownloadsAction.ToggleExportItem(item.sourceId)) },
                )
            }
        }
    }
}

@Composable
private fun DownloadExportSelection(state: DownloadsState, onAction: (DownloadsAction) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text =
                stringResource(
                    CoreR.string.download_export_selection,
                    state.selectedItems.size,
                    Formatter.formatFileSize(context, state.selectedSize),
                ),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(
            enabled = !state.isExportBusy,
            onClick = { onAction(DownloadsAction.ToggleSelectAll) },
        ) {
            Text(
                stringResource(
                    if (state.selectedSourceIds.size == state.exportItems.size) {
                        CoreR.string.download_export_deselect_all
                    } else {
                        CoreR.string.download_export_select_all
                    }
                )
            )
        }
    }
}

@Composable
private fun DownloadExportProgress(state: DownloadsState, onAction: (DownloadsAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
        Text(
            stringResource(CoreR.string.download_export_saving),
            style = MaterialTheme.typography.titleSmall,
        )
        LinearProgressIndicator(
            progress = { state.exportState.progress },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(CoreR.string.download_export_background_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(
            enabled = !state.isStartingExport,
            onClick = { onAction(DownloadsAction.CancelExport) },
        ) {
            Text(stringResource(CoreR.string.download_export_cancel))
        }
    }
}

@Composable
private fun DownloadExportRow(
    item: DownloadExportItem,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESSED_SCALE else 1f,
        animationSpec = Motion.spatialSpec(),
        label = "export-press-scale",
    )
    val elevation by animateDpAsState(
        targetValue = if (selected || pressed) MaterialTheme.spacings.extraSmall else 0.dp,
        animationSpec = Motion.effectsSpec(),
        label = "export-selection-tone",
    )
    Surface(
        checked = selected,
        onCheckedChange = { onToggle() },
        enabled = enabled,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = elevation,
        modifier =
            modifier.fillMaxWidth().graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacings.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        ) {
            Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    Formatter.formatFileSize(LocalContext.current, item.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@PreviewScreenSizes
@Composable
private fun DownloadExportDialogPreview() {
    val items =
        dummyMovies.map { movie ->
            val source = movie.sources.first()
            DownloadExportItem(
                itemId = movie.id,
                sourceId = source.id,
                title = movie.name,
                fileName = source.remoteFilePath.substringAfterLast('/'),
                size = source.size,
            )
        }
    FindroidTheme {
        DownloadExportDialog(
            state = DownloadsState(exportItems = items),
            onAction = {},
        )
    }
}

private const val DIALOG_CONTENT_HEIGHT_FRACTION = 0.55f
private const val PRESSED_SCALE = 0.98f
