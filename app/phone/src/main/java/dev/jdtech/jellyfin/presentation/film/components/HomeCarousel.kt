package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovies
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import kotlinx.coroutines.delay

// 自动轮播间隔
private const val AUTO_SCROLL_DELAY = 5000L

// banner 宽高比（宽 / 高），比默认的 16:9 略高；首页「继续观看」封面同样复用该比例
internal const val BANNER_ASPECT_RATIO = 800f / 550f

// 单屏能容纳两页、三页 banner 的宽度阈值（dp）
private const val TWO_PAGES_WIDTH_DP = 600f
private const val THREE_PAGES_WIDTH_DP = 840f

@Composable
fun HomeCarousel(
    items: List<FindroidItem>,
    itemsPadding: PaddingValues,
    onAction: (HomeAction) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    val pagerIsDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val layoutDirection = LocalLayoutDirection.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val pageSpacing = MaterialTheme.spacings.medium

    // 由屏幕宽度直接推算 banner 的最终宽高：
    // 首次进入时就用这一尺寸渲染，不再先按默认宽高显示、之后再变高
    val horizontalPadding =
        itemsPadding.calculateStartPadding(layoutDirection) +
            itemsPadding.calculateEndPadding(layoutDirection)
    val availableWidth = (screenWidthDp.dp - horizontalPadding).coerceAtLeast(0.dp)
    val pages =
        when {
            availableWidth >= THREE_PAGES_WIDTH_DP.dp -> 3
            availableWidth >= TWO_PAGES_WIDTH_DP.dp -> 2
            else -> 1
        }
    val bannerWidth = (availableWidth - pageSpacing * (pages - 1)) / pages
    val bannerHeight = bannerWidth / BANNER_ASPECT_RATIO

    val pageSize =
        remember(bannerWidth) {
            object : PageSize {
                override fun Density.calculateMainAxisPageSize(
                    availableSpace: Int,
                    pageSpacing: Int,
                ): Int = bannerWidth.roundToPx().coerceAtMost(availableSpace)
            }
        }

    if (!pagerIsDragged) {
        LaunchedEffect(pagerState) {
            while (true) {
                delay(AUTO_SCROLL_DELAY)
                val nextPage =
                    if (pagerState.canScrollForward) {
                        pagerState.currentPage + 1
                    } else {
                        0
                    }
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        contentPadding = itemsPadding,
        pageSize = pageSize,
        pageSpacing = MaterialTheme.spacings.medium,
    ) { page ->
        val item = items[page]
        HomeCarouselItem(item = item, onAction = onAction, height = bannerHeight)
    }
}

@Composable
@Preview(showBackground = true)
private fun HomeCarouselPreview() {
    FindroidTheme {
        HomeCarousel(
            items = dummyMovies,
            itemsPadding = PaddingValues(horizontal = 0.dp),
            onAction = {},
        )
    }
}
