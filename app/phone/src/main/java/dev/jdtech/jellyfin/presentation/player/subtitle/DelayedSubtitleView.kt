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
            clearPending()
            schedule(latestCues)
        }

    private val pending = mutableSetOf<Runnable>()
    private var latestCues: List<Cue> = emptyList()

    /**
     * 抑制渲染的开关：为 true 时收到的 Cue 一律丢弃并清空画面。
     *
     * 播放页的字幕对照面板打开时置 true —— 面板本身已经铺了 5 行，
     * 底部再来一行真实字幕会在画面上堆成 6 行。退出面板时置回 false。
     */
    var isSuppressed: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (value) {
                clearPending()
                subtitleView.setCues(emptyList())
            }
        }

    /** 由 SubtitleAdjustManager 在收到 onCues 时调用，替代 PlayerView 的内部转发。 */
    fun setCues(cues: List<Cue>) {
        latestCues = cues.toList()
        if (isSuppressed) return
        if (delayMs == 0L) {
            clearPending()
            subtitleView.setCues(cues)
        } else {
            schedule(cues)
        }
    }

    private fun schedule(cues: List<Cue>) {
        if (isSuppressed) return
        if (delayMs == 0L) {
            subtitleView.setCues(cues)
            return
        }
        lateinit var runnable: Runnable
        runnable = Runnable {
            pending.remove(runnable)
            subtitleView.setCues(cues)
        }
        pending.add(runnable)
        handler.postDelayed(runnable, delayMs)
    }

    /** seek、切集和离开页面时丢弃旧时间轴上的所有延迟任务。 */
    fun clear() {
        clearPending()
        latestCues = emptyList()
        subtitleView.setCues(emptyList())
    }

    override fun onDetachedFromWindow() {
        clear()
        super.onDetachedFromWindow()
    }

    private fun clearPending() {
        pending.forEach(handler::removeCallbacks)
        pending.clear()
    }
}
