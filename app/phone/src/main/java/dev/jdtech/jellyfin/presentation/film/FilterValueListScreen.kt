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
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.filtervalue.FilterValueListAction
import dev.jdtech.jellyfin.film.presentation.filtervalue.FilterValueListState
import dev.jdtech.jellyfin.film.presentation.filtervalue.FilterValueListViewModel
import dev.jdtech.jellyfin.models.MetadataFacet
import dev.jdtech.jellyfin.presentation.components.ErrorDialog
import dev.jdtech.jellyfin.presentation.film.components.ErrorCard
import dev.jdtech.jellyfin.presentation.film.components.ListSearchField
import dev.jdtech.jellyfin.presentation.film.components.titleRes
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

// 标签格的宽度下限（dp）：chip 长度差异较大，按可用宽度自适应列数
private val FILTER_VALUE_MIN_WIDTH = 120.dp

@Composable
fun FilterValueListScreen(
    facet: MetadataFacet,
    onValueClick: (String) -> Unit,
    navigateBack: () -> Unit,
    viewModel: FilterValueListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(facet) { viewModel.loadValues(facet) }

    FilterValueListScreenLayout(
        facet = facet,
        state = state,
        onValueClick = onValueClick,
        onBackClick = navigateBack,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterValueListScreenLayout(
    facet: MetadataFacet,
    state: FilterValueListState,
    onValueClick: (String) -> Unit,
    onBackClick: () -> Unit,
    onAction: (FilterValueListAction) -> Unit,
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
                onQueryChange = { onAction(FilterValueListAction.OnSearchQueryChange(it)) },
                placeholder = stringResource(CoreR.string.search_values_hint),
                modifier = Modifier.padding(horizontal = MaterialTheme.spacings.default),
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.values.isEmpty() ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    state.error != null ->
                        ErrorCard(
                            onShowStacktrace = { showErrorDialog = true },
                            onRetryClick = { onAction(FilterValueListAction.Retry) },
                            modifier = Modifier.fillMaxWidth().padding(contentPadding),
                        )
                    state.visibleValues.isEmpty() ->
                        Text(
                            text = stringResource(CoreR.string.no_metadata_values),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    else ->
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = FILTER_VALUE_MIN_WIDTH),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = contentPadding,
                            horizontalArrangement =
                                Arrangement.spacedBy(MaterialTheme.spacings.small),
                            verticalArrangement =
                                Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
                        ) {
                            items(items = state.visibleValues, key = { it }) { value ->
                                SuggestionChip(
                                    onClick = { onValueClick(value) },
                                    label = {
                                        Text(
                                            text = value,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
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

@PreviewScreenSizes
@Composable
private fun FilterValueListScreenLayoutPreview() {
    FindroidTheme {
        FilterValueListScreenLayout(
            facet = MetadataFacet.YEAR,
            state =
                FilterValueListState(
                    values = listOf("2026", "2025", "2024", "2023", "2022", "2021")
                ),
            onValueClick = {},
            onBackClick = {},
            onAction = {},
        )
    }
}
