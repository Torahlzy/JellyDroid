package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovies
import dev.jdtech.jellyfin.film.presentation.itemfilter.ItemFilterAction
import dev.jdtech.jellyfin.film.presentation.itemfilter.ItemFilterState
import dev.jdtech.jellyfin.film.presentation.itemfilter.ItemFilterViewModel
import dev.jdtech.jellyfin.models.CoverDisplayMode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.MetadataFacet
import dev.jdtech.jellyfin.presentation.film.components.BANNER_ASPECT_RATIO
import dev.jdtech.jellyfin.presentation.film.components.Direction
import dev.jdtech.jellyfin.presentation.film.components.ItemCard
import dev.jdtech.jellyfin.presentation.film.components.PagingErrorGroup
import dev.jdtech.jellyfin.presentation.film.components.SortByDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.GridCellsAdaptiveWithMinColumns
import dev.jdtech.jellyfin.presentation.utils.plus
import kotlinx.coroutines.flow.flowOf

// 横图封面的最小宽度（dp）：窄屏排一列，折叠屏展开等宽屏可放下两列
private const val LANDSCAPE_COVER_MIN_WIDTH_DP = 300

@Composable
fun ItemFilterScreen(
    facet: MetadataFacet,
    filterKey: String,
    title: String,
    onItemClick: (FindroidItem) -> Unit,
    navigateBack: () -> Unit,
    viewModel: ItemFilterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(facet, filterKey) { viewModel.loadItems(facet, filterKey) }

    ItemFilterScreenLayout(
        title = title,
        state = state,
        onItemClick = onItemClick,
        onBackClick = navigateBack,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemFilterScreenLayout(
    title: String,
    state: ItemFilterState,
    onItemClick: (FindroidItem) -> Unit,
    onBackClick: () -> Unit,
    onAction: (ItemFilterAction) -> Unit,
) {
    val contentPadding = PaddingValues(all = MaterialTheme.spacings.default)

    val items = state.items.collectAsLazyPagingItems()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showSortByDialog by remember { mutableStateOf(false) }

    val direction =
        if (state.coverMode == CoverDisplayMode.LANDSCAPE) {
            Direction.HORIZONTAL
        } else {
            Direction.VERTICAL
        }

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = stringResource(CoreR.string.navigate_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSortByDialog = true }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_down_up),
                            contentDescription = stringResource(CoreR.string.sort_by),
                        )
                    }
                },
                windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column {
            PagingErrorGroup(
                loadStates = items.loadState,
                onRefresh = { items.refresh() },
                modifier = Modifier.fillMaxWidth().padding(contentPadding + innerPadding),
            )

            Box(modifier = Modifier.fillMaxSize()) {
                // 分页流交付第一份结果前是 NotLoading 的「未完成」态，不能当成空列表，否则进页面会先闪
                // 一帧「没有符合该筛选的条目」；只有加载完成且已到末尾，才是服务端真的没给任何条目
                val refreshState = items.loadState.refresh
                val isEmptyResult =
                    refreshState is LoadState.NotLoading && refreshState.endOfPaginationReached

                when {
                    items.itemCount == 0 && refreshState !is LoadState.Error && !isEmptyResult ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    // refresh 报错时上方已有错误卡片，这里不再补一句空态文案
                    items.itemCount == 0 && isEmptyResult ->
                        Text(
                            text = stringResource(CoreR.string.no_filtered_items),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    else ->
                        LazyVerticalGrid(
                            columns =
                                if (direction == Direction.HORIZONTAL) {
                                    // 横图卡片较宽：按可用宽度自适应，窄屏一列铺满，宽屏每行两个
                                    GridCells.Adaptive(minSize = LANDSCAPE_COVER_MIN_WIDTH_DP.dp)
                                } else {
                                    GridCellsAdaptiveWithMinColumns(minSize = 160.dp, minColumns = 2)
                                },
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = contentPadding + innerPadding,
                            horizontalArrangement =
                                Arrangement.spacedBy(MaterialTheme.spacings.default),
                            verticalArrangement =
                                Arrangement.spacedBy(MaterialTheme.spacings.default),
                        ) {
                            items(count = items.itemCount, key = items.itemKey { it.id }) {
                                val item = items[it]
                                item?.let { item ->
                                    ItemCard(
                                        item = item,
                                        direction = direction,
                                        onClick = { onItemClick(item) },
                                        modifier = Modifier.animateItem(),
                                        aspectRatio =
                                            if (direction == Direction.HORIZONTAL) {
                                                BANNER_ASPECT_RATIO
                                            } else {
                                                null
                                            },
                                        isWidthAdaptive = direction == Direction.HORIZONTAL,
                                    )
                                }
                            }
                        }
                }
            }
        }
    }

    if (showSortByDialog) {
        SortByDialog(
            currentSortBy = state.sortBy,
            currentSortOrder = state.sortOrder,
            currentCoverMode = state.coverMode,
            onUpdate = { sortBy, sortOrder, coverMode ->
                onAction(ItemFilterAction.ChangeSorting(sortBy, sortOrder))
                onAction(ItemFilterAction.ChangeCoverMode(coverMode))
            },
            onDismissRequest = { showSortByDialog = false },
        )
    }
}

@PreviewScreenSizes
@Composable
private fun ItemFilterScreenLayoutPreview() {
    FindroidTheme {
        ItemFilterScreenLayout(
            title = "Action",
            state = ItemFilterState(items = flowOf(PagingData.from(dummyMovies))),
            onItemClick = {},
            onBackClick = {},
            onAction = {},
        )
    }
}
