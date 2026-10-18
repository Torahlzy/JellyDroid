package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.film.presentation.collection.CollectionAction
import dev.jdtech.jellyfin.models.CollectionSection
import dev.jdtech.jellyfin.models.CoverDisplayMode
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.GridCellsAdaptiveWithMinColumns

// 横图封面的最小宽度（dp）：窄屏排一列，折叠屏展开等宽屏可放下两列
internal const val LANDSCAPE_COVER_MIN_WIDTH_DP = 300

/**
 * 分组条目网格。
 *
 * [coverMode] 由「我喜欢」页从偏好读出后传入（与媒体库列表共用），合集与下载页不传，保持竖图。
 */
@Composable
fun CollectionGrid(
    sections: List<CollectionSection>,
    innerPadding: PaddingValues,
    onAction: (CollectionAction) -> Unit,
    coverMode: CoverDisplayMode = CoverDisplayMode.defaultValue,
) {
    val isLandscape = coverMode == CoverDisplayMode.LANDSCAPE

    LazyVerticalGrid(
        columns =
            if (isLandscape) {
                GridCells.Adaptive(minSize = LANDSCAPE_COVER_MIN_WIDTH_DP.dp)
            } else {
                GridCellsAdaptiveWithMinColumns(minSize = 160.dp, minColumns = 2)
            },
        modifier = Modifier.padding(innerPadding).fillMaxSize(),
        contentPadding = PaddingValues(all = MaterialTheme.spacings.default),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
    ) {
        sections.forEach { section ->
            stickyHeader {
                Card {
                    Text(
                        text = section.name.asString(),
                        modifier =
                            Modifier.padding(
                                horizontal = MaterialTheme.spacings.medium,
                                vertical = MaterialTheme.spacings.medium,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            items(items = section.items, key = { it.id }) { item ->
                // 横图模式下所有条目统一铺满单元格；竖图模式保留分集原有的 16:9 剧照形态
                val direction =
                    if (isLandscape || item is FindroidEpisode) Direction.HORIZONTAL
                    else Direction.VERTICAL
                ItemCard(
                    item = item,
                    direction = direction,
                    // 横图封面沿用首页 banner 的宽高比，竖图走默认 2:3
                    aspectRatio = if (isLandscape) BANNER_ASPECT_RATIO else null,
                    isWidthAdaptive = isLandscape,
                    onClick = { onAction(CollectionAction.OnItemClick(item)) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}
