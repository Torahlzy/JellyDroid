package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.ItemMetadataEdit
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

// 多值字段在输入框里用逗号分隔，确认时才切成列表
private const val METADATA_LIST_SEPARATOR = ", "

// 表单最高高度：字段较多，超出时内部滚动，避免弹窗撑满屏幕
private val METADATA_FORM_MAX_HEIGHT = 420.dp

/**
 * 「编辑 nfo」弹窗。
 *
 * 打开时用服务器上的元数据回填全部可编辑字段，底部提供「清空」与「确认」。
 * 「标题」是唯一必填项，为空时给出必填提示并禁用确认；演职员、外部刮削 ID 等不在表单里的内容不会被改动。
 */
@Composable
fun EditItemMetadataDialog(
    metadata: ItemMetadataEdit?,
    isLoading: Boolean,
    errorText: String?,
    onConfirm: (ItemMetadataEdit) -> Unit,
    onDismiss: () -> Unit,
) {
    // 表单整体按文本保存，确认时才解析成 ItemMetadataEdit，避免输入途中的半成品值被丢弃
    var form by remember(metadata) { mutableStateOf(metadata?.toForm() ?: MetadataForm()) }

    val isConfirmEnabled = !isLoading && metadata != null && form.isValid

    AlertDialog(
        title = { Text(text = stringResource(CoreR.string.edit_item_metadata)) },
        text = {
            when {
                // 加载失败时优先说明原因，避免让用户误以为服务器上真的没有这些字段
                errorText != null ->
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                isLoading || metadata == null ->
                    CircularProgressIndicator(
                        modifier =
                            Modifier.padding(MaterialTheme.spacings.small)
                                .size(MaterialTheme.spacings.default),
                        strokeWidth = 2.dp,
                    )
                else ->
                    Column(
                        modifier =
                            Modifier.heightIn(max = METADATA_FORM_MAX_HEIGHT)
                                .verticalScroll(rememberScrollState())
                    ) {
                        MetadataFormFields(form = form, onFormChange = { changed -> form = changed })
                    }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            Row {
                TextButton(onClick = { form = MetadataForm() }) {
                    Text(text = stringResource(CoreR.string.clear))
                }
                TextButton(
                    onClick = { onConfirm(form.toMetadataEdit()) },
                    enabled = isConfirmEnabled,
                ) {
                    Text(text = stringResource(CoreR.string.confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(CoreR.string.cancel)) }
        },
    )
}

/**
 * 弹窗内的表单：所有字段一律按文本保存。
 *
 * 数值 / 日期字段在输入途中只是「半成品」（例如只敲了一个 `-`），因此不改用解析后的类型，
 * 只在确认时解析，解析失败就地提示并禁用确认。
 */
private data class MetadataForm(
    val name: String = "",
    val originalTitle: String = "",
    val overview: String = "",
    val genres: String = "",
    val tags: String = "",
    val studios: String = "",
    val productionLocations: String = "",
    val tagline: String = "",
    val officialRating: String = "",
    val productionYear: String = "",
    val premiereDate: String = "",
    val communityRating: String = "",
) {
    /** 标题必填；数值 / 日期字段允许留空（留空即清空服务器上的对应字段），但不允许填错。 */
    val isValid: Boolean
        get() =
            name.isNotBlank() &&
                !isProductionYearInvalid &&
                !isCommunityRatingInvalid &&
                !isPremiereDateInvalid

    val productionYearValue: Int?
        get() = productionYear.trim().toIntOrNull()

    val isProductionYearInvalid: Boolean
        get() = productionYear.isNotBlank() && productionYearValue == null

    val communityRatingValue: Float?
        get() = communityRating.trim().toFloatOrNull()

    val isCommunityRatingInvalid: Boolean
        get() = communityRating.isNotBlank() && communityRatingValue == null

    /** 首播日期只编辑到日，时间统一取当天零点。 */
    val premiereDateValue: LocalDateTime?
        get() = premiereDate.trim().takeIf { it.isNotEmpty() }?.let { text -> parseDate(text) }

    val isPremiereDateInvalid: Boolean
        get() = premiereDate.isNotBlank() && premiereDateValue == null
}

private fun ItemMetadataEdit.toForm(): MetadataForm =
    MetadataForm(
        name = name,
        originalTitle = originalTitle,
        overview = overview,
        genres = genres.joinToString(METADATA_LIST_SEPARATOR),
        tags = tags.joinToString(METADATA_LIST_SEPARATOR),
        studios = studios.joinToString(METADATA_LIST_SEPARATOR),
        productionLocations = productionLocations.joinToString(METADATA_LIST_SEPARATOR),
        tagline = tagline,
        officialRating = officialRating,
        productionYear = productionYear?.toString().orEmpty(),
        premiereDate = premiereDate?.toLocalDate()?.toString().orEmpty(),
        communityRating = communityRating?.toString().orEmpty(),
    )

private fun MetadataForm.toMetadataEdit(): ItemMetadataEdit =
    ItemMetadataEdit(
        name = name.trim(),
        originalTitle = originalTitle.trim(),
        overview = overview.trim(),
        genres = genres.toMetadataList(),
        tags = tags.toMetadataList(),
        studios = studios.toMetadataList(),
        productionLocations = productionLocations.toMetadataList(),
        tagline = tagline.trim(),
        officialRating = officialRating.trim(),
        productionYear = productionYearValue,
        premiereDate = premiereDateValue,
        communityRating = communityRatingValue,
    )

/** 逗号分隔的文本切成多值字段，顺带去掉空项与首尾空白。 */
private fun String.toMetadataList(): List<String> =
    split(',').map { it.trim() }.filter { it.isNotEmpty() }

private fun parseDate(text: String): LocalDateTime? =
    try {
        LocalDate.parse(text.trim()).atStartOfDay()
    } catch (_: DateTimeParseException) {
        null
    }

@Composable
private fun MetadataFormFields(form: MetadataForm, onFormChange: (MetadataForm) -> Unit) {
    Column {
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_name),
            value = form.name,
            onValueChange = { onFormChange(form.copy(name = it)) },
            isError = form.name.isBlank(),
            minLines = 2,
            // 必填项常驻提示，标题为空时转为错误色
            supportingText = { Text(text = stringResource(CoreR.string.edit_metadata_required)) },
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_original_title),
            value = form.originalTitle,
            onValueChange = { onFormChange(form.copy(originalTitle = it)) },
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_overview),
            value = form.overview,
            onValueChange = { onFormChange(form.copy(overview = it)) },
            minLines = 3,
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_genres),
            value = form.genres,
            onValueChange = { onFormChange(form.copy(genres = it)) },
            supportingText = { Text(text = stringResource(CoreR.string.edit_metadata_list_hint)) },
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_tags),
            value = form.tags,
            onValueChange = { onFormChange(form.copy(tags = it)) },
            supportingText = { Text(text = stringResource(CoreR.string.edit_metadata_list_hint)) },
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_studios),
            value = form.studios,
            onValueChange = { onFormChange(form.copy(studios = it)) },
            supportingText = { Text(text = stringResource(CoreR.string.edit_metadata_list_hint)) },
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_production_locations),
            value = form.productionLocations,
            onValueChange = { onFormChange(form.copy(productionLocations = it)) },
            supportingText = { Text(text = stringResource(CoreR.string.edit_metadata_list_hint)) },
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_tagline),
            value = form.tagline,
            onValueChange = { onFormChange(form.copy(tagline = it)) },
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_official_rating),
            value = form.officialRating,
            onValueChange = { onFormChange(form.copy(officialRating = it)) },
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_production_year),
            value = form.productionYear,
            onValueChange = { onFormChange(form.copy(productionYear = it)) },
            isError = form.isProductionYearInvalid,
            supportingText =
                if (form.isProductionYearInvalid) {
                    { Text(text = stringResource(CoreR.string.edit_metadata_invalid_number)) }
                } else {
                    null
                },
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_premiere_date),
            value = form.premiereDate,
            onValueChange = { onFormChange(form.copy(premiereDate = it)) },
            isError = form.isPremiereDateInvalid,
            supportingText = {
                Text(
                    text =
                        stringResource(
                            if (form.isPremiereDateInvalid) {
                                CoreR.string.edit_metadata_invalid_date
                            } else {
                                CoreR.string.edit_metadata_date_hint
                            }
                        )
                )
            },
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_community_rating),
            value = form.communityRating,
            onValueChange = { onFormChange(form.copy(communityRating = it)) },
            isError = form.isCommunityRatingInvalid,
            supportingText =
                if (form.isCommunityRatingInvalid) {
                    { Text(text = stringResource(CoreR.string.edit_metadata_invalid_number)) }
                } else {
                    null
                },
        )
    }
}

@Composable
private fun MetadataTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
    minLines: Int = 1,
    supportingText: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = label) },
            isError = isError,
            supportingText = supportingText,
            singleLine = minLines == 1,
            minLines = minLines,
        )
        Spacer(Modifier.height(MaterialTheme.spacings.small))
    }
}

@Preview
@Composable
private fun EditItemMetadataDialogPreview() {
    FindroidTheme {
        EditItemMetadataDialog(
            metadata =
                ItemMetadataEdit(
                    name = "示例影片",
                    originalTitle = "Sample Movie",
                    overview = "这是一段简介。",
                    genres = listOf("动作", "科幻"),
                    tags = listOf("高分"),
                    studios = listOf("示例影业"),
                    productionLocations = listOf("美国"),
                    tagline = "宣传语",
                    officialRating = "PG-13",
                    productionYear = 2024,
                    premiereDate = LocalDateTime.of(2024, 5, 1, 0, 0),
                    communityRating = 7.5f,
                ),
            isLoading = false,
            errorText = null,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
