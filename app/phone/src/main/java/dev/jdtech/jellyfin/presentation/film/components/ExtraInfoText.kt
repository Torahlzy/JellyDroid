package dev.jdtech.jellyfin.presentation.film.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.VideoMetadata
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.formatWithTime
import java.time.LocalDateTime

/**
 * 媒体信息 4 行（大小 / 视频 / 音频 / 字幕）；字号与颜色跟文件路径行保持一致，同属次要信息。
 *
 * [addedAt] 为服务器文件入库时间，非空时紧跟在大小行下方；离线或服务器没给时为 null、不显示。
 */
@Composable
fun ExtraInfoText(videoMetadata: VideoMetadata, addedAt: LocalDateTime? = null) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
        Text(
            text =
                "${stringResource(CoreR.string.size)}: ${Formatter.formatFileSize(context, videoMetadata.size)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        addedAt?.let { dateCreated ->
            Text(
                text = stringResource(CoreR.string.file_added_at, dateCreated.formatWithTime()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (videoMetadata.videoTracks.isNotEmpty()) {
            Text(
                text =
                    "${stringResource(CoreR.string.video)}: ${videoMetadata.videoTracks.joinToString { it }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (videoMetadata.audioTracks.isNotEmpty()) {
            Text(
                text =
                    "${stringResource(CoreR.string.audio)}: ${videoMetadata.audioTracks.joinToString { it }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (videoMetadata.subtitleTracks.isNotEmpty()) {
            Text(
                text =
                    "${stringResource(CoreR.string.subtitle)}: ${videoMetadata.subtitleTracks.joinToString { it }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
