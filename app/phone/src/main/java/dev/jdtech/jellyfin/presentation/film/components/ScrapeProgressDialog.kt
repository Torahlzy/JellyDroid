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
import dev.jdtech.jellyfin.core.scraper.ScrapeProgress
import dev.jdtech.jellyfin.core.scraper.ScraperSite
import dev.jdtech.jellyfin.presentation.components.BaseDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 抓取过程中的临时弹窗：逐条列出「正在抓哪个站 / 哪个站成功」。
 *
 * [title] 与 [failureSummary] 由调用方给出，用于区分抓的是 nfo 还是封面。
 * 抓取中只能点「取消」中断；抓取成功由上层直接把弹窗从界面上移除；
 * 全部失败时保留弹窗并给出「关闭」，让用户看清每个站点的失败原因。
 *
 * [isTranslating] 为 true 表示抓取已经抓完、正在调大模型翻译，此时把提示换成「正在翻译」，
 * 否则用户会以为还卡在抓取上。
 */
@Composable
fun ScrapeProgressDialog(
    title: String,
    steps: List<ScrapeProgress>,
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
            steps.forEach { step ->
                Text(
                    text = step.toText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = step.textColor(emphasizeFailure = !isRunning),
                    modifier = Modifier.padding(vertical = MaterialTheme.spacings.extraSmall),
                )
            }
            // 只有真的尝试过站点并失败时才补一句总结；「没配置」已由上面的行说清楚，不再重复
            if (!isRunning && steps.any { it is ScrapeProgress.Failed }) {
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

@Composable
private fun ScrapeProgress.toText(): String =
    when (this) {
        is ScrapeProgress.Started ->
            stringResource(CoreR.string.scrape_site_started, site.displayName)
        is ScrapeProgress.Succeeded ->
            stringResource(CoreR.string.scrape_site_succeeded, site.displayName)
        is ScrapeProgress.Failed ->
            stringResource(CoreR.string.scrape_site_failed, site.displayName, reason)
        ScrapeProgress.NotConfigured -> stringResource(CoreR.string.scrape_site_not_configured)
    }

/**
 * 步骤行的颜色。
 *
 * 抓取还在进行时，单个站点失败只意味着「换下一个」，用中性色即可——否则最后抓取成功了，
 * 过程中闪过的红字会让人以为整件事失败了；只有全部站点都失败（弹窗留下不走）才标红。
 * 「没配置」同样是失败态，一并标红。
 */
@Composable
private fun ScrapeProgress.textColor(emphasizeFailure: Boolean): Color =
    when (this) {
        is ScrapeProgress.Succeeded -> MaterialTheme.colorScheme.primary
        is ScrapeProgress.Failed ->
            if (emphasizeFailure) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        is ScrapeProgress.Started -> Color.Unspecified
        ScrapeProgress.NotConfigured ->
            if (emphasizeFailure) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
    }

@Preview
@Composable
private fun ScrapeProgressDialogPreview() {
    FindroidTheme {
        ScrapeProgressDialog(
            title = "Scraping",
            steps =
                listOf(
                    ScrapeProgress.Started(ScraperSite.JAVDB),
                    ScrapeProgress.Failed(ScraperSite.JAVDB, "403 禁止访问"),
                    ScrapeProgress.Started(ScraperSite.JAVBUS),
                    ScrapeProgress.Succeeded(ScraperSite.JAVBUS),
                ),
            isRunning = false,
            isTranslating = false,
            failureSummary = "No data was found on any site",
            onCancel = {},
            onDismiss = {},
        )
    }
}
