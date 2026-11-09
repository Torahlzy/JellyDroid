package dev.jdtech.jellyfin.presentation.player.subtitle

import android.graphics.Color
import android.view.View
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.player.local.R as PlayerR
import dev.jdtech.jellyfin.player.local.mpv.MPVPlayer
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.models.SubtitleAppearance
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 字幕调节模式的状态与编排：字号、字体透明度、位置、进度偏移的实时预览与持久化。
 *
 * 职责：
 * - 播放期间全程挂载（[attach]）：加载已保存的设置，计算默认位置（黑边够高时整条字幕落进
 *   黑边、顶边贴视频底边；不够高时一半盖画面一半盖黑边，底边至少 15dp），
 *   折叠屏折叠 / 展开等布局变化时随视频尺寸重算。
 * - 进入调节模式后，各 [SubtitleAdjustState] 字段即"预览值"，改动实时应用到当前后端；
 *   「应用」才落盘，「关闭」从偏好重新加载丢弃预览，「重置」恢复全部预览默认值（含进度偏移）。
 * - 进度偏移按条目 id 实时保存，不走"应用"按钮。
 *
 * 记录策略：字体透明度全局一份；字号倍率与自定义位置按屏幕高度（px）分开记录，
 * 未记录的尺寸用默认算法 / 默认位置规则。
 */
