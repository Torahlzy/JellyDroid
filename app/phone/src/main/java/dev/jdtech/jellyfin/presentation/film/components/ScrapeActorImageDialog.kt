package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.scraper.SiteActorImageScrapeResult
import dev.jdtech.jellyfin.core.scraper.ScraperSite
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

// 抓到的头像预览：与演员行的小头像一样按圆形方图显示
private val ACTOR_IMAGE_PREVIEW_SIZE = 48.dp

/**
 * 「获取头像」弹窗：长按演员条目弹出，打开即同时在各站点搜索该演员的头像。
 *
 * 按 [sites] 列出站点，每个站点抓到什么、失败什么原因都直接显示在对应行上；
 * 成功的行末有「上传」按钮，上传后才真正写到服务器的人物条目上（人物头像只能整体覆盖，
 * 该演员已有头像时先在标题下方给出提示）。
 * 左下角可设置抓取代理，右下角「关闭」，关掉即中断抓取并丢掉内存里的头像。
 */
@Composable
fun ScrapeActorImageDialog(
    actorName: String,
    sites: List<ScraperSite>,
    results: List<SiteActorImageScrapeResult>,
    isUploading: Boolean,
    hasAvatar: Boolean,
    proxy: String,
    onProxyChange: (String) -> Unit,
    onUploadClick: (SiteActorImageScrapeResult.Success) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showProxyDialog by remember { mutableStateOf(false) }
    // 结果按站点索引：结果与站点一一对应，用站点关联而不是下标
    val resultsBySite = results.associateBy { it.site }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(MaterialTheme.spacings.default)) {
                Text(
                    text = stringResource(CoreR.string.scrape_actor_image),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
                Text(
                    text = actorName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 人物头像只能整体覆盖，已有的头像传上去就没了，先说清楚
                if (hasAvatar) {
                    Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
                    Text(
                        text = stringResource(CoreR.string.actor_has_avatar_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(MaterialTheme.spacings.default))
                if (sites.isEmpty()) {
                    Text(
                        text = stringResource(CoreR.string.scrape_site_not_configured),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)
                    ) {
                        sites.forEach { site ->
                            ActorSiteRow(
                                site = site,
                                result = resultsBySite[site],
                                isUploading = isUploading,
                                onUploadClick = onUploadClick,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(MaterialTheme.spacings.default))
                // 按钮行：代理入口在左下角，关闭在右下角
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showProxyDialog = true }) {
                        Text(
                            text =
                                stringResource(
                                    CoreR.string.scrape_proxy_entry,
                                    proxy.ifEmpty {
                                        stringResource(CoreR.string.scrape_proxy_unset)
                                    },
                                )
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(CoreR.string.close))
                    }
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

/** 一个站点的抓取结果行；[result] 为 null 表示还没出结果，显示转圈。 */
@Composable
private fun ActorSiteRow(
    site: ScraperSite,
    result: SiteActorImageScrapeResult?,
    isUploading: Boolean,
    onUploadClick: (SiteActorImageScrapeResult.Success) -> Unit,
) {
    when (result) {
        null ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = site.displayName, style = MaterialTheme.typography.bodyMedium)
                CircularProgressIndicator(
                    modifier = Modifier.size(MaterialTheme.spacings.default),
                    strokeWidth = 2.dp,
                )
            }
        is SiteActorImageScrapeResult.Failure ->
            Text(
                text =
                    stringResource(
                        CoreR.string.scrape_site_failed,
                        site.displayName,
                        result.reason,
                    ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        is SiteActorImageScrapeResult.Success ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = site.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                AsyncImage(
                    model = result.bytes,
                    contentDescription = site.displayName,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier.size(ACTOR_IMAGE_PREVIEW_SIZE).clip(CircleShape),
                )
                TextButton(
                    onClick = { onUploadClick(result) },
                    // 上传期间禁用，避免同一张头像被重复上传
                    enabled = !isUploading,
                ) {
                    Text(text = stringResource(CoreR.string.upload))
                }
            }
    }
}

@Preview
@Composable
private fun ScrapeActorImageDialogPreview() {
    FindroidTheme {
        ScrapeActorImageDialog(
            actorName = "清原あかね",
            sites = listOf(ScraperSite.JAVBUS, ScraperSite.JAVDB),
            results =
                listOf(
                    SiteActorImageScrapeResult.Failure(
                        ScraperSite.JAVBUS,
                        "站点上找不到该演员",
                    ),
                    SiteActorImageScrapeResult.Success(ScraperSite.JAVDB, ByteArray(0)),
                ),
            isUploading = false,
            hasAvatar = true,
            proxy = "",
            onProxyChange = {},
            onUploadClick = {},
            onDismiss = {},
        )
    }
}
