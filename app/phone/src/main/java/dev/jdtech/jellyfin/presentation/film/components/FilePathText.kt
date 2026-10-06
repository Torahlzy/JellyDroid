package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 文件存放路径：默认只显示 1 行，内容被截断时点击可在展开 / 收起之间切换，并在末尾显示状态图标。
 */
@Composable
fun FilePathText(path: String, modifier: Modifier = Modifier) {
    var isExpanded by remember(path) { mutableStateOf(false) }
    var hasOverflow by remember(path) { mutableStateOf(false) }

    Row(
        modifier =
            modifier
                .then(
                    if (hasOverflow) Modifier.clickable { isExpanded = !isExpanded } else Modifier
                )
                .animateContentSize(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(CoreR.drawable.ic_folder),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp).size(14.dp),
        )
        Spacer(Modifier.width(MaterialTheme.spacings.extraSmall))
        Text(
            text = path,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            overflow = TextOverflow.Ellipsis,
            maxLines = if (isExpanded) Int.MAX_VALUE else 1,
            onTextLayout = { textLayoutResult ->
                // 只在折叠状态下判断是否被截断，有截断才允许点击展开
                if (!isExpanded) {
                    hasOverflow = textLayoutResult.hasVisualOverflow
                }
            },
        )
        if (hasOverflow) {
            Icon(
                painter =
                    painterResource(
                        if (isExpanded) CoreR.drawable.ic_chevron_up
                        else CoreR.drawable.ic_chevron_down
                    ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.padding(start = MaterialTheme.spacings.extraSmall, top = 3.dp)
                        .size(14.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FilePathTextPreview() {
    FindroidTheme {
        FilePathText(
            path = "/media/movies/Alita Battle Angel (2019)/Alita.Battle.Angel.2019.1080p.mkv"
        )
    }
}
