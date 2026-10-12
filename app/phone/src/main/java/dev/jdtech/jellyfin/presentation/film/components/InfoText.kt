package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.presentation.theme.spacings

/** 标注可点击取值在 [androidx.compose.ui.text.AnnotatedString] 里的 tag。 */
private const val FACET_VALUE_TAG = "facet_value"

@Composable
fun InfoText(
    genres: List<String>,
    tags: List<String> = emptyList(),
    director: FindroidItemPerson?,
    writers: List<FindroidItemPerson>,
    // 回调为 null 时按普通文本渲染；离线模式下筛选结果页不可用，界面会传 null
    onGenreClick: ((String) -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
        if (genres.isNotEmpty()) {
            FacetValueText(
                label = stringResource(CoreR.string.genres),
                values = genres,
                onValueClick = onGenreClick,
            )
        }
        if (tags.isNotEmpty()) {
            FacetValueText(
                label = stringResource(CoreR.string.tags),
                values = tags,
                onValueClick = onTagClick,
            )
        }
        if (director != null) {
            Text(
                text = "${stringResource(CoreR.string.director)}: ${director.name}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (writers.isNotEmpty()) {
            Text(
                text =
                    "${stringResource(CoreR.string.writers)}: ${writers.joinToString { it.name }}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * 「标签: 值1, 值2」样式的单行文本。
 *
 * [onValueClick] 非 null 时每个取值用主色高亮并可点击，分隔符与「标签:」前缀不可点。
 */
@Composable
private fun FacetValueText(
    label: String,
    values: List<String>,
    onValueClick: ((String) -> Unit)?,
) {
    val text = buildAnnotatedString {
        append("$label: ")
        values.forEachIndexed { index, value ->
            if (index > 0) append(", ")
            if (onValueClick != null) {
                pushStringAnnotation(tag = FACET_VALUE_TAG, annotation = value)
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(value) }
                pop()
            } else {
                append(value)
            }
        }
    }

    if (onValueClick == null) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
        return
    }

    // Text 不可直接接收点击位置对应的字符偏移，先记下布局结果再换算
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        onTextLayout = { layoutResult = it },
        modifier =
            Modifier.pointerInput(text) {
                detectTapGestures { position ->
                    val offset = layoutResult?.getOffsetForPosition(position) ?: return@detectTapGestures
                    text
                        .getStringAnnotations(FACET_VALUE_TAG, offset, offset)
                        .firstOrNull()
                        ?.let { onValueClick(it.item) }
                }
            },
    )
}
