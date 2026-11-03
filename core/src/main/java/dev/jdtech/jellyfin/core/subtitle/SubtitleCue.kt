package dev.jdtech.jellyfin.core.subtitle

/**
 * 字幕里的一条台词。
 *
 * [text] 已经把 SRT / VTT 的行内标记（`<i>`、`{\an8}` 这类）去掉，多行合并成一行，
 * 供对照面板直接渲染；[startMs] / [endMs] 是它在时间轴上的区间。
 */
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)
