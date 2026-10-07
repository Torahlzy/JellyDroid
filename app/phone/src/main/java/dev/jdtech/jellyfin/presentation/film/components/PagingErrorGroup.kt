package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import dev.jdtech.jellyfin.presentation.components.ErrorDialog

/**
 * 分页列表的错误汇总：把 refresh / prepend / append 三个方向的错误统一渲染为一张可重试的错误卡片。
 *
 * 媒体库与按元数据筛选两类列表共用，避免各自重复实现同一套错误处理。
 */
@Composable
fun PagingErrorGroup(
    loadStates: CombinedLoadStates,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showErrorDialog by rememberSaveable { mutableStateOf(false) }

    val loadStateError =
        when {
            loadStates.refresh is LoadState.Error -> {
                loadStates.refresh as LoadState.Error
            }
            loadStates.prepend is LoadState.Error -> {
                loadStates.prepend as LoadState.Error
            }
            loadStates.append is LoadState.Error -> {
                loadStates.append as LoadState.Error
            }
            else -> null
        }

    loadStateError?.let {
        ErrorCard(
            onShowStacktrace = { showErrorDialog = true },
            onRetryClick = onRefresh,
            modifier = modifier,
        )
        if (showErrorDialog) {
            ErrorDialog(exception = it.error, onDismissRequest = { showErrorDialog = false })
        }
    }
}
