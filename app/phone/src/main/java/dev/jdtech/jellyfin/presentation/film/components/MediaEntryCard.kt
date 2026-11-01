package dev.jdtech.jellyfin.presentation.film.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/** 我的媒体页列表底部的通用入口卡：图标 + 文案。 */
@Composable
fun MediaEntryCard(
    @DrawableRes iconRes: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified,
) {
    OutlinedCard(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacings.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
            )
            Text(text = text)
        }
    }
}

@Preview
@Composable
private fun MediaEntryCardPreview() {
    FindroidTheme {
        MediaEntryCard(
            iconRes = CoreR.drawable.ic_heart_filled,
            // 与详情页里「已收藏」状态保持同一套视觉：实心红心
            iconTint = Color.Red,
            text = "My favorites",
            onClick = {},
            modifier = Modifier.width(320.dp),
        )
    }
}
