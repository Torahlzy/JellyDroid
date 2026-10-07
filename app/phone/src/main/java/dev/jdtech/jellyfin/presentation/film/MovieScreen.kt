package dev.jdtech.jellyfin.presentation.film

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.PlayerActivity
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderAction
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderEvent
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderViewModel
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.core.presentation.dummy.dummyVideoMetadata
import dev.jdtech.jellyfin.film.presentation.movie.MovieAction
import dev.jdtech.jellyfin.film.presentation.movie.MovieEvent
import dev.jdtech.jellyfin.film.presentation.movie.MovieState
import dev.jdtech.jellyfin.film.presentation.movie.MovieViewModel
import dev.jdtech.jellyfin.presentation.film.components.ActorsRow
import dev.jdtech.jellyfin.presentation.film.components.CollapsibleText
import dev.jdtech.jellyfin.presentation.film.components.DeleteItemImagesDialog
import dev.jdtech.jellyfin.presentation.film.components.DeleteItemWithFilesDialog
import dev.jdtech.jellyfin.presentation.film.components.EditItemMetadataDialog
import dev.jdtech.jellyfin.presentation.film.components.ExtraInfoText
import dev.jdtech.jellyfin.presentation.film.components.FilePathText
import dev.jdtech.jellyfin.presentation.film.components.InfoText
import dev.jdtech.jellyfin.presentation.film.components.ItemButtonsBar
import dev.jdtech.jellyfin.presentation.film.components.ItemHeader
import dev.jdtech.jellyfin.presentation.film.components.ItemTopBar
import dev.jdtech.jellyfin.presentation.film.components.MoreMenuDialog
import dev.jdtech.jellyfin.presentation.film.components.OverviewText
import dev.jdtech.jellyfin.presentation.film.components.PlaybackSourceDialog
import dev.jdtech.jellyfin.presentation.film.components.ScrapeKeywordDialog
import dev.jdtech.jellyfin.presentation.film.components.ScrapeProgressDialog
import dev.jdtech.jellyfin.presentation.film.components.VideoMetadataBar
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun MovieScreen(
    movieId: UUID,
    navigateBack: () -> Unit,
    navigateHome: () -> Unit,
    navigateToPerson: (personId: UUID) -> Unit,
    viewModel: MovieViewModel = hiltViewModel(),
    downloaderViewModel: DownloaderViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val isOfflineMode = LocalOfflineMode.current

    val state by viewModel.state.collectAsStateWithLifecycle()
    val downloaderState by downloaderViewModel.state.collectAsStateWithLifecycle()

    // 「更多」菜单与它下面的确认弹窗共用同一个状态，保证同一时刻只开一个；
    // 用 rememberSaveable 让弹窗在旋转屏幕后仍保持打开（枚举可直接存入 Bundle）
    var moreDialog by rememberSaveable { mutableStateOf<MoreMenuDialogState?>(null) }

    // 「选择播放版本」弹窗：条目在服务器上存在多个来源时，播放前先让用户选一个
    var showPlaybackSourceDialog by rememberSaveable { mutableStateOf(false) }
    // 记住本次点击是「从头播放」还是「继续播放」，选完版本后照此启动播放器
    var playFromBeginningAfterSelect by rememberSaveable { mutableStateOf(false) }

    // 抓取流程的两个弹窗：先输关键词，再显示进度；进度弹窗叠在编辑弹窗之上
    var showScrapeKeywordDialog by rememberSaveable { mutableStateOf(false) }
    var showScrapeProgressDialog by rememberSaveable { mutableStateOf(false) }

    // 离线模式连不上服务器，删除服务器信息必然失败，直接不显示「更多」按钮
    val onMoreClick: (() -> Unit)? =
        if (isOfflineMode) {
            null
        } else {
            { moreDialog = MoreMenuDialogState.MENU }
        }

    LaunchedEffect(true) { viewModel.loadMovie(movieId = movieId) }

    // 元数据用 effect 加载而不是在点击时加载：moreDialog 走的是 rememberSaveable，
    // 进程重建后弹窗会被恢复成打开状态，靠 effect 重跑才能补上表单数据，不至于一直转圈
    LaunchedEffect(moreDialog) {
        if (moreDialog == MoreMenuDialogState.EDIT_METADATA) {
            viewModel.loadItemMetadata()
        }
    }

    // 抓到内容后表单已被覆盖，进度弹窗自动消失，直接用更新后的表单继续编辑；
    // 抓取失败时 scrapedMetadata 保持为 null，弹窗留在界面上让用户看清失败原因
    LaunchedEffect(state.scrapedMetadata) {
        if (state.scrapedMetadata != null) {
            showScrapeProgressDialog = false
        }
    }

    LaunchedEffect(state.movie) { state.movie?.let { movie -> downloaderViewModel.update(movie) } }

    ObserveAsEvents(downloaderViewModel.events) { event ->
        when (event) {
            is DownloaderEvent.Successful -> {
                viewModel.loadMovie(movieId = movieId)
            }
            is DownloaderEvent.Deleted -> {
                if (isOfflineMode) {
                    navigateBack()
                } else {
                    viewModel.loadMovie(movieId = movieId)
                }
            }
        }
    }

    // 三个删除操作失败时只需告诉用户「哪一步失败 + 原因」，统一走这里避免重复
    val showFailureToast: (Int, Exception) -> Unit = { messageResId, error ->
        Toast.makeText(
                context,
                context.getString(
                    messageResId,
                    error.localizedMessage ?: context.getString(CoreR.string.unknown_error),
                ),
                Toast.LENGTH_LONG,
            )
            .show()
    }

    // 删除服务器文件没有可直接观察的界面状态，用一次性事件把执行结果反馈给用户
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is MovieEvent.ItemImagesDeleted ->
                Toast.makeText(
                        context,
                        context.getString(CoreR.string.item_images_deleted),
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            is MovieEvent.MetadataUpdated ->
                Toast.makeText(
                        context,
                        context.getString(CoreR.string.metadata_updated),
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            is MovieEvent.ItemDeleted -> {
                Toast.makeText(
                        context,
                        context.getString(CoreR.string.item_deleted),
                        Toast.LENGTH_SHORT,
                    )
                    .show()
                // 条目已从服务器删掉，本页没有可显示的内容了
                navigateBack()
            }
            is MovieEvent.ItemImagesDeleteFailed ->
                showFailureToast(CoreR.string.delete_item_images_failed, event.error)
            is MovieEvent.MetadataUpdateFailed ->
                showFailureToast(CoreR.string.update_metadata_failed, event.error)
            is MovieEvent.ItemDeleteFailed ->
                showFailureToast(CoreR.string.delete_item_failed, event.error)
        }
    }

    MovieScreenLayout(
        state = state,
        downloaderState = downloaderState,
        onAction = { action ->
            when (action) {
                is MovieAction.Play -> {
                    // 服务器把同一影片识别成多个版本时先让用户选；只有一个来源就直接开播，不多一步
                    if (state.playbackSources.size > 1) {
                        playFromBeginningAfterSelect = action.startFromBeginning
                        showPlaybackSourceDialog = true
                    } else {
                        startPlayer(
                            context = context,
                            itemId = movieId,
                            itemKind = BaseItemKind.MOVIE,
                            startFromBeginning = action.startFromBeginning,
                            mediaSourceIndex = null,
                        )
                    }
                }
                is MovieAction.PlayTrailer -> {
                    try {
                        uriHandler.openUri(action.trailer)
                    } catch (e: IllegalArgumentException) {
                        Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
                    }
                }
                is MovieAction.OnBackClick -> navigateBack()
                is MovieAction.OnHomeClick -> navigateHome()
                is MovieAction.NavigateToPerson -> navigateToPerson(action.personId)
                else -> Unit
            }
            viewModel.onAction(action)
        },
        onMoreClick = onMoreClick,
        onDownloaderAction = { action -> downloaderViewModel.onAction(action) },
    )

    when (moreDialog) {
        MoreMenuDialogState.MENU ->
            MoreMenuDialog(
                onDeleteImagesClick = {
                    moreDialog = MoreMenuDialogState.DELETE_IMAGES
                    viewModel.loadItemImages()
                },
                onEditMetadataClick = { moreDialog = MoreMenuDialogState.EDIT_METADATA },
                onDeleteItemClick = { moreDialog = MoreMenuDialogState.DELETE_ALL },
                onDismiss = { moreDialog = null },
            )
        MoreMenuDialogState.DELETE_IMAGES ->
            DeleteItemImagesDialog(
                itemImages = state.itemImages,
                isLoadingImages = state.isLoadingItemImages,
                errorText =
                    state.itemImagesError?.let { error ->
                        stringResource(
                            CoreR.string.item_images_load_failed,
                            error.localizedMessage ?: stringResource(CoreR.string.unknown_error),
                        )
                    },
                onConfirm = { images ->
                    moreDialog = null
                    viewModel.onAction(MovieAction.DeleteItemImages(images = images))
                },
                onDismiss = { moreDialog = null },
            )
        MoreMenuDialogState.EDIT_METADATA ->
            EditItemMetadataDialog(
                metadata = state.itemMetadata,
                scrapedMetadata = state.scrapedMetadata,
                fileName = state.fileName,
                isLoading = state.isLoadingItemMetadata,
                errorText =
                    state.itemMetadataError?.let { error ->
                        stringResource(
                            CoreR.string.edit_metadata_load_failed,
                            error.localizedMessage ?: stringResource(CoreR.string.unknown_error),
                        )
                    },
                onConfirm = { metadata ->
                    moreDialog = null
                    viewModel.onAction(MovieAction.UpdateItemMetadata(metadata = metadata))
                },
                onScrapeClick = { showScrapeKeywordDialog = true },
                onDismiss = { moreDialog = null },
            )
        MoreMenuDialogState.DELETE_ALL ->
            DeleteItemWithFilesDialog(
                itemName = state.movie?.name,
                onConfirm = {
                    moreDialog = null
                    viewModel.onAction(MovieAction.DeleteItemWithFiles)
                },
                onDismiss = { moreDialog = null },
            )
        null -> Unit
    }

    if (showPlaybackSourceDialog) {
        PlaybackSourceDialog(
            sources = state.playbackSources,
            onSelect = { index ->
                showPlaybackSourceDialog = false
                startPlayer(
                    context = context,
                    itemId = movieId,
                    itemKind = BaseItemKind.MOVIE,
                    startFromBeginning = playFromBeginningAfterSelect,
                    mediaSourceIndex = index,
                )
            },
            onDismiss = { showPlaybackSourceDialog = false },
        )
    }

    if (showScrapeKeywordDialog) {
        ScrapeKeywordDialog(
            defaultKeyword = state.defaultScrapeKeyword,
            proxy = state.scrapeProxy,
            onProxyChange = { address ->
                viewModel.onAction(MovieAction.UpdateScrapeProxy(address = address))
            },
            onConfirm = { keyword ->
                showScrapeKeywordDialog = false
                showScrapeProgressDialog = true
                viewModel.onAction(MovieAction.ScrapeMetadata(keyword = keyword))
            },
            onDismiss = { showScrapeKeywordDialog = false },
        )
    }

    if (showScrapeProgressDialog) {
        ScrapeProgressDialog(
            steps = state.scrapeSteps,
            isRunning = state.isScraping,
            onCancel = {
                showScrapeProgressDialog = false
                viewModel.onAction(MovieAction.CancelScrape)
            },
            onDismiss = {
                showScrapeProgressDialog = false
                viewModel.onAction(MovieAction.DismissScrapeFailure)
            },
        )
    }
}

