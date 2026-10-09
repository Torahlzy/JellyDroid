package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.components.BaseDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 抓取前确认关键词的弹窗。
 *
 * [title] 由调用方给出，用于区分抓的是 nfo 还是封面；[defaultKeyword] 是自动识别出的番号，
 * 用户可以改成任意关键词（站点支持按标题搜）。
 * 代理设置也放在这里（复用 [ScrapeProxyDialog]）：nfo / 封面抓取是主要需要翻墙的联网操作。
 */
@Composable
fun ScrapeKeywordDialog(
    title: String,
    defaultKeyword: String,
    proxy: String,
    onProxyChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var keyword by remember { mutableStateOf(defaultKeyword) }
    var showProxyDialog by remember { mutableStateOf(false) }

    BaseDialog(
        title = title,
        onDismiss = onDismiss,
        negativeButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(CoreR.string.cancel)) }
        },
        positiveButton = {
            TextButton(
                onClick = { onConfirm(keyword.trim()) },
                enabled = keyword.isNotBlank(),
            ) {
                Text(text = stringResource(CoreR.string.confirm))
            }
        },
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
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

@Preview
@Composable
private fun ScrapeKeywordDialogPreview() {
    FindroidTheme {
        ScrapeKeywordDialog(
            title = "Scrape nfo",
            defaultKeyword = "ABC-123",
            proxy = "",
            onProxyChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}
