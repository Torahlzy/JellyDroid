package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

@Composable
fun DownloadDetailsDialog(item: FindroidItem, onDismiss: () -> Unit) {
    AlertDialog(
        title = { Text(text = stringResource(CoreR.string.download_details)) },
        text = {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(CoreR.string.download_file_path),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.sources.firstNotNullOfOrNull { source ->
                        source.path.ifBlank { null }
                    } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(CoreR.string.close))
            }
        },
    )
}

@Composable
@Preview
private fun DownloadDetailsDialogPreview() {
    FindroidTheme { DownloadDetailsDialog(item = dummyMovie, onDismiss = {}) }
}
