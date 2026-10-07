package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.components.BaseDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 抓取前确认关键词的弹窗。
 *
 * [defaultKeyword] 是自动识别出的番号，用户可以改成任意关键词（站点支持按标题搜）。
 * 代理设置也放在这里：抓取是唯一需要翻墙的联网操作，没必要让它出现在别处。
 */
@Composable
fun ScrapeKeywordDialog(
    defaultKeyword: String,
    proxy: String,
    onProxyChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var keyword by remember { mutableStateOf(defaultKeyword) }
    var showProxyDialog by remember { mutableStateOf(false) }

    BaseDialog(
        title = stringResource(CoreR.string.scrape_keyword_title),
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

/** 抓取代理的输入框：留空即直连。 */
@Composable
private fun ScrapeProxyDialog(
    proxy: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var address by remember { mutableStateOf(proxy) }

    BaseDialog(
        title = stringResource(CoreR.string.scrape_proxy),
        onDismiss = onDismiss,
        negativeButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(CoreR.string.cancel)) }
        },
        positiveButton = {
            TextButton(onClick = { onConfirm(address.trim()) }) {
                Text(text = stringResource(CoreR.string.confirm))
            }
        },
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
            Text(
                text = stringResource(CoreR.string.scrape_proxy_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(MaterialTheme.spacings.medium))
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(address.trim()) }),
                singleLine = true,
            )
        }
    }
}

@Preview
@Composable
private fun ScrapeKeywordDialogPreview() {
    FindroidTheme {
        ScrapeKeywordDialog(
            defaultKeyword = "ABC-123",
            proxy = "",
            onProxyChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}
