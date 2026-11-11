package dev.jdtech.jellyfin.presentation.components

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
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 抓取站点网址的输入弹窗：按 [urls] 的 key 顺序每个站点一个输入框，站点 key 由 [urls] 决定，
 * 同时作为输入框标签与偏好的存储 key；渲染不枚举站点，清单由 `:core` 的 `ScraperSite` 枚举统一给出，
 * 新增站点只需加枚举项，设置页与详情页的回填 map 会一起更新。
 * [urls] 传的是各站点当前实际生效的地址（含打包时配置的地址），打开即回显，免得看到空框以为没配置。
 *
 * 防呆只提示不拦人：网址归一化后解析不出 host、不是 http(s) 站点根地址（弹窗只收根地址，带其它
 * scheme 或路径 / 查询参数都会提示），或 nfo 站点的 host 里不含该站点的 key（可能把别的站点的网址
 * 贴错位置）时，就在输入框下给一句「可能不匹配」，但不阻止保存；字幕站可填任意镜像域名，不校验
 * host 与站点 key 是否一致。保存时统一补全 scheme（没写 http / https 的自动按 https 处理）并去掉
 * 首尾空白。
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
            urlInputs.keys.forEach { siteKey ->
                val url = urlInputs[siteKey].orEmpty()
                // 已生效的回填值（含打包配置）不参与防呆，只提示用户本次输入可能写错，
                // 否则打包域名不含站点 key 时一打开弹窗就一片「可能不匹配」
                val isMismatch =
                    url != urls[siteKey].orEmpty() && isPossibleMismatch(siteKey, url)
                OutlinedTextField(
                    value = url,
                    onValueChange = { value -> urlInputs = urlInputs + (siteKey to value) },
                    modifier =
                        Modifier.fillMaxWidth().padding(bottom = MaterialTheme.spacings.small),
                    // 标签用站点 key：它同时是配置 key 的后缀，刻意不展示完整网址
                    label = { Text(text = siteKey) },
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
 * [url] 与 [siteKey] 是否疑似不匹配：留空（清除该站点配置）永远不算；网址归一化后解析不出 host、
 * 不是 http(s) 站点根地址，或 nfo 站点的 host 里不含该站点的 key（可能把别的站点的网址贴错位置）
 * 时提示。纯提示用，不作为能否保存的门槛。
 */
private fun isPossibleMismatch(siteKey: String, url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return false
    // 保存只认 http(s) 前缀，其它 scheme 会被补成 https://ftp://... 之类的无效地址
    if (trimmed.contains("://") && !trimmed.hasHttpScheme()) return true
    // 与保存走同一套归一化，避免「提示通过却把 URL 拼坏」
    val uri = runCatching { Uri.parse(trimmed.normalizeUrl()) }.getOrNull() ?: return true
    val host = uri.host
    if (host.isNullOrEmpty()) return true
    // 站点根地址之后还会再拼搜索 / 详情路径，带路径或查询参数会拼出无效 URL
    val path = uri.path
    if (uri.query != null || uri.fragment != null || (!path.isNullOrEmpty() && path != "/")) {
        return true
    }
    // 字幕站等自定义站点可以换成任意镜像域名，不能要求 host 含站点 key
    val isNfoSite = ScraperSite.entries.any { it.key == siteKey }
    return isNfoSite && !host.contains(siteKey, ignoreCase = true)
}

/** 是否已经带 http(s) 前缀；保存与防呆共用这一判定，避免两处规则漂移。 */
private fun String.hasHttpScheme(): Boolean =
    startsWith("http://") || startsWith("https://")

/** 去首尾空白，没写 http / https 的自动补 https；保存与防呆共用这一套规则。 */
private fun String.normalizeUrl(): String {
    val trimmed = trim()
    return when {
        trimmed.isEmpty() -> ""
        trimmed.hasHttpScheme() -> trimmed
        else -> "https://$trimmed"
    }
}

private fun Map<String, String>.normalized(): Map<String, String> =
    mapValues { (_, url) -> url.normalizeUrl() }
