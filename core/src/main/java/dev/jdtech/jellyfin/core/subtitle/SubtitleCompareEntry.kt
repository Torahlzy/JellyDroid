package dev.jdtech.jellyfin.core.subtitle

import java.io.File

/**
 * 对照面板里的一行：某条候选落盘后的字幕文件与它的展示 / 上传所需信息。
 *
 * [order] 是落盘快照里的次序（1 起），面板的 ①~⑤ 就用它，**不随任何理由重排**：
 * 用户边看边点，行位置一变就容易点错。
 */
class SubtitleCompareEntry(
    val order: Int,
    val title: String,
    val language: SubtitleLanguage,
    val format: String,
    val durationDiffMs: Long?,
    val isRecommended: Boolean,
    val file: File,
)
