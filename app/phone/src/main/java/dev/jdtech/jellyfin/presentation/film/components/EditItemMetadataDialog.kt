package dev.jdtech.jellyfin.presentation.film.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.scraper.ScraperSite
import dev.jdtech.jellyfin.models.ItemMetadataEdit
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.theme.warningColor
import dev.jdtech.jellyfin.settings.domain.models.TranslateSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

// 多值字段在输入框里用逗号分隔，确认时才切成列表
private const val METADATA_LIST_SEPARATOR = ", "

// 弹窗最高高度与表单滚动区高度：字段较多，超出时内部滚动，避免弹窗撑满屏幕；
// 上限里给「抓取后的来源选项行」（约 40dp）留了位置，免得那一行把下面按钮行挤出卡片
private val METADATA_DIALOG_MAX_HEIGHT = 580.dp
private val METADATA_FORM_MAX_HEIGHT = 360.dp

/**
 * 「编辑 nfo」弹窗。
 *
 * 打开时用服务器上的元数据回填全部可编辑字段：标题行右侧「清空」，按钮行左下角「抓取」「翻译」，
 * 右下角「取消 / 确认」。「标题」是唯一必填项，为空时给出必填提示并禁用确认；
 * 演职员、外部刮削 ID 等不在表单里的内容不会被改动。
 *
 * 抓取是各站点同时抓一遍，因此抓到内容后标题行下方会多出一行来源选项（[scrapedSites] +「原来」）：
 * 点站点名换成该站抓到的内容，点「原来」回到抓取前服务器上的内容（[onSelectScrapeSource]）。
 * 再点一次当前选中的站点会整份重置成该站结果，把逐框点标签恢复过的字段重新填回来。
 *
 * 表单里是抓取结果时，点某个字段的标签（如「标题」）就把这一框恢复成服务器原值，
 * 其余字段保持抓取内容——站点结果可以只采用一部分，点完给一次「字段已重置」的提示。
 *
 * 内容仍来自抓取、且与服务器现值不同的字段（与服务器现值逐框比较，整框同色、不做字内 diff）
 * 标签用提示色标出；某个框被恢复成原值后标记随之消失，一眼能看出还剩哪些框是站点的内容。
 * 没抓到内容或服务器元数据没加载出来（没有可比基准）时不标，标签也不可点。
 *
 * 「翻译」打开翻译设置弹窗（OpenAI 兼容接口），本身不直接翻译；真正的翻译有两个入口：
 * 勾了自动翻译时由抓取触发，或在设置弹窗里点「翻译现有」翻当前表单的内容（[onTranslateExisting]）。
 *
 * [scrapedMetadata] 非空表示表单已被抓取结果覆盖、但还没保存，此时标题改为「已更新（未保存）」；
 * 它在「原来」被选中或还没抓过时为空，表单回到 [metadata]。
 * [fileName] 是影片文件的名字，「清空」会把标题填成它：抓不到信息时文件名是唯一还能用的标题。
 */
