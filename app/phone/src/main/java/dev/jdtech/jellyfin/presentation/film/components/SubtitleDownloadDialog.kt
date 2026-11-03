package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.subtitle.labelRes
import dev.jdtech.jellyfin.core.presentation.subtitle.subtitleDurationDiffText
import dev.jdtech.jellyfin.core.subtitle.SubtitleCandidate
import dev.jdtech.jellyfin.core.subtitle.SubtitleCandidateState
import dev.jdtech.jellyfin.core.subtitle.SubtitleDownloadStage
import dev.jdtech.jellyfin.core.subtitle.SubtitleLanguage
import dev.jdtech.jellyfin.presentation.components.BaseDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 下载字幕弹窗，三个阶段共用一个弹窗：
 *
 * 1. 关键词输入（[stage] 为 null 且没有候选）：关键词 + 代理入口 + 确定 / 取消；
 * 2. 搜索中（[stage] 为 [SubtitleDownloadStage.Searching]）：一行进度提示；
 * 3. 候选列表（[candidates] 非空）：搜索一回来就把标题铺出来，每条右侧按自身进度显示转圈 /
 *    带符号时长差 + 「上传」按钮 / 「不可用」，用户自己挑一条上传。
 *
 * 关掉弹窗由上层负责中断剩余下载；上传成功由事件关掉弹窗。
 */
@Composable
fun SubtitleDownloadDialog(
    defaultKeyword: String,
    proxy: String,
    stage: SubtitleDownloadStage?,
    candidates: List<SubtitleCandidateState>,
    searchFoundNothing: Boolean,
    onProxyChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onUpload: (SubtitleCandidate) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    /** 可对照的条数（已下到可用字幕）；为 0 时底部「播放视频时对比」禁用。 */
    readyCount: Int = 0,
    onCompare: () -> Unit = {},
) {
    var keyword by remember { mutableStateOf(defaultKeyword) }
    var showProxyDialog by remember { mutableStateOf(false) }
    // 播放页的关键词要联网从来源路径识别，可能比弹窗晚到；用户还没输入时跟着补上
    LaunchedEffect(defaultKeyword) {
        if (keyword.isBlank()) keyword = defaultKeyword
    }
    val isSearching = stage == SubtitleDownloadStage.Searching
    val uploadingCandidate = (stage as? SubtitleDownloadStage.Uploading)?.candidate
    // 候选已经搜出来就切换到列表，之后不再回到输入框（除非用户点「取消」）
    val showList = candidates.isNotEmpty()

    BaseDialog(
        title = stringResource(CoreR.string.download_subtitle),
        onDismiss = onDismiss,
        negativeButton = {},
        positiveButton = {
            if (isSearching || showList) {
                // 搜索 / 列表阶段用「取消」结束整批：中断剩余下载并回到关键词输入
                TextButton(onClick = onCancel) {
                    Text(text = stringResource(CoreR.string.cancel))
                }
            } else {
                TextButton(
                    onClick = { onConfirm(keyword.trim()) },
                    enabled = keyword.isNotBlank(),
                ) {
                    Text(text = stringResource(CoreR.string.confirm))
                }
            }
        },
    ) { contentPadding ->
        when {
            isSearching ->
                SubtitleSearchingRow(modifier = Modifier.padding(contentPadding))
            showList ->
                Column(
                    // weight 让列表只占内容需要的高度（条目多时再滚动），弹窗不会被撑满
                    modifier = Modifier.weight(1f, fill = false).padding(contentPadding)
                ) {
                    SubtitleCandidateList(
                        candidates = candidates,
                        uploadingCandidate = uploadingCandidate,
                        onUpload = onUpload,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // 入口 A：把已下好的候选带进播放器，5 条同屏边看边挑（不上传任何一条）
                    TextButton(
                        onClick = onCompare,
                        enabled = readyCount > 0 && uploadingCandidate == null,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(text = stringResource(CoreR.string.subtitle_compare_entry))
                    }
                }
            else ->
                SubtitleKeywordInput(
                    keyword = keyword,
                    onKeywordChange = { keyword = it },
                    proxy = proxy,
                    searchFoundNothing = searchFoundNothing,
                    onProxyClick = { showProxyDialog = true },
                    modifier = Modifier.padding(contentPadding),
                )
        }
    }

    if (showProxyDialog) {
        ScrapeProxyDialog(
            proxy = proxy,
            onConfirm = { address ->
                showProxyDialog = false
                onProxyChange(address)
            },
            onDismiss = { showProxyDialog = false },
        )
    }
}

/** 关键词输入阶段：与抓取弹窗一致，共用抓取代理入口。 */
@Composable
private fun SubtitleKeywordInput(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    proxy: String,
    searchFoundNothing: Boolean,
    onProxyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(CoreR.string.scrape_keyword_label)) },
            singleLine = true,
        )
        // 搜不到候选时留在输入阶段并就地提示，用户可以直接改关键词重试
        if (searchFoundNothing) {
            Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
            Text(
                text = stringResource(CoreR.string.subtitle_no_candidates),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(MaterialTheme.spacings.small))
        TextButton(onClick = onProxyClick) {
            Text(
                text =
                    stringResource(
                        CoreR.string.scrape_proxy_entry,
                        proxy.ifEmpty { stringResource(CoreR.string.scrape_proxy_unset) },
                    )
            )
        }
    }
}

