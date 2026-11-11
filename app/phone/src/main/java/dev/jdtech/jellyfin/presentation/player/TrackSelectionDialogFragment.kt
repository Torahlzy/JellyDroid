package dev.jdtech.jellyfin.presentation.player

import android.app.Dialog
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.C
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.player.local.R
import dev.jdtech.jellyfin.player.local.domain.getTrackNames
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import java.lang.IllegalStateException

class TrackSelectionDialogFragment : DialogFragment() {
    private val viewModel: PlayerViewModel by activityViewModels()
    private val type: Int
        get() = requireArguments().getInt(TRACK_TYPE)

    companion object {
        private const val TRACK_TYPE = "trackType"

        fun newInstance(type: @C.TrackType Int) = TrackSelectionDialogFragment().apply {
            arguments = Bundle().apply { putInt(TRACK_TYPE, type) }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val titleResource =
            when (type) {
                C.TRACK_TYPE_AUDIO -> R.string.select_audio_track
                C.TRACK_TYPE_TEXT -> R.string.select_subtitle_track
                else -> throw IllegalStateException("TrackType must be AUDIO or TEXT")
            }
        val allGroups = viewModel.player.currentTracks.groups.filter { it.type == type }
        val tracksGroups = allGroups.filter { it.isSupported }
        AppLog.tag("SubSideload").d(
            "打开轨道弹窗：type=%d，全部组=%d，可用组=%d，名称=%s，选中项=%d",
            type,
            allGroups.size,
            tracksGroups.size,
            tracksGroups.getTrackNames().joinToString(" | "),
            tracksGroups.indexOfFirst { it.isSelected } + 1,
        )
        return activity?.let { activity ->
            val builder = MaterialAlertDialogBuilder(activity)
            builder.setTitle(getString(titleResource)).setSingleChoiceItems(
                arrayOf(getString(R.string.none)) +
                    tracksGroups.getTrackNames(), // 顶部保留关闭轨道选项
                tracksGroups.indexOfFirst { it.isSelected } +
                    1, // 关闭选项占用第 0 项
            ) { dialog, which ->
                viewModel.switchToTrack(
                    type,
                    which - 1, // -1 表示关闭轨道
                )
                dialog.dismiss()
            }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onDestroy() {
        super.onDestroy()
        // API 30 以下关闭弹窗后需要重新隐藏系统栏
        activity?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
