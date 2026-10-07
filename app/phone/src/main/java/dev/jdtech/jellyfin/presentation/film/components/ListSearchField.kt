package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import dev.jdtech.jellyfin.core.R as CoreR

/**
 * 列表页顶部的内容过滤框。
 *
 * 只负责输入与清除，过滤交给各列表页（有的本地过滤，有的走服务端搜索）。
 */
@Composable
fun ListSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(text = placeholder, overflow = TextOverflow.Ellipsis, maxLines = 1) },
        leadingIcon = {
            Icon(painter = painterResource(CoreR.drawable.ic_search), contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_x),
                        contentDescription = stringResource(CoreR.string.clear),
                    )
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // 按下输入法搜索键即收起输入法，让列表完整可见
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}
