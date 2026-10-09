package dev.jdtech.jellyfin.presentation.film

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import dev.jdtech.jellyfin.core.presentation.dummy.dummyPersonDetail
import dev.jdtech.jellyfin.film.presentation.actorimage.ActorImageScrapeAction
import dev.jdtech.jellyfin.film.presentation.actorimage.ActorImageScrapeEvent
import dev.jdtech.jellyfin.film.presentation.actorimage.ActorImageScrapeState
import dev.jdtech.jellyfin.film.presentation.actorimage.ActorImageScrapeViewModel
import dev.jdtech.jellyfin.film.presentation.personlist.PersonListAction
import dev.jdtech.jellyfin.film.presentation.personlist.PersonListState
import dev.jdtech.jellyfin.film.presentation.personlist.PersonListViewModel
import dev.jdtech.jellyfin.models.FindroidPerson
import dev.jdtech.jellyfin.models.MetadataFacet
import dev.jdtech.jellyfin.presentation.components.ErrorDialog
import dev.jdtech.jellyfin.presentation.film.components.ErrorCard
import dev.jdtech.jellyfin.presentation.film.components.ListSearchField
import dev.jdtech.jellyfin.presentation.film.components.ScrapeActorImageDialog
import dev.jdtech.jellyfin.presentation.film.components.titleRes
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.GridCellsAdaptiveWithMinColumns
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode
import dev.jdtech.jellyfin.utils.ObserveAsEvents

// 人物卡片的最小宽度与头像直径（dp）
private const val PERSON_MIN_WIDTH_DP = 110
private val PERSON_AVATAR_SIZE = 96.dp

