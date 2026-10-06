package dev.jdtech.jellyfin.presentation.film.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.models.FindroidBoxSet
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun ItemCard(
    item: FindroidItem,
    direction: Direction,
    onClick: (FindroidItem) -> Unit,
    // 可选宽高比覆盖，透传给封面
    aspectRatio: Float? = null,
    // 无封面时展示的占位图标（drawable 资源），透传给封面
    @DrawableRes placeholderIconRes: Int? = null,
    // 网格布局中宽度由单元格决定时置 true：卡片铺满可用宽度，进度条同步按实际宽度计算
    isWidthAdaptive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val width =
        when (direction) {
            Direction.HORIZONTAL -> 260
            Direction.VERTICAL -> 150
        }
    Column(
        modifier =
            modifier
                .then(
                    if (isWidthAdaptive) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.width(width.dp)
                    }
                )
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = { onClick(item) })
    ) {
        Surface(shape = MaterialTheme.shapes.small) {
            BoxWithConstraints {
                val cardWidth = if (isWidthAdaptive) maxWidth.value.toInt() else width
                Box {
                    ItemPoster(
                        item = item,
                        direction = direction,
                        aspectRatio = aspectRatio,
                        placeholderIconRes = placeholderIconRes,
                    )
                    Row(
                        modifier =
                            Modifier.align(Alignment.TopEnd)
                                .padding(MaterialTheme.spacings.small),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                    ) {
                        // 封面多半取自合集内部条目，用角标让用户知道这是合集而非单个影片
                        if (item is FindroidBoxSet) BoxSetBadge()
                        if (item.isDownloaded()) DownloadedBadge()
                        if (item.played) PlayedBadge()
                        item.unplayedItemCount?.takeIf { it > 0 }?.let { ItemCountBadge(it) }
                    }
                    if (direction == Direction.HORIZONTAL) {
                        ProgressBar(
                            item = item,
                            width = cardWidth,
                            modifier =
                                Modifier.align(Alignment.BottomStart)
                                    .padding(MaterialTheme.spacings.small),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
        Text(
            text = if (item is FindroidEpisode) item.seriesName else item.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (item is FindroidEpisode) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (item is FindroidEpisode) {
            Text(
                text =
                    stringResource(
                        id = R.string.episode_name_extended,
                        item.parentIndexNumber,
                        item.indexNumber,
                        item.name,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun ItemCardPreviewMovie() {
    FindroidTheme { ItemCard(item = dummyMovie, direction = Direction.HORIZONTAL, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun ItemCardPreviewMovieVertical() {
    FindroidTheme { ItemCard(item = dummyMovie, direction = Direction.VERTICAL, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun ItemCardPreviewEpisode() {
    FindroidTheme { ItemCard(item = dummyEpisode, direction = Direction.HORIZONTAL, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun ItemCardPreviewEpisodeVertical() {
    FindroidTheme { ItemCard(item = dummyEpisode, direction = Direction.VERTICAL, onClick = {}) }
}