/** 搜索中：候选标题还没回来，先给一行进度提示。 */
@Composable
private fun SubtitleSearchingRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacings.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(MaterialTheme.spacings.default),
            strokeWidth = 2.dp,
        )
        Text(
            text = stringResource(CoreR.string.subtitle_searching),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * 候选列表：保持站点给出的顺序，**不做按推荐度重排**——列表是边下边补的，
 * 重排会让用户正要点的按钮换位置。
 */
@Composable
private fun SubtitleCandidateList(
    candidates: List<SubtitleCandidateState>,
    uploadingCandidate: SubtitleCandidate?,
    onUpload: (SubtitleCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 上传期间其余行的按钮一并禁用，避免重复提交
    val hasUploading = uploadingCandidate != null
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        itemsIndexed(candidates, key = { _, item -> item.candidate.pageUrl }) { index, item ->
            SubtitleCandidateRow(
                item = item,
                isUploading = item.candidate == uploadingCandidate,
                uploadEnabled = !hasUploading,
                onUpload = { onUpload(item.candidate) },
            )
            if (index != candidates.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** 列表里的一行：左侧标题（可折行）+ 语言 / 推荐 / 时长差，右侧按状态显示进度或上传按钮。 */
@Composable
private fun SubtitleCandidateRow(
    item: SubtitleCandidateState,
    isUploading: Boolean,
    uploadEnabled: Boolean,
    onUpload: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacings.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // 不设 maxLines / overflow：长标题完整折行，不出现省略号
            Text(text = item.candidate.title, style = MaterialTheme.typography.bodyMedium)
            if (item is SubtitleCandidateState.Ready) {
                Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
                // 语言 / 推荐 / 时长差排在同一条副信息里；窄屏或大字号下自动换行，不会裁掉
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
                ) {
                    // 语言必须显示：每条候选内部按简 > 繁 > 英取一份，不显示的话用户不知道自己会拿到哪种
                    Text(
                        text = stringResource(item.language.labelRes()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.isRecommended) {
                        Text(
                            text = stringResource(CoreR.string.subtitle_candidate_recommended),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier =
                                Modifier.background(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = MaterialTheme.shapes.extraSmall,
                                    )
                                    .padding(horizontal = MaterialTheme.spacings.extraSmall),
                        )
                    }
                    // 时长差写成「字幕短 / 长多少」，只给 ±秒 用户分不清是字幕短还是视频短
                    Text(
                        text = subtitleDurationDiffText(item.durationDiffMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        when (item) {
            is SubtitleCandidateState.Loading ->
                CircularProgressIndicator(
                    modifier = Modifier.size(MaterialTheme.spacings.default),
                    strokeWidth = 2.dp,
                )
            is SubtitleCandidateState.Unavailable ->
                Text(
                    text = stringResource(CoreR.string.subtitle_candidate_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            is SubtitleCandidateState.Ready ->
                TextButton(onClick = onUpload, enabled = uploadEnabled) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(MaterialTheme.spacings.medium),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(MaterialTheme.spacings.extraSmall))
                        Text(text = stringResource(CoreR.string.subtitle_uploading_candidate))
                    } else {
                        Text(text = stringResource(CoreR.string.upload))
                    }
                }
        }
    }
}

@Preview
@Composable
private fun SubtitleDownloadDialogPreview() {
    val candidates =
        listOf(
            SubtitleCandidate(
                title = "SSIS-001 中文字幕完整版（简体 + 繁体对照）",
                pageUrl = "https://example.com/subs/1",
            ),
            SubtitleCandidate(title = "SSIS-001 英文字幕", pageUrl = "https://example.com/subs/2"),
            SubtitleCandidate(title = "SSIS-001 另一个版本", pageUrl = "https://example.com/subs/3"),
        )
    FindroidTheme {
        SubtitleDownloadDialog(
            defaultKeyword = "SSIS-001",
            proxy = "",
            stage = null,
            candidates =
                listOf(
                    SubtitleCandidateState.Ready(
                        candidate = candidates[0],
                        language = SubtitleLanguage.SIMPLIFIED_CHINESE,
                        durationDiffMs = -25_000,
                        isRecommended = true,
                    ),
                    SubtitleCandidateState.Loading(candidate = candidates[1]),
                    SubtitleCandidateState.Unavailable(candidate = candidates[2]),
                ),
            searchFoundNothing = false,
            onProxyChange = {},
            onConfirm = {},
            onUpload = {},
            onCancel = {},
            onDismiss = {},
        )
    }
}
