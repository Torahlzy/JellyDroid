package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.scraper.ScraperSite
import dev.jdtech.jellyfin.core.scraper.SiteMetadataScrapeResult
import dev.jdtech.jellyfin.presentation.components.BaseDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 抓取过程中的临时弹窗：逐条列出各站点这次抓到了什么、哪个站失败。
 *
 * 各站点是同时抓的，因此 [sites] 里的站点按 [ScraperSite] 的声明顺序列出，[results] 里还没有的
 * 就是「还在抓」。[title] 与 [failureSummary] 由调用方给出，用于区分抓的是 nfo 还是封面。
 * 抓取中只能点「取消」中断；抓取成功由上层直接把弹窗从界面上移除；
 * 全部失败时保留弹窗并给出「关闭」，让用户看清每个站点的失败原因。
 *
 * [isTranslating] 为 true 表示抓取已经抓完、正在调大模型翻译，此时把提示换成「正在翻译」，
 * 否则用户会以为还卡在抓取上。
 */
@Composable
fun ScrapeProgressDialog(
    title: String,
    sites: List<ScraperSite>,
    results: List<SiteMetadataScrapeResult>,
    isRunning: Boolean,
    isTranslating: Boolean,
    failureSummary: String,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    BaseDialog(
        title = title,
        onDismiss = { if (!isRunning) onDismiss() },
        negativeButton = {},
        positiveButton = {
            if (isRunning) {
                TextButton(onClick = onCancel) {
                    Text(text = stringResource(CoreR.string.cancel))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(CoreR.string.close))
                }
            }
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
        ) {
            if (isRunning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(MaterialTheme.spacings.default),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(MaterialTheme.spacings.small))
                    Text(
                        text =
                            stringResource(
                                if (isTranslating) {
                                    CoreR.string.translate_in_progress
                                } else {
                                    CoreR.string.scrape_in_progress
                                }
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(MaterialTheme.spacings.small))
            }
            // 一个站点都没配地址时说明去哪儿填，比列一堆「正在抓取」有用
            if (sites.isEmpty()) {
                Text(
                    text = stringResource(CoreR.string.scrape_site_not_configured),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = MaterialTheme.spacings.extraSmall),
                )
            } else {
                sites.forEach { site ->
                    SiteProgressRow(
                        site = site,
                        result = results.firstOrNull { it.site == site },
                        // 抓取还在进行时单个站点失败只意味着「别的站点也许有」，用中性色即可
                        emphasizeFailure = !isRunning,
                    )
                }
            }
            // 只有真的尝试过站点并失败时才补一句总结；「没配置」已由上面的行说清楚，不再重复
            if (!isRunning && results.any { it is SiteMetadataScrapeResult.Failure }) {
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                Text(
                    text = failureSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * 一个站点的进度行；[result] 为 null 表示这个站点还在抓。
 *
 * 抓取进行中单个站点失败用中性色，避免最后抓取成功了、过程中闪过的红字却让人以为整件事失败；
 * 全部站点都失败（弹窗留下不走）时才标红。
 */
@Composable
private fun SiteProgressRow(
    site: ScraperSite,
    result: SiteMetadataScrapeResult?,
    emphasizeFailure: Boolean,
) {
    val text =
        when (result) {
            null -> stringResource(CoreR.string.scrape_site_started, site.displayName)
            is SiteMetadataScrapeResult.Success ->
                stringResource(CoreR.string.scrape_site_succeeded, site.displayName)
            is SiteMetadataScrapeResult.Failure ->
                stringResource(CoreR.string.scrape_site_failed, site.displayName, result.reason)
        }
    val color =
        when (result) {
            is SiteMetadataScrapeResult.Success -> MaterialTheme.colorScheme.primary
            is SiteMetadataScrapeResult.Failure ->
                if (emphasizeFailure) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            null -> Color.Unspecified
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.padding(vertical = MaterialTheme.spacings.extraSmall),
    )
}

@Preview
@Composable
private fun ScrapeProgressDialogPreview() {
    FindroidTheme {
        ScrapeProgressDialog(
            title = "Scraping",
            sites = listOf(ScraperSite.JAV321, ScraperSite.JAVBUS, ScraperSite.JAVDB),
            results =
                listOf(
                    SiteMetadataScrapeResult.Failure(ScraperSite.JAV321, "403 禁止访问"),
                    SiteMetadataScrapeResult.Failure(ScraperSite.JAVDB, "页面结构变化"),
                ),
            isRunning = true,
            isTranslating = false,
            failureSummary = "No data was found on any site",
            onCancel = {},
            onDismiss = {},
        )
    }
}
