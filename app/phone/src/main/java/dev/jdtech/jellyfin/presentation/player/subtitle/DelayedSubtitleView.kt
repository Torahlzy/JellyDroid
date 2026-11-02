package dev.jdtech.jellyfin.presentation.player.subtitle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.media3.common.text.Cue
import androidx.media3.ui.SubtitleView

/**
 * 支持整体延迟渲染的字幕容器（字幕调节模式的"进度偏移"在 ExoPlayer 后端的实现载体）。
 *
 * media3 没有内置字幕延迟 API，且 [SubtitleView] 是 final 不能继承，因此这里用 FrameLayout
 * 包一层真实的 [SubtitleView]：布局里不再放 `exo_subtitles`（PlayerView 找不到它就不会自己
 * 渲染字幕），由 [SubtitleAdjustManager] 监听 `onCues` 后把整组 Cue 交给本容器，
 * 延后 [delayMs] 再转发给内层视图渲染。
 *
 * 只支持正偏移（延后）：负偏移（提前）需要提前拿到还没送到的 Cue，media3 拿不到未来时刻的
 * 字幕，无法实现，上层会按 0 处理并提示。
 */
class DelayedSubtitleView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** 实际渲染字幕的内层视图，字号 / 颜色 / 位置等样式由 SubtitleAdjustManager 直接设置。 */
    val subtitleView: SubtitleView =
        SubtitleView(context).also { inner ->
            addView(
                inner,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
            )
        }

    private val handler = Handler(Looper.getMainLooper())

    /** 字幕延后显示的毫秒数；0 表示立即渲染。 */
    var delayMs: Long = 0L
        set(value) {
            val clamped = value.coerceAtLeast(0L)
            if (field == clamped) {
                return
            }
            field = clamped
            // 偏移变化时按新偏移重新调度还没渲染的那组 Cue，归零则立即渲染
            pending?.let { (_, cues) -> schedule(cues) }
        }

    /** 还没渲染的待播 Cue 组，null 表示当前没有挂起的渲染。 */
    private var pending: Pair<Runnable, List<Cue>>? = null

    /** 由 SubtitleAdjustManager 在收到 onCues 时调用，替代 PlayerView 的内部转发。 */
    fun setCues(cues: List<Cue>) {
        if (delayMs == 0L) {
            clearPending()
            subtitleView.setCues(cues)
        } else {
            schedule(cues)
        }
    }

    private fun schedule(cues: List<Cue>) {
        clearPending()
        if (delayMs == 0L) {
            subtitleView.setCues(cues)
            return
        }
        val runnable = Runnable {
            pending = null
            subtitleView.setCues(cues)
        }
        pending = runnable to cues
        handler.postDelayed(runnable, delayMs)
    }

    private fun clearPending() {
        pending?.let { (runnable, _) -> handler.removeCallbacks(runnable) }
        pending = null
    }
}
