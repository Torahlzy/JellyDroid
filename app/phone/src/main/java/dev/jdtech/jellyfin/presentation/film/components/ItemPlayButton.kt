package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
// Dp 重载来自 unit 包，Float 重载来自 util 包，两者同名需起别名区分
import androidx.compose.ui.unit.lerp as dpLerp
import androidx.compose.ui.util.lerp
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.SafePadding
import kotlin.math.roundToInt

// 收起态与 ItemTopBar 的 IconButton 视觉圆片尺寸一致（40dp），飞过去后能接在顶栏按钮后面
private val COLLAPSED_BUTTON_SIZE = 40.dp
// 展开态盖在封面中央的半透明圆片：收起态的约一倍大，居中更醒目
private val EXPANDED_BUTTON_SIZE = 144.dp
// 图标尺寸指画布尺寸：三角只占画布高度的 14/24，所以画布给到 124dp，三角才约 72dp
private val EXPANDED_ICON_SIZE = 124.dp
private val COLLAPSED_ICON_SIZE = 24.dp
// 展开态半透明以少遮挡封面；收起态与顶栏按钮的透明度保持一致
private const val EXPANDED_CONTAINER_ALPHA = 0.55f
private const val COLLAPSED_CONTAINER_ALPHA = 0.7f
// 白色三角在展开态压得较淡，只作提示；收起态与顶栏按钮的图标亮度看齐
private const val EXPANDED_ICON_ALPHA = 0.5f
private const val COLLAPSED_ICON_ALPHA = 0.7f

/**
 * 封面上的悬浮播放按钮：未滚动时悬浮在封面中央并跟随内容一起滚动，
 * 封面滑出屏幕超过一半后收起，动画飞到顶栏「回到首页」按钮后方。
 *
 * 悬浮层容器不消费点击事件，只有按钮圆形区域可点击。
 */
@Composable
fun ItemPlayButton(
    scrollState: ScrollState,
    headerHeightPx: Int,
    safePadding: SafePadding,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    // 悬浮层宽度，用于计算展开态的封面水平中心
    var overlayWidthPx by remember { mutableIntStateOf(0) }

    // 封面滑出超过一半（滚动量 > 封面高度一半）时收起
    val isCollapsed by
        remember(headerHeightPx) { derivedStateOf { scrollState.value > headerHeightPx / 2 } }
    val progress by
        animateFloatAsState(
            targetValue = if (isCollapsed) 1f else 0f,
            // Material 运动规范推荐 spring 物理动画
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "playButtonCollapse",
        )

    val buttonSize = dpLerp(EXPANDED_BUTTON_SIZE, COLLAPSED_BUTTON_SIZE, progress)
    val iconSize = dpLerp(EXPANDED_ICON_SIZE, COLLAPSED_ICON_SIZE, progress)
    val containerAlpha = lerp(EXPANDED_CONTAINER_ALPHA, COLLAPSED_CONTAINER_ALPHA, progress)
    val iconAlpha = lerp(EXPANDED_ICON_ALPHA, COLLAPSED_ICON_ALPHA, progress)
    // 圆片尺寸即布局尺寸（下方已关掉最小触摸目标撑大），取一半作为圆心到左上角的偏移量
    val buttonRadiusPx = with(density) { buttonSize.toPx() / 2f }

    // Material3 的 IconButton 会把触摸目标撑到最小 48dp：布局尺寸 = max(视觉尺寸, 触摸目标)，40dp 圆片居中其中。
    // ItemTopBar 用的是同款 IconButton，所以收起锚点要按 48dp 槽位推，按 40dp 圆片推会压到前面的按钮；
    // 而本按钮自身的撑大必须关掉（见下方 IconButton 处），否则自绘圆片会跟着涨到 48dp
    val topBarButtonSlotSize =
        maxOf(COLLAPSED_BUTTON_SIZE, LocalMinimumInteractiveComponentSize.current)

    // 收起锚点：顶栏「回到首页」按钮后方（back、home 之后的第三个按钮）的圆心，
    // 与 ItemTopBar 的布局对齐；RTL 时顶栏按钮从右往左排，镜像计算
    val collapsedCyPx =
        with(density) {
            (safePadding.top + MaterialTheme.spacings.small + topBarButtonSlotSize / 2)
                .roundToPx()
        }
    val collapsedOffsetFromStartPx =
        with(density) {
            (safePadding.start + MaterialTheme.spacings.small + topBarButtonSlotSize * 2.5f)
                .roundToPx()
        }
    val collapsedOffsetFromEndPx =
        with(density) {
            (safePadding.end + MaterialTheme.spacings.small + topBarButtonSlotSize * 2.5f)
                .roundToPx()
        }
    val topPx = with(density) { safePadding.top.roundToPx() }

    Box(modifier = modifier.fillMaxSize().onSizeChanged { overlayWidthPx = it.width }) {
        // 封面尺寸尚未测量完成时不显示，避免按钮从错误位置跳出
        if (headerHeightPx > 0) {
            // 关掉 Material3 IconButton 的最小触摸目标撑大：它会把布局尺寸顶到 48dp，
            // 而 clip / background 是按布局尺寸绘制的，圆片会因此比顶栏按钮大一圈、
            // 还会吃掉与「主页」按钮之间的间距。关掉后布局尺寸就正好是圆片尺寸
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                IconButton(
                    onClick = onClick,
                    modifier =
                        Modifier.offset {
                                val progressValue = progress
                                // 展开锚点：封面中心在屏幕上的当前位置，跟随内容滚动，
                                // 但不越过收起后的纵坐标，保证按钮始终可见、且过渡时不跳变
                                val expandedCx = overlayWidthPx / 2f
                                val expandedCy =
                                    (topPx + headerHeightPx / 2f - scrollState.value)
                                        .coerceAtLeast(collapsedCyPx.toFloat())
                                // 收起锚点：顶栏第三个按钮的圆心
                                val collapsedCx =
                                    if (layoutDirection == LayoutDirection.Rtl) {
                                        (overlayWidthPx - collapsedOffsetFromEndPx).toFloat()
                                    } else {
                                        collapsedOffsetFromStartPx.toFloat()
                                    }
                                val cx = lerp(expandedCx, collapsedCx, progressValue)
                                val cy = lerp(expandedCy, collapsedCyPx.toFloat(), progressValue)
                                IntOffset(
                                    (cx - buttonRadiusPx).roundToInt(),
                                    (cy - buttonRadiusPx).roundToInt(),
                                )
                            }
                            .size(buttonSize)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = containerAlpha)),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_filled),
                        contentDescription = stringResource(CoreR.string.play),
                        tint = Color.White.copy(alpha = iconAlpha),
                        modifier = Modifier.size(iconSize),
                    )
                }
            }
        }
    }
}
