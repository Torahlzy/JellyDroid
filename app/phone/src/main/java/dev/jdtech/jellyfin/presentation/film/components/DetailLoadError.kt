package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.jdtech.jellyfin.presentation.components.ErrorDialog
import dev.jdtech.jellyfin.presentation.theme.spacings

/** 详情加载失败时提供错误详情与重试，已有内容仍可继续查看。 */
@Composable
fun DetailLoadError(error: Exception, onRetryClick: () -> Unit, modifier: Modifier = Modifier) {
    var showDetails by remember(error) { mutableStateOf(false) }
    ErrorCard(
        onShowStacktrace = { showDetails = true },
        onRetryClick = onRetryClick,
        modifier = modifier.padding(MaterialTheme.spacings.default),
    )
    if (showDetails) {
        ErrorDialog(exception = error, onDismissRequest = { showDetails = false })
    }
}