class SubtitleAdjustManager(
    private val playerView: PlayerView,
    private val player: Player,
    private val appPreferences: AppPreferences,
) : Player.Listener {

    companion object {
        /** 放大 / 缩小按钮的步进与范围（相对默认字号的百分比）。 */
        private const val SCALE_STEP_PERCENT = 5
        private const val MIN_SCALE_PERCENT = 50
        private const val MAX_SCALE_PERCENT = 200

        /** 默认字幕底边距屏幕底部的最小距离（dp），黑边不足时兜底。 */
        private const val MIN_BOTTOM_OFFSET_DP = 15

        /** 字幕进度偏移档位（ms），加减方向对称。 */
        val OFFSET_STEPS_MS = longArrayOf(300L, 1_000L, 5_000L, 20_000L)

        /** media3 SubtitleView 的默认底部留白比例，视口尺寸未知时的兜底。 */
        private const val DEFAULT_BOTTOM_PADDING_FRACTION = 0.08f

        /** 字幕与视频底边之间的间隙（dp），黑边足够时字幕顶边贴着视频底边往下放。 */
        private const val SUBTITLE_VIDEO_GAP_DP = 4

        /** 单行字幕估高的行距系数（默认字号 × 倍率 × 该系数 ≈ 一行字幕的渲染高度）。 */
        private const val LINE_HEIGHT_FACTOR = 1.6f
    }

    data class SubtitleAdjustState(
        val isAdjustMode: Boolean = false,
        /** 字号倍率，以百分比表示（100 = 默认算法）。 */
        val scalePercent: Int = 100,
        /** 字体不透明度百分比（0–100）。 */
        val textOpacityPercent: Int = SubtitleAppearance.DEFAULT_OPACITY_PERCENT,
        /** 字幕底边距屏幕底部的距离（px），自定义或默认规则计算值。 */
        val bottomOffsetPx: Int = 0,
        /** 当前视口高度（px），供覆盖层摆放拖动手柄。 */
        val viewHeightPx: Int = 0,
        /** 字幕进度偏移（秒），正数延后、负数提前。 */
        val offsetSeconds: Double = 0.0,
        /** 当前位置是否为用户自定义（决定「应用」时落盘还是清除记录）。 */
        val isUsingCustomPosition: Boolean = false,
    )

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<SubtitleAdjustState> = _state.asStateFlow()

    private val layoutListener =
        object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View?,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int,
            ) {
                onViewportChanged()
            }
        }

    /** ExoPlayer 不支持字幕提前（负偏移），提示一次即可，避免反复弹。 */
    private var hasWarnedNegativeOffsetOnExo = false

    /**
     * 自托管字幕容器：布局里刻意不放 `exo_subtitles`（PlayerView 按该 id 硬转型 SubtitleView），
     * PlayerView 的 subtitleView 为 null 时会安全跳过内部渲染，Cue 派发由本类接管。
     */
    private val delayedSubtitleView: DelayedSubtitleView?
        get() = playerView.findViewById(R.id.delayed_subtitle_view)

    /** 实际渲染字幕的内层视图，样式（颜色 / 字号 / 底部留白）都设在它身上。 */
    private val subtitleView: SubtitleView?
        get() = delayedSubtitleView?.subtitleView

    /** 播放器全程挂载：加载外观、计算默认位置、跟随条目切换恢复偏移。 */
    fun attach() {
        // 关掉字幕内嵌样式（统一外观）；media3 里该开关只清样式与字号，内嵌定位天然保留
        subtitleView?.setApplyEmbeddedStyles(false)
        player.addListener(this)
        playerView.addOnLayoutChangeListener(layoutListener)
        onViewportChanged()
        applyAppearance()
        applyPosition()
        applyOffsetForCurrentItem()
    }

    fun detach() {
        player.removeListener(this)
        playerView.removeOnLayoutChangeListener(layoutListener)
    }

    /**
     * 隐藏 / 恢复真实字幕轨的渲染。
     *
     * 播放页的字幕对照面板打开时要关掉它：面板已经铺了 5 行，底部再来一行真实字幕会堆成 6 行。
     * 两条后端走各自的开关：ExoPlayer 侧在 [DelayedSubtitleView] 里抑制转发，mpv 侧写 `sub-visibility`。
     */
    fun setRealSubtitleVisible(visible: Boolean) {
        AppLog.d(
            "真实字幕轨渲染：%s（exo 视图 %s，后端 %s）",
            if (visible) "恢复" else "隐藏",
            if (delayedSubtitleView != null) "有" else "无",
            if (player is MPVPlayer) "mpv" else "exoplayer",
        )
        delayedSubtitleView?.isSuppressed = !visible
        (player as? MPVPlayer)?.setSubtitleVisible(visible)
    }

    // 进入 / 退出调节模式 -----------------------------------------------------------------------

    fun enterAdjustMode() {
        _state.update { it.copy(isAdjustMode = true, viewHeightPx = playerView.height) }
    }

    /** 「应用」：把预览值持久化并退出调节模式。 */
    fun apply() {
        val current = _state.value
        val screenHeight = screenHeightPx()
        val scale = current.scalePercent / 100f

        appPreferences.setSubtitleAppearance(SubtitleAppearance(textOpacityPercent = current.textOpacityPercent))
        appPreferences.setSubtitleScaleForScreen(screenHeight, scale)
        appPreferences.setSubtitleBottomOffsetForScreen(
            screenHeight,
            if (current.isUsingCustomPosition) current.bottomOffsetPx else null,
        )

        _state.update { it.copy(isAdjustMode = false) }

        if (appPreferences.getValue(appPreferences.playerBackend) == "mpv") {
            Toast.makeText(playerView.context, PlayerR.string.subtitle_adjust_mpv_warning, Toast.LENGTH_LONG)
                .show()
        }
    }

    /** 「关闭」= 取消：丢弃预览，从偏好重新加载已保存的值并退出调节模式。 */
    fun close() {
        _state.update { it.copy(isAdjustMode = false) }
        reloadSavedAppearance()
        applyAppearance()
        applyPosition()
        applyOffsetForCurrentItem()
    }

    /** 「重置」：恢复全部预览默认值（字号 / 透明度 / 位置），并把进度偏移清零落盘。 */
    fun reset() {
        _state.update {
            it.copy(
                scalePercent = 100,
                textOpacityPercent = SubtitleAppearance.DEFAULT_OPACITY_PERCENT,
                bottomOffsetPx = computeDefaultBottomOffsetPx(),
                isUsingCustomPosition = false,
                offsetSeconds = 0.0,
            )
        }
        applyAppearance()
        applyPosition()
        applyOffset()
        currentItemId()?.let { appPreferences.setSubtitleOffset(it, 0L) }
    }

    // 面板动作 ----------------------------------------------------------------------------------

    fun onScaleStepClicked(increase: Boolean) {
        val delta = if (increase) SCALE_STEP_PERCENT else -SCALE_STEP_PERCENT
        _state.update { it.copy(scalePercent = (it.scalePercent + delta).coerceIn(MIN_SCALE_PERCENT, MAX_SCALE_PERCENT)) }
        applyAppearance()
        if (!_state.value.isUsingCustomPosition) {
            // 字号变了字幕占高也变，非自定义位置时默认位置跟随重算
            _state.update { it.copy(bottomOffsetPx = computeDefaultBottomOffsetPx()) }
            applyPosition()
        }
    }

    fun onTextOpacityChanged(percent: Int) {
        _state.update { it.copy(textOpacityPercent = percent.coerceIn(0, 100)) }
        applyAppearance()
    }

    /** 拖动手柄：以字幕底边的目标 y（距屏幕顶部 px）换算成底边距并实时应用。 */
    fun onBottomOffsetDragged(newOffsetPx: Int) {
        val clamped = newOffsetPx.coerceIn(0, maxOf(0, playerView.height))
        _state.update { it.copy(bottomOffsetPx = clamped, isUsingCustomPosition = true) }
        applyPosition()
    }

    /** 档位按钮：加减偏移（ms）。 */
    fun onOffsetStepClicked(deltaMs: Long) {
        setOffset((_state.value.offsetSeconds * 1000).roundToLong() + deltaMs)
    }

    /** 输入框：直接输入偏移（秒，支持小数与负数）。 */
    fun onOffsetInputChanged(seconds: Double) {
        setOffset((seconds * 1000).roundToLong())
    }

    private fun setOffset(offsetMs: Long) {
        _state.update { it.copy(offsetSeconds = offsetMs / 1000.0) }
        applyOffset()
        // 偏移按条目 id 实时保存，不依赖「应用」按钮
        currentItemId()?.let { appPreferences.setSubtitleOffset(it, offsetMs) }
        maybeWarnNegativeOffsetOnExo(offsetMs)
    }

    private fun maybeWarnNegativeOffsetOnExo(offsetMs: Long) {
        if (offsetMs < 0 && player !is MPVPlayer && !hasWarnedNegativeOffsetOnExo) {
            hasWarnedNegativeOffsetOnExo = true
            Toast.makeText(
                    playerView.context,
                    PlayerR.string.subtitle_adjust_exo_negative_unsupported,
                    Toast.LENGTH_LONG,
                )
                .show()
        }
    }

    // Player.Listener ---------------------------------------------------------------------------

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        applyOffsetForCurrentItem()
    }

    /** PlayerView 不再渲染字幕（没有 exo_subtitles），整组 Cue 由这里转发给延迟容器。 */
    override fun onCues(cueGroup: CueGroup) {
        delayedSubtitleView?.setCues(cueGroup.cues)
    }

    /** 视频尺寸变化（换片、旋转）会影响黑边高度，非自定义位置时重算默认位置。 */
    override fun onVideoSizeChanged(videoSize: VideoSize) {
        onViewportChanged()
    }

    /**
     * 播放就绪时重算一次默认位置。mpv 后端不派发 onVideoSizeChanged（getVideoSize 是实时查询），
     * 只有这一条回调能保证视频参数就绪后默认位置生效。
     */
    override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            onViewportChanged()
        }
    }

    // 内部实现 ----------------------------------------------------------------------------------

    private fun onViewportChanged() {
        // 折叠屏折叠 / 展开、视频尺寸变化都会走到这里；非自定义位置时跟随默认规则
        val defaultOffset = computeDefaultBottomOffsetPx()
        _state.update {
            it.copy(
                viewHeightPx = playerView.height,
                bottomOffsetPx = if (it.isUsingCustomPosition) it.bottomOffsetPx else defaultOffset,
            )
        }
        if (!_state.value.isUsingCustomPosition) {
            applyPosition()
        }
    }

    /**
     * 默认字幕底边距（fit 缩放、视频比视口"宽"时上下留黑边）：
     * - 黑边放得下一行字幕：整条字幕落进黑边，顶边贴着视频底边往下放（完全不遮挡画面）；
     * - 黑边放不下：字幕一半盖画面一半盖黑边（中心对齐视频底边），底边至少 15dp；
     * - 没有视频参数时兜底 15dp。
     */
    private fun computeDefaultBottomOffsetPx(): Int {
        val minOffset = dpToPx(MIN_BOTTOM_OFFSET_DP)
        val viewHeight = playerView.height
        val viewWidth = playerView.width
        if (viewHeight <= 0 || viewWidth <= 0) {
            return minOffset
        }
        val videoSize = player.videoSize
        if (videoSize.width <= 0 || videoSize.height <= 0) {
            return minOffset
        }
        val ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
        if (ratio <= 0f) {
            return minOffset
        }
        // 90/270 度旋转视频的显示宽高比取倒数（与截图设封面的 displayAspectRatio 同一套折算）
        val displayRatio =
            if (videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270) {
                1f / ratio
            } else {
                ratio
            }
        val displayedVideoHeight =
            if (displayRatio > viewWidth.toFloat() / viewHeight) {
                viewWidth / displayRatio
            } else {
                viewHeight.toFloat()
            }
        val blackBarHeight = ((viewHeight - displayedVideoHeight) / 2f).roundToInt().coerceAtLeast(0)
        val textHeight = estimateSubtitleTextHeightPx()
        val gap = dpToPx(SUBTITLE_VIDEO_GAP_DP)
        return if (blackBarHeight >= textHeight + gap) {
            (blackBarHeight - textHeight - gap).coerceAtLeast(0)
        } else {
            maxOf(blackBarHeight - textHeight / 2, minOffset).coerceAtLeast(0)
        }
    }

    /** 单行字幕渲染高度估算：默认字号比例 × 字号倍率 × 行距系数（含描边余量）。供设置卡定位用。 */
    fun estimateSubtitleTextHeightPx(): Int {
        val viewHeight = playerView.height
        if (viewHeight <= 0) {
            return 0
        }
        val scale = _state.value.scalePercent / 100f
        return (viewHeight * SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale * LINE_HEIGHT_FACTOR)
            .roundToInt()
    }

    private fun loadInitialState(): SubtitleAdjustState {
        val appearance = appPreferences.getSubtitleAppearance()
        val screenHeight = playerView.resources.displayMetrics.heightPixels
        val savedScale = appPreferences.getSubtitleScaleForScreen(screenHeight) ?: 1f
        val savedPosition = appPreferences.getSubtitleBottomOffsetForScreen(screenHeight)
        return SubtitleAdjustState(
            scalePercent = (savedScale * 100).roundToInt(),
            textOpacityPercent = appearance.textOpacityPercent,
            bottomOffsetPx = savedPosition ?: 0,
            isUsingCustomPosition = savedPosition != null,
        )
    }

    /** 「关闭」时重新读取已保存的值（丢弃预览）。 */
    private fun reloadSavedAppearance() {
        val saved = loadInitialState()
        _state.update {
            it.copy(
                scalePercent = saved.scalePercent,
                textOpacityPercent = saved.textOpacityPercent,
                bottomOffsetPx = saved.bottomOffsetPx,
                isUsingCustomPosition = saved.isUsingCustomPosition,
            )
        }
    }

    private fun applyAppearance() {
        val current = _state.value
        val scale = current.scalePercent / 100f
        subtitleView?.apply {
            setStyle(captionStyle(current.textOpacityPercent))
            setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale)
        }
        (player as? MPVPlayer)?.let { mpv ->
            mpv.setSubtitleScale(scale)
            mpv.setSubtitleOpacity(current.textOpacityPercent)
        }
    }

    private fun applyPosition() {
        val current = _state.value
        val viewHeight = playerView.height
        subtitleView?.setBottomPaddingFraction(
            if (viewHeight > 0) current.bottomOffsetPx.toFloat() / viewHeight
            else DEFAULT_BOTTOM_PADDING_FRACTION
        )
        (player as? MPVPlayer)?.setSubtitleBottomOffset(current.bottomOffsetPx, viewHeight)
    }

    private fun applyOffset() {
        val delayMs = (_state.value.offsetSeconds * 1000).roundToLong()
        delayedSubtitleView?.delayMs = delayMs
        (player as? MPVPlayer)?.setSubtitleDelay(delayMs / 1000.0)
    }

    /** 按当前条目恢复已保存的偏移（条目切换与进入播放器时调用）。 */
    private fun applyOffsetForCurrentItem() {
        val itemId = currentItemId() ?: return
        val offsetMs = appPreferences.getSubtitleOffset(itemId)
        _state.update { it.copy(offsetSeconds = offsetMs / 1000.0) }
        applyOffset()
    }

    private fun currentItemId(): String? =
        player.currentMediaItem?.mediaId?.takeIf { id ->
            id.isNotEmpty() && runCatching { UUID.fromString(id) }.isSuccess
        }

    /**
     * 白字与黑色包边共用同一个 alpha；背景与窗口色保持透明。
     *
     * media3 的描边单独取 edgeColor，若用不透明黑，字体淡出后黑色包边仍是实心。
     */
    private fun captionStyle(textOpacityPercent: Int): CaptionStyleCompat {
        val alpha = textOpacityPercent.coerceIn(0, 100) * 0xFF / 100
        return CaptionStyleCompat(
            /* foregroundColor = */ (alpha shl 24) or 0x00FFFFFF,
            /* backgroundColor = */ Color.TRANSPARENT,
            /* windowColor = */ Color.TRANSPARENT,
            /* edgeType = */ CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            /* edgeColor = */ Color.argb(alpha, 0, 0, 0),
            /* typeface = */ null,
        )
    }

    private fun screenHeightPx(): Int = playerView.resources.displayMetrics.heightPixels

    private fun dpToPx(dp: Int): Int =
        (dp * playerView.resources.displayMetrics.density).roundToInt()
}
