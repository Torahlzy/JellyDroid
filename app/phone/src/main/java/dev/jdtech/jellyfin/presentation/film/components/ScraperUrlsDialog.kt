package dev.jdtech.jellyfin.presentation.film.components

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.scraper.ScraperSite
import dev.jdtech.jellyfin.presentation.components.BaseDialog
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 抓取站点网址的输入弹窗：每个站点一个输入框，站点 [ScraperSite.key]（javdb / javbus / jav321）
 * 作为输入框标签与偏好的存储 key。
 *
 * 防呆只提示不拦人：填了内容的网址如果 host 里不含该站点的 key（可能把别的站点的网址贴错位置）
 * 就在输入框下给一句「可能不匹配」，但不阻止保存。保存时统一补全 scheme（没写 http / https 的
 * 自动按 https 处理）并去掉首尾空白。
 */
@Composable
fun ScraperUrlsDialog(
    urls: Map<String, String>,
    onConfirm: (urls: Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var urlInputs by remember { mutableStateOf(urls) }

    BaseDialog(
        title = stringResource(CoreR.string.scrape_urls_title),
        onDismiss = onDismiss,
        negativeButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(CoreR.string.cancel)) }
        },
        positiveButton = {
            TextButton(onClick = { onConfirm(urlInputs.normalized()) }) {
                Text(text = stringResource(CoreR.string.confirm))
            }
        },
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
            Text(
                text = stringResource(CoreR.string.scrape_urls_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(MaterialTheme.spacings.medium))
            ScraperSite.entries.forEach { site ->
                val url = urlInputs[site.key].orEmpty()
                val isMismatch = isPossibleMismatch(site, url)
                OutlinedTextField(
                    value = url,
                    onValueChange = { value -> urlInputs = urlInputs + (site.key to value) },
                    modifier =
                        Modifier.fillMaxWidth().padding(bottom = MaterialTheme.spacings.small),
                    // 标签用站点 key：它同时是配置 key 的后缀，刻意不展示完整网址
                    label = { Text(text = site.key) },
                    isError = isMismatch,
                    supportingText =
                        if (isMismatch) {
                            { Text(text = stringResource(CoreR.string.scrape_urls_error_mismatch)) }
                        } else {
                            null
                        },
                    keyboardOptions =
                        KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    singleLine = true,
                )
            }
        }
    }
}

/**
 * [url] 与 [site] 是否疑似不匹配：只看 host 里有没有站点 key，纯提示用，不作为能否保存的门槛。
 * 留空（清除该站点配置）永远不算不匹配。
 */
private fun isPossibleMismatch(site: ScraperSite, url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return false
    val host =
        runCatching {
            // 没写 scheme 时按 https 补全，否则 Uri 解析不出 host
            val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            Uri.parse(withScheme).host
        }.getOrNull()
    return host.isNullOrEmpty() || !host.contains(site.key, ignoreCase = true)
}

/** 保存前的归一化：去首尾空白，没写 http / https 的自动补 https。 */
private fun Map<String, String>.normalized(): Map<String, String> =
    mapValues { (_, url) ->
        val trimmed = url.trim()
        when {
            trimmed.isEmpty() -> ""
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
    }
