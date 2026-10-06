package dev.jdtech.jellyfin.presentation.film.components

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.DownloadStatus
import kotlin.math.roundToInt

@Composable
fun DownloaderCard(state: DownloaderState, onCancelClick: () -> Unit, onRetryClick: () -> Unit) {
    val context = LocalContext.current
    val animatedProgress by
        animateFloatAsState(
            targetValue = state.progress ?: 0f,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        )

    val textColor =
        when (state.status) {
            DownloadStatus.PAUSED -> Color.Yellow
            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        }

    val statusText =
        when (state.status) {
            DownloadStatus.QUEUED -> stringResource(CoreR.string.download_pending)
            DownloadStatus.PAUSED -> stringResource(CoreR.string.download_paused)
            DownloadStatus.FAILED -> stringResource(CoreR.string.download_failed)
            else -> stringResource(CoreR.string.download_downloading)
        }

    val progressIndicatorColor =
        when (state.status) {
            DownloadStatus.PAUSED -> Color.Yellow
            DownloadStatus.SUCCESSFUL -> Color.Green
            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
            else -> ProgressIndicatorDefaults.linearColor
        }

    val progressTrackColor =
        when (state.status) {
            DownloadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
            else -> ProgressIndicatorDefaults.linearTrackColor
        }

    OutlinedCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacings.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = statusText,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        // 进度未知时用占位符，避免让用户误以为已经下载了 0%
                        text =
                            if (state.progress == null) {
                                stringResource(CoreR.string.download_progress_unknown)
                            } else {
                                animatedProgress.times(100).roundToInt().toString() + "%"
                            },
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                when {
                    // 排队中、或下载中但拿不到文件总大小时，只能显示不确定进度条；
                    // 暂停（等待重试）时必须静止，否则会误导用户以为下载还在推进
                    state.status == DownloadStatus.QUEUED ||
                        (state.status == DownloadStatus.RUNNING &&
                            state.progress == null) -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = progressIndicatorColor,
                            trackColor = progressTrackColor,
                        )
                    }
                }
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                // 速度只在真正传输中有值；未测出（未开始、暂停、已完成）时不占位置
                state.speedBytesPerSecond?.let { speed ->
                    Text(
                        text =
                            stringResource(
                                CoreR.string.download_speed,
                                Formatter.formatFileSize(context, speed),
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                }
                state.errorText?.let { errorText ->
                    Text(
                        text = errorText.asString(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                when (state.status) {
                    DownloadStatus.QUEUED,
                    DownloadStatus.RUNNING,
                    // 暂停是在等待重试或等待网络，此时同样要能取消
                    DownloadStatus.PAUSED -> {
                        FilledTonalIconButton(onClick = onCancelClick) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_x),
                                contentDescription = null,
                            )
                        }
                    }
                    DownloadStatus.FAILED -> {
                        FilledTonalIconButton(onClick = onRetryClick) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                                contentDescription = null,
                            )
                        }
                    }
                    // 已完成或状态未知时没有可执行的操作，不显示按钮
                    DownloadStatus.SUCCESSFUL,
                    DownloadStatus.UNKNOWN -> Unit
                }
            }
        }
    }
}

@Composable
@Preview
private fun DownloaderCardPendingPreview() {
    FindroidTheme {
        DownloaderCard(
            state = DownloaderState(status = DownloadStatus.QUEUED),
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}

@Composable
@Preview
private fun DownloaderCardDownloadingPreview() {
    FindroidTheme {
        DownloaderCard(
            state =
                DownloaderState(
                    status = DownloadStatus.RUNNING,
                    progress = 0.5f,
                    speedBytesPerSecond = 2_500_000L,
                ),
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}

@Composable
@Preview
private fun DownloaderCardFailedPreview() {
    FindroidTheme {
        DownloaderCard(
            state =
                DownloaderState(
                    status = DownloadStatus.FAILED,
                    progress = 0.5f,
                    errorText =
                        UiText.StringResource(CoreR.string.not_enough_storage, "2 GB", "1 GB"),
                ),
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}
