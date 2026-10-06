package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

/**
 * 可折叠文本：默认最多显示 [collapsedMaxLines] 行，文本被截断时点击可在展开 / 收起之间切换；未截断时不可点击。
 */
@Composable
fun CollapsibleText(
    text: String,
    collapsedMaxLines: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    var isExpanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }

    Text(
        text = text,
        modifier =
            modifier
                .then(
                    if (hasOverflow) Modifier.clickable { isExpanded = !isExpanded } else Modifier
                )
                .animateContentSize(),
        overflow = TextOverflow.Ellipsis,
        maxLines = if (isExpanded) Int.MAX_VALUE else collapsedMaxLines,
        onTextLayout = { textLayoutResult ->
            // 只在折叠状态下判断是否被截断，有截断才允许点击展开
            if (!isExpanded) {
                hasOverflow = textLayoutResult.hasVisualOverflow
            }
        },
        style = style,
        fontSize = fontSize,
    )
}

@Preview(showBackground = true)
@Composable
private fun CollapsibleTextPreview() {
    FindroidTheme {
        CollapsibleText(
            text = "这是一段用于预览的文本，长度足够触发折叠，点击后可以展开或收起。",
            collapsedMaxLines = 2,
        )
    }
}
