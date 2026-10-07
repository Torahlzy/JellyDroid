package dev.jdtech.jellyfin.presentation.film

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyCollections
import dev.jdtech.jellyfin.film.presentation.media.MediaAction
import dev.jdtech.jellyfin.film.presentation.media.MediaState
import dev.jdtech.jellyfin.film.presentation.media.MediaViewModel
import dev.jdtech.jellyfin.film.presentation.search.SearchAction
import dev.jdtech.jellyfin.film.presentation.search.SearchState
import dev.jdtech.jellyfin.film.presentation.search.SearchViewModel
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.MetadataFacet
import dev.jdtech.jellyfin.presentation.components.ErrorDialog
import dev.jdtech.jellyfin.presentation.film.components.BANNER_ASPECT_RATIO
import dev.jdtech.jellyfin.presentation.film.components.Direction
import dev.jdtech.jellyfin.presentation.film.components.ErrorCard
import dev.jdtech.jellyfin.presentation.film.components.FavoritesCard
import dev.jdtech.jellyfin.presentation.film.components.FilmSearchBar
import dev.jdtech.jellyfin.presentation.film.components.ItemCard
import dev.jdtech.jellyfin.presentation.film.components.MediaBrowseSection
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding

// 媒体库卡片的列数：手机两列、折叠屏展开四列。
// 卡片是 16:9 的横图，列数翻倍后宽度减半、高度也减半，一屏能看到更多媒体库。
private const val PHONE_LIBRARY_COLUMNS = 2
private const val EXPANDED_LIBRARY_COLUMNS = 4

@Composable
fun MediaScreen(
    onItemClick: (FindroidItem) -> Unit,
    onFavoritesClick: () -> Unit,
    onFacetClick: (MetadataFacet) -> Unit,
    searchExpanded: Boolean,
    onSearchExpand: (Boolean) -> Unit,
    viewModel: MediaViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadData() }

    MediaScreenLayout(
        state = state,
        searchState = searchState,
        searchExpanded = searchExpanded,
        onSearchExpand = onSearchExpand,
        onFacetClick = onFacetClick,
        onAction = { action ->
            when (action) {
                is MediaAction.OnItemClick -> onItemClick(action.item)
                is MediaAction.OnFavoritesClick -> onFavoritesClick()
                else -> Unit
            }
            viewModel.onAction(action)
        },
        onSearchAction = { action ->
            when (action) {
                is SearchAction.OnItemClick -> onItemClick(action.item)
                else -> Unit
            }
            searchViewModel.onAction(action)
        },
    )
}

@Composable
private fun MediaScreenLayout(
    state: MediaState,
    searchState: SearchState,
    searchExpanded: Boolean,
    onSearchExpand: (Boolean) -> Unit,
    onFacetClick: (MetadataFacet) -> Unit,
    onAction: (MediaAction) -> Unit,
    onSearchAction: (SearchAction) -> Unit,
) {
    val safePadding = rememberSafePadding(handleStartInsets = false)

    val paddingStart = safePadding.start + MaterialTheme.spacings.default
    val paddingEnd = safePadding.end + MaterialTheme.spacings.default
    val paddingBottom = safePadding.bottom + MaterialTheme.spacings.default

    val contentPaddingTop by
        animateDpAsState(
            targetValue =
                if (state.error != null) {
                    safePadding.top + 144.dp
                } else {
                    safePadding.top + 88.dp
                },
            label = "content_padding",
        )

    var showErrorDialog by rememberSaveable { mutableStateOf(false) }

    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val libraryColumns =
        if (windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
            EXPANDED_LIBRARY_COLUMNS
        } else {
            PHONE_LIBRARY_COLUMNS
        }

    Box(modifier = Modifier.fillMaxSize()) {
        FilmSearchBar(
            state = searchState,
            expanded = searchExpanded,
            onExpand = onSearchExpand,
            onAction = onSearchAction,
            modifier = Modifier.fillMaxWidth(),
            paddingStart = paddingStart,
            paddingEnd = paddingEnd,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(libraryColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = paddingStart,
                    top = contentPaddingTop,
                    end = paddingEnd,
                    bottom = paddingBottom,
                ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        ) {
            items(state.libraries, key = { it.id }) { library ->
                ItemCard(
                    item = library,
                    direction = Direction.HORIZONTAL,
                    onClick = { onAction(MediaAction.OnItemClick(library)) },
                    modifier = Modifier.animateItem(),
                    // 媒体库封面沿用首页 banner 的宽高比
                    aspectRatio = BANNER_ASPECT_RATIO,
                    placeholderIconRes = library.placeholderIconRes(),
                )
            }
            // 「我喜欢」与浏览入口都排在媒体库列表之后，各自独占整行（折叠屏展开也不会被分列）
            item(span = { GridItemSpan(maxLineSpan) }) {
                FavoritesCard(onClick = { onAction(MediaAction.OnFavoritesClick) })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                MediaBrowseSection(onFacetClick = onFacetClick)
            }
        }
        if (state.error != null) {
            ErrorCard(
                onShowStacktrace = { showErrorDialog = true },
                onRetryClick = { onAction(MediaAction.OnRetryClick) },
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(
                            start = paddingStart,
                            top = safePadding.top + 80.dp,
                            end = paddingEnd,
                        ),
            )
            if (showErrorDialog) {
                ErrorDialog(
                    exception = state.error!!,
                    onDismissRequest = { showErrorDialog = false },
                )
            }
        }
    }
}

@PreviewScreenSizes
@Composable
private fun MediaScreenLayoutPreview() {
    FindroidTheme {
        MediaScreenLayout(
            state =
                MediaState(libraries = dummyCollections, error = Exception("Failed to load data")),
            searchState = SearchState(),
            searchExpanded = false,
            onSearchExpand = {},
            onFacetClick = {},
            onAction = {},
            onSearchAction = {},
        )
    }
}

/** 媒体库卡片无封面时的占位图标：合集库在服务器上没有封面，用通用图标代替，避免出现空白卡片。 */
@DrawableRes
private fun FindroidCollection.placeholderIconRes(): Int? =
    when (type) {
        CollectionType.BoxSets -> CoreR.drawable.ic_collection
        else -> null
    }
