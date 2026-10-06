package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 「选择播放版本」弹窗。
 *
 * 服务器会把同一影片的多个文件识别为同一部电影的多个版本（mediaSources），
 * 播放前让用户明确选择要播哪一个，避免默认取其中一个却不是想要的那个。
 *
 * @param sources 可选来源，顺序与播放端一致，[onSelect] 回传的就是该列表的下标
 */
@Composable
fun PlaybackSourceDialog(
    sources: List<FindroidSource>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = { Text(text = stringResource(CoreR.string.select_playback_source)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                sources.forEachIndexed { index, source ->
                    PlaybackSourceItem(source = source, onClick = { onSelect(index) })
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(CoreR.string.cancel)) }
        },
    )
}

/** 弹窗中的单个来源：用图标区分本地文件与服务器文件，主行显示文件名、副行显示完整路径。 */
@Composable
private fun PlaybackSourceItem(source: FindroidSource, onClick: () -> Unit) {
    val isLocal = source.type == FindroidSourceType.LOCAL
    val path = if (isLocal) source.localFilePath else source.remoteFilePath

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = MaterialTheme.spacings.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter =
                painterResource(
                    if (isLocal) CoreR.drawable.ic_folder else CoreR.drawable.ic_cloud
                ),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(MaterialTheme.spacings.small))
        Column {
            Text(
                text = path.substringAfterLast('/').ifBlank { source.name },
                style = MaterialTheme.typography.bodyLarge,
            )
            // 拿不到路径时（如直链地址为空）只显示文件名，避免渲染一行空白
            if (path.isNotBlank()) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PlaybackSourceDialogPreview() {
    FindroidTheme {
        PlaybackSourceDialog(
            sources =
                listOf(
                    dummyMovie.sources.first(),
                    dummyMovie.sources.first().copy(
                        type = FindroidSourceType.LOCAL,
                        localFilePath =
                            "/storage/emulated/0/Android/data/dev.jdtech.jellyfin.debug/files/downloads/Alita.2019.1080p.mkv",
                    ),
                ),
            onSelect = {},
            onDismiss = {},
        )
    }
}
