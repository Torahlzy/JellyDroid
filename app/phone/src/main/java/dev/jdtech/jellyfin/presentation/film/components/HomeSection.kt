package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.models.HomeSection
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun HomeSection(
    section: HomeSection,
    itemsPadding: PaddingValues,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    // 可选宽高比覆盖，透传给卡片封面
    aspectRatio: Float? = null,
    // 分区专属的共享元素 key 前缀；为空时沿用卡片的默认 key
    heroKeyPrefix: String? = null,
) {
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().height(42.dp).padding(itemsPadding)) {
            Text(
                text = section.name.asString(),
                modifier = Modifier.align(Alignment.CenterStart),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
        LazyRow(
            contentPadding = itemsPadding,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        ) {
            items(section.items, key = { it.id }) { item ->
                val heroKey = heroKeyPrefix?.let { "$it-${item.id}" }
                ItemCard(
                    item = item,
                    direction = Direction.HORIZONTAL,
                    aspectRatio = aspectRatio,
                    onClick = { onAction(HomeAction.OnItemClick(item, heroKey = heroKey)) },
                    heroKey = heroKey,
                    // 保持卡片为 item 根节点，避免额外的动画布局干扰共享元素转场
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}
