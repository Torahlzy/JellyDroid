package dev.jdtech.jellyfin.presentation.film

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
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
import dev.jdtech.jellyfin.film.presentation.actorimage.ActorImageScrapeAction
import dev.jdtech.jellyfin.film.presentation.actorimage.ActorImageScrapeEvent
import dev.jdtech.jellyfin.film.presentation.actorimage.ActorImageScrapeViewModel
import dev.jdtech.jellyfin.film.presentation.movie.MovieAction
import dev.jdtech.jellyfin.film.presentation.movie.MovieEvent
import dev.jdtech.jellyfin.film.presentation.movie.MovieState
import dev.jdtech.jellyfin.film.presentation.movie.MovieViewModel
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.MetadataFacet
import dev.jdtech.jellyfin.presentation.film.components.ActorsRow
import dev.jdtech.jellyfin.presentation.film.components.CollapsibleText
import dev.jdtech.jellyfin.presentation.film.components.DeleteItemWithFilesDialog
import dev.jdtech.jellyfin.presentation.film.components.EditItemImagesDialog
import dev.jdtech.jellyfin.presentation.film.components.EditItemMetadataDialog
import dev.jdtech.jellyfin.presentation.film.components.ExtraInfoText
import dev.jdtech.jellyfin.presentation.film.components.FilePathText
import dev.jdtech.jellyfin.presentation.film.components.InfoText
import dev.jdtech.jellyfin.presentation.film.components.ItemButtonsBar
import dev.jdtech.jellyfin.presentation.film.components.ItemHeader
import dev.jdtech.jellyfin.presentation.film.components.ItemPlayButton
import dev.jdtech.jellyfin.presentation.film.components.ItemTopBar
import dev.jdtech.jellyfin.presentation.film.components.MoreMenuDialog
import dev.jdtech.jellyfin.presentation.film.components.MovieCommentsSection
import dev.jdtech.jellyfin.presentation.film.components.OverviewText
import dev.jdtech.jellyfin.presentation.film.components.PlaybackSourceDialog
import dev.jdtech.jellyfin.presentation.film.components.ScrapeActorImageDialog
import dev.jdtech.jellyfin.presentation.film.components.ScrapeKeywordDialog
import dev.jdtech.jellyfin.presentation.film.components.ScrapeProgressDialog
import dev.jdtech.jellyfin.presentation.film.components.ScraperUrlsDialog
import dev.jdtech.jellyfin.presentation.film.components.VideoMetadataBar
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.Motion
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.LocalNavAnimatedVisibilityScope
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode
import dev.jdtech.jellyfin.presentation.utils.heroSharedBounds
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun MovieScreen(
    movieId: UUID,
    navigateBack: () -> Unit,
    navigateHome: () -> Unit,
    navigateToPerson: (personId: UUID, personName: String) -> Unit,
    navigateToMetadataFacet: (facet: MetadataFacet, key: String) -> Unit,
    navigateToCommentSettings: () -> Unit,
    viewModel: MovieViewModel = hiltViewModel(),
    downloaderViewModel: DownloaderViewModel = hiltViewModel(),
    actorImageScrapeViewModel: ActorImageScrapeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val isOfflineMode = LocalOfflineMode.current

    val state by viewModel.state.collectAsStateWithLifecycle()
    val downloaderState by downloaderViewModel.state.collectAsStateWithLifecycle()
    // 演员头像抓取与人物列表页共用同一个 ViewModel
    val actorImageScrapeState by
        actorImageScrapeViewModel.state.collectAsStateWithLifecycle()

    // 「更多」菜单与它下面的确认弹窗共用同一个状态，保证同一时刻只开一个；
    // 用 rememberSaveable 让弹窗在旋转屏幕后仍保持打开（枚举可直接存入 Bundle）
    var moreDialog by rememberSaveable { mutableStateOf<MoreMenuDialogState?>(null) }

    // 「选择播放版本」弹窗：条目在服务器上存在多个来源时，播放前先让用户选一个
    var showPlaybackSourceDialog by rememberSaveable { mutableStateOf(false) }
    // 记住本次点击是「从头播放」还是「继续播放」，选完版本后照此启动播放器
    var playFromBeginningAfterSelect by rememberSaveable { mutableStateOf(false) }

    // nfo 抓取流程的两个弹窗：先输关键词，再显示进度；进度弹窗叠在编辑 nfo 弹窗之上
    var showScrapeKeywordDialog by rememberSaveable { mutableStateOf(false) }
    var showScrapeProgressDialog by rememberSaveable { mutableStateOf(false) }

    // 封面抓取的关键词弹窗；抓取结果直接显示在下方的「编辑封面」弹窗里，不再另开一个弹窗
    var showImageScrapeKeywordDialog by rememberSaveable { mutableStateOf(false) }

    // 离线模式连不上服务器，删除服务器信息必然失败，直接不显示「更多」按钮
    val onMoreClick: (() -> Unit)? =
        if (isOfflineMode) {
            null
        } else {
            { moreDialog = MoreMenuDialogState.MENU }
        }

    LaunchedEffect(true) { viewModel.loadMovie(movieId = movieId) }

    // 元数据 / 图片列表用 effect 加载而不是在点击时加载：moreDialog 走的是 rememberSaveable，
    // 进程重建后弹窗会被恢复成打开状态，靠 effect 重跑才能补上数据，不至于一直转圈
    LaunchedEffect(moreDialog) {
        when (moreDialog) {
            MoreMenuDialogState.EDIT_METADATA -> viewModel.loadItemMetadata()
            MoreMenuDialogState.EDIT_IMAGES -> viewModel.loadItemImages()
            else -> Unit
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

    // 改动服务器内容失败时只需告诉用户「哪一步失败 + 原因」，统一走这里避免重复
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
            is MovieEvent.ItemImagesUploaded ->
                Toast.makeText(
                        context,
                        context.getString(CoreR.string.item_images_uploaded),
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            is MovieEvent.MetadataUnchanged ->
                Toast.makeText(
                        context,
                        context.getString(CoreR.string.metadata_unchanged),
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
            is MovieEvent.MetadataTranslateFailed ->
                showFailureToast(CoreR.string.translate_failed, event.error)
            is MovieEvent.ItemImagesUploadFailed ->
                showFailureToast(CoreR.string.item_images_upload_failed, event.error)
            is MovieEvent.ItemDeleteFailed ->
                showFailureToast(CoreR.string.delete_item_failed, event.error)
        }
    }

    // 演员头像上传完成 / 失败的提示由共享 ViewModel 发出；成功后重载详情页让演员行的头像立即生效
    ObserveAsEvents(actorImageScrapeViewModel.events) { event ->
        when (event) {
            is ActorImageScrapeEvent.Uploaded -> {
                Toast.makeText(
                        context,
                        context.getString(CoreR.string.actor_images_uploaded),
                        Toast.LENGTH_SHORT,
                    )
                    .show()
                viewModel.loadMovie(movieId = movieId)
            }
            is ActorImageScrapeEvent.UploadFailed ->
                showFailureToast(CoreR.string.actor_images_upload_failed, event.error)
        }
    }

    MovieScreenLayout(
        movieId = movieId,
        state = state,
        downloaderState = downloaderState,
        onScrapeImageClick = { person ->
            actorImageScrapeViewModel.onAction(
                ActorImageScrapeAction.Scrape(
                    personId = person.id,
                    name = person.name,
                    hasAvatar = person.image.uri != null,
                )
            )
        },
        onLoginClick = navigateToCommentSettings,
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
                is MovieAction.NavigateToPerson ->
                    navigateToPerson(action.person.id, action.person.name)
                is MovieAction.NavigateToGenre -> navigateToMetadataFacet(MetadataFacet.GENRE, action.genre)
                is MovieAction.NavigateToTag -> navigateToMetadataFacet(MetadataFacet.TAG, action.tag)
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
                onEditImagesClick = { moreDialog = MoreMenuDialogState.EDIT_IMAGES },
                onEditMetadataClick = { moreDialog = MoreMenuDialogState.EDIT_METADATA },
                onDeleteItemClick = { moreDialog = MoreMenuDialogState.DELETE_ALL },
                onScrapeUrlsClick = { moreDialog = MoreMenuDialogState.SCRAPER_URLS },
                onDismiss = { moreDialog = null },
            )
        MoreMenuDialogState.EDIT_IMAGES ->
            EditItemImagesDialog(
                itemImages = state.itemImages,
                isLoadingImages = state.isLoadingItemImages,
                errorText =
                    state.itemImagesError?.let { error ->
                        stringResource(
                            CoreR.string.item_images_load_failed,
                            error.localizedMessage ?: stringResource(CoreR.string.unknown_error),
                        )
                    },
                scrapeSites = state.imageScrapeSites,
                scrapeResults = state.imageScrapeResults,
                isScraping = state.isScrapingImages,
                isUploading = state.isUploadingImages,
                onUploadClick = { images ->
                    viewModel.onAction(MovieAction.UploadScrapedImages(images = images))
                },
                onCleanConfirm = {
                    // 清理后弹窗保持打开，用户能立刻看到服务器上已经没有图片
                    viewModel.onAction(MovieAction.DeleteItemImages(images = state.itemImages))
                },
                onScrapeClick = { showImageScrapeKeywordDialog = true },
                onDismiss = {
                    moreDialog = null
                    // 关掉弹窗就中断抓取并丢掉已抓到的图片（都只在内存里）
                    viewModel.onAction(MovieAction.CloseImageScrape)
                },
            )
        MoreMenuDialogState.EDIT_METADATA ->
            EditItemMetadataDialog(
                metadata = state.itemMetadata,
                scrapedMetadata = state.scrapedMetadata,
                // 抓到内容的站点：弹窗把它们列在标题下方，供用户在站点之间切换内容
                scrapedSites = state.scrapedSiteMetadata.map { it.site },
                selectedScrapeSite = state.selectedScrapeSite,
                fileName = state.fileName,
                isLoading = state.isLoadingItemMetadata,
                isTranslating = state.isTranslating,
                translateSettings = state.translateSettings,
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
                onSelectScrapeSource = { site ->
                    viewModel.onAction(MovieAction.SelectScrapeSource(site = site))
                },
                onScrapeClick = { showScrapeKeywordDialog = true },
                onTranslateSettingsChange = { settings ->
                    viewModel.onAction(MovieAction.UpdateTranslateSettings(settings = settings))
                },
                onTranslateExisting = { metadata, settings ->
                    viewModel.onAction(
                        MovieAction.TranslateMetadata(metadata = metadata, settings = settings)
                    )
                },
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
        MoreMenuDialogState.SCRAPER_URLS ->
            ScraperUrlsDialog(
                urls = state.scrapeSiteUrls,
                onConfirm = { urls ->
                    moreDialog = null
                    viewModel.onAction(MovieAction.SaveScrapeUrls(urls = urls))
                },
                onDismiss = { moreDialog = null },
            )
        null -> Unit
    }

    // 「获取头像」弹窗由共享 ViewModel 的 state 驱动（长按演员条目打开），关掉即中断抓取并丢掉内存里的头像
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
            title = stringResource(CoreR.string.scrape_keyword_title),
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
            title = stringResource(CoreR.string.scrape_progress_title),
            sites = state.metadataScrapeSites,
            results = state.metadataScrapeResults,
            isRunning = state.isScraping,
            isTranslating = state.isTranslating,
            failureSummary = stringResource(CoreR.string.scrape_all_failed),
            onCancel = {
                // 取消也按「部分成功」处理：有站点抓到内容时表单回填、弹窗随之自动关闭；
                // 一个都没抓到时弹窗留在界面上给出失败原因，由用户点「关闭」收尾
                viewModel.onAction(MovieAction.CancelScrape)
            },
            onDismiss = {
                showScrapeProgressDialog = false
                viewModel.onAction(MovieAction.DismissScrapeFailure)
            },
        )
    }

    if (showImageScrapeKeywordDialog) {
        ScrapeKeywordDialog(
            title = stringResource(CoreR.string.scrape_images_keyword_title),
            defaultKeyword = state.defaultScrapeKeyword,
            proxy = state.scrapeProxy,
            onProxyChange = { address ->
                viewModel.onAction(MovieAction.UpdateScrapeProxy(address = address))
            },
            onConfirm = { keyword ->
                showImageScrapeKeywordDialog = false
                // 结果直接填进下面那个「编辑封面」弹窗，抓完自动显示
                viewModel.onAction(MovieAction.ScrapeItemImages(keyword = keyword))
            },
            onDismiss = { showImageScrapeKeywordDialog = false },
        )
    }
}

