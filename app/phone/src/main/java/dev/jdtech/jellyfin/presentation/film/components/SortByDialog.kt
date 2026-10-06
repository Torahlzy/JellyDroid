package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.CoverDisplayMode
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 列表展示设置弹窗：排序方式（顺序 + 字段）与封面显示模式。
 *
 * 每次选择都会把当前的全部选项回传，由调用方按需分发动作。
 */
@Composable
fun SortByDialog(
    currentSortBy: SortBy,
    currentSortOrder: SortOrder,
    currentCoverMode: CoverDisplayMode,
    onUpdate: (sortBy: SortBy, sortOrder: SortOrder, coverMode: CoverDisplayMode) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val optionValues = SortBy.selectableValues
    val optionNames = stringArrayResource(CoreR.array.sort_by_options)
    val options = optionValues.zip(optionNames)

    val orderValues = SortOrder.entries
    val orderNames = stringArrayResource(CoreR.array.sort_order_options)
    val orderOptions = orderValues.zip(orderNames)

    val coverModeValues = CoverDisplayMode.entries
    val coverModeNames = stringArrayResource(CoreR.array.cover_mode_options)
    val coverModeOptions = coverModeValues.zip(coverModeNames)

    var selectedOption by remember { mutableStateOf(currentSortBy) }
    var selectedOrder by remember { mutableStateOf(currentSortOrder) }
    var selectedCoverMode by remember { mutableStateOf(currentCoverMode) }

    val lazyListState = rememberLazyListState()

    val isAtTop by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset == 0
        }
    }

    Dialog(onDismissRequest = { onDismissRequest() }) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
        ) {
            Column {
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
                Text(
                    text = stringResource(CoreR.string.sort_by),
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = MaterialTheme.spacings.default),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                OptionsSegmentedRow(
                    options = orderOptions,
                    selectedOption = selectedOrder,
                    onSelect = { order ->
                        selectedOrder = order
                        onUpdate(selectedOption, selectedOrder, selectedCoverMode)
                    },
                    // 随机排序的结果与顺序无关，禁用顺序按钮以免用户点了没有反应
                    enabled = selectedOption != SortBy.RANDOM,
                    modifier =
                        Modifier.padding(horizontal = MaterialTheme.spacings.default)
                            .fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                Text(
                    text = stringResource(CoreR.string.cover_mode),
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = MaterialTheme.spacings.default),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
                OptionsSegmentedRow(
                    options = coverModeOptions,
                    selectedOption = selectedCoverMode,
                    onSelect = { coverMode ->
                        selectedCoverMode = coverMode
                        onUpdate(selectedOption, selectedOrder, selectedCoverMode)
                    },
                    modifier =
                        Modifier.padding(horizontal = MaterialTheme.spacings.default)
                            .fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                if (!isAtTop) {
                    HorizontalDivider()
                }
                LazyColumn(modifier = Modifier.fillMaxWidth(), state = lazyListState) {
                    items(options) { option ->
                        SortByDialogItem(
                            option = option,
                            isSelected = option.first == selectedOption,
                            onSelect = {
                                selectedOption = option.first
                                onUpdate(selectedOption, selectedOrder, selectedCoverMode)
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
            }
        }
    }
}

/** 单选的横向分段按钮组，用于排序顺序与封面显示模式；整体可禁用。 */
@Composable
private fun <T> OptionsSegmentedRow(
    options: List<Pair<T, String>>,
    selectedOption: T,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option.first == selectedOption,
                onClick = { onSelect(option.first) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors =
                    SegmentedButtonDefaults.colors(
                        inactiveContainerColor = Color.Transparent
                    ),
                icon = {
                    AnimatedVisibility(
                        visible = option.first == selectedOption,
                        enter = fadeIn(),
                        exit = ExitTransition.None,
                    ) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_check),
                            contentDescription = null,
                        )
                    }
                },
                label = { Text(option.second) },
            )
        }
    }
}

@Composable
private fun SortByDialogItem(
    option: Pair<SortBy, String>,
    isSelected: Boolean,
    onSelect: (SortBy) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable { onSelect(option.first) }
                .padding(horizontal = MaterialTheme.spacings.default),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = { onSelect(option.first) })
        Spacer(modifier = Modifier.width(MaterialTheme.spacings.medium))
        Text(text = option.second)
    }
}

@Preview
@Composable
private fun SortByDialogPreview() {
    FindroidTheme {
        SortByDialog(
            currentSortBy = SortBy.NAME,
            currentSortOrder = SortOrder.ASCENDING,
            currentCoverMode = CoverDisplayMode.PORTRAIT,
            onUpdate = { _, _, _ -> },
            onDismissRequest = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SortByDialogItemPreview() {
    FindroidTheme {
        SortByDialogItem(option = Pair(SortBy.NAME, "Title"), isSelected = true, onSelect = {})
    }
}