@Composable
fun PersonListScreen(
    facet: MetadataFacet,
    onPersonClick: (FindroidPerson) -> Unit,
    navigateBack: () -> Unit,
    viewModel: PersonListViewModel = hiltViewModel(),
    actorImageScrapeViewModel: ActorImageScrapeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 演员头像抓取与电影详情页共用同一个 ViewModel
    val actorImageScrapeState by
        actorImageScrapeViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(facet) { viewModel.loadPersons(facet) }

    // 演员头像上传完成 / 失败的提示由共享 ViewModel 发出；成功后重新拉一遍列表，新头像才能显示出来
    ObserveAsEvents(actorImageScrapeViewModel.events) { event ->
        when (event) {
            is ActorImageScrapeEvent.Uploaded -> {
                Toast.makeText(
                        context,
                        context.getString(CoreR.string.actor_images_uploaded),
                        Toast.LENGTH_SHORT,
                    )
                    .show()
                // 复用 Retry 的重取逻辑刷新列表（limit 与搜索词保持不变）
                viewModel.onAction(PersonListAction.Retry)
            }
            is ActorImageScrapeEvent.UploadFailed ->
                Toast.makeText(
                        context,
                        context.getString(
                            CoreR.string.actor_images_upload_failed,
                            event.error.localizedMessage
                                ?: context.getString(CoreR.string.unknown_error),
                        ),
                        Toast.LENGTH_LONG,
                    )
                    .show()
        }
    }

    PersonListScreenLayout(
        facet = facet,
        state = state,
        actorImageScrapeState = actorImageScrapeState,
        onPersonClick = onPersonClick,
        onBackClick = navigateBack,
        onAction = viewModel::onAction,
        onScrapeImageClick = { person ->
            actorImageScrapeViewModel.onAction(
                ActorImageScrapeAction.Scrape(
                    personId = person.id,
                    name = person.name,
                    hasAvatar = person.images.primary != null,
                )
            )
        },
    )

    // 「获取头像」弹窗由共享 ViewModel 的 state 驱动（长按人物卡片打开），关掉即中断抓取并丢掉内存里的头像
    if (actorImageScrapeState.personId != null) {
        ScrapeActorImageDialog(
            actorName = actorImageScrapeState.personName,
            sites = actorImageScrapeState.sites,
            results = actorImageScrapeState.results,
            isUploading = actorImageScrapeState.isUploading,
            hasAvatar = actorImageScrapeState.hasAvatar,
            proxy = actorImageScrapeState.proxy,
            onProxyChange = { address ->
                actorImageScrapeViewModel.onAction(
                    ActorImageScrapeAction.UpdateProxy(address = address)
                )
            },
            onUploadClick = { image ->
                actorImageScrapeViewModel.onAction(ActorImageScrapeAction.Upload(image = image))
            },
            onDismiss = { actorImageScrapeViewModel.onAction(ActorImageScrapeAction.Close) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonListScreenLayout(
    facet: MetadataFacet,
    state: PersonListState,
    actorImageScrapeState: ActorImageScrapeState,
    onPersonClick: (FindroidPerson) -> Unit,
    onBackClick: () -> Unit,
    onAction: (PersonListAction) -> Unit,
    onScrapeImageClick: (person: FindroidPerson) -> Unit,
) {
    val contentPadding = PaddingValues(all = MaterialTheme.spacings.default)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val isOfflineMode = LocalOfflineMode.current

    // 长按获取头像只对演员维度开放：抓取走的是女优搜索，导演 / 编剧列表里基本没有可搜的人；
    // 抓取要联网、上传要写服务器，离线模式也不提供
    val allowScrapeImage = !isOfflineMode && facet == MetadataFacet.ACTOR

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
                onQueryChange = { onAction(PersonListAction.Search(it)) },
                placeholder = stringResource(CoreR.string.search_persons_hint),
                modifier = Modifier.padding(horizontal = MaterialTheme.spacings.default),
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.persons.isEmpty() ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    state.error != null ->
                        ErrorCard(
                            onShowStacktrace = { showErrorDialog = true },
                            onRetryClick = { onAction(PersonListAction.Retry) },
                            modifier = Modifier.fillMaxWidth().padding(contentPadding),
                        )
                    state.persons.isEmpty() ->
                        Text(
                            text = stringResource(CoreR.string.no_persons),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    else ->
                        LazyVerticalGrid(
                            columns =
                                GridCellsAdaptiveWithMinColumns(
                                    minSize = PERSON_MIN_WIDTH_DP.dp,
                                    minColumns = 3,
                                ),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = contentPadding,
                            horizontalArrangement =
                                Arrangement.spacedBy(MaterialTheme.spacings.small),
                            verticalArrangement =
                                Arrangement.spacedBy(MaterialTheme.spacings.default),
                        ) {
                            items(items = state.persons, key = { it.id }) { person ->
                                PersonGridCard(
                                    person = person,
                                    onClick = { onPersonClick(person) },
                                    onScrapeImageClick =
                                        if (allowScrapeImage) {
                                            { onScrapeImageClick(person) }
                                        } else null,
                                    modifier = Modifier.animateItem(),
                                )
                            }

                            // 服务端的人物接口没有偏移分页，只能按更大的 limit 重新取一页
                            if (state.canLoadMore) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        TextButton(
                                            onClick = { onAction(PersonListAction.OnLoadMore) }
                                        ) {
                                            Text(text = stringResource(CoreR.string.load_more))
                                        }
                                    }
                                }
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

/** 人物卡片：圆形头像 + 姓名，没有头像时用底色占位；[onScrapeImageClick] 非空时支持长按弹出「获取头像」菜单。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonGridCard(
    person: FindroidPerson,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onScrapeImageClick: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Column(
        modifier =
            modifier
                .fillMaxWidth()
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
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = person.images.primary,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier.size(PERSON_AVATAR_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
        )
        Spacer(Modifier.height(MaterialTheme.spacings.small))
        Text(
            text = person.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

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

@PreviewScreenSizes
@Composable
private fun PersonListScreenLayoutPreview() {
    FindroidTheme {
        PersonListScreenLayout(
            facet = MetadataFacet.ACTOR,
            state = PersonListState(persons = listOf(dummyPersonDetail), canLoadMore = true),
            actorImageScrapeState = ActorImageScrapeState(),
            onPersonClick = {},
            onBackClick = {},
            onAction = {},
            onScrapeImageClick = {},
        )
    }
}
