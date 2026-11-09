package dev.jdtech.jellyfin.presentation.player

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.player.local.R as PlayerR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/** 菜单里的一个格子：图标、文案与是否可用（没有字幕轨时相关入口置灰）。 */
data class PlayerMenuItem(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

private val MenuCardMaxWidth = 760.dp
private val MenuCardCorner = 28.dp
private val MenuTileCorner = 20.dp
private val MenuTileIconSize = 32.dp
private const val MenuWidthFraction = 0.9f
private const val MenuLabelMaxLines = 3
private const val ScrimAlpha = 0.6f
private const val PressedScale = 0.94f
private const val DisabledContainerAlpha = 0.12f
private const val DisabledContentAlpha = 0.38f

/**
 * 横屏播放器菜单：所有入口横向排成一行正方形格子，无标题行，卡片上下留白。
 *
 * 半透明蒙层点击空白处关闭菜单；卡片本身消费点击，避免空白区域的触摸穿透到蒙层。
 */
@Composable
fun PlayerMenuContent(
    items: List<PlayerMenuItem>,
    onDismiss: () -> Unit,
    onSelect: (PlayerMenuItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = ScrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier =
                Modifier.fillMaxWidth(MenuWidthFraction)
                    .widthIn(max = MenuCardMaxWidth)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            shape = RoundedCornerShape(MenuCardCorner),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Row(
                modifier =
                    Modifier.padding(
                        horizontal = MaterialTheme.spacings.medium,
                        vertical = MaterialTheme.spacings.default,
                    ),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    PlayerMenuTile(
                        item = item,
                        onClick = { onSelect(item) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerMenuTile(
    item: PlayerMenuItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // 当前 M3 版本的 MotionScheme 仍是 internal，这里按同一套规则直接给弹簧 spec：
    // 缩放用带回弹的空间弹簧，颜色用无回弹弹簧，避免颜色过渡抖动
    val scale by
        animateFloatAsState(
            targetValue = if (isPressed && item.enabled) PressedScale else 1f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            label = "menu-tile-scale",
        )
    val containerColor by
        animateColorAsState(
            targetValue =
                if (item.enabled) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledContainerAlpha),
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
            label = "menu-tile-container",
        )
    val contentColor by
        animateColorAsState(
            targetValue =
                if (item.enabled) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledContentAlpha),
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
            label = "menu-tile-content",
        )

    Surface(
        modifier =
            modifier.aspectRatio(1f).graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(MenuTileCorner),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .clickable(
                        enabled = item.enabled,
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick,
                    )
                    .padding(MaterialTheme.spacings.extraSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    MaterialTheme.spacings.extraSmall,
                    Alignment.CenterVertically,
                ),
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = null,
                modifier = Modifier.size(MenuTileIconSize),
            )
            Text(
                text = stringResource(item.labelRes),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = MenuLabelMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun PlayerMenuContentPreview() {
    FindroidTheme {
        PlayerMenuContent(
            items =
                listOf(
                    // 无字幕轨时这两项置灰，其余入口仍可用
                    PlayerMenuItem(
                        labelRes = PlayerR.string.select_subtitle_track,
                        iconRes = CoreR.drawable.ic_closed_caption,
                        enabled = false,
                    ) {},
                    PlayerMenuItem(
                        labelRes = PlayerR.string.player_menu_subtitle_adjust,
                        iconRes = CoreR.drawable.ic_settings,
                        enabled = false,
                    ) {},
                    PlayerMenuItem(
                        labelRes = PlayerR.string.player_menu_download_subtitle,
                        iconRes = CoreR.drawable.ic_download,
                    ) {},
                    PlayerMenuItem(
                        labelRes = PlayerR.string.select_playback_speed,
                        iconRes = CoreR.drawable.ic_gauge,
                    ) {},
                    PlayerMenuItem(
                        labelRes = PlayerR.string.select_audio_track,
                        iconRes = CoreR.drawable.ic_speaker,
                    ) {},
                    PlayerMenuItem(
                        labelRes = PlayerR.string.player_menu_capture_backdrop,
                        iconRes = CoreR.drawable.ic_film,
                    ) {},
                ),
            onDismiss = {},
            onSelect = {},
        )
    }
}
