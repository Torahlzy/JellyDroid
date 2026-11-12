package dev.jdtech.jellyfin.presentation.film

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovies
import dev.jdtech.jellyfin.film.presentation.collection.CollectionAction
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsAction
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsState
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsViewModel
import dev.jdtech.jellyfin.models.CollectionSection
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.film.components.CollectionGrid
import dev.jdtech.jellyfin.presentation.film.components.DetailLoadError
import dev.jdtech.jellyfin.presentation.film.components.DownloadExportDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding

/** M3 NavigationBar 的默认高度；底栏浮在内容之上，标签根页面需要让出这段高度。 */
private val NAVIGATION_BAR_HEIGHT = 80.dp

@Composable
fun DownloadsScreen(
    onItemClick: (item: FindroidItem) -> Unit,
    isTabRoot: Boolean = false,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val storagePermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onAction(
                if (granted) DownloadsAction.ExportSelected
                else DownloadsAction.StoragePermissionDenied
            )
        }

    LaunchedEffect(true) { viewModel.loadItems() }

    DownloadsScreenLayout(
        state = state,
        isTabRoot = isTabRoot,
        onAction = { action ->
            when (action) {
                is DownloadsAction.OnItemClick -> onItemClick(action.item)
                DownloadsAction.ExportSelected -> {
                    val needsStoragePermission =
                        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            ) != PackageManager.PERMISSION_GRANTED
                    if (needsStoragePermission) {
                        storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        viewModel.onAction(action)
                    }
                }
                else -> viewModel.onAction(action)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsScreenLayout(
    state: DownloadsState,
    isTabRoot: Boolean,
    onAction: (DownloadsAction) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // 作为标签根页面时底栏覆盖在列表之上，最后一行会被压住，额外让出底栏与手势区的高度
    val bottomContentPadding =
        rememberSafePadding().bottom +
            MaterialTheme.spacings.default +
            if (isTabRoot) NAVIGATION_BAR_HEIGHT else 0.dp

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(CoreR.string.title_download)) },
                actions = {
                    TextButton(onClick = { onAction(DownloadsAction.OpenExportDialog) }) {
                        Text(stringResource(CoreR.string.download_export_title))
                    }
                },
                windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (state.isExportBusy) {
                LinearProgressIndicator(
                    progress = { state.exportState.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.error?.let { error ->
                DetailLoadError(error, onRetryClick = { onAction(DownloadsAction.Retry) })
            }
            Box(modifier = Modifier.weight(1f)) {
                if (state.sections.isEmpty() && !state.isLoading && state.error == null) {
                    Text(
                        text = stringResource(CoreR.string.no_downloads),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                CollectionGrid(
                    sections = state.sections,
                    innerPadding = PaddingValues(),
                    onAction = { action ->
                        if (action is CollectionAction.OnItemClick) {
                            onAction(DownloadsAction.OnItemClick(action.item))
                        }
                    },
                    bottomContentPadding = bottomContentPadding,
                )
            }
        }
    }

    if (state.showExportDialog) DownloadExportDialog(state, onAction)
}

@PreviewScreenSizes
@Composable
private fun DownloadsScreenLayoutPreview() {
    FindroidTheme {
        DownloadsScreenLayout(
            state =
                DownloadsState(
                    sections =
                        listOf(
                            CollectionSection(
                                id = 0,
                                name = UiText.StringResource(CoreR.string.movies_label),
                                items = dummyMovies,
                            )
                        )
                ),
            isTabRoot = true,
            onAction = {},
        )
    }
}
