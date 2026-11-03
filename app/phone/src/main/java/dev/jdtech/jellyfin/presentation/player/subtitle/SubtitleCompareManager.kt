package dev.jdtech.jellyfin.presentation.player.subtitle

import androidx.media3.common.Player
import dev.jdtech.jellyfin.core.subtitle.SubtitleCompareCache
import dev.jdtech.jellyfin.core.subtitle.SubtitleCompareEntry
import dev.jdtech.jellyfin.core.subtitle.SubtitleCue
import dev.jdtech.jellyfin.core.subtitle.SubtitleCueParser
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 播放页「多字幕同屏对照」的状态与取句。
 *
 * 为什么不用播放器的字幕轨：ExoPlayer 一个 TextRenderer 只渲染一个选中的 group，
 * mpv 也只有 `sid` + 一个 `secondary-sid`，都撑不住 5 条。因此由 App 自己解析字幕、
 * 自己按播放位置取当前一句，两条后端行为一致，也不必改 trackSelector。
 *
 * 快照语义：面板只认这次落盘的那一批（见 [SubtitleCompareCache]）。播放期间再从菜单重新搜索，
 * 候选会变，但面板**不会**自动刷新——否则 ①~⑤ 指代的字幕会在用户眼皮底下换掉。
 *
 * 取句位置要叠加已保存的字幕进度偏移（[AppPreferences.getSubtitleOffset]）：
 * 用户设过 +5 秒时，画面上显示的是「偏移后」的那句，面板必须跟着走，否则会被误判成「这条不对轴」。
 */
