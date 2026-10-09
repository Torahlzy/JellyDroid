package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyPerson
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 演员条目：点击进入作品列表；[onScrapeImageClick] 非空时支持长按弹出「获取头像」菜单
 * （写服务器头像需要联网与权限，离线模式不提供）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonItem(
    person: FindroidItemPerson,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onScrapeImageClick: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Column(
        modifier =
            modifier
                .width(110.dp)
                .clip(MaterialTheme.shapes.small)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick =
                        onScrapeImageClick?.let {
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuExpanded = true
                            }
                        },
                )
    ) {
        AsyncImage(
            model = person.image.uri,
            contentDescription = null,
            modifier =
                Modifier.clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .fillMaxWidth()
                    // 头像框固定正方形；图片多为竖版，用 Fit 整体缩放进框内，避免裁掉人脸
                    .aspectRatio(1f),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
        Text(
            text = person.name,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = person.role,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(2.dp))

        if (onScrapeImageClick != null) {
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(CoreR.string.scrape_actor_image)) },
                    onClick = {
                        menuExpanded = false
                        onScrapeImageClick()
                    },
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun PersonItemPreview() {
    FindroidTheme { PersonItem(person = dummyPerson, onClick = {}) }
}
