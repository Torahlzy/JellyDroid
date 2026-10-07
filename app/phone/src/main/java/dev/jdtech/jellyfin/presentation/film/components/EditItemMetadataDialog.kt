package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.ItemMetadataEdit
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.domain.models.TranslateSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

// 多值字段在输入框里用逗号分隔，确认时才切成列表
private const val METADATA_LIST_SEPARATOR = ", "

// 弹窗最高高度与表单滚动区高度：字段较多，超出时内部滚动，避免弹窗撑满屏幕
private val METADATA_DIALOG_MAX_HEIGHT = 540.dp
private val METADATA_FORM_MAX_HEIGHT = 360.dp

/**
 * 「编辑 nfo」弹窗。
 *
 * 打开时用服务器上的元数据回填全部可编辑字段：标题行右侧「清空」，按钮行左下角「抓取」「翻译」，
 * 右下角「取消 / 确认」。「标题」是唯一必填项，为空时给出必填提示并禁用确认；
 * 演职员、外部刮削 ID 等不在表单里的内容不会被改动。
 *
 * 「翻译」打开翻译设置弹窗（OpenAI 兼容接口），本身不直接翻译；真正的翻译有两个入口：
 * 勾了自动翻译时由抓取触发，或在设置弹窗里点「翻译现有」翻当前表单的内容（[onTranslateExisting]）。
 *
 * [scrapedMetadata] 非空表示表单已被抓取结果覆盖、但还没保存，此时标题改为「已更新（未保存）」。
 * [fileName] 是影片文件的名字，「清空」会把标题填成它：抓不到信息时文件名是唯一还能用的标题。
 */
