package dev.jdtech.jellyfin.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.HomeRoute
import dev.jdtech.jellyfin.TabBarItem
import dev.jdtech.jellyfin.downloadsTab
import dev.jdtech.jellyfin.homeTab
import dev.jdtech.jellyfin.mediaTab
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding

/** 悬浮底栏中单个 tab 的宽度，整体宽度按 tab 数量累加。 */
private val FLOATING_TAB_WIDTH = 120.dp

/** 悬浮底栏的投影高度，与 Material3 底部导航栏的默认高度一致。 */
private val FLOATING_ELEVATION = 3.dp

/**
 * 应用底部导航栏。
 *
 * 窄屏（手机）下铺满底部；宽屏（折叠屏展开 / 平板）下收成悬浮在底部中央的胶囊——三个 tab 平铺在
 * 宽屏底部会显得空旷，而放到左侧又离手指太远。
 *
 * @param floating 是否以悬浮胶囊的形式呈现。
 */
@Composable
fun AppNavigationBar(
    items: List<TabBarItem>,
    selectedRoute: String?,
    onItemClick: (TabBarItem) -> Unit,
    modifier: Modifier = Modifier,
    floating: Boolean = false,
) {
    if (floating) {
        Surface(
            modifier =
                modifier
                    // 悬浮胶囊与屏幕底部留一段距离，同时避开手势导航区
                    .padding(
                        bottom = rememberSafePadding().bottom + MaterialTheme.spacings.default
                    )
                    .width(FLOATING_TAB_WIDTH * items.size),
            shape = RoundedCornerShape(percent = 50),
            color = NavigationBarDefaults.containerColor,
            tonalElevation = FLOATING_ELEVATION,
            shadowElevation = FLOATING_ELEVATION,
        ) {
            NavigationTabs(
                items = items,
                selectedRoute = selectedRoute,
                onItemClick = onItemClick,
                // 两端留一点内边距，避免 tab 压到胶囊的圆弧上
                modifier = Modifier.padding(horizontal = MaterialTheme.spacings.small),
                // 圆角、底色与投影都由外层 Surface 提供，内层只放内容
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        }
    } else {
        NavigationTabs(
            items = items,
            selectedRoute = selectedRoute,
            onItemClick = onItemClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun NavigationTabs(
    items: List<TabBarItem>,
    selectedRoute: String?,
    onItemClick: (TabBarItem) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = NavigationBarDefaults.containerColor,
    tonalElevation: Dp = FLOATING_ELEVATION,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = containerColor,
        tonalElevation = tonalElevation,
        windowInsets = windowInsets,
    ) {
        items.forEach { item ->
            NavigationTab(
                item = item,
                selected = selectedRoute == item.route::class.qualifiedName,
                onItemClick = onItemClick,
            )
        }
    }
}

@Composable
private fun RowScope.NavigationTab(
    item: TabBarItem,
    selected: Boolean,
    onItemClick: (TabBarItem) -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = { onItemClick(item) },
        icon = {
            Icon(
                painter = painterResource(item.icon),
                contentDescription = stringResource(item.title),
            )
        },
        enabled = item.enabled,
        label = { Text(text = stringResource(item.title)) },
        // 均分底栏宽度，tab 数量变化（离线模式少一个）时也保持等宽
        modifier = Modifier.weight(1f),
    )
}

@Preview(showBackground = true)
@Composable
private fun AppNavigationBarPreview() {
    FindroidTheme {
        AppNavigationBar(
            items = listOf(homeTab, mediaTab, downloadsTab),
            selectedRoute = HomeRoute::class.qualifiedName,
            onItemClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 840)
@Composable
private fun AppNavigationBarFloatingPreview() {
    FindroidTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            AppNavigationBar(
                items = listOf(homeTab, mediaTab, downloadsTab),
                selectedRoute = HomeRoute::class.qualifiedName,
                onItemClick = {},
                floating = true,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
