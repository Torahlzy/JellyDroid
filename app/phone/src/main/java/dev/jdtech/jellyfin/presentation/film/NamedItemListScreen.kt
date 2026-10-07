package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyNamedItems
import dev.jdtech.jellyfin.film.presentation.nameditem.NamedItemListAction
import dev.jdtech.jellyfin.film.presentation.nameditem.NamedItemListState
import dev.jdtech.jellyfin.film.presentation.nameditem.NamedItemListViewModel
import dev.jdtech.jellyfin.models.FindroidNamedItem
import dev.jdtech.jellyfin.models.MetadataFacet
import dev.jdtech.jellyfin.presentation.components.ErrorDialog
import dev.jdtech.jellyfin.presentation.film.components.ErrorCard
import dev.jdtech.jellyfin.presentation.film.components.ListSearchField
import dev.jdtech.jellyfin.presentation.film.components.titleRes
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.GridCellsAdaptiveWithMinColumns

// 命名实体卡片的最小宽度（dp）
private const val NAMED_ITEM_MIN_WIDTH_DP = 160

@Composable
fun NamedItemListScreen(
    facet: MetadataFacet,
    onItemClick: (FindroidNamedItem) -> Unit,
    navigateBack: () -> Unit,
    viewModel: NamedItemListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(facet) { viewModel.loadItems(facet) }

    NamedItemListScreenLayout(
        facet = facet,
        state = state,
        onItemClick = onItemClick,
        onBackClick = navigateBack,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NamedItemListScreenLayout(
    facet: MetadataFacet,
    state: NamedItemListState,
    onItemClick: (FindroidNamedItem) -> Unit,
    onBackClick: () -> Unit,
    onAction: (NamedItemListAction) -> Unit,
) {
    val contentPadding = PaddingValues(all = MaterialTheme.spacings.default)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showErrorDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(facet.titleRes())) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = stringResource(CoreR.string.navigate_back),
                        )
                    }
                },
                windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ListSearchField(
                query = state.searchQuery,
                onQueryChange = { onAction(NamedItemListAction.OnSearchQueryChange(it)) },
                placeholder = stringResource(CoreR.string.search),
                modifier = Modifier.padding(horizontal = MaterialTheme.spacings.default),
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.items.isEmpty() ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    state.error != null ->
                        ErrorCard(
                            onShowStacktrace = { showErrorDialog = true },
                            onRetryClick = { onAction(NamedItemListAction.Retry) },
                            modifier = Modifier.fillMaxWidth().padding(contentPadding),
                        )
                    state.visibleItems.isEmpty() ->
                        Text(
                            text = stringResource(CoreR.string.no_metadata_values),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    else ->
                        LazyVerticalGrid(
                            columns =
                                GridCellsAdaptiveWithMinColumns(
                                    minSize = NAMED_ITEM_MIN_WIDTH_DP.dp,
                                    minColumns = 2,
                                ),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = contentPadding,
                            horizontalArrangement =
                                Arrangement.spacedBy(MaterialTheme.spacings.default),
                            verticalArrangement =
                                Arrangement.spacedBy(MaterialTheme.spacings.default),
                        ) {
                            items(items = state.visibleItems, key = { it.id }) { item ->
                                NamedItemCard(
                                    item = item,
                                    onClick = { onItemClick(item) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                }
            }
        }
    }

    state.error?.let { error ->
        if (showErrorDialog) {
            ErrorDialog(exception = error, onDismissRequest = { showErrorDialog = false })
        }
    }
}

/** 类别 / 制片公司卡片：有封面用封面，没有则只显示名称。 */
@Composable
private fun NamedItemCard(
    item: FindroidNamedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
    ) {
        item.images.primary?.let { image ->
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacings.medium),
        )
    }
}

@PreviewScreenSizes
@Composable
private fun NamedItemListScreenLayoutPreview() {
    FindroidTheme {
        NamedItemListScreenLayout(
            facet = MetadataFacet.GENRE,
            state = NamedItemListState(items = dummyNamedItems),
            onItemClick = {},
            onBackClick = {},
            onAction = {},
        )
    }
}
