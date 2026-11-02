package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 首页加载失败时的整页提示。
 *
 * 原先失败只体现为头部的叹号图标与刷新按钮，用户看不出发生了什么；这里给出可读的原因说明和
 * 显式重试入口，详细堆栈仍保留在「查看详情」里供排查。
 */
@Composable
fun HomeLoadFailed(
    onRetryClick: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacings.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_alert_circle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Spacer(Modifier.height(MaterialTheme.spacings.medium))
        Text(
            text = stringResource(CoreR.string.home_load_failed_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(MaterialTheme.spacings.small))
        Text(
            text = stringResource(CoreR.string.home_load_failed_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(MaterialTheme.spacings.large))
        FilledTonalButton(onClick = onRetryClick) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(MaterialTheme.spacings.small))
            Text(text = stringResource(CoreR.string.retry))
        }

        TextButton(onClick = onShowDetails) {
            Text(text = stringResource(CoreR.string.error_details))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeLoadFailedPreview() {
    FindroidTheme { HomeLoadFailed(onRetryClick = {}, onShowDetails = {}) }
}
