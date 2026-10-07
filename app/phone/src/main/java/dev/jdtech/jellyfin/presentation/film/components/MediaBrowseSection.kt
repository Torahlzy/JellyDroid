package dev.jdtech.jellyfin.presentation.film.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.MetadataFacet
import dev.jdtech.jellyfin.presentation.theme.spacings

/** 入口图标的尺寸 */
private val ENTRY_ICON_SIZE = 24.dp

/** 每行入口数量：固定两列，折叠屏展开后也不会把卡片拉得过宽 */
private const val ENTRIES_PER_ROW = 2

/** 单个入口：一个可浏览的元数据维度。 */
private data class BrowseEntry(
    val facet: MetadataFacet,
    @param:DrawableRes val iconRes: Int,
)

// 人物三个维度的图标相同，靠文案区分；其余维度各有专属图标
private val browseEntries =
    listOf(
        BrowseEntry(MetadataFacet.GENRE, CoreR.drawable.ic_genre),
        BrowseEntry(MetadataFacet.TAG, CoreR.drawable.ic_tag),
        BrowseEntry(MetadataFacet.OFFICIAL_RATING, CoreR.drawable.ic_rating),
        BrowseEntry(MetadataFacet.YEAR, CoreR.drawable.ic_calendar),
        BrowseEntry(MetadataFacet.ACTOR, CoreR.drawable.ic_user),
        BrowseEntry(MetadataFacet.DIRECTOR, CoreR.drawable.ic_user),
        BrowseEntry(MetadataFacet.WRITER, CoreR.drawable.ic_user),
        BrowseEntry(MetadataFacet.STUDIO, CoreR.drawable.ic_studio),
    )

/**
 * 「我的媒体」页列表底部的浏览入口区块：按 nfo 已入库的维度进入对应的浏览页。
 *
 * 固定每行 [ENTRIES_PER_ROW] 个入口：入口卡片本身很窄，跟随网格列数自适应的话，折叠屏展开后
 * 会被拉成过宽的一两行，固定两列能保持展开态与折叠态一致的阅读节奏。
 */
@Composable
fun MediaBrowseSection(
    onFacetClick: (MetadataFacet) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(CoreR.string.browse_section_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = MaterialTheme.spacings.small),
        )

        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
            browseEntries.chunked(ENTRIES_PER_ROW).forEach { rowEntries ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                ) {
                    rowEntries.forEach { entry ->
                        BrowseEntryCard(
                            entry = entry,
                            onClick = { onFacetClick(entry.facet) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 末行不足两个时补等宽占位，保证卡片宽度与上一行一致
                    repeat(ENTRIES_PER_ROW - rowEntries.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 单个入口卡片：图标 + 文案横向排布，占半行宽。 */
@Composable
private fun BrowseEntryCard(
    entry: BrowseEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.spacings.medium,
                        vertical = MaterialTheme.spacings.small,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(entry.iconRes),
                contentDescription = null,
                modifier = Modifier.size(ENTRY_ICON_SIZE),
            )
            Spacer(Modifier.width(MaterialTheme.spacings.small))
            Text(
                text = stringResource(entry.facet.titleRes()),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
