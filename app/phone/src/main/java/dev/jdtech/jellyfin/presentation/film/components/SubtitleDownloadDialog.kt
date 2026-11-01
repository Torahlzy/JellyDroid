package dev.jdtech.jellyfin.presentation.film.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.subtitle.SubtitleDownloadStage
import dev.jdtech.jellyfin.core.subtitle.SubtitleLanguage
import dev.jdtech.jellyfin.presentation.components.BaseDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 下载字幕弹窗，结构与 [ScrapeKeywordDialog] 一致（关键词 + 代理入口 + 确定 / 取消）。
 *
 * 两个阶段共用一个弹窗：[stage] 为 null 时显示关键词输入，非空时换成进度提示，
 * 此时只能「取消」（中断下载），下载成功由上层直接关掉弹窗。
 * 失败后 [stage] 会回到 null，弹窗留在原地，用户可以改关键词重试。
 */
@Composable
fun SubtitleDownloadDialog(
    defaultKeyword: String,
    proxy: String,
    stage: SubtitleDownloadStage?,
    onProxyChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var keyword by remember { mutableStateOf(defaultKeyword) }
    var showProxyDialog by remember { mutableStateOf(false) }

    BaseDialog(
        title = stringResource(CoreR.string.download_subtitle),
        onDismiss = onDismiss,
        negativeButton = {},
        positiveButton = {
            if (stage == null) {
                TextButton(
                    onClick = { onConfirm(keyword.trim()) },
                    enabled = keyword.isNotBlank(),
                ) {
                    Text(text = stringResource(CoreR.string.confirm))
                }
            } else {
                TextButton(onClick = onCancel) {
                    Text(text = stringResource(CoreR.string.cancel))
                }
            }
        },
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
            if (stage == null) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(CoreR.string.scrape_keyword_label)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                TextButton(onClick = { showProxyDialog = true }) {
                    Text(
                        text =
                            stringResource(
                                CoreR.string.scrape_proxy_entry,
                                proxy.ifEmpty { stringResource(CoreR.string.scrape_proxy_unset) },
                            )
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(MaterialTheme.spacings.default),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(MaterialTheme.spacings.small))
                    Text(
                        text = stage.description(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    if (showProxyDialog) {
        ScrapeProxyDialog(
            proxy = proxy,
            onConfirm = { address ->
                showProxyDialog = false
                onProxyChange(address)
            },
            onDismiss = { showProxyDialog = false },
        )
    }
}

/** 阶段的提示文案；文案里用到的语言名走 [SubtitleLanguage.labelRes]。 */
@Composable
private fun SubtitleDownloadStage.description(): String =
    when (this) {
        SubtitleDownloadStage.Searching -> stringResource(CoreR.string.subtitle_searching)
        is SubtitleDownloadStage.Resolving ->
            stringResource(CoreR.string.subtitle_reading_candidate, index, total)
        is SubtitleDownloadStage.Downloading ->
            stringResource(
                CoreR.string.subtitle_trying_candidate,
                index,
                total,
                stringResource(language.labelRes()),
            )
        SubtitleDownloadStage.Uploading -> stringResource(CoreR.string.subtitle_uploading)
    }

/** 语言在界面上的名字；同时供详情页的完成提示复用。 */
@StringRes
internal fun SubtitleLanguage.labelRes(): Int =
    when (this) {
        SubtitleLanguage.SIMPLIFIED_CHINESE -> CoreR.string.subtitle_language_simplified
        SubtitleLanguage.TRADITIONAL_CHINESE -> CoreR.string.subtitle_language_traditional
        SubtitleLanguage.ENGLISH -> CoreR.string.subtitle_language_english
    }

@Preview
@Composable
private fun SubtitleDownloadDialogPreview() {
    FindroidTheme {
        SubtitleDownloadDialog(
            defaultKeyword = "DSOD-001",
            proxy = "",
            stage = SubtitleDownloadStage.Downloading(1, 5, SubtitleLanguage.SIMPLIFIED_CHINESE),
            onProxyChange = {},
            onConfirm = {},
            onCancel = {},
            onDismiss = {},
        )
    }
}