/** 「更多」菜单的弹窗状态：同一时刻只会打开其中一个。 */
private enum class MoreMenuDialogState {
    /** 操作入口的菜单。 */
    MENU,

    /** 编辑封面的图片列表（服务器图片缩略图 + 抓取结果）。 */
    EDIT_IMAGES,

    /** 编辑 nfo 的表单弹窗。 */
    EDIT_METADATA,

    /** 删除全部（含视频文件）的确认弹窗。 */
    DELETE_ALL,

    /** 各抓取站点自定义网址的输入弹窗。 */
    SCRAPER_URLS,
}

@Composable
private fun MovieScreenLayout(
    movieId: UUID,
    state: MovieState,
    downloaderState: DownloaderState,
    onScrapeImageClick: (person: FindroidItemPerson) -> Unit,
    onLoginClick: () -> Unit,
    onAction: (MovieAction) -> Unit,
    onMoreClick: (() -> Unit)?,
    onDownloaderAction: (DownloaderAction) -> Unit,
) {
    val isOfflineMode = LocalOfflineMode.current
    val safePadding = rememberSafePadding()
    val density = LocalDensity.current

    val paddingStart = safePadding.start + MaterialTheme.spacings.default
    val paddingEnd = safePadding.end + MaterialTheme.spacings.default
    val paddingBottom = safePadding.bottom + MaterialTheme.spacings.default

    val scrollState = rememberScrollState()

    // 封面高度（不含状态栏 padding），驱动悬浮播放按钮的展开 / 收起与飞行位置
    var headerHeightPx by remember { mutableIntStateOf(0) }
    // 屏幕高度：判断头图是否超过半屏，并作为上滑缩小的终点
    var screenHeightPx by remember { mutableIntStateOf(0) }

    val navScope = LocalNavAnimatedVisibilityScope.current

    // 内容区块在进场转场接近尾声（提前约 100ms）就开始组合并依次入场：
    // 完全等转场结束会显得拖沓，入场的 stagger 收尾与 morph 落点正好衔接
    var contentAppeared by rememberSaveable { mutableStateOf(false) }
    // 入场动画是否开始播：与 contentAppeared（控制内容组合的时机）分开，保证区块先以 alpha=0 组合再起播
    var entranceStarted by remember { mutableStateOf(contentAppeared) }
    LaunchedEffect(state.movie, navScope) {
        if (state.movie == null || contentAppeared) return@LaunchedEffect
        val transition = navScope?.transition
        if (transition == null) {
            contentAppeared = true
            return@LaunchedEffect
        }
        coroutineScope {
            // 「转场结束」与「提前量到点」谁先到谁触发：
            // 数据到得早时按提前量抢跑，数据到得晚（转场已结束）时 snapshotFlow 立即放行
            launch {
                snapshotFlow { transition.isRunning }.first { !it }
                contentAppeared = true
            }
            launch {
                delay((Motion.DurationLong1 - ENTRANCE_HEAD_START_MILLIS).toLong())
                contentAppeared = true
            }
        }
    }
    LaunchedEffect(contentAppeared) {
        if (contentAppeared && !entranceStarted) {
            withFrameNanos { }
            entranceStarted = true
        }
    }

    // 加载转圈延迟出现：数据通常几百毫秒内就到，立即显示会「一闪而过」；
    // 超过阈值还没到才显示，快路径下加载期间是干净的空页面（正好被 morph 覆盖）
    var showLoadingSpinner by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADING_SPINNER_DELAY_MILLIS.toLong())
        // state 是属性委托，此处读到的是延迟结束后的最新值（不是首帧快照）：
        // 数据已到就不显示转圈
        showLoadingSpinner = state.movie == null
    }

    // 头图吸顶仅在短评区块存在时启用：吸顶的视觉终点是「头图固定 + 短评占满下方一屏」，
    // 没有短评时页面滚到底会只剩头图悬在顶部、下方一片空白，观感不对
    val isHeaderPinned = state.commentsEnabled

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { screenHeightPx = it.height }) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            state.movie?.let { movie ->
                // 头部：数据到位后立即渲染，承接共享元素落点；
                // 数据到达前没有落点，morph 自动退化为普通转场。
                // 吸顶层整体（垫片 + 头图）随滚动反向平移；垫片在共享元素之外，
                // 否则 morph 会把它当共享内容一起缩放，转场期间顶部会露出色块
                val emptyClickInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier =
                        Modifier.zIndex(1f)
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    // 吸顶：放置时反向平移抵消页面滚动。用布局放置而非 graphicsLayer——
                                    // graphicsLayer 是纯渲染变换，不进入布局坐标：共享元素 morph 的目标
                                    // 会算到滚上去的旧位置、结束时跳回吸顶位；放置偏移则坐标随动，
                                    // morph 目标与命中测试（点击拦截区域）都落在吸顶位置。
                                    // 状态读取放在 placement 阶段，滚动时只重放置、不重新测量
                                    val y = if (isHeaderPinned) scrollState.value else 0
                                    placeable.place(0, y)
                                }
                            }
                ) {
                    // 未缩放高度占位：头图缩小是布局级的，面板会随之变矮；
                    // 占位保持吸顶层总高不变，避免滚动范围在滚动中变化引发跳动
                    Spacer(
                        modifier =
                            Modifier.height(
                                with(density) { safePadding.top + headerHeightPx.toDp() }
                            )
                    )
                    // 全宽遮罩带：高度随头图缩放同步收缩，盖住缩小头图左右两侧透出的滚动内容；
                    // 同时拦截带内点击，避免穿透到被遮住的元素。带高在测量阶段读取滚动状态，
                    // 滚动时只重测量这个空盒子，不触发重组
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .layout { measurable, constraints ->
                                    val scale =
                                        headerScaleForScroll(
                                            scrollPx = scrollState.value,
                                            headerHeightPx = headerHeightPx,
                                            screenHeightPx = screenHeightPx,
                                        )
                                    val bandHeight =
                                        (safePadding.top.toPx() + headerHeightPx * scale)
                                            .roundToInt()
                                    val placeable =
                                        measurable.measure(
                                            constraints.copy(
                                                minHeight = bandHeight,
                                                maxHeight = bandHeight,
                                            )
                                        )
                                    layout(placeable.width, placeable.height) {
                                        placeable.place(0, 0)
                                    }
                                }
                                .background(MaterialTheme.colorScheme.background)
                                .clickable(
                                    interactionSource = emptyClickInteraction,
                                    indication = null,
                                ) {
                                    // 空点击：吸顶后头图与遮罩盖住下方内容，拦截手势防止点击穿透
                                }
                    )
                    // 面板（状态栏垫片 + 头图）：头图缩小由下方 layout 完成，面板负责把缩小后的头图水平居中
                    Box(contentAlignment = Alignment.TopCenter) {
                        // 状态栏底色垫片：挡住从头图上方透出的滑动内容
                        Box(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .height(safePadding.top)
                                    .background(MaterialTheme.colorScheme.background)
                        )
                        Box(
                            modifier =
                                Modifier.padding(top = safePadding.top)
                                    .heroSharedBounds(key = "movie-hero-$movieId")
                                    // 头图缩小走布局（上报缩放后尺寸 + 图层缩放内容），且放在
                                    // heroSharedBounds 内侧：共享元素 morph 取的是布局边界，
                                    // 这样返回动画从当前缩小后的尺寸起飞，不会从最大尺寸跳变。
                                    // 滚动状态在测量阶段读取，滚动时只重测量头图、不触发重组
                                    .layout { measurable, constraints ->
                                        val scale =
                                            headerScaleForScroll(
                                                scrollPx = scrollState.value,
                                                headerHeightPx = headerHeightPx,
                                                screenHeightPx = screenHeightPx,
                                            )
                                        val placeable = measurable.measure(constraints)
                                        if (scale == 1f) {
                                            layout(placeable.width, placeable.height) {
                                                placeable.place(0, 0)
                                            }
                                        } else {
                                            layout(
                                                (placeable.width * scale).roundToInt(),
                                                (placeable.height * scale).roundToInt(),
                                            ) {
                                                placeable.placeWithLayer(0, 0) {
                                                    scaleX = scale
                                                    scaleY = scale
                                                    // 原点左上：内容恰好填满上报的缩放后尺寸
                                                    transformOrigin = TransformOrigin(0f, 0f)
                                                }
                                            }
                                        }
                                    }
                        ) {
                            ItemHeader(
                                item = movie,
                                scrollState = scrollState,
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .onSizeChanged { headerHeightPx = it.height },
                                // 标题已移到图片下方，不再需要底部渐隐，避免图片下方出现空白
                                fadeToBackground = false,
                                // 按图片实际宽高比自适应高度，完整显示背景图、不裁切左右
                                fitBackdrop = true,
                                // 吸顶时框架已固定，图内视差会让图片漂移露出缺口，须禁用
                                parallaxRate = if (isHeaderPinned) 0 else 2,
                            )
                        }
                    }
                }
            }
            state.movie?.takeIf { contentAppeared }?.let { movie ->
                Column(modifier = Modifier.padding(start = paddingStart, end = paddingEnd)) {
                    Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
                    // 标题与文件路径：依次入场的第 1 组
                    AppearingSection(index = 0, visible = entranceStarted) {
                        // 标题：默认收起 3 行，超出时可点击展开 / 收起
                        CollapsibleText(
                            text = movie.name,
                            collapsedMaxLines = 3,
                            style = MaterialTheme.typography.headlineMedium,
                            fontSize =
                                (MaterialTheme.typography.headlineMedium.fontSize.value - 2).sp,
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
                    }
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    // 年份 / 时长 / 分级 / 视频信息行：依次入场的第 2 组
                    AppearingSection(index = 1, visible = entranceStarted) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(MaterialTheme.spacings.small),
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
                                Text(
                                    text = officialRating,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
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
                    }
                    // 操作按钮行：依次入场的第 3 组
                    AppearingSection(index = 2, visible = entranceStarted) {
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
                    }
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    // 简介 / 类型 / 标签：依次入场的第 4 组
                    AppearingSection(index = 3, visible = entranceStarted) {
                        if (state.displayExtraInfo && state.videoMetadata != null) {
                            ExtraInfoText(videoMetadata = state.videoMetadata!!)
                            Spacer(Modifier.height(MaterialTheme.spacings.medium))
                        }
                        OverviewText(text = movie.overview, maxCollapsedLines = 3)
                        Spacer(Modifier.height(MaterialTheme.spacings.medium))
                        InfoText(
                            genres = movie.genres,
                            tags = movie.tags,
                            director = state.director,
                            writers = state.writers,
                            // 离线模式下筛选结果页不可用，类型 / 标签保持纯文本
                            onGenreClick =
                                if (isOfflineMode) null
                                else { genre -> onAction(MovieAction.NavigateToGenre(genre)) },
                            onTagClick =
                                if (isOfflineMode) null
                                else { tag -> onAction(MovieAction.NavigateToTag(tag)) },
                        )
                        Spacer(Modifier.height(MaterialTheme.spacings.medium))
                    }
                }
                // 演员行：依次入场的第 5 组
                AppearingSection(index = 4, visible = entranceStarted) {
                    if (state.actors.isNotEmpty()) {
                        ActorsRow(
                            actors = state.actors,
                            onActorClick = { person ->
                                onAction(MovieAction.NavigateToPerson(person))
                            },
                            contentPadding = PaddingValues(start = paddingStart, end = paddingEnd),
                            // 抓取头像要联网也要写服务器，离线模式不提供长按菜单
                            onScrapeImageClick =
                                if (isOfflineMode) null
                                else { person -> onScrapeImageClick(person) },
                        )
                    }
                }
                // 外网短评：依次入场的第 6 组，关闭「开启外网评论」后不显示这个区块
                AppearingSection(index = 5, visible = entranceStarted) {
                    if (state.commentsEnabled) {
                        MovieCommentsSection(
                            comments = state.comments,
                            keyword = state.commentKeyword,
                            isLoading = state.isLoadingComments,
                            error = state.commentsError,
                            onRetry = { onAction(MovieAction.ReloadComments) },
                            isJavDbLoggedIn = state.isJavDbLoggedIn,
                            onLoginClick = onLoginClick,
                            contentPadding = PaddingValues(start = paddingStart, end = paddingEnd),
                        )
                    }
                }
                Spacer(Modifier.height(paddingBottom))
            }
        }

        // 加载中：居中转圈（延迟出现）；内容区块仍等进场转场结束再组合，避免与 morph 抢帧
        if (state.movie == null && showLoadingSpinner) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // 顶栏：与文字区块同一时机淡入（entranceStarted，见 contentAppeared 处注释），
        // 转场期间不显示，避免随页面级 fadeIn 提前出现
        AnimatedVisibility(
            visible = entranceStarted,
            enter =
                fadeIn(
                    tween(
                        durationMillis = Motion.DurationShort3,
                        easing = Motion.EasingEmphasizedDecelerate,
                    )
                ),
        ) {
            ItemTopBar(
                hasBackButton = true,
                hasHomeButton = true,
                onBackClick = { onAction(MovieAction.OnBackClick) },
                onHomeClick = { onAction(MovieAction.OnHomeClick) },
            )
        }

        // 封面上的悬浮播放按钮：跟随封面滚动，封面滑出超过一半后收起并飞到顶栏「回到首页」按钮后方。
        // 与文字区块同一时机淡入（entranceStarted），仅带一小段延迟排在顶栏之后，保持 stagger 节奏
        AnimatedVisibility(
            visible = entranceStarted,
            enter =
                fadeIn(
                    tween(
                        durationMillis = Motion.DurationShort3,
                        delayMillis = APPEAR_STEP_MILLIS * 2,
                        easing = Motion.EasingEmphasizedDecelerate,
                    )
                ),
            exit =
                fadeOut(
                    tween(
                        durationMillis = Motion.DurationShort4,
                        easing = Motion.EasingEmphasizedAccelerate,
                    )
                ),
        ) {
            ItemPlayButton(
                scrollState = scrollState,
                headerHeightPx = headerHeightPx,
                safePadding = safePadding,
                isHeaderPinned = isHeaderPinned,
                onClick = { onAction(MovieAction.Play(startFromBeginning = false)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// 依次入场的节奏：相邻区块的延迟与整体位移量（属于动画量而非布局间距，故不用 MaterialTheme.spacings）
private const val APPEAR_STEP_MILLIS = 25
private val APPEAR_SLIDE_DISTANCE = 12.dp

// 入场相对转场结束的提前量：morph 全程约 450ms（Motion.DurationLong1），
// 提前 100ms 开始入场，让 stagger 收尾与 morph 落点衔接、不显拖沓
private const val ENTRANCE_HEAD_START_MILLIS = 100

// 加载转圈的延迟出现阈值：数据在此之前到达就完全不显示 loading
private const val LOADING_SPINNER_DELAY_MILLIS = 400

// 头图允许占用的最大屏幕高度比例：横屏 / 平板上宽幅头图会铺满整屏，上滑时缩小到该比例为止
private const val HEADER_MAX_HEIGHT_FRACTION_OF_SCREEN = 2f / 5f

/**
 * 头图随上滑的等比缩放比例。
 *
 * 头图高度不超过 [HEADER_MAX_HEIGHT_FRACTION_OF_SCREEN] 屏时恒为 1（竖屏手机基本落在这里，行为不变）；
 * 超过时（横屏 / 平板上宽幅头图会占满整屏），从 1 线性缩小到
 * 「限高 / 头图高」，正好在滚过头图与限高的差值距离时缩到限高并停住。
 */
private fun headerScaleForScroll(
    scrollPx: Int,
    headerHeightPx: Int,
    screenHeightPx: Int,
): Float {
    if (headerHeightPx <= 0 || screenHeightPx <= 0) return 1f
    val maxHeaderHeight = screenHeightPx * HEADER_MAX_HEIGHT_FRACTION_OF_SCREEN
    if (headerHeightPx <= maxHeaderHeight) return 1f
    val shrinkScrollDistance = headerHeightPx - maxHeaderHeight
    val progress = (scrollPx / shrinkScrollDistance).coerceIn(0f, 1f)
    return lerp(1f, maxHeaderHeight / headerHeightPx, progress)
}

/**
 * 详情页内容区块的依次入场：淡入 + 轻微上移。
 *
 * [index] 越大延迟越久，形成自上而下的 stagger 节奏；时长与缓动取 M3 token。
 */
@Composable
private fun AppearingSection(
    index: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress by
        animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec =
                tween(
                    durationMillis = Motion.DurationShort3,
                    delayMillis = index * APPEAR_STEP_MILLIS,
                    easing = Motion.EasingEmphasizedDecelerate,
                ),
            label = "movie-section-appear-$index",
        )
    // 用 Column 而不是 Box：一组内的多个文字块（标题 / 原名 / 文件路径等）是纵向排列的，
    // Box 会把它们叠在同一位置；Column 同时保持与原先 Column 直接子级一致的宽度约束
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = progress
                    translationY = (1f - progress) * APPEAR_SLIDE_DISTANCE.toPx()
                }
    ) {
        content()
    }
}

@PreviewScreenSizes
@Composable
private fun EpisodeScreenLayoutPreview() {
    FindroidTheme {
        MovieScreenLayout(
            movieId = dummyMovie.id,
            state =
                MovieState(
                    movie = dummyMovie,
                    videoMetadata = dummyVideoMetadata,
                    // 预览用：详情页的文件路径与「选择版本」弹窗都由此列表派生
                    playbackSources = dummyMovie.sources,
                ),
            downloaderState = DownloaderState(),
            onScrapeImageClick = {},
            onLoginClick = {},
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
