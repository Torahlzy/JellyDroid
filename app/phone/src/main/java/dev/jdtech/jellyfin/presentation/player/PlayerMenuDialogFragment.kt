package dev.jdtech.jellyfin.presentation.player

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.C
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.player.local.R as PlayerR
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

/**
 * 播放器右上角菜单：横屏下用一行正方形格子展示字幕、播放速度、音轨等入口。
 *
 * 截图与字幕调节通过宿主接口分发；离线模式下隐藏需要修改服务器的入口。窗口用无边框透明主题，
 * 卡片与蒙层交给 Compose 绘制，才能去掉标题行、控制上下留白并横向铺开。
 */
class PlayerMenuDialogFragment : DialogFragment() {
    private val viewModel: PlayerViewModel by activityViewModels()

    /** 回调从恢复后的宿主获取，Fragment 的构造器不保存运行时对象。 */
    interface Host {
        val canModifyServer: Boolean
        fun captureBackdrop()
        fun adjustSubtitles()
        fun downloadSubtitles()
    }

    private val host: Host
        get() = requireActivity() as Host

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val items = buildMenuItems()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                FindroidTheme {
                    PlayerMenuContent(
                        items = items,
                        onDismiss = ::dismiss,
                        onSelect = ::onMenuItemSelected,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // 蒙层由 PlayerMenuContent 自己绘制，关掉系统 dim 免得叠成两层黑
            window.setDimAmount(0f)
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        hideSystemBars()
    }

    private fun buildMenuItems(): List<PlayerMenuItem> {
        // 与 TrackSelectionDialogFragment 同一口径：没有可支持的文本轨时，选轨与字幕设置
        // 都无从下手，置灰但仍占位，菜单布局不因视频有无字幕而跳动
        val hasSubtitleTracks =
            viewModel.player.currentTracks.groups.any {
                it.type == C.TRACK_TYPE_TEXT && it.isSupported
            }
        return listOfNotNull(
            PlayerMenuItem(
                labelRes = PlayerR.string.select_subtitle_track,
                iconRes = CoreR.drawable.ic_closed_caption,
                enabled = hasSubtitleTracks,
                onClick = { showTrackSelection(C.TRACK_TYPE_TEXT) },
            ),
            run {
                val action = host::adjustSubtitles
                PlayerMenuItem(
                    labelRes = PlayerR.string.player_menu_subtitle_adjust,
                    iconRes = CoreR.drawable.ic_settings,
                    enabled = hasSubtitleTracks,
                    onClick = action,
                )
            },
            if (host.canModifyServer) run {
                val action = host::downloadSubtitles
                PlayerMenuItem(
                    labelRes = PlayerR.string.player_menu_download_subtitle,
                    iconRes = CoreR.drawable.ic_download,
                    onClick = action,
                )
            } else null,
            PlayerMenuItem(
                labelRes = PlayerR.string.select_playback_speed,
                iconRes = CoreR.drawable.ic_gauge,
                onClick = ::showSpeedSelection,
            ),
            PlayerMenuItem(
                labelRes = PlayerR.string.select_audio_track,
                iconRes = CoreR.drawable.ic_speaker,
                onClick = { showTrackSelection(C.TRACK_TYPE_AUDIO) },
            ),
            if (host.canModifyServer) run {
                val action = host::captureBackdrop
                PlayerMenuItem(
                    labelRes = PlayerR.string.player_menu_capture_backdrop,
                    iconRes = CoreR.drawable.ic_film,
                    onClick = action,
                )
            } else null,
        )
    }

    private fun onMenuItemSelected(item: PlayerMenuItem) {
        if (!item.enabled) return
        dismiss()
        item.onClick()
    }

    private fun showTrackSelection(type: @C.TrackType Int) {
        TrackSelectionDialogFragment.newInstance(type)
            .show(requireActivity().supportFragmentManager, "trackselectiondialog")
    }

    private fun showSpeedSelection() {
        SpeedSelectionDialogFragment()
            .show(requireActivity().supportFragmentManager, "speedselectiondialog")
    }

    private fun hideSystemBars() {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // API < 30 在弹窗关闭后不会自动恢复沉浸模式，重新隐藏系统栏
        hideSystemBars()
    }
}