@Composable
fun EditItemMetadataDialog(
    metadata: ItemMetadataEdit?,
    scrapedMetadata: ItemMetadataEdit?,
    fileName: String?,
    isLoading: Boolean,
    isTranslating: Boolean,
    translateSettings: TranslateSettings,
    errorText: String?,
    onConfirm: (ItemMetadataEdit) -> Unit,
    onScrapeClick: () -> Unit,
    onTranslateSettingsChange: (TranslateSettings) -> Unit,
    onTranslateExisting: (ItemMetadataEdit, TranslateSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    // 「清空」的结果不是空表单，而是只剩标题（用文件名兜底）——其余字段确实清掉了
    val clearedForm = MetadataForm(name = fileName.orEmpty())

    // 表单整体按文本保存，确认时才解析成 ItemMetadataEdit，避免输入途中的半成品值被丢弃；
    // 抓取结果一到就整份覆盖表单，所以把它也作为重建表单的依据；
    // 这里刻意不把 fileName 作为重建依据：文件名随后到达（如下载完成）时不该冲掉用户正在编辑的内容
    var form by remember(metadata, scrapedMetadata) {
        mutableStateOf((scrapedMetadata ?: metadata)?.toForm() ?: clearedForm)
    }

    val isConfirmEnabled =
        !isLoading &&
            !isTranslating &&
            (scrapedMetadata != null || metadata != null) &&
            form.isValid

    // 表单里是抓取结果、还没保存时，标题用提醒色，提示用户记得确认
    val titleColor =
        if (scrapedMetadata != null) MaterialTheme.colorScheme.tertiary else Color.Unspecified

    var showTranslateSettings by rememberSaveable { mutableStateOf(false) }

    // 「翻译现有」翻的是当前表单，翻译结果又是整份回填，所以表单本身得是合法的
    // （数值 / 日期填错时回填会把用户填了一半的内容丢掉）；再按字段说明哪些字段有内容可翻，
    // 由设置弹窗与它当场勾选的值合起来判断——只勾了标题而标题为空时就该禁用
    val hasTranslatableTitle = form.isValid && form.name.isNotBlank()
    val hasTranslatableOverview = form.isValid && form.overview.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = METADATA_DIALOG_MAX_HEIGHT),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = MaterialTheme.spacings.default)) {
                // 标题行：标题在左，清空在右上角；左右边距与按钮行一致
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = MaterialTheme.spacings.default),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            stringResource(
                                if (scrapedMetadata != null) {
                                    CoreR.string.edit_metadata_scraped_title
                                } else {
                                    CoreR.string.edit_item_metadata
                                }
                            ),
                        style = MaterialTheme.typography.headlineSmall,
                        color = titleColor,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { form = clearedForm },
                        // 已经是清空后的样子就没有可清的内容
                        enabled = form != clearedForm,
                    ) {
                        Text(text = stringResource(CoreR.string.clear))
                    }
                }
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                Column(modifier = Modifier.padding(horizontal = MaterialTheme.spacings.default)) {
                    // 抓到内容就优先显示表单：服务器元数据加载失败的原因不该挡住刚抓到的信息
                    val showForm =
                        scrapedMetadata != null ||
                            (!isLoading && metadata != null && errorText == null)
                    when {
                        showForm ->
                            Column(
                                modifier =
                                    Modifier.heightIn(max = METADATA_FORM_MAX_HEIGHT)
                                        .verticalScroll(rememberScrollState())
                            ) {
                                MetadataFormFields(
                                    form = form,
                                    onFormChange = { changed -> form = changed },
                                )
                            }
                        // 加载失败时说明原因，避免让用户误以为服务器上真的没有这些字段
                        errorText != null ->
                            Text(
                                text = errorText,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        else ->
                            CircularProgressIndicator(
                                modifier =
                                    Modifier.padding(MaterialTheme.spacings.small)
                                        .size(MaterialTheme.spacings.default),
                                strokeWidth = 2.dp,
                            )
                    }
                }
                Spacer(Modifier.height(MaterialTheme.spacings.default))
                // 按钮行：抓取 / 翻译在左下角，取消 / 确认在右下角
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = MaterialTheme.spacings.default),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 翻译期间左侧换成进度提示：这两个按钮本来就用不了，占着位置不如说明在等什么
                    if (isTranslating) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(MaterialTheme.spacings.default),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(MaterialTheme.spacings.small))
                            Text(
                                text = stringResource(CoreR.string.translate_in_progress),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
                            TextButton(onClick = onScrapeClick, enabled = !isLoading) {
                                Text(text = stringResource(CoreR.string.scrape))
                            }
                            TextButton(
                                onClick = { showTranslateSettings = true },
                                enabled = !isLoading,
                            ) {
                                Text(text = stringResource(CoreR.string.translate))
                            }
                        }
                    }
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text(text = stringResource(CoreR.string.cancel))
                        }
                        TextButton(
                            onClick = { onConfirm(form.toMetadataEdit()) },
                            enabled = isConfirmEnabled,
                        ) {
                            Text(text = stringResource(CoreR.string.confirm))
                        }
                    }
                }
            }
        }
    }

    // 设置弹窗叠在编辑弹窗之上：设置改完先落本地，再按用户点的动作决定要不要立刻翻一遍
    if (showTranslateSettings) {
        TranslateSettingsDialog(
            settings = translateSettings,
            hasTranslatableTitle = hasTranslatableTitle,
            hasTranslatableOverview = hasTranslatableOverview,
            onConfirm = { settings ->
                showTranslateSettings = false
                onTranslateSettingsChange(settings)
            },
            onTranslateExisting = { settings ->
                showTranslateSettings = false
                onTranslateExisting(form.toMetadataEdit(), settings)
            },
            onDismiss = { showTranslateSettings = false },
        )
    }
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
            scrapedMetadata = null,
            fileName = "ABC-123",
            isLoading = false,
            isTranslating = false,
            translateSettings = TranslateSettings(),
            errorText = null,
            onConfirm = {},
            onScrapeClick = {},
            onTranslateSettingsChange = {},
            onTranslateExisting = { _, _ -> },
            onDismiss = {},
        )
    }
}
