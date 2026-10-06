package dev.jdtech.jellyfin.presentation.player

import android.app.Dialog
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.media3.common.C
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.jdtech.jellyfin.player.local.R
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import java.lang.IllegalStateException

/**
 * 播放器右上角菜单，把字幕、播放速度、音轨收进一个弹窗，并额外提供「截图设为横屏封面」。
 *
 * 截图依赖 Activity 持有的视频视图，所以通过构造参数把动作交回宿主处理；
 * [onCaptureBackdrop] 为 null 表示当前环境不提供该入口（离线模式下无法写入服务器）。
 */
class PlayerMenuDialogFragment(
    private val viewModel: PlayerViewModel,
    private val onCaptureBackdrop: (() -> Unit)?,
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val menuItems =
            listOfNotNull(
                R.string.select_subtitle_track to { showTrackSelection(C.TRACK_TYPE_TEXT) },
                R.string.select_playback_speed to { showSpeedSelection() },
                R.string.select_audio_track to { showTrackSelection(C.TRACK_TYPE_AUDIO) },
                onCaptureBackdrop?.let { R.string.player_menu_capture_backdrop to it },
            )
        return activity?.let { activity ->
            MaterialAlertDialogBuilder(activity)
                .setTitle(getString(R.string.player_controls_menu))
                .setItems(menuItems.map { getString(it.first) }.toTypedArray()) { dialog, which ->
                    dialog.dismiss()
                    menuItems[which].second()
                }
                .create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private fun showTrackSelection(type: @C.TrackType Int) {
        TrackSelectionDialogFragment(type, viewModel)
            .show(requireActivity().supportFragmentManager, "trackselectiondialog")
    }

    private fun showSpeedSelection() {
        SpeedSelectionDialogFragment(viewModel)
            .show(requireActivity().supportFragmentManager, "speedselectiondialog")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Fix for hiding the system bars on API < 30
        activity?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