@Composable
fun EditItemMetadataDialog(
    metadata: ItemMetadataEdit?,
    scrapedMetadata: ItemMetadataEdit?,
    scrapedSites: List<ScraperSite>,
    selectedScrapeSite: ScraperSite?,
    fileName: String?,
    isLoading: Boolean,
    isTranslating: Boolean,
    translateSettings: TranslateSettings,
    errorText: String?,
    onConfirm: (ItemMetadataEdit) -> Unit,
    onSelectScrapeSource: (ScraperSite?) -> Unit,
    onScrapeClick: () -> Unit,
    onTranslateSettingsChange: (TranslateSettings) -> Unit,
    onTranslateExisting: (ItemMetadataEdit, TranslateSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    // 「清空」的结果不是空表单，而是只剩标题（用文件名兜底）——其余字段确实清掉了
    val clearedForm = MetadataForm(name = fileName.orEmpty())

    // 服务器上的原值：既是「原来」来源的内容，也是逐框点标签恢复时的目标
    val originalForm = remember(metadata) { metadata?.toForm() }

    // 每点一次来源选项就自增，作为重建表单的额外依据：同一站点再点一次时
    // scrapedMetadata 的值没变，只靠它无法把用户手动恢复过的框重新填成站点内容
    var sourceSwitchCount by remember { mutableIntStateOf(0) }

    // 表单整体按文本保存，确认时才解析成 ItemMetadataEdit，避免输入途中的半成品值被丢弃；
    // 抓取结果一到就整份覆盖表单，所以把它也作为重建表单的依据；
    // 这里刻意不把 fileName 作为重建依据：文件名随后到达（如下载完成）时不该冲掉用户正在编辑的内容
    var form by remember(metadata, scrapedMetadata, sourceSwitchCount) {
        mutableStateOf((scrapedMetadata ?: metadata)?.toForm() ?: clearedForm)
    }

    // 抓取结果相对服务器原值换掉了哪些框（只看来源，不随用户输入变化）
    val scrapedChangedFields =
        remember(scrapedMetadata, originalForm) {
            if (scrapedMetadata == null || originalForm == null) {
                emptySet()
            } else {
                scrapedMetadata.toForm().changedFieldsFrom(originalForm)
            }
        }

    // 其中当前仍与服务器原值不同的才用提示色标注：某个框点标签恢复成原值后标记随之消失，
    // 一眼能看出还剩哪些框是站点的内容；抓取之前或没有原值可比（originalForm 为空）时不标
    val changedFields =
        if (originalForm == null) {
            emptySet()
        } else {
            scrapedChangedFields intersect form.changedFieldsFrom(originalForm)
        }

    // 表单里是抓取结果、且知道服务器原值时才能逐框恢复（否则没有「原来的」可回）
    val restoreBaseline = originalForm?.takeIf { scrapedMetadata != null }

    val context = LocalContext.current

    // 弹窗内的即时反馈与 MovieScreen 里的事件提示一致，都用 Toast：
    // 恢复 / 重置这类操作在表单里只改动一两个框，光看界面不容易察觉到底生效了没有
    fun showToast(messageRes: Int) {
        Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
    }

    // 逐框恢复成服务器原值，并提示一次，说明这次点击确实生效了
    val restoreFieldAction: ((MetadataField) -> Unit)? =
        restoreBaseline?.let { baseline ->
            { field ->
                form = form.restoreField(field, baseline)
                showToast(CoreR.string.edit_metadata_field_reset)
            }
        }

    val isConfirmEnabled =
        !isLoading &&
            !isTranslating &&
            (scrapedMetadata != null || metadata != null) &&
            form.isValid

    // 表单里是抓取结果、还没保存时，标题用与被抓取字段标签一致的警告色，提示用户记得确认
    val titleColor = if (scrapedMetadata != null) MaterialTheme.warningColor else Color.Unspecified

    var showTranslateSettings by rememberSaveable { mutableStateOf(false) }

    // 「翻译现有」翻的是当前表单，翻译结果又是整份回填，所以表单本身得是合法的
    // （数值 / 日期填错时回填会把用户填了一半的内容丢掉）；再按字段说明哪些字段有内容可翻，
    // 由设置弹窗与它当场勾选的值合起来判断——只勾了标题而标题为空时就该禁用
    val hasTranslatableTitle = form.isValid && form.name.isNotBlank()
    val hasTranslatableOverview = form.isValid && form.overview.isNotBlank()
    // 分类 / 标签只要有内容就允许点「翻译现有」：具体哪几个值需要翻译由翻译器自己判定
    val hasTranslatableGenresTags = form.isValid && (form.genres.isNotBlank() || form.tags.isNotBlank())

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
                // 抓到内容的站点才列出来：没抓到任何东西时这一行没有可切换的对象
                if (scrapedSites.isNotEmpty()) {
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    ScrapeSourceRow(
                        sites = scrapedSites,
                        selectedSite = selectedScrapeSite,
                        // 服务器元数据没加载出来时「原来」没有内容可回，禁用
                        isOriginalAvailable = metadata != null,
                        onSelect = { site ->
                            // 每次点选（含重复点当前站点）都整份重建表单，把逐框恢复过的内容重新填满
                            sourceSwitchCount++
                            onSelectScrapeSource(site)
                            // 「原来」是回到服务器内容，与「应用站点数据」不是一回事，不给这条提示
                            if (site != null) showToast(CoreR.string.edit_metadata_site_applied)
                        },
                    )
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
                                // 提示放进滚动区首项而不是占弹窗固定高度：英文与放大字号下它会折行，
                                // 写死高度要么挤压按钮行、要么裁字
                                if (restoreBaseline != null) {
                                    RestoreFieldHint()
                                }
                                MetadataFormFields(
                                    form = form,
                                    changedFields = changedFields,
                                    onFormChange = { changed -> form = changed },
                                    onRestoreField = restoreFieldAction,
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
            hasTranslatableGenresTags = hasTranslatableGenresTags,
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
 * 标题下方的来源选项行：抓到内容的站点各占一个，点一下就把那个站点的结果填进表单；
 * 「原来」回到抓取前服务器上的内容。
 *
 * 站点数量不定（最多三个站点），横向滚动而不是换行，免得把表单区往上顶。
 */
@Composable
private fun ScrapeSourceRow(
    sites: List<ScraperSite>,
    selectedSite: ScraperSite?,
    isOriginalAvailable: Boolean,
    onSelect: (ScraperSite?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacings.default),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selectedSite == null,
            onClick = { onSelect(null) },
            enabled = isOriginalAvailable,
            label = { Text(text = stringResource(CoreR.string.edit_metadata_source_original)) },
        )
        sites.forEach { site ->
            FilterChip(
                selected = selectedSite == site,
                onClick = { onSelect(site) },
                label = { Text(text = site.displayName) },
            )
        }
    }
}

/** 抓取结果填进表单后的说明：点字段标签即可把这一框恢复成服务器原值，实现站点结果的部分采用。 */
@Composable
private fun RestoreFieldHint(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(CoreR.string.edit_metadata_restore_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = MaterialTheme.spacings.small),
    )
}

/** 表单的各个输入框，供抓取后与服务器现值逐框比较、标注「这一框的内容被换过」。 */
private enum class MetadataField {
    NAME,
    ORIGINAL_TITLE,
    OVERVIEW,
    GENRES,
    TAGS,
    ACTRESSES,
    DIRECTORS,
    STUDIOS,
    PRODUCTION_LOCATIONS,
    TAGLINE,
    OFFICIAL_RATING,
    PRODUCTION_YEAR,
    PREMIERE_DATE,
    COMMUNITY_RATING,
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
    val actresses: String = "",
    val directors: String = "",
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

    /** 按字段取当前文本：逐框比较与单框恢复都按它来，避免两处各写一份 when。 */
    fun textOf(field: MetadataField): String =
        when (field) {
            MetadataField.NAME -> name
            MetadataField.ORIGINAL_TITLE -> originalTitle
            MetadataField.OVERVIEW -> overview
            MetadataField.GENRES -> genres
            MetadataField.TAGS -> tags
            MetadataField.ACTRESSES -> actresses
            MetadataField.DIRECTORS -> directors
            MetadataField.STUDIOS -> studios
            MetadataField.PRODUCTION_LOCATIONS -> productionLocations
            MetadataField.TAGLINE -> tagline
            MetadataField.OFFICIAL_RATING -> officialRating
            MetadataField.PRODUCTION_YEAR -> productionYear
            MetadataField.PREMIERE_DATE -> premiereDate
            MetadataField.COMMUNITY_RATING -> communityRating
        }

    /** 换掉单个字段的文本，其余字段保持当前输入。 */
    fun withText(field: MetadataField, text: String): MetadataForm =
        when (field) {
            MetadataField.NAME -> copy(name = text)
            MetadataField.ORIGINAL_TITLE -> copy(originalTitle = text)
            MetadataField.OVERVIEW -> copy(overview = text)
            MetadataField.GENRES -> copy(genres = text)
            MetadataField.TAGS -> copy(tags = text)
            MetadataField.ACTRESSES -> copy(actresses = text)
            MetadataField.DIRECTORS -> copy(directors = text)
            MetadataField.STUDIOS -> copy(studios = text)
            MetadataField.PRODUCTION_LOCATIONS -> copy(productionLocations = text)
            MetadataField.TAGLINE -> copy(tagline = text)
            MetadataField.OFFICIAL_RATING -> copy(officialRating = text)
            MetadataField.PRODUCTION_YEAR -> copy(productionYear = text)
            MetadataField.PREMIERE_DATE -> copy(premiereDate = text)
            MetadataField.COMMUNITY_RATING -> copy(communityRating = text)
        }

    /**
     * 与 [baseline]（通常是服务器现值）逐框比较文本，返回内容不同的字段；整框同色，不做字内 diff。
     * [baseline] 为 null（没有可比较的原值）时返回空集。
     */
    fun changedFieldsFrom(baseline: MetadataForm?): Set<MetadataField> {
        if (baseline == null) return emptySet()
        return MetadataField.entries
            .filter { field -> textOf(field) != baseline.textOf(field) }
            .toSet()
    }

    /**
     * 把 [field] 恢复成 [baseline]（服务器原值）里的内容，其余字段保持当前输入：
     * 抓取结果只采用一部分时，逐框点标签走这里。
     */
    fun restoreField(field: MetadataField, baseline: MetadataForm): MetadataForm =
        withText(field, baseline.textOf(field))
}

private fun ItemMetadataEdit.toForm(): MetadataForm =
    MetadataForm(
        name = name,
        originalTitle = originalTitle,
        overview = overview,
        genres = genres.joinToString(METADATA_LIST_SEPARATOR),
        tags = tags.joinToString(METADATA_LIST_SEPARATOR),
        actresses = actresses.joinToString(METADATA_LIST_SEPARATOR),
        directors = directors.joinToString(METADATA_LIST_SEPARATOR),
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
        actresses = actresses.toMetadataList(),
        directors = directors.toMetadataList(),
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
private fun MetadataFormFields(
    form: MetadataForm,
    changedFields: Set<MetadataField>,
    onFormChange: (MetadataForm) -> Unit,
    onRestoreField: ((MetadataField) -> Unit)?,
) {
    // 点标签把该框恢复成服务器原值；没有可回的原值（[onRestoreField] 为 null）时标签保持普通文本
    fun restoreAction(field: MetadataField): (() -> Unit)? =
        onRestoreField?.let { restore -> { restore(field) } }

    Column {
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_name),
            value = form.name,
            onValueChange = { onFormChange(form.copy(name = it)) },
            isError = form.name.isBlank(),
            isChanged = MetadataField.NAME in changedFields,
            onRestore = restoreAction(MetadataField.NAME),
            minLines = 2,
            // 必填项常驻提示，标题为空时转为错误色
            supportingText = { Text(text = stringResource(CoreR.string.edit_metadata_required)) },
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_original_title),
            value = form.originalTitle,
            onValueChange = { onFormChange(form.copy(originalTitle = it)) },
            isChanged = MetadataField.ORIGINAL_TITLE in changedFields,
            onRestore = restoreAction(MetadataField.ORIGINAL_TITLE),
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_overview),
            value = form.overview,
            onValueChange = { onFormChange(form.copy(overview = it)) },
            isChanged = MetadataField.OVERVIEW in changedFields,
            onRestore = restoreAction(MetadataField.OVERVIEW),
            minLines = 3,
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_genres),
            value = form.genres,
            onValueChange = { onFormChange(form.copy(genres = it)) },
            isChanged = MetadataField.GENRES in changedFields,
            onRestore = restoreAction(MetadataField.GENRES),
            supportingText = { Text(text = stringResource(CoreR.string.edit_metadata_list_hint)) },
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_tags),
            value = form.tags,
            onValueChange = { onFormChange(form.copy(tags = it)) },
            isChanged = MetadataField.TAGS in changedFields,
            onRestore = restoreAction(MetadataField.TAGS),
            supportingText = { Text(text = stringResource(CoreR.string.edit_metadata_list_hint)) },
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_actresses),
            value = form.actresses,
            onValueChange = { onFormChange(form.copy(actresses = it)) },
            isChanged = MetadataField.ACTRESSES in changedFields,
            onRestore = restoreAction(MetadataField.ACTRESSES),
            supportingText = { Text(text = stringResource(CoreR.string.edit_metadata_list_hint)) },
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_directors),
            value = form.directors,
            onValueChange = { onFormChange(form.copy(directors = it)) },
            isChanged = MetadataField.DIRECTORS in changedFields,
            onRestore = restoreAction(MetadataField.DIRECTORS),
            supportingText = { Text(text = stringResource(CoreR.string.edit_metadata_list_hint)) },
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_studios),
            value = form.studios,
            onValueChange = { onFormChange(form.copy(studios = it)) },
            isChanged = MetadataField.STUDIOS in changedFields,
            onRestore = restoreAction(MetadataField.STUDIOS),
            supportingText = { Text(text = stringResource(CoreR.string.edit_metadata_list_hint)) },
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_production_locations),
            value = form.productionLocations,
            onValueChange = { onFormChange(form.copy(productionLocations = it)) },
            isChanged = MetadataField.PRODUCTION_LOCATIONS in changedFields,
            onRestore = restoreAction(MetadataField.PRODUCTION_LOCATIONS),
            supportingText = { Text(text = stringResource(CoreR.string.edit_metadata_list_hint)) },
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_tagline),
            value = form.tagline,
            onValueChange = { onFormChange(form.copy(tagline = it)) },
            isChanged = MetadataField.TAGLINE in changedFields,
            onRestore = restoreAction(MetadataField.TAGLINE),
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_official_rating),
            value = form.officialRating,
            onValueChange = { onFormChange(form.copy(officialRating = it)) },
            isChanged = MetadataField.OFFICIAL_RATING in changedFields,
            onRestore = restoreAction(MetadataField.OFFICIAL_RATING),
        )
        MetadataTextField(
            label = stringResource(CoreR.string.edit_metadata_field_production_year),
            value = form.productionYear,
            onValueChange = { onFormChange(form.copy(productionYear = it)) },
            isError = form.isProductionYearInvalid,
            isChanged = MetadataField.PRODUCTION_YEAR in changedFields,
            onRestore = restoreAction(MetadataField.PRODUCTION_YEAR),
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
            isChanged = MetadataField.PREMIERE_DATE in changedFields,
            onRestore = restoreAction(MetadataField.PREMIERE_DATE),
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
            isChanged = MetadataField.COMMUNITY_RATING in changedFields,
            onRestore = restoreAction(MetadataField.COMMUNITY_RATING),
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
    isChanged: Boolean = false,
    onRestore: (() -> Unit)? = null,
    minLines: Int = 1,
    supportingText: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // 抓取换掉内容的字段标签用警告色（偏红的提醒色，与错误红有区分）；
    // 只覆盖 label 颜色，isError 的红色提示不受影响
    val labelColor = if (isChanged) MaterialTheme.warningColor else Color.Unspecified
    // 抓取结果在表单里时，点标签把这一框恢复成服务器原值（只采用站点结果的一部分）；
    // 可点的标签带纹波，没有原值可回时保持纯文本
    val labelModifier =
        if (onRestore != null) Modifier.clickable(onClick = onRestore) else Modifier
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = label, modifier = labelModifier) },
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedLabelColor = labelColor,
                    unfocusedLabelColor = labelColor,
                ),
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
            scrapedMetadata =
                ItemMetadataEdit(
                    name = "ABC-123 抓到的示例影片",
                    originalTitle = "Sampled Movie",
                    overview = "这是从站点抓到的简介。",
                    genres = listOf("剧情"),
                    studios = listOf("示例制作"),
                    productionYear = 2024,
                    premiereDate = LocalDateTime.of(2024, 5, 2, 0, 0),
                    communityRating = 8.2f,
                ),
            scrapedSites = listOf(ScraperSite.JAV321, ScraperSite.JAVBUS, ScraperSite.JAVDB),
            selectedScrapeSite = ScraperSite.JAVBUS,
            fileName = "ABC-123",
            isLoading = false,
            isTranslating = false,
            translateSettings = TranslateSettings(),
            errorText = null,
            onConfirm = {},
            onSelectScrapeSource = {},
            onScrapeClick = {},
            onTranslateSettingsChange = {},
            onTranslateExisting = { _, _ -> },
            onDismiss = {},
        )
    }
}
