package dev.jdtech.jellyfin.presentation.player.subtitle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.presentation.film.components.SubtitleDownloadDialog

/**
 * 播放页的「下载字幕」弹窗宿主：与详情页完全相同的一套 UI 与状态机，
 * 只是数据源换成本页的 [SubtitlePlaybackCoordinator]（它自己那份 controller）。
 */
@Composable
fun PlayerSubtitleDownloadDialogHost(coordinator: SubtitlePlaybackCoordinator) {
    val isShowing by coordinator.isDialogShowing.collectAsStateWithLifecycle()
    if (!isShowing) return
    val state by coordinator.downloadState.collectAsStateWithLifecycle()
    val keyword by coordinator.keyword.collectAsStateWithLifecycle()
    val proxy by coordinator.proxy.collectAsStateWithLifecycle()
    SubtitleDownloadDialog(
        defaultKeyword = keyword,
        proxy = proxy,
        stage = state.stage,
        candidates = state.candidates,
        searchFoundNothing = state.searchFoundNothing,
        onProxyChange = coordinator::updateProxy,
        onConfirm = coordinator::downloadSubtitle,
        onUpload = coordinator::uploadSubtitle,
        onCancel = coordinator::cancelDownload,
        onDismiss = coordinator::dismissDialog,
        readyCount = state.readyCount,
        onCompare = coordinator::prepareCompare,
    )
}