/** 「更多」菜单的弹窗状态：同一时刻只会打开其中一个。 */
private enum class MoreMenuDialogState {
    /** 三个操作入口的菜单。 */
    MENU,

    /** 删除封面的图片确认列表。 */
    DELETE_IMAGES,

    /** 编辑 nfo 的表单弹窗。 */
    EDIT_METADATA,

    /** 删除全部（含视频文件）的确认弹窗。 */
    DELETE_ALL,
}

@Composable
private fun MovieScreenLayout(
    state: MovieState,
    downloaderState: DownloaderState,
    onAction: (MovieAction) -> Unit,
    onMoreClick: (() -> Unit)?,
    onDownloaderAction: (DownloaderAction) -> Unit,
) {
    val safePadding = rememberSafePadding()

    val paddingStart = safePadding.start + MaterialTheme.spacings.default
    val paddingEnd = safePadding.end + MaterialTheme.spacings.default
    val paddingBottom = safePadding.bottom + MaterialTheme.spacings.default

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        state.movie?.let { movie ->
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                ItemHeader(
                    item = movie,
                    scrollState = scrollState,
                    // 头部图片上方留出状态栏高度
                    modifier = Modifier.padding(top = safePadding.top),
                    // 标题已移到图片下方，不再需要底部渐隐，避免图片下方出现空白
                    fadeToBackground = false,
                    // 按图片实际宽高比自适应高度，完整显示背景图、不裁切左右
                    fitBackdrop = true,
                )
                Column(modifier = Modifier.padding(start = paddingStart, end = paddingEnd)) {
                    Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
                    // 标题：默认收起 3 行，超出时可点击展开 / 收起
                    CollapsibleText(
                        text = movie.name,
                        collapsedMaxLines = 3,
                        style = MaterialTheme.typography.headlineMedium,
                        fontSize = (MaterialTheme.typography.headlineMedium.fontSize.value - 2).sp,
                    )
                    movie.originalTitle?.let { originalTitle ->
                        if (originalTitle != movie.name) {
                            Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
                            // 副标题（原名）：默认收起 2 行，超出时可点击展开 / 收起
                            CollapsibleText(
                                text = originalTitle,
                                collapsedMaxLines = 2,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    // 文件存放路径：已下载时先显示本地路径（文件夹图标），再显示服务器路径（云图标）
                    // 多个版本会各自占一行（在 State 里去重后拼接），每行默认只显示 1 行，超出时可点击展开 / 收起
                    state.localFilePath?.let { filePath ->
                        Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
                        FilePathText(path = filePath, isRemote = false)
                    }
                    state.remoteFilePath?.let { filePath ->
                        Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
                        FilePathText(path = filePath, isRemote = true)
                    }
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        movie.premiereDate?.let { premiereDate ->
                            Text(
                                text = premiereDate.year.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            text =
                                stringResource(
                                    CoreR.string.runtime_minutes,
                                    movie.runtimeTicks.div(600000000),
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        movie.officialRating?.let { officialRating ->
                            Text(text = officialRating, style = MaterialTheme.typography.bodyMedium)
                        }
                        movie.communityRating?.let { communityRating ->
                            Row(verticalAlignment = Alignment.Bottom) {
                                Icon(
                                    painter = painterResource(CoreR.drawable.ic_star),
                                    contentDescription = null,
                                    tint = Color("#F2C94C".toColorInt()),
                                )
                                Spacer(Modifier.width(MaterialTheme.spacings.extraSmall))
                                Text(
                                    text = "%.1f".format(communityRating),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    state.videoMetadata?.let { videoMetadata ->
                        VideoMetadataBar(videoMetadata)
                        Spacer(Modifier.height(MaterialTheme.spacings.small))
                    }
                    ItemButtonsBar(
                        item = movie,
                        downloaderState = downloaderState,
                        onPlayClick = { startFromBeginning ->
                            onAction(MovieAction.Play(startFromBeginning = startFromBeginning))
                        },
                        onMarkAsPlayedClick = {
                            when (movie.played) {
                                true -> onAction(MovieAction.UnmarkAsPlayed)
                                false -> onAction(MovieAction.MarkAsPlayed)
                            }
                        },
                        onMarkAsFavoriteClick = {
                            when (movie.favorite) {
                                true -> onAction(MovieAction.UnmarkAsFavorite)
                                false -> onAction(MovieAction.MarkAsFavorite)
                            }
                        },
                        onTrailerClick = { uri -> onAction(MovieAction.PlayTrailer(uri)) },
                        onDownloadClick = { storageIndex ->
                            onDownloaderAction(DownloaderAction.Download(movie, storageIndex))
                        },
                        onDownloadCancelClick = {
                            onDownloaderAction(DownloaderAction.CancelDownload(movie))
                        },
                        onDownloadDeleteClick = {
                            onDownloaderAction(DownloaderAction.DeleteDownload(movie))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        onMoreClick = onMoreClick,
                    )
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    if (state.displayExtraInfo && state.videoMetadata != null) {
                        ExtraInfoText(videoMetadata = state.videoMetadata!!)
                        Spacer(Modifier.height(MaterialTheme.spacings.medium))
                    }
                    OverviewText(text = movie.overview, maxCollapsedLines = 3)
                    Spacer(Modifier.height(MaterialTheme.spacings.medium))
                    InfoText(
                        genres = movie.genres,
                        director = state.director,
                        writers = state.writers,
                    )
                    Spacer(Modifier.height(MaterialTheme.spacings.medium))
                }
                if (state.actors.isNotEmpty()) {
                    ActorsRow(
                        actors = state.actors,
                        onActorClick = { personId ->
                            onAction(MovieAction.NavigateToPerson(personId))
                        },
                        contentPadding = PaddingValues(start = paddingStart, end = paddingEnd),
                    )
                }
                Spacer(Modifier.height(paddingBottom))
            }
        } ?: run { CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) }

        ItemTopBar(
            hasBackButton = true,
            hasHomeButton = true,
            onBackClick = { onAction(MovieAction.OnBackClick) },
            onHomeClick = { onAction(MovieAction.OnHomeClick) },
        )
    }
}

@PreviewScreenSizes
@Composable
private fun EpisodeScreenLayoutPreview() {
    FindroidTheme {
        MovieScreenLayout(
            state =
                MovieState(
                    movie = dummyMovie,
                    videoMetadata = dummyVideoMetadata,
                    // 预览用：详情页的文件路径与「选择版本」弹窗都由此列表派生
                    playbackSources = dummyMovie.sources,
                ),
            downloaderState = DownloaderState(),
            onAction = {},
            onMoreClick = {},
            onDownloaderAction = {},
        )
    }
}

/**
 * 启动播放器。
 *
 * [mediaSourceIndex] 指定播放条目下的哪个来源（多版本时由用户选择），
 * 传 null 则由播放器按默认规则挑选（已下载的本地来源优先，其次列表首个）。
 */
private fun startPlayer(
    context: Context,
    itemId: UUID,
    itemKind: BaseItemKind,
    startFromBeginning: Boolean,
    mediaSourceIndex: Int?,
) {
    val intent = Intent(context, PlayerActivity::class.java)
    intent.putExtra("itemId", itemId.toString())
    intent.putExtra("itemKind", itemKind.serialName)
    intent.putExtra("startFromBeginning", startFromBeginning)
    mediaSourceIndex?.let { intent.putExtra(PlayerActivity.EXTRA_MEDIA_SOURCE_INDEX, it) }
    context.startActivity(intent)
}