class SubtitleCompareManager(
    private val player: Player,
    private val appPreferences: AppPreferences,
    private val cache: SubtitleCompareCache,
) {

    /** 面板里的一行：静态信息（序号 / 时长差）来自快照，[text] 随播放位置变化。 */
    data class CompareRow(
        val order: Int,
        val durationDiffMs: Long?,
        val text: String,
    )

    data class CompareState(
        val isShowing: Boolean = false,
        val rows: List<CompareRow> = emptyList(),
        /** 正在上传并切轨，期间禁用所有「应用」按钮，避免重复提交。 */
        val isApplying: Boolean = false,
    )

    private val _state = MutableStateFlow(CompareState())
    val state: StateFlow<CompareState> = _state.asStateFlow()

    private var itemId: UUID? = null
    private var entries: List<SubtitleCompareEntry> = emptyList()

    /** 与 [entries] 一一对应的 cue 列表；解不出文本的那条为空表（文本区留空但仍可应用）。 */
    private var cues: List<List<SubtitleCue>> = emptyList()

    private var offsetMs: Long = 0L

    /** 上一次打心跳日志时的播放位置；用于限流，避免每 150ms 刷一条。 */
    private var lastLoggedPositionMs: Long = -POSITION_LOG_INTERVAL_MS

    /**
     * 读 [itemId] 对应的快照并解析字幕。
     *
     * @return 是否有可显示的候选；false 表示缓存缺失（调用方提示用户重新下载，不静默重下）。
     */
    suspend fun load(itemId: UUID): Boolean {
        val loaded = cache.load(itemId)
        if (loaded.isEmpty()) {
            AppLog.w("对照面板打不开：%s 没有可用快照（缓存缺失或清单损坏）", itemId)
            return false
        }
        val parsed =
            withContext(Dispatchers.IO) {
                loaded.map { entry ->
                    runCatching { SubtitleCueParser.parse(entry.file.readBytes()) }
                        .getOrElse { error ->
                            AppLog.w(error, "读取对照字幕失败：%s", entry.file)
                            emptyList()
                        }
                }
            }
        this.itemId = itemId
        entries = loaded
        cues = parsed
        offsetMs = appPreferences.getSubtitleOffset(itemId.toString())
        val positionMs = currentPositionMs() - offsetMs
        val rows = buildRows(positionMs)
        // 保留 isApplying：增量补行可能正好发生在「应用」的上传过程中，不能把按钮悄悄解禁
        _state.value =
            CompareState(
                isShowing = true,
                rows = rows,
                isApplying = _state.value.isApplying,
            )
        AppLog.d(
            "对照面板已打开：%s，快照 %d 条，字幕偏移 %d ms，播放位置 %d ms（取句位置 %d ms）",
            itemId,
            entries.size,
            offsetMs,
            currentPositionMs(),
            positionMs,
        )
        entries.forEachIndexed { index, entry ->
            AppLog.d(
                "  第 %d 行：%s（%s，%s，%d 字节）→ %d 条 cue，时长差 %s ms，当前文本 %d 字",
                entry.order,
                entry.title,
                entry.language,
                entry.format,
                entry.file.length(),
                cues.getOrNull(index)?.size ?: 0,
                entry.durationDiffMs,
                rows.getOrNull(index)?.text?.length ?: 0,
            )
        }
        return true
    }

    /** 关闭面板并丢弃快照（磁盘上的文件由调用方决定何时清理）。 */
    fun hide() {
        if (_state.value.isShowing) {
            AppLog.d("对照面板已关闭：%s", itemId)
        }
        itemId = null
        entries = emptyList()
        cues = emptyList()
        _state.value = CompareState()
    }

    fun setApplying(applying: Boolean) {
        _state.value = _state.value.copy(isApplying = applying)
    }

    /** 按当前播放位置重算 5 行文本；由播放页的轮询协程每 100~200ms 调一次。 */
    fun refresh() {
        if (!_state.value.isShowing) return
        val positionMs = currentPositionMs() - offsetMs
        val rows = buildRows(positionMs)
        // 文本没变就不发状态，避免每 150ms 全表重组一次
        if (rows != _state.value.rows) {
            _state.value = _state.value.copy(rows = rows)
            logRefresh("文本变化", positionMs, rows)
        } else if (positionMs - lastLoggedPositionMs >= POSITION_LOG_INTERVAL_MS) {
            // 心跳：能看出轮询有没有在跑、位置有没有在涨（全空行时尤其需要它）
            logRefresh("心跳", positionMs, rows)
        }
    }

    /** 面板上第 [order] 行（1 起）对应的快照；找不到时返回 null。 */
    fun entryOrNull(order: Int): SubtitleCompareEntry? = entries.firstOrNull { it.order == order }

    /** 面板当前对应的条目 id；面板没打开时为 null。 */
    fun currentItemId(): UUID? = itemId

    private fun logRefresh(reason: String, positionMs: Long, rows: List<CompareRow>) {
        lastLoggedPositionMs = positionMs
        AppLog.d(
            "对照刷新（%s）：取句位置 %d ms，非空 %d/%d 行，各行字数 [%s]",
            reason,
            positionMs,
            rows.count { it.text.isNotEmpty() },
            rows.size,
            rows.joinToString(",") { "${it.order}:${it.text.length}" },
        )
    }

    private fun buildRows(positionMs: Long): List<CompareRow> =
        entries.mapIndexed { index, entry ->
            CompareRow(
                order = entry.order,
                durationDiffMs = entry.durationDiffMs,
                // 此刻没有台词时留空串：行照常显示，不隐藏也不加占位文案
                text = cueAt(cues.getOrNull(index).orEmpty(), positionMs)?.text.orEmpty(),
            )
        }

    /** cues 已按 startMs 升序：二分找到最后一个开始时间不晚于 [positionMs] 的 cue，再判断是否在它区间内。 */
    private fun cueAt(cues: List<SubtitleCue>, positionMs: Long): SubtitleCue? {
        var low = 0
        var high = cues.lastIndex
        var found = -1
        while (low <= high) {
            val middle = (low + high) / 2
            if (cues[middle].startMs <= positionMs) {
                found = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        val cue = cues.getOrNull(found) ?: return null
        return cue.takeIf { positionMs <= it.endMs }
    }

    private fun currentPositionMs(): Long {
        // 未 ready 时 currentPosition 可能是 C.TIME_UNSET 或 0，取 0 更安全（面板显示留空）
        val position = player.currentPosition
        return if (position < 0) 0L else position
    }

    private companion object {
        /** 心跳日志的最小间隔：位置差超过这个值才再打一条，避免 150ms 轮询刷屏。 */
        const val POSITION_LOG_INTERVAL_MS = 10_000L
    }
}
